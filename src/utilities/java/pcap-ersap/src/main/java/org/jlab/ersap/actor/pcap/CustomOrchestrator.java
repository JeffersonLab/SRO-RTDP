package org.jlab.ersap.actor.pcap;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A custom orchestrator that uses the IP addresses from the PCAP file.
 * This class implements the same functionality as the Orchestrator class
 * but uses the IP addresses from the PCAP file.
 */
public class CustomOrchestrator {

    private static final Logger LOGGER = Logger.getLogger(CustomOrchestrator.class.getName());
    private static final String OUTPUT_DIR = "output";

    private final List<ConnectionInfo> connections = new ArrayList<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final ConcurrentHashMap<String, BufferedWriter> writers = new ConcurrentHashMap<>();
    private final AtomicLong packetCounter = new AtomicLong(0);

    /**
     * Creates a new CustomOrchestrator.
     */
    public CustomOrchestrator() {
        // Initialize the orchestrator
    }

    /**
     * Starts the orchestrator with the IP addresses from the PCAP file.
     * 
     * @throws IOException if an I/O error occurs
     */
    public void start() throws IOException {
        // Create output directory if it doesn't exist
        Path outputPath = Paths.get(OUTPUT_DIR);
        if (Files.exists(outputPath)) {
            LOGGER.log(Level.INFO, "Using existing output directory: {0}", outputPath.toAbsolutePath());
        } else {
            Files.createDirectories(outputPath);
            LOGGER.log(Level.INFO, "Created output directory: {0}", outputPath.toAbsolutePath());
        }

        // Read the IP addresses from the configuration file
        String configFile = "/workspace/src/utilities/java/pcap2streams/custom-config/ip-based-config.json";
        if (Files.exists(Paths.get(configFile))) {
            try {
                String content = new String(Files.readAllBytes(Paths.get(configFile)));
                JSONObject json = new JSONObject(content);
                JSONArray connectionsArray = json.getJSONArray("connections");

                // Add each connection from the configuration file
                for (int i = 0; i < connectionsArray.length(); i++) {
                    JSONObject connection = connectionsArray.getJSONObject(i);
                    String ip = connection.getString("ip");
                    String host = connection.getString("host");
                    int port = connection.getInt("port");

                    // Add the connection to the orchestrator
                    ConnectionInfo connectionInfo = new ConnectionInfo();
                    connectionInfo.ip = ip;
                    connectionInfo.host = host;
                    connectionInfo.port = port;
                    connections.add(connectionInfo);

                    LOGGER.log(Level.INFO, "Added connection for IP: {0} on port: {1}", new Object[] { ip, port });
                }

                // Start the reader threads
                LOGGER.log(Level.INFO, "Starting orchestrator with {0} reader threads", connections.size());

                // Create a thread for each connection
                for (ConnectionInfo connection : connections) {
                    executor.submit(() -> {
                        try {
                            readPackets(connection);
                        } catch (IOException e) {
                            LOGGER.log(Level.SEVERE, "Error reading packets: {0}", e.getMessage());
                        }
                        return null;
                    });
                }

                System.out.println("Services started successfully.");

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error reading configuration file: {0}", e.getMessage());
                throw new IOException("Error reading configuration file", e);
            }
        } else {
            LOGGER.log(Level.SEVERE, "Configuration file not found: {0}", configFile);
            throw new IOException("Configuration file not found: " + configFile);
        }
    }

