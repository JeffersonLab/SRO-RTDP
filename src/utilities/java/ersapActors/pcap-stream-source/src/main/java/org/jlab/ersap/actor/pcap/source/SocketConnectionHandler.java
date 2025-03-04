package org.jlab.ersap.actor.pcap.source;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.lmax.disruptor.RingBuffer;

/**
 * Handles socket connections and publishes received data to the ring buffer.
 */
public class SocketConnectionHandler extends AbstractConnectionHandler {

    private static final Logger LOGGER = Logger.getLogger(SocketConnectionHandler.class.getName());

    private final String host;
    private final int port;
    private final int connectionTimeout;
    private final int readTimeout;

    private Socket socket;
    private DataInputStream inputStream;

    /**
     * Constructor.
     * 
     * @param host                the host to connect to
     * @param port                the port to connect to
     * @param connectionTimeout   the connection timeout in milliseconds
     * @param readTimeout         the read timeout in milliseconds
     * @param disruptorRingBuffer the ring buffer to publish events to
     */
    public SocketConnectionHandler(
            String host,
            int port,
            int connectionTimeout,
            int readTimeout,
            RingBuffer<Event> disruptorRingBuffer) {
        super(disruptorRingBuffer);
        this.host = host;
        this.port = port;
        this.connectionTimeout = connectionTimeout;
        this.readTimeout = readTimeout;
    }

    @Override
    public void open() throws IOException {
        if (connected.get()) {
            return;
        }

        try {
            LOGGER.info("Connecting to " + host + ":" + port);
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), connectionTimeout);
            socket.setSoTimeout(readTimeout);
            inputStream = new DataInputStream(socket.getInputStream());
            connected.set(true);
            LOGGER.info("Connected to " + host + ":" + port);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error connecting to " + host + ":" + port, e);
            close();
            throw e;
        }
    }

    @Override
    public void close() throws IOException {
        connected.set(false);

        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error closing input stream", e);
            }
            inputStream = null;
        }

        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error closing socket", e);
            }
            socket = null;
        }
    }

    @Override
    public void run() {
        try {
            open();

            byte[] buffer = new byte[65536]; // 64KB buffer

            while (running.get() && connected.get()) {
                try {
                    // Read packet length (4 bytes)
                    int length = inputStream.readInt();

                    if (length <= 0 || length > buffer.length) {
                        LOGGER.warning("Invalid packet length: " + length);
                        continue;
                    }

                    // Read packet data
                    inputStream.readFully(buffer, 0, length);

                    // Publish to ring buffer
                    publishEvent(buffer, length);

                } catch (IOException e) {
                    if (running.get()) {
                        LOGGER.log(Level.WARNING, "Error reading from socket", e);

                        // Try to reconnect
                        try {
                            close();
                            Thread.sleep(1000); // Wait 1 second before reconnecting
                            open();
                        } catch (Exception reconnectError) {
                            LOGGER.log(Level.SEVERE, "Error reconnecting", reconnectError);
                            break;
                        }
                    } else {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in connection handler", e);
        } finally {
            try {
                close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error closing connection", e);
            }
            running.set(false);
        }
    }
}