package org.jlab.ersap.actor.pcap.services;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
 * ERSAP service that reads packet data from pcap2streams socket servers.
 */
public class PcapSourceService implements IEngine {

    private static final Logger LOGGER = Logger.getLogger(PcapSourceService.class.getName());

    private static final String CONFIG_FILE_KEY = "config_file";
    private static final String DEFAULT_CONFIG_FILE = "/workspace/src/utilities/java/pcap2streams/custom-config/ip-based-config.json";

    private final List<Thread> readerThreads = new ArrayList<>();
    private final Map<String, Socket> sockets = new ConcurrentHashMap<>();
    private final AtomicLong packetCounter = new AtomicLong(0);

    private String configFile = DEFAULT_CONFIG_FILE;
    private boolean running = false;

    @Override
    public EngineData configure(EngineData input) {
        LOGGER.log(Level.SEVERE, "PcapSourceService: configure called");
        
        if (input.getMetadata() != null) {
            Map<String, Object> metadata = input.getMetadata();
            if (metadata.containsKey(CONFIG_FILE_KEY)) {
                configFile = (String) metadata.get(CONFIG_FILE_KEY);
                LOGGER.log(Level.SEVERE, "PcapSourceService: Using config file: {0}", configFile);
            }
        }

        try {
            setupDataSource();
            running = true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "PcapSourceService: Error setting up data source", e);
            EngineData output = new EngineData();
            output.setStatus(1);
            output.setDescription("Error setting up data source: " + e.getMessage());
            return output;
        }

        EngineData output = new EngineData();
        output.setStatus(0);
        output.setDescription("PcapSourceService configured successfully");
        return output;
    }

    @Override
    public EngineData execute(EngineData input) {
        LOGGER.log(Level.SEVERE, "PcapSourceService: execute called");
        
        EngineData output = new EngineData();
        output.setStatus(1);
        output.setDescription("PcapSourceService is a source service, execute should not be called");
        return output;
    }

    @Override
    public EngineData executeGroup(EngineData input) {
        LOGGER.log(Level.SEVERE, "PcapSourceService: executeGroup not implemented");
        EngineData output = new EngineData();
        output.setStatus(1);
        output.setDescription("executeGroup not implemented");
        return output;
    }

    @Override
    public void reset() {
        LOGGER.log(Level.SEVERE, "PcapSourceService: reset called, total packets read: {0}", packetCounter.get());
        
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
        LOGGER.log(Level.SEVERE, "PcapSourceService: destroy called, total packets read: {0}", packetCounter.get());
        reset();
    }

    @Override
    public String getDescription() {
        return "ERSAP service that reads packet data from pcap2streams socket servers";
    }

    @Override
    public String getName() {
        return "PcapSourceService";
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
        LOGGER.log(Level.SEVERE, "PcapSourceService: Setting up data source from config file: {0}", configFile);
        
        // Read the configuration file
        Path configPath = Paths.get(configFile);
        if (!Files.exists(configPath)) {
            throw new IOException("Config file not found: " + configFile);
        }
        
        String configJson = new String(Files.readAllBytes(configPath));
        
        try {
            JSONObject config = new JSONObject(configJson);
            JSONArray connections = config.getJSONArray("connections");
            
            LOGGER.log(Level.SEVERE, "PcapSourceService: Found {0} connections in config file", connections.length());
            
            for (int i = 0; i < connections.length(); i++) {
                JSONObject connectionConfig = connections.getJSONObject(i);
                String ip = connectionConfig.getString("ip");
                int port = connectionConfig.getInt("port");
                
                // Start a reader thread for this connection
                Thread readerThread = new Thread(() -> {
                    try {
                        readPackets(ip, port);
                    } catch (IOException e) {
                        LOGGER.log(Level.SEVERE, "Error reading packets for IP: " + ip, e);
                    }
                });
                readerThread.setName("PcapReader-" + ip);
                readerThread.start();
                
                readerThreads.add(readerThread);
                LOGGER.log(Level.SEVERE, "PcapSourceService: Started reader thread for IP: {0} on port: {1}", new Object[]{ip, port});
            }
        } catch (JSONException e) {
            throw new IOException("Error parsing config JSON: " + e.getMessage(), e);
        }
    }

    private void readPackets(String ip, int port) throws IOException {
        LOGGER.log(Level.SEVERE, "PcapSourceService: Starting reader thread for IP {0} on port {1}", new Object[]{ip, port});
        
        try (Socket socket = new Socket("localhost", port)) {
            LOGGER.log(Level.SEVERE, "PcapSourceService: Connected to localhost:{0} for IP {1}", new Object[]{port, ip});
            
            sockets.put(ip, socket);
            
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[16384]; // 16KB buffer
            
            while (running) {
                try {
                    // Read packet header (12 bytes)
                    byte[] header = new byte[12];
                    int headerBytesRead = in.read(header, 0, 12);
                    
                    if (headerBytesRead < 12) {
                        LOGGER.log(Level.SEVERE, "PcapSourceService: Incomplete header received, expected 12 bytes but got {0}", headerBytesRead);
                        continue;
                    }
                    
                    // Parse header
                    int packetSize = ((header[8] & 0xFF) << 24) |
                                    ((header[9] & 0xFF) << 16) |
                                    ((header[10] & 0xFF) << 8) |
                                    (header[11] & 0xFF);
                    
                    if (packetSize <= 0 || packetSize > buffer.length) {
                        LOGGER.log(Level.SEVERE, "PcapSourceService: Invalid packet length: {0}", packetSize);
                        continue;
                    }
                    
                    // Read packet data
                    int bytesRead = in.read(buffer, 0, packetSize);
                    
                    if (bytesRead < packetSize) {
                        LOGGER.log(Level.SEVERE, "PcapSourceService: Incomplete packet received, expected {0} bytes but got {1}", 
                                new Object[]{packetSize, bytesRead});
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
                    
                    LOGGER.log(Level.SEVERE, "PcapSourceService: Created packet event #{0} from IP {1}", new Object[]{packetId, ip});
                    
                    // Create output data
                    EngineData output = new EngineData();
                    output.setData(PcapDataTypes.PACKET_EVENT, event);
                    
                    // TODO: Send the output data to the next service
                    
                } catch (IOException e) {
                    if (running) {
                        LOGGER.log(Level.SEVERE, "PcapSourceService: Error reading from socket for IP: " + ip, e);
                    }
                    break;
                }
            }
            
            LOGGER.log(Level.SEVERE, "PcapSourceService: Reader thread for IP {0} completed", ip);
            
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "PcapSourceService: Error connecting to socket for IP: " + ip, e);
            throw e;
        }
    }
}