    /**
     * Reads packets from a connection.
     * 
     * @param connection the connection to read from
     * @throws IOException if an I/O error occurs
     */
    private void readPackets(ConnectionInfo connection) throws IOException {
        LOGGER.log(Level.INFO, "Starting reader thread for IP {0} on port {1}",
                new Object[] { connection.ip, connection.port });

        Socket socket = new Socket();
        try {
            // Connect with timeout
            socket.connect(new InetSocketAddress(connection.host, connection.port), 5000);
            socket.setSoTimeout(30000); // Increase timeout to 30 seconds

            LOGGER.log(Level.INFO, "Connected to {0}:{1} for IP {2}",
                    new Object[] { connection.host, connection.port, connection.ip });

            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[65536]; // Use a smaller buffer size that matches the socket buffer size

            while (running.get()) {
                try {
                    // Read packet header (12 bytes)
                    byte[] header = new byte[12];
                    int headerBytesRead = 0;
                    int totalHeaderBytesRead = 0;

                    // Read header in chunks until we have all 12 bytes
                    while (totalHeaderBytesRead < 12) {
                        headerBytesRead = in.read(header, totalHeaderBytesRead, 12 - totalHeaderBytesRead);
                        if (headerBytesRead <= 0) {
                            LOGGER.log(Level.SEVERE, "End of stream reached while reading header");
                            return; // End of stream
                        }
                        totalHeaderBytesRead += headerBytesRead;
                    }

                    // Parse header
                    int packetSize = ((header[8] & 0xFF) << 24) |
                            ((header[9] & 0xFF) << 16) |
                            ((header[10] & 0xFF) << 8) |
                            (header[11] & 0xFF);

                    // Sanity check for packet size
                    if (packetSize <= 0 || packetSize > 1000000) { // 1MB max packet size
                        LOGGER.log(Level.WARNING,
                                "Invalid packet size: {0}, skipping packet",
                                packetSize);
                        continue;
                    }

                    // Read packet data in chunks
                    byte[] packetData = new byte[packetSize];
                    int totalBytesRead = 0;
                    int bytesRead = 0;

                    while (totalBytesRead < packetSize) {
                        int bytesToRead = Math.min(buffer.length, packetSize - totalBytesRead);
                        bytesRead = in.read(packetData, totalBytesRead, bytesToRead);

                        if (bytesRead <= 0) {
                            LOGGER.log(Level.SEVERE, "End of stream reached while reading packet data");
                            return; // End of stream
                        }

                        totalBytesRead += bytesRead;
                    }

                    // Process the packet
                    processPacket(connection.ip, header, packetData, packetSize);

                } catch (IOException e) {
                    if (running.get()) {
                        LOGGER.log(Level.SEVERE, "Error reading packet: {0}", e.getMessage());
                    }
                    break;
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error connecting to socket for IP: {0}", connection.ip);
            throw e;
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error closing socket: {0}", e.getMessage());
            }
        }
    }

    /**
     * Processes a packet.
     * 
     * @param ip        the IP address
     * @param header    the packet header
     * @param buffer    the packet data
     * @param bytesRead the number of bytes read
     */
    private void processPacket(String ip, byte[] header, byte[] buffer, int bytesRead) {
        // Increment the packet counter
        long packetCount = packetCounter.incrementAndGet();

        // Log every 1000 packets
        if (packetCount % 1000 == 0) {
            LOGGER.log(Level.INFO, "Processed {0} packets", packetCount);
        }

        // Write the packet to a file
        try {
            // Get or create the writer for this IP
            BufferedWriter writer = writers.computeIfAbsent(ip, k -> {
                try {
                    Path outputFile = Paths.get(OUTPUT_DIR, ip.replace('.', '_') + ".txt");
                    LOGGER.log(Level.INFO, "Creating output file: {0}", outputFile.toAbsolutePath());
                    return Files.newBufferedWriter(outputFile);
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Error creating writer for IP {0}: {1}",
                            new Object[] { ip, e.getMessage() });
                    return null;
                }
            });

            if (writer != null) {
                // Write the packet header and data
                writer.write("Packet " + packetCount + " from IP " + ip + "\n");
                writer.write("Header: ");
                for (byte b : header) {
                    writer.write(String.format("%02X ", b));
                }
                writer.write("\n");
                writer.write("Data Size: " + bytesRead + " bytes\n");
                writer.write("Data Sample: ");
                // Only write the first 100 bytes of data as a sample
                for (int i = 0; i < Math.min(bytesRead, 100); i++) {
                    writer.write(String.format("%02X ", buffer[i]));
                }
                writer.write("\n\n");
                writer.flush();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error writing packet: {0}", e.getMessage());
        }
    }

    /**
     * Stops the orchestrator.
     */
    public void stop() {
        running.set(false);
        executor.shutdown();

        // Close all writers
        for (BufferedWriter writer : writers.values()) {
            try {
                writer.flush();
                writer.close();
                LOGGER.log(Level.INFO, "Closed writer");
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error closing writer: {0}", e.getMessage());
            }
        }

        // Log final statistics
        LOGGER.log(Level.INFO, "Orchestrator stopped. Processed {0} packets total.", packetCounter.get());

        try {
            // Wait for all threads to finish
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Connection information.
     */
    private static class ConnectionInfo {
        String ip;
        String host;
        int port;
    }
}