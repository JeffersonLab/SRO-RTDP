package org.jlab.ersap.actor.pcap2streams;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Main class for the Pcap2Streams application.
 * This application analyzes a PCAP file, identifies unique IP addresses,
 * and creates separate socket servers for each IP address.
 */
public class Pcap2Streams {

    private static final Logger LOGGER = Logger.getLogger(Pcap2Streams.class.getName());
    private static final int BASE_PORT = 9000;

    private final String pcapFile;
    private final String configDir;
    private final Map<String, IPBasedPcapServer> servers;
    private final Map<String, Integer> ipToPorts;

    /**
     * Creates a new Pcap2Streams instance.
     * 
     * @param pcapFile  the path to the PCAP file to analyze
     * @param configDir the directory to store configuration files
     */
    public Pcap2Streams(String pcapFile, String configDir) {
        this.pcapFile = pcapFile;
        this.configDir = configDir;
        this.servers = new ConcurrentHashMap<>();
        this.ipToPorts = new HashMap<>();
    }

    /**
     * Starts the application.
     * 
     * @throws IOException if an error occurs
     */
    public void start() throws IOException {
        LOGGER.info("Starting Pcap2Streams with PCAP file: " + pcapFile);

        // Create config directory if it doesn't exist
        File configDirFile = new File(configDir);
        if (!configDirFile.exists()) {
            configDirFile.mkdirs();
        }

        // Analyze the PCAP file
        PcapIPAnalyzer analyzer = new PcapIPAnalyzer(pcapFile);
        Set<String> uniqueIPs = analyzer.analyze();

        LOGGER.info("Found " + uniqueIPs.size() + " unique IP addresses");

        // Create and start servers for each IP
        int port = BASE_PORT;
        for (String ip : uniqueIPs) {
            ipToPorts.put(ip, port);

            Set<Long> packetPositions = analyzer.getIpToPacketPositions().get(ip);
            if (packetPositions != null && !packetPositions.isEmpty()) {
                IPBasedPcapServer server = new IPBasedPcapServer(pcapFile, ip, port, packetPositions);
                servers.put(ip, server);
                server.start();

                LOGGER.info("Started server for IP " + ip + " on port " + port +
                        " with " + packetPositions.size() + " packets");

                port++;
            }
        }

        // Generate configuration file
        generateConfigFile();

        LOGGER.info("Pcap2Streams started with " + servers.size() + " servers");
    }

    /**
     * Stops all servers.
     */
    public void stop() {
        LOGGER.info("Stopping all servers...");

        for (IPBasedPcapServer server : servers.values()) {
            server.stop();
        }

        servers.clear();
        LOGGER.info("All servers stopped");
    }

    /**
     * Generates a configuration file for ERSAP.
     * 
     * @throws IOException if an error occurs writing the file
     */
    private void generateConfigFile() throws IOException {
        JSONObject config = new JSONObject();
        JSONArray connections = new JSONArray();

        for (Map.Entry<String, Integer> entry : ipToPorts.entrySet()) {
            String ip = entry.getKey();
            int port = entry.getValue();

            JSONObject connection = new JSONObject();
            connection.put("ip", ip);
            connection.put("host", "localhost");
            connection.put("port", port);
            connection.put("connection_timeout", 5000);
            connection.put("read_timeout", 30000);
            connection.put("buffer_size", 1024);
            connection.put("packet_count", servers.get(ip) != null ? servers.get(ip).getPacketCount() : 0);

            connections.put(connection);
        }

        config.put("connections", connections);

        // Write the configuration file
        String configFile = configDir + "/ip-based-config.json";
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write(config.toString(2));
        }

        LOGGER.info("Generated configuration file: " + configFile);
    }

    /**
     * Main method.
     * 
     * @param args command line arguments: pcapFile configDir
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: Pcap2Streams <pcap_file> <config_dir>");
            System.exit(1);
        }

        String pcapFile = args[0];
        String configDir = args[1];

        Pcap2Streams app = new Pcap2Streams(pcapFile, configDir);

        try {
            app.start();

            // Add shutdown hook to stop servers gracefully
            Runtime.getRuntime().addShutdownHook(new Thread(app::stop));

            // Keep the application running until interrupted
            LOGGER.info("Press Ctrl+C to stop the application");
            Thread.currentThread().join();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error starting Pcap2Streams", e);
            System.exit(1);
        } catch (InterruptedException e) {
            LOGGER.info("Application interrupted");
            Thread.currentThread().interrupt();
        } finally {
            app.stop();
        }
    }
}