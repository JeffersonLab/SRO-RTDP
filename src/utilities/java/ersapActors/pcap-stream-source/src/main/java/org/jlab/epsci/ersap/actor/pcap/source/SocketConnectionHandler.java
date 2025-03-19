package org.jlab.epsci.ersap.actor.pcap.source;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
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
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 3;
    private static final int RECONNECT_DELAY_MS = 1000;

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
        running.set(true);
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
            reconnectAttempts = 0;
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

            while (running.get()) {
                if (!connected.get() || socket == null || socket.isClosed()) {
                    // Try to reconnect if not connected
                    if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                        reconnectAttempts++;
                        LOGGER.info("Attempting to reconnect (" + reconnectAttempts + "/" + 
                                   MAX_RECONNECT_ATTEMPTS + ") to " + host + ":" + port);
                        try {
                            close();
                            Thread.sleep(RECONNECT_DELAY_MS);
                            open();
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING, "Reconnect attempt failed", e);
                            Thread.sleep(RECONNECT_DELAY_MS);
                            continue;
                        }
                    } else {
                        LOGGER.warning("Max reconnect attempts reached for " + host + ":" + port);
                        break;
                    }
                }

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
                    
                    // Reset reconnect attempts on successful read
                    reconnectAttempts = 0;

                } catch (SocketTimeoutException e) {
                    // This is normal for read timeouts, just continue
                    LOGGER.fine("Socket read timeout");
                } catch (SocketException e) {
                    if (running.get()) {
                        LOGGER.log(Level.WARNING, "Socket error: " + e.getMessage(), e);
                        connected.set(false);
                    } else {
                        break;
                    }
                } catch (IOException e) {
                    if (running.get()) {
                        LOGGER.log(Level.WARNING, "Error reading from socket: " + e.getMessage(), e);
                        connected.set(false);
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
            LOGGER.info("Connection handler for " + host + ":" + port + " stopped");
        }
    }
    
    /**
     * Checks if the socket is actually connected and valid.
     * 
     * @return true if the socket is connected and valid, false otherwise
     */
    @Override
    public boolean isConnected() {
        return connected.get() && socket != null && !socket.isClosed() && socket.isConnected();
    }
}