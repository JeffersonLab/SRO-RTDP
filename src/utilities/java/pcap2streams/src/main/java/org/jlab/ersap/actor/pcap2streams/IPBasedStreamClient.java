package org.jlab.ersap.actor.pcap2streams;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A client that connects to IP-based PCAP servers and receives packets.
 * This client can connect to multiple servers, each streaming packets for a
 * specific IP.
 */
public class IPBasedStreamClient {

    private static final Logger LOGGER = Logger.getLogger(IPBasedStreamClient.class.getName());
    private static final int DEFAULT_TIMEOUT = 5000; // 5 seconds
    private static final int MAX_PACKET_SIZE = 9000; // Maximum packet size (Jumbo frame size)
    private static final int MIN_PACKET_SIZE = 64; // Minimum packet size (Ethernet minimum)
    private static final int CHUNK_SIZE = 8192; // Size of chunks when reading data (8KB)

    private final String configFile;
    private final ConcurrentMap<String, ConnectionHandler> connections;
    private final AtomicBoolean running;

    /**
     * Creates a new IP-based stream client.
     * 
     * @param configFile the path to the configuration file
     */
    public IPBasedStreamClient(String configFile) {
        this.configFile = configFile;
        this.connections = new ConcurrentHashMap<>();
        this.running = new AtomicBoolean(false);
    }

    /**
     * Starts the client.
     * 
     * @throws IOException if an error occurs
     */
    public void start() throws IOException {
        if (running.compareAndSet(false, true)) {
            LOGGER.info("Starting client with configuration file: " + configFile);

            // Read configuration
            String jsonContent = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(configFile)));
            JSONObject config = new JSONObject(jsonContent);
            JSONArray connectionsArray = config.getJSONArray("connections");

            LOGGER.info("Found " + connectionsArray.length() + " connections in configuration");

            // Create and start connection handlers
            for (int i = 0; i < connectionsArray.length(); i++) {
                JSONObject connConfig = connectionsArray.getJSONObject(i);

                String ip = connConfig.getString("ip");
                String host = connConfig.getString("host");
                int port = connConfig.getInt("port");
                int connectionTimeout = connConfig.optInt("connection_timeout", DEFAULT_TIMEOUT);
                int readTimeout = connConfig.optInt("read_timeout", DEFAULT_TIMEOUT);

                ConnectionHandler handler = new ConnectionHandler(ip, host, port, connectionTimeout, readTimeout);
                connections.put(ip, handler);

                Thread handlerThread = new Thread(handler);
                handlerThread.setDaemon(true);
                handlerThread.start();

                LOGGER.info("Started connection handler for IP " + ip + " on " + host + ":" + port);
            }
        }
    }

    /**
     * Stops the client.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            LOGGER.info("Stopping client...");

            for (ConnectionHandler handler : connections.values()) {
                handler.stop();
            }

            connections.clear();
            LOGGER.info("Client stopped");
        }
    }

    /**
     * Gets the next packet from any connection.
     * 
     * @return the next packet as a byte array, or null if no packet is available
     */
    public byte[] getNextPacket() {
        // Try each connection in round-robin fashion
        for (ConnectionHandler handler : connections.values()) {
            byte[] packet = handler.getNextPacket();
            if (packet != null) {
                return packet;
            }
        }

        return null;
    }

    /**
     * Gets the next packet from a specific IP.
     * 
     * @param ip the IP address to get a packet from
     * @return the next packet as a byte array, or null if no packet is available
     */
    public byte[] getNextPacketForIP(String ip) {
        ConnectionHandler handler = connections.get(ip);
        if (handler != null) {
            return handler.getNextPacket();
        }

        return null;
    }

    /**
     * Gets the list of IP addresses this client is connected to.
     * 
     * @return a list of IP addresses
     */
    public List<String> getConnectedIPs() {
        return new ArrayList<>(connections.keySet());
    }

    /**
     * Gets the connection status for a specific IP.
     * 
     * @param ip the IP address to check
     * @return true if connected, false otherwise
     */
    public boolean isConnected(String ip) {
        ConnectionHandler handler = connections.get(ip);
        return handler != null && handler.isConnected();
    }

    /**
     * Gets the number of packets received for a specific IP.
     * 
     * @param ip the IP address to check
     * @return the number of packets received
     */
    public int getPacketCount(String ip) {
        ConnectionHandler handler = connections.get(ip);
        return handler != null ? handler.getPacketCount() : 0;
    }

    /**
     * Gets the total number of packets received across all connections.
     * 
     * @return the total number of packets received
     */
    public int getTotalPacketCount() {
        int total = 0;
        for (ConnectionHandler handler : connections.values()) {
            total += handler.getPacketCount();
        }
        return total;
    }

    /**
     * A handler for a single connection to an IP-based PCAP server.
     */
    private class ConnectionHandler implements Runnable {

        private final String ip;
        private final String host;
        private final int port;
        private final int connectionTimeout;
        private final int readTimeout;
        private final AtomicBoolean connected;
        private final AtomicBoolean running;
        private final List<byte[]> packetQueue;
        private Socket socket;
        private int packetCount;

        /**
         * Creates a new connection handler.
         * 
         * @param ip                the IP address this handler is for
         * @param host              the host to connect to
         * @param port              the port to connect to
         * @param connectionTimeout the connection timeout in milliseconds
         * @param readTimeout       the read timeout in milliseconds
         */
        public ConnectionHandler(String ip, String host, int port, int connectionTimeout, int readTimeout) {
            this.ip = ip;
            this.host = host;
            this.port = port;
            this.connectionTimeout = connectionTimeout;
            this.readTimeout = readTimeout;
            this.connected = new AtomicBoolean(false);
            this.running = new AtomicBoolean(true);
            this.packetQueue = new ArrayList<>();
            this.packetCount = 0;
        }

        /**
         * Stops the connection handler.
         */
        public void stop() {
            if (running.compareAndSet(true, false)) {
                try {
                    if (socket != null && !socket.isClosed()) {
                        socket.close();
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Error closing socket for IP " + ip, e);
                }

                connected.set(false);
                LOGGER.info("Connection handler for IP " + ip + " stopped");
            }
        }

        /**
         * Gets the next packet from this connection.
         * 
         * @return the next packet as a byte array, or null if no packet is available
         */
        public synchronized byte[] getNextPacket() {
            if (!packetQueue.isEmpty()) {
                return packetQueue.remove(0);
            }

            return null;
        }

        /**
         * Checks if this connection is connected.
         * 
         * @return true if connected, false otherwise
         */
        public boolean isConnected() {
            return connected.get();
        }

        /**
         * Gets the number of packets received on this connection.
         * 
         * @return the number of packets received
         */
        public int getPacketCount() {
            return packetCount;
        }

        @Override
        public void run() {
            while (running.get()) {
                try {
                    // Connect to the server
                    socket = new Socket(host, port);
                    socket.setSoTimeout(readTimeout);
                    connected.set(true);

                    LOGGER.info("Connected to server for IP " + ip + " on " + host + ":" + port);

                    // Read packets
                    try (DataInputStream in = new DataInputStream(socket.getInputStream())) {
                        while (running.get() && connected.get()) {
                            try {
                                // Read packet length
                                int packetLength = in.readInt();
                                LOGGER.info("Reading packet of length " + packetLength + " for IP " + ip);

                                // Validate packet length
                                if (packetLength < MIN_PACKET_SIZE || packetLength > MAX_PACKET_SIZE) {
                                    LOGGER.warning("Invalid packet length: " + packetLength + " for IP " + ip + 
                                                 ". Must be between " + MIN_PACKET_SIZE + " and " + MAX_PACKET_SIZE + " bytes.");
                                    continue;
                                }

                                // Read packet data in chunks
                                byte[] packetData = new byte[packetLength];
                                int bytesRead = 0;
                                while (bytesRead < packetLength) {
                                    int count = in.read(packetData, bytesRead, 
                                        Math.min(CHUNK_SIZE, packetLength - bytesRead));
                                    if (count == -1) {
                                        LOGGER.warning("End of stream reached while reading packet for IP " + ip);
                                        break;
                                    }
                                    bytesRead += count;
                                }

                                if (bytesRead < packetLength) {
                                    LOGGER.warning("Incomplete packet read for IP " + ip + 
                                                 ". Expected " + packetLength + " bytes, got " + bytesRead);
                                    continue;
                                }

                                // Add packet to queue with a maximum size
                                synchronized (this) {
                                    while (packetQueue.size() >= 1000) { // Max 1000 packets in queue
                                        try {
                                            LOGGER.warning("Packet queue full for IP " + ip + 
                                                         ", waiting for consumer...");
                                            wait(100); // Wait up to 100ms
                                        } catch (InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                            break;
                                        }
                                    }
                                    packetQueue.add(packetData);
                                    notifyAll(); // Notify waiting consumers
                                }

                                packetCount++;

                                if (packetCount % 100 == 0) {
                                    LOGGER.info("Received " + packetCount + " packets for IP " + ip);
                                }
                            } catch (SocketTimeoutException e) {
                                // This is expected due to the timeout on read
                                continue;
                            } catch (IOException e) {
                                LOGGER.log(Level.WARNING, "Error reading packet for IP " + ip, e);
                                // Try to reconnect
                                if (running.get()) {
                                    LOGGER.info("Attempting to reconnect for IP " + ip);
                                    try {
                                        socket.close();
                                        socket = new Socket(host, port);
                                        socket.setSoTimeout(readTimeout);
                                        LOGGER.info("Successfully reconnected for IP " + ip);
                                    } catch (IOException ex) {
                                        LOGGER.log(Level.SEVERE, "Failed to reconnect for IP " + ip, ex);
                                        break;
                                    }
                                } else {
                                    break;
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    if (running.get()) {
                        LOGGER.log(Level.WARNING, "Error in connection for IP " + ip, e);

                        // Wait before reconnecting
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                } finally {
                    connected.set(false);

                    try {
                        if (socket != null && !socket.isClosed()) {
                            socket.close();
                        }
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Error closing socket for IP " + ip, e);
                    }
                }
            }
        }
    }

    /**
     * Main method for testing.
     * 
     * @param args command line arguments: configFile
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: IPBasedStreamClient <config_file>");
            System.exit(1);
        }

        String configFile = args[0];
        IPBasedStreamClient client = new IPBasedStreamClient(configFile);

        try {
            client.start();

            // Add shutdown hook to stop client gracefully
            Runtime.getRuntime().addShutdownHook(new Thread(client::stop));

            // Process packets for a while
            long startTime = System.currentTimeMillis();
            long endTime = startTime + (60 * 1000); // Run for 60 seconds

            while (System.currentTimeMillis() < endTime) {
                byte[] packet = client.getNextPacket();
                if (packet != null) {
                    // Process the packet (just count it for now)
                    if (client.getTotalPacketCount() % 1000 == 0) {
                        LOGGER.info("Processed " + client.getTotalPacketCount() + " packets");
                    }
                } else {
                    // No packet available, sleep a bit
                    Thread.sleep(10);
                }
            }

            LOGGER.info("Test complete. Processed " + client.getTotalPacketCount() + " packets");

            // Print statistics for each IP
            for (String ip : client.getConnectedIPs()) {
                LOGGER.info("IP " + ip + ": " + client.getPacketCount(ip) + " packets");
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error starting client", e);
            System.exit(1);
        } catch (InterruptedException e) {
            LOGGER.info("Client interrupted");
            Thread.currentThread().interrupt();
        } finally {
            client.stop();
        }
    }
}