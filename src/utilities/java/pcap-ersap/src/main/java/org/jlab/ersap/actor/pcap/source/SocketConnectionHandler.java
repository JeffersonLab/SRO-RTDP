package org.jlab.ersap.actor.pcap.source;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.lmax.disruptor.RingBuffer;

/**
 * Socket-based implementation of the AbstractConnectionHandler.
 * This class handles connections to a socket-based data source
 * and publishes events to a ring buffer.
 */
public class SocketConnectionHandler extends AbstractConnectionHandler {

    private static final Logger LOGGER = Logger.getLogger(SocketConnectionHandler.class.getName());

    private final String host;
    private final int port;
    private final ByteOrder byteOrder;
    private final int connectionTimeout; // Connection timeout in milliseconds.
    private final int readTimeout; // Read timeout in milliseconds.
    private final int socketBufferSize; // Socket buffer size in bytes.

    /**
     * Constructor to initialize the socket connection handler.
     *
     * @param disruptorRingBuffer The ring buffer to use
     * @param host                The host name or IP address
     * @param port                The port number
     * @param byteOrder           The byte order
     * @param connectionTimeout   The connection timeout in milliseconds
     * @param readTimeout         The read timeout in milliseconds
     * @param socketBufferSize    The socket buffer size in bytes
     */
    public SocketConnectionHandler(
            RingBuffer<Event> disruptorRingBuffer,
            String host,
            int port,
            ByteOrder byteOrder,
            int connectionTimeout,
            int readTimeout,
            int socketBufferSize) {
        super(disruptorRingBuffer);
        this.host = host;
        this.port = port;
        this.byteOrder = byteOrder;
        this.connectionTimeout = connectionTimeout;
        this.readTimeout = readTimeout;
        this.socketBufferSize = socketBufferSize;
    }

    @Override
    public Socket establishConnection() {
        try {
            Socket socket = new Socket(host, port);
            socket.setSoTimeout(readTimeout);
            LOGGER.log(Level.INFO, "Connected to {0}:{1}", new Object[] { host, port });
            return socket;
        } catch (UnknownHostException e) {
            LOGGER.log(Level.SEVERE, "Unknown host: {0}", host);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not connect to {0}:{1}: {2}", new Object[] { host, port, e.getMessage() });
        }
        return null;
    }

    @Override
    public void listenAndPublish(Object connection) {
        if (!(connection instanceof Socket)) {
            throw new IllegalArgumentException("Connection must be a Socket instance.");
        }

        Socket socket = (Socket) connection;

        new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted() && !socket.isClosed()) {
                    byte[] data = receiveData(socket);
                    if (data != null) {
                        publishEvent(data);
                    }
                }
            } finally {
                closeConnection(socket);
            }
        }).start();
    }

    @Override
    public ByteOrder getByteOrder() {
        return byteOrder;
    }

    @Override
    public void closeConnection(Object connection) {
        if (!(connection instanceof Socket)) {
            throw new IllegalArgumentException("Connection must be a Socket instance.");
        }

        Socket socket = (Socket) connection;
        try {
            if (!socket.isClosed()) {
                socket.close();
                LOGGER.log(Level.INFO, "Closed connection to {0}:{1}", new Object[] { host, port });
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error closing socket: {0}", e.getMessage());
        }
    }

    @Override
    protected byte[] receiveData(Object connection) {
        if (!(connection instanceof Socket)) {
            throw new IllegalArgumentException("Connection must be a Socket instance.");
        }

        Socket socket = (Socket) connection;
        try {
            DataInputStream inputStream = new DataInputStream(socket.getInputStream());

            // Read the packet length first (4 bytes)
            int packetLength = inputStream.readInt();

            if (packetLength <= 0) {
                LOGGER.warning("Invalid packet length: " + packetLength);
                return null;
            }

            // Determine how much data to read
            int bytesToRead = Math.min(packetLength, socketBufferSize);

            // Create a buffer of the appropriate size
            byte[] buffer = new byte[bytesToRead];

            // Read the data
            int bytesRead = inputStream.read(buffer, 0, bytesToRead);

            if (bytesRead <= 0) {
                if (bytesRead == -1) {
                    LOGGER.warning("End of stream reached");
                } else {
                    LOGGER.warning("No data read from socket");
                }
                return null;
            }

            // Skip any remaining bytes if the packet is larger than our buffer
            if (packetLength > socketBufferSize) {
                LOGGER.log(Level.WARNING,
                        "Packet size ({0} bytes) exceeds buffer size ({1} bytes). Skipping {2} bytes.",
                        new Object[] { packetLength, socketBufferSize, packetLength - socketBufferSize });
                inputStream.skipBytes(packetLength - socketBufferSize);
            }

            // Create a new buffer with exactly the number of bytes read
            byte[] data = new byte[bytesRead];
            System.arraycopy(buffer, 0, data, 0, bytesRead);

            LOGGER.log(Level.FINE, "Received {0} bytes from {1}:{2}", new Object[] { bytesRead, host, port });
            return data;

        } catch (SocketException e) {
            LOGGER.log(Level.WARNING, "Socket error: {0}", e.getMessage());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "I/O error: {0}", e.getMessage());
        }

        return null;
    }

    /**
     * Publishes the received data to the RingBuffer.
     *
     * @param data The data to publish
     */
    private void publishEvent(byte[] data) {
        long sequence = super.disruptorRingBuffer.next(); // Reserve next slot
        try {
            Event event = super.disruptorRingBuffer.get(sequence); // Get entry for sequence
            event.setData(data); // Set data in event
        } finally {
            super.disruptorRingBuffer.publish(sequence); // Publish event
        }
    }
}