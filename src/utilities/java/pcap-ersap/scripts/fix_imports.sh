#!/bin/bash

# This script fixes the imports in the service classes

echo "Fixing imports in service classes..."

# Create a directory for the fixed files
mkdir -p /workspace/src/utilities/java/pcap-ersap/src/main/java/org/jlab/ersap/actor/pcap/services/fixed

# Fix PcapSinkService.java
cat > /workspace/src/utilities/java/pcap-ersap/src/main/java/org/jlab/ersap/actor/pcap/services/PcapSinkService.java << 'EOF'
package org.jlab.ersap.actor.pcap.services;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jlab.epsci.ersap.base.EngineData;
import org.jlab.epsci.ersap.engine.IEngine;
import org.jlab.ersap.actor.pcap.data.PacketEvent;

/**
 * ERSAP service that writes packet data to files.
 */
public class PcapSinkService implements IEngine {

    private static final Logger LOGGER = Logger.getLogger(PcapSinkService.class.getName());

    private static final String OUTPUT_DIR_KEY = "output_dir";
    private static final String DEFAULT_OUTPUT_DIR = "/workspace/src/utilities/java/pcap-ersap/output";

    private final Map<String, BufferedWriter> writers = new ConcurrentHashMap<>();
    private String outputDir = DEFAULT_OUTPUT_DIR;
    private int packetCount = 0;

    @Override
    public EngineData configure(EngineData input) {
        LOGGER.severe("PcapSinkService: configure called");
        
        if (input.getMetadata() != null) {
            Map<String, Object> metadata = input.getMetadata();
            if (metadata.containsKey(OUTPUT_DIR_KEY)) {
                outputDir = (String) metadata.get(OUTPUT_DIR_KEY);
                LOGGER.severe("PcapSinkService: Using output directory: " + outputDir);
            }
        }

        // Create output directory if it doesn't exist
        Path outputPath = Paths.get(outputDir);
        try {
            if (!Files.exists(outputPath)) {
                Files.createDirectories(outputPath);
                LOGGER.severe("PcapSinkService: Created output directory: " + outputDir);
            } else {
                LOGGER.severe("PcapSinkService: Using existing output directory: " + outputDir);
            }
            
            // Test file creation to verify permissions
            Path testFile = outputPath.resolve("test.txt");
            try (BufferedWriter writer = Files.newBufferedWriter(
                    testFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                writer.write("Test file creation successful");
                LOGGER.severe("PcapSinkService: Test file creation successful: " + testFile);
            }
            Files.deleteIfExists(testFile);
            
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "PcapSinkService: Error creating output directory or test file", e);
        }

        EngineData output = new EngineData();
        output.setStatus(0);
        output.setDescription("PcapSinkService configured successfully");
        return output;
    }

    @Override
    public EngineData execute(EngineData input) {
        LOGGER.severe("PcapSinkService: execute called");
        
        EngineData output = new EngineData();
        
        if (input == null) {
            LOGGER.severe("PcapSinkService: Input is null");
            output.setStatus(1);
            output.setDescription("Input is null");
            return output;
        }
        
        Object data = input.getData();
        if (data == null) {
            LOGGER.severe("PcapSinkService: Data is null");
            output.setStatus(1);
            output.setDescription("Data is null");
            return output;
        }
        
        LOGGER.severe("PcapSinkService: Data class: " + data.getClass().getName());
        
        if (data instanceof PacketEvent) {
            try {
                PacketEvent event = (PacketEvent) data;
                writePacket(event);
                packetCount++;
                
                LOGGER.severe("PcapSinkService: Processed packet #" + packetCount + " from IP " + event.getSourceIp());
                
                output.setStatus(0);
                output.setDescription("Packet processed successfully");
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "PcapSinkService: Error writing packet to file", e);
                output.setStatus(1);
                output.setDescription("Error writing packet to file: " + e.getMessage());
            }
        } else {
            LOGGER.severe("PcapSinkService: Data is not a PacketEvent: " + data.getClass().getName());
            output.setStatus(1);
            output.setDescription("Data is not a PacketEvent");
        }
        
        return output;
    }

    @Override
    public EngineData executeGroup(EngineData input) {
        LOGGER.severe("PcapSinkService: executeGroup not implemented");
        EngineData output = new EngineData();
        output.setStatus(1);
        output.setDescription("executeGroup not implemented");
        return output;
    }

    @Override
    public void reset() {
        LOGGER.severe("PcapSinkService: reset called, total packets processed: " + packetCount);
        closeWriters();
        packetCount = 0;
    }

    @Override
    public void destroy() {
        LOGGER.severe("PcapSinkService: destroy called, total packets processed: " + packetCount);
        closeWriters();
    }

    @Override
    public String getDescription() {
        return "ERSAP service that writes packet data to files";
    }

    @Override
    public String getName() {
        return "PcapSinkService";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public String getAuthor() {
        return "ERSAP Team";
    }

    private void writePacket(PacketEvent event) throws IOException {
        // Get or create a writer for this packet
        String filename = "packet_" + event.getPacketId() + ".txt";
        BufferedWriter writer = getWriter(filename);

        // Write packet information to file
        writer.write(event.toString());
        writer.newLine();

        // Flush to ensure data is written
        writer.flush();

        LOGGER.severe("PcapSinkService: Wrote packet #" + event.getPacketId() + " to file " + filename);
    }

    private BufferedWriter getWriter(String filename) throws IOException {
        // Check if we already have a writer for this filename
        BufferedWriter writer = writers.get(filename);

        if (writer == null) {
            // Create a new writer
            writer = createWriter(filename);
            writers.put(filename, writer);
        }

        return writer;
    }

    private BufferedWriter createWriter(String filename) throws IOException {
        // Create a file for this packet
        Path filePath = Paths.get(outputDir, filename);

        // Create parent directories if they don't exist
        Path parent = filePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        // Create or overwrite the file
        return Files.newBufferedWriter(
                filePath,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    private void closeWriters() {
        for (BufferedWriter writer : writers.values()) {
            try {
                writer.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error closing writer", e);
            }
        }
        writers.clear();
    }
}
EOF

# Fix PcapSourceService.java
cat > /workspace/src/utilities/java/pcap-ersap/src/main/java/org/jlab/ersap/actor/pcap/services/PcapSourceService.java << 'EOF'
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
EOF

# Fix PcapProcessorService.java
cat > /workspace/src/utilities/java/pcap-ersap/src/main/java/org/jlab/ersap/actor/pcap/services/PcapProcessorService.java << 'EOF'
package org.jlab.ersap.actor.pcap.services;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jlab.epsci.ersap.base.EngineData;
import org.jlab.epsci.ersap.engine.IEngine;
import org.jlab.ersap.actor.pcap.data.PacketEvent;

/**
 * ERSAP service that processes packet data.
 */
public class PcapProcessorService implements IEngine {

    private static final Logger LOGGER = Logger.getLogger(PcapProcessorService.class.getName());

    private static final String PROTOCOL_FILTER_KEY = "protocol_filter";
    private static final String IP_FILTER_KEY = "ip_filter";

    private String protocolFilter = "";
    private String ipFilter = "";

    @Override
    public EngineData configure(EngineData input) {
        LOGGER.log(Level.SEVERE, "PcapProcessorService: configure called");
        
        if (input.getMetadata() != null) {
            Map<String, Object> metadata = input.getMetadata();
            if (metadata.containsKey(PROTOCOL_FILTER_KEY)) {
                protocolFilter = (String) metadata.get(PROTOCOL_FILTER_KEY);
                LOGGER.log(Level.SEVERE, "PcapProcessorService: Using protocol filter: {0}", protocolFilter);
            }
            if (metadata.containsKey(IP_FILTER_KEY)) {
                ipFilter = (String) metadata.get(IP_FILTER_KEY);
                LOGGER.log(Level.SEVERE, "PcapProcessorService: Using IP filter: {0}", ipFilter);
            }
        }

        EngineData output = new EngineData();
        output.setStatus(0);
        output.setDescription("PcapProcessorService configured successfully");
        return output;
    }

    @Override
    public EngineData execute(EngineData input) {
        LOGGER.log(Level.SEVERE, "PcapProcessorService: execute called");
        
        EngineData output = new EngineData();
        
        if (input == null) {
            LOGGER.log(Level.SEVERE, "PcapProcessorService: Input is null");
            output.setStatus(1);
            output.setDescription("Input is null");
            return output;
        }
        
        Object data = input.getData();
        if (data == null) {
            LOGGER.log(Level.SEVERE, "PcapProcessorService: Data is null");
            output.setStatus(1);
            output.setDescription("Data is null");
            return output;
        }
        
        if (data instanceof PacketEvent) {
            PacketEvent event = (PacketEvent) data;
            
            // Apply filters
            if (shouldFilter(event)) {
                LOGGER.log(Level.SEVERE, "PcapProcessorService: Packet filtered out: {0}", event);
                output.setStatus(0);
                output.setDescription("Packet filtered out");
                return output;
            }
            
            // Process the packet (for now, just pass it through)
            output.setData(PcapDataTypes.PACKET_EVENT, event);
            output.setStatus(0);
            output.setDescription("Packet processed successfully");
            
            LOGGER.log(Level.SEVERE, "PcapProcessorService: Processed packet #{0} from IP {1}", 
                    new Object[]{event.getPacketId(), event.getSourceIp()});
        } else {
            LOGGER.log(Level.SEVERE, "PcapProcessorService: Data is not a PacketEvent: {0}", data.getClass().getName());
            output.setStatus(1);
            output.setDescription("Data is not a PacketEvent");
        }
        
        return output;
    }

    @Override
    public EngineData executeGroup(EngineData input) {
        LOGGER.log(Level.SEVERE, "PcapProcessorService: executeGroup not implemented");
        EngineData output = new EngineData();
        output.setStatus(1);
        output.setDescription("executeGroup not implemented");
        return output;
    }

    @Override
    public void reset() {
        LOGGER.log(Level.SEVERE, "PcapProcessorService: reset called");
    }

    @Override
    public void destroy() {
        LOGGER.log(Level.SEVERE, "PcapProcessorService: destroy called");
    }

    @Override
    public String getDescription() {
        return "ERSAP service that processes packet data";
    }

    @Override
    public String getName() {
        return "PcapProcessorService";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public String getAuthor() {
        return "ERSAP Team";
    }

    private boolean shouldFilter(PacketEvent event) {
        // Apply protocol filter
        if (!protocolFilter.isEmpty() && !event.getProtocol().equals(protocolFilter)) {
            return true;
        }
        
        // Apply IP filter
        if (!ipFilter.isEmpty() && !event.getSourceIp().equals(ipFilter) && !event.getDestinationIp().equals(ipFilter)) {
            return true;
        }
        
        return false;
    }
}
EOF

# Create PacketEvent.java in the data package
mkdir -p /workspace/src/utilities/java/pcap-ersap/src/main/java/org/jlab/ersap/actor/pcap/data
cat > /workspace/src/utilities/java/pcap-ersap/src/main/java/org/jlab/ersap/actor/pcap/data/PacketEvent.java << 'EOF'
package org.jlab.ersap.actor.pcap.data;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Represents a packet event from a PCAP file.
 * This class is used to pass packet data between ERSAP services.
 */
public class PacketEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long packetId;
    private final String sourceIp;
    private final String destinationIp;
    private final String protocol;
    private final int etherType;
    private final byte[] data;
    private final long timestamp;

    /**
     * Creates a new packet event.
     *
     * @param packetId      the packet ID
     * @param sourceIp      the source IP address
     * @param destinationIp the destination IP address
     * @param protocol      the protocol name (e.g., "TCP", "UDP", "ICMP")
     * @param etherType     the EtherType value
     * @param data          the packet data
     * @param timestamp     the packet timestamp
     */
    public PacketEvent(long packetId, String sourceIp, String destinationIp,
            String protocol, int etherType, byte[] data, long timestamp) {
        this.packetId = packetId;
        this.sourceIp = sourceIp;
        this.destinationIp = destinationIp;
        this.protocol = protocol;
        this.etherType = etherType;
        this.data = data != null ? Arrays.copyOf(data, data.length) : null;
        this.timestamp = timestamp;
    }

    /**
     * Gets the packet ID.
     *
     * @return the packet ID
     */
    public long getPacketId() {
        return packetId;
    }

    /**
     * Gets the source IP address.
     *
     * @return the source IP address
     */
    public String getSourceIp() {
        return sourceIp;
    }

    /**
     * Gets the destination IP address.
     *
     * @return the destination IP address
     */
    public String getDestinationIp() {
        return destinationIp;
    }

    /**
     * Gets the protocol name.
     *
     * @return the protocol name
     */
    public String getProtocol() {
        return protocol;
    }

    /**
     * Gets the EtherType value.
     *
     * @return the EtherType value
     */
    public int getEtherType() {
        return etherType;
    }

    /**
     * Gets the packet data.
     *
     * @return the packet data
     */
    public byte[] getData() {
        return data != null ? Arrays.copyOf(data, data.length) : null;
    }

    /**
     * Gets the packet timestamp.
     *
     * @return the packet timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Returns a string representation of the packet event.
     *
     * @return a string representation of the packet event
     */
    @Override
    public String toString() {
        return "PacketEvent{" +
                "packetId=" + packetId +
                ", sourceIp='" + sourceIp + '\'' +
                ", destinationIp='" + destinationIp + '\'' +
                ", protocol='" + protocol + '\'' +
                ", etherType=0x" + Integer.toHexString(etherType) +
                ", dataLength=" + (data != null ? data.length : 0) +
                ", timestamp=" + timestamp +
                '}';
    }
}
EOF

# Create PcapDataTypes.java
cat > /workspace/src/utilities/java/pcap-ersap/src/main/java/org/jlab/ersap/actor/pcap/services/PcapDataTypes.java << 'EOF'
package org.jlab.ersap.actor.pcap.services;

/**
 * MIME types for PCAP data.
 */
public class PcapDataTypes {
    public static final String PACKET_EVENT = "binary/pcap-event";
}
EOF

echo "Imports fixed successfully." 