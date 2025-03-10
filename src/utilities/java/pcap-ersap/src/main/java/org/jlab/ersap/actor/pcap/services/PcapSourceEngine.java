package org.jlab.ersap.actor.pcap.services;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jlab.epsci.ersap.base.EngineData;
import org.jlab.epsci.ersap.engine.IEngine;
import org.jlab.ersap.actor.pcap.data.PacketEvent;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * ERSAP engine that reads packet data from pcap2streams socket servers.
 */
public class PcapSourceEngine implements IEngine {

    private static final Logger LOGGER = Logger.getLogger(PcapSourceEngine.class.getName());

    private static final String CONFIG_FILE_KEY = "config_file";
    private static final String DEFAULT_CONFIG_FILE = "/workspace/src/utilities/java/pcap2streams/custom-config/ip-based-config.json";

    // New configuration keys
    private static final String STREAM_HOST_KEY = "streamHost";
    private static final String STREAM_PORT_KEY = "streamPort";
    private static final String RING_BUFFER_SIZE_KEY = "ringBufferSize";
    private static final String CONNECTION_TIMEOUT_KEY = "connectionTimeout";
    private static final String READ_TIMEOUT_KEY = "readTimeout";

    private final List<Thread> readerThreads = new ArrayList<>();
    private final Map<String, Socket> sockets = new ConcurrentHashMap<>();
    private final AtomicLong packetCounter = new AtomicLong(0);

    private String configFile = DEFAULT_CONFIG_FILE;
    private boolean running = false;

    // New configuration parameters with default values
    private String streamHost = "localhost";
    private int streamPort = 7777;
    private int ringBufferSize = 1024;
    private int connectionTimeout = 5000;
    private int readTimeout = 2000;

    @Override
    public EngineData configure(EngineData input) {
        LOGGER.log(Level.SEVERE, "PcapSourceEngine: configure called");

        if (input.getMetadata() != null) {
            Map<String, Object> metadata = input.getMetadata();
            if (metadata.containsKey(CONFIG_FILE_KEY)) {
                configFile = (String) metadata.get(CONFIG_FILE_KEY);
                LOGGER.log(Level.SEVERE, "PcapSourceEngine: Using config file: {0}", configFile);
            }

            // Read new configuration parameters
            if (metadata.containsKey(STREAM_HOST_KEY)) {
                streamHost = (String) metadata.get(STREAM_HOST_KEY);
                LOGGER.log(Level.SEVERE, "PcapSourceEngine: Using stream host: {0}", streamHost);
            }

            if (metadata.containsKey(STREAM_PORT_KEY)) {
                streamPort = Integer.parseInt(metadata.get(STREAM_PORT_KEY).toString());
                LOGGER.log(Level.SEVERE, "PcapSourceEngine: Using stream port: {0}", streamPort);
            }

            if (metadata.containsKey(RING_BUFFER_SIZE_KEY)) {
                ringBufferSize = Integer.parseInt(metadata.get(RING_BUFFER_SIZE_KEY).toString());
                LOGGER.log(Level.SEVERE, "PcapSourceEngine: Using ring buffer size: {0}", ringBufferSize);
            }

            if (metadata.containsKey(CONNECTION_TIMEOUT_KEY)) {
                connectionTimeout = Integer.parseInt(metadata.get(CONNECTION_TIMEOUT_KEY).toString());
                LOGGER.log(Level.SEVERE, "PcapSourceEngine: Using connection timeout: {0}", connectionTimeout);
            }

            if (metadata.containsKey(READ_TIMEOUT_KEY)) {
                readTimeout = Integer.parseInt(metadata.get(READ_TIMEOUT_KEY).toString());
                LOGGER.log(Level.SEVERE, "PcapSourceEngine: Using read timeout: {0}", readTimeout);
            }
        }

        try {
            setupDataSource();
            running = true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "PcapSourceEngine: Error setting up data source", e);
            EngineData output = new EngineData();
            output.setStatus(1);
            output.setDescription("Error setting up data source: " + e.getMessage());
            return output;
        }

        EngineData output = new EngineData();
        output.setStatus(0);
        output.setDescription("PcapSourceEngine configured successfully");
        return output;
    }

    @Override
    public EngineData execute(EngineData input) {
        LOGGER.log(Level.SEVERE, "PcapSourceEngine: execute called");

        EngineData output = new EngineData();
        output.setStatus(1);
        output.setDescription("PcapSourceEngine is a source service, execute should not be called");
        return output;
    }

    @Override
    public EngineData executeGroup(EngineData input) {
        LOGGER.log(Level.SEVERE, "PcapSourceEngine: executeGroup not implemented");
        EngineData output = new EngineData();
        output.setStatus(1);
        output.setDescription("executeGroup not implemented");
        return output;
    }

    @Override
    public void reset() {
        LOGGER.log(Level.SEVERE, "PcapSourceEngine: reset called, total packets read: {0}", packetCounter.get());

        // Stop all reader threads
        running = false;

        // Close all sockets
        for (Socket socket : sockets.values()) {
            try {
                socket.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error closing socket", e);
            }
        }
        sockets.clear();

        // Wait for all reader threads to complete
        for (Thread thread : readerThreads) {
            try {
                thread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warning("Interrupted while waiting for reader thread to complete");
            }
        }
        readerThreads.clear();

        // Reset packet counter
        packetCounter.set(0);
    }

    @Override
    public void destroy() {
        LOGGER.log(Level.SEVERE, "PcapSourceEngine: destroy called, total packets read: {0}", packetCounter.get());
        reset();
    }

    @Override
    public String getDescription() {
        return "ERSAP engine that reads packet data from pcap2streams socket servers";
    }

    @Override
    public String getName() {
        return "PcapSourceEngine";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public String getAuthor() {
        return "ERSAP Team";
    }

    private void setupDataSource() throws IOException {
        LOGGER.log(Level.SEVERE, "PcapSourceEngine: Setting up data source from config file: {0}", configFile);

        // Read the configuration file
        Path configPath = Paths.get(configFile);
        if (!Files.exists(configPath)) {
            throw new IOException("Config file not found: " + configFile);
        }

        String configJson = new String(Files.readAllBytes(configPath));

        try {
            JSONObject config = new JSONObject(configJson);
            JSONArray connections = config.getJSONArray("connections");

            LOGGER.log(Level.SEVERE, "PcapSourceEngine: Found {0} connections in config file", connections.length());

            for (int i = 0; i < connections.length(); i++) {
                JSONObject connectionConfig = connections.getJSONObject(i);
                String ip = connectionConfig.getString("ip");
                int port = connectionConfig.getInt("port");

                // Use configured host if available in the connection config
                String host = connectionConfig.has("host") ? connectionConfig.getString("host") : streamHost;

                // Use configured timeouts if available in the connection config
                int connTimeout = connectionConfig.has("connection_timeout")
                        ? connectionConfig.getInt("connection_timeout")
                        : connectionTimeout;
                int readTo = connectionConfig.has("read_timeout") ? connectionConfig.getInt("read_timeout")
                        : readTimeout;

                // Start a reader thread for this connection
                Thread readerThread = new Thread(() -> {
                    try {
                        readPackets(ip, host, port, connTimeout, readTo);
                    } catch (IOException e) {
                        LOGGER.log(Level.SEVERE, "Error reading packets for IP: " + ip, e);
                    }
                });
                readerThread.setName("PcapReader-" + ip);
                readerThread.start();

                readerThreads.add(readerThread);
                LOGGER.log(Level.SEVERE, "PcapSourceEngine: Started reader thread for IP: {0} on {1}:{2}",
                        new Object[] { ip, host, port });
            }
        } catch (JSONException e) {
            throw new IOException("Error parsing config JSON: " + e.getMessage(), e);
        }
    }

    private void readPackets(String ip, String host, int port, int connTimeout, int readTo) throws IOException {
        LOGGER.log(Level.SEVERE, "PcapSourceEngine: Starting reader thread for IP {0} on {1}:{2}",
                new Object[] { ip, host, port });

        Socket socket = new Socket();
        try {
            // Connect with timeout
            socket.connect(new InetSocketAddress(host, port), connTimeout);
            socket.setSoTimeout(readTo);

            LOGGER.log(Level.SEVERE, "PcapSourceEngine: Connected to {0}:{1} for IP {2}",
                    new Object[] { host, port, ip });

            sockets.put(ip, socket);

            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[16384]; // 16KB buffer

            while (running) {
                try {
                    // Read packet header (12 bytes)
                    byte[] header = new byte[12];
                    int headerBytesRead = in.read(header, 0, 12);

                    if (headerBytesRead < 12) {
                        LOGGER.log(Level.SEVERE,
                                "PcapSourceEngine: Incomplete header received, expected 12 bytes but got {0}",
                                headerBytesRead);
                        continue;
                    }

                    // Parse header
                    int packetSize = ((header[8] & 0xFF) << 24) |
                            ((header[9] & 0xFF) << 16) |
                            ((header[10] & 0xFF) << 8) |
                            (header[11] & 0xFF);

                    if (packetSize <= 0 || packetSize > buffer.length) {
                        LOGGER.log(Level.SEVERE, "PcapSourceEngine: Invalid packet length: {0}", packetSize);
                        continue;
                    }

                    // Read packet data
                    int bytesRead = in.read(buffer, 0, packetSize);

                    if (bytesRead < packetSize) {
                        LOGGER.log(Level.SEVERE,
                                "PcapSourceEngine: Incomplete packet received, expected {0} bytes but got {1}",
                                new Object[] { packetSize, bytesRead });
                        continue;
                    }

                    // Extract MAC addresses and EtherType
                    String destMac = String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                            buffer[0] & 0xFF, buffer[1] & 0xFF, buffer[2] & 0xFF,
                            buffer[3] & 0xFF, buffer[4] & 0xFF, buffer[5] & 0xFF);

                    String sourceMac = String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                            buffer[6] & 0xFF, buffer[7] & 0xFF, buffer[8] & 0xFF,
                            buffer[9] & 0xFF, buffer[10] & 0xFF, buffer[11] & 0xFF);

                    int etherType = ((buffer[12] & 0xFF) << 8) | (buffer[13] & 0xFF);

                    // Create packet event
                    long packetId = packetCounter.getAndIncrement();
                    PacketEvent event = new PacketEvent(
                            packetId,
                            ip, // sourceIp
                            "unknown", // destinationIp
                            "unknown", // protocol
                            etherType,
                            buffer,
                            System.currentTimeMillis());

                    // Process the packet (in a real implementation, this would publish to a ring
                    // buffer)
                    LOGGER.log(Level.FINE, "PcapSourceEngine: Processed packet #{0} from IP {1}",
                            new Object[] { packetId, ip });

                } catch (IOException e) {
                    if (running) {
                        LOGGER.log(Level.WARNING, "Error reading from socket for IP: " + ip, e);
                    }
                    break;
                }
            }
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error closing socket for IP: " + ip, e);
            }
        }

        LOGGER.log(Level.SEVERE, "PcapSourceEngine: Reader thread for IP {0} completed", ip);
    }
}