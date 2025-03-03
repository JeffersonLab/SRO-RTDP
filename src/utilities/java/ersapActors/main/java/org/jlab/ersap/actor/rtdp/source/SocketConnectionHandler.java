package org.jlab.ersap.actor.rtdp.source;

import com.lmax.disruptor.RingBuffer;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A specific implementation of AbstractConnectionHandler for managing
 * socket connections and publishing data to a Disruptor RingBuffer.
 */
public class SocketConnectionHandler extends AbstractConnectionHandler {

    private static final Logger LOGGER = Logger.getLogger(SocketConnectionHandler.class.getName());

    private final String host;
    private final int port;
    private final ByteOrder byteOrder;
    private final int connectionTimeout; // Connection timeout in milliseconds.
    private final int readTimeout;      // Read timeout in milliseconds.
    private final int maxRetries;       // Maximum number of connection retries
    private final int retryDelayMs;     // Delay between retries in milliseconds
    private final AtomicBoolean isRunning; // Flag to control the listener thread

    /**
     * Constructor for SocketConnectionHandler.
     *
     * @param disruptorRingBuffer the RingBuffer instance for managing events.
     * @param host                the hostname or IP address of the socket server.
     * @param port                the port number of the socket server.
     * @param sourceId            the unique identifier for this source
     * @param byteOrder           the ByteOrder used for reading data.
     * @param connectionTimeout   the timeout for establishing the connection in milliseconds.
     * @param readTimeout         the timeout for reading data in milliseconds.
     */
    public SocketConnectionHandler(
            RingBuffer<Event> disruptorRingBuffer,
            String host,
            int port,
            int sourceId,
            ByteOrder byteOrder,
            int connectionTimeout,
            int readTimeout) {
        super(disruptorRingBuffer, sourceId);
        this.host = host;
        this.port = port;
        this.byteOrder = byteOrder;
        this.connectionTimeout = connectionTimeout;
        this.readTimeout = readTimeout;
        this.maxRetries = 5;
        this.retryDelayMs = 1000;
        this.isRunning = new AtomicBoolean(true);
    }

    @Override
    public Socket establishConnection() {
        int retries = 0;
        Socket socket = null;
        
        while (isRunning.get() && retries < maxRetries) {
            try {
                socket = new Socket();
                socket.connect(new java.net.InetSocketAddress(host, port), connectionTimeout);
                socket.setSoTimeout(readTimeout); // Set timeout for reading data.
                LOGGER.info(String.format("Socket connection established to %s:%d (Source ID: %d)", 
                        host, port, sourceId));
                return socket;
            } catch (UnknownHostException e) {
                LOGGER.log(Level.SEVERE, "Unknown host: " + host, e);
                break; // No point retrying if the host is unknown
            } catch (IOException e) {
                retries++;
                if (retries >= maxRetries) {
                    LOGGER.log(Level.SEVERE, String.format(
                            "Failed to establish connection to %s:%d after %d attempts: %s", 
                            host, port, retries, e.getMessage()));
                } else {
                    LOGGER.log(Level.WARNING, String.format(
                            "Connection attempt %d/%d to %s:%d failed: %s. Retrying in %d ms...", 
                            retries, maxRetries, host, port, e.getMessage(), retryDelayMs));
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        LOGGER.log(Level.WARNING, "Connection retry interrupted", ie);
                        break;
                    }
                }
            }
        }
        
        if (socket == null) {
            throw new IllegalStateException(String.format(
                    "Unable to establish connection to %s:%d after %d attempts", 
                    host, port, retries));
        }
        
        return socket;
    }

    @Override
    public void listenAndPublish(Object connection) {
        if (!(connection instanceof Socket)) {
            throw new IllegalArgumentException("Connection must be a Socket instance.");
        }

        Socket socket = (Socket) connection;

        Thread listenerThread = new Thread(() -> {
            try {
                while (isRunning.get() && !socket.isClosed() && !Thread.currentThread().isInterrupted()) {
                    byte[] data = receiveData(socket);
                    if (data != null) {
                        publishEvent(data);
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, String.format(
                        "Error in listener thread for %s:%d (Source ID: %d): %s", 
                        host, port, sourceId, e.getMessage()), e);
            } finally {
                closeConnection(socket);
            }
        });
        
        listenerThread.setName("SocketListener-" + host + ":" + port + "-" + sourceId);
        listenerThread.setDaemon(true);
        listenerThread.start();
        
        LOGGER.info(String.format("Started listener thread for %s:%d (Source ID: %d)", 
                host, port, sourceId));
    }

    @Override
    public ByteOrder getByteOrder() {
        return byteOrder;
    }

    @Override
    public void closeConnection(Object connection) {
        isRunning.set(false); // Signal the listener thread to stop
        
        if (connection instanceof Socket) {
            try {
                Socket socket = (Socket) connection;
                if (!socket.isClosed()) {
                    socket.close();
                    LOGGER.info(String.format("Socket connection closed for %s:%d (Source ID: %d)", 
                            host, port, sourceId));
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, String.format(
                        "Failed to close socket for %s:%d (Source ID: %d): %s", 
                        host, port, sourceId, e.getMessage()), e);
            }
        } else if (connection != null) {
            LOGGER.warning(String.format(
                    "Invalid connection object for %s:%d (Source ID: %d). Expected a Socket.", 
                    host, port, sourceId));
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
            
            // First read the length of the data (assuming 4-byte integer header)
            int dataLength = inputStream.readInt();
            
            if (dataLength <= 0 || dataLength > 1024 * 1024) { // Sanity check: max 1MB packet
                LOGGER.warning(String.format(
                        "Invalid data length received from %s:%d (Source ID: %d): %d bytes", 
                        host, port, sourceId, dataLength));
                return null;
            }
            
            // Now read the actual data
            byte[] data = new byte[dataLength];
            int bytesRead = 0;
            int totalBytesRead = 0;
            
            while (totalBytesRead < dataLength) {
                bytesRead = inputStream.read(data, totalBytesRead, dataLength - totalBytesRead);
                if (bytesRead == -1) {
                    LOGGER.warning(String.format(
                            "End of stream reached while reading from %s:%d (Source ID: %d)", 
                            host, port, sourceId));
                    return null;
                }
                totalBytesRead += bytesRead;
            }
            
            return data;
        } catch (SocketTimeoutException e) {
            // This is normal when no data is available, so we'll just return null
            return null;
        } catch (SocketException e) {
            if (isRunning.get()) {
                LOGGER.log(Level.WARNING, String.format(
                        "Socket error while reading from %s:%d (Source ID: %d): %s", 
                        host, port, sourceId, e.getMessage()), e);
            }
            return null;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, String.format(
                    "I/O error while reading from %s:%d (Source ID: %d): %s", 
                    host, port, sourceId, e.getMessage()), e);
            return null;
        }
    }
} 