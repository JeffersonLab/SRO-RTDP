package org.jlab.ersap.pcap;

import org.jlab.epsci.ersap.base.ErsapUtil;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.std.services.AbstractService;
import org.jlab.ersap.actor.datatypes.JavaObjectType;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.HashSet;
import java.util.Set;

public class PacketSink extends AbstractService {
    
    private static final Logger LOGGER = Logger.getLogger(PacketSink.class.getName());
    
    private Path outputDir;
    private Map<String, FileWriter> writers;
    private ScheduledExecutorService scheduler;
    private int flushInterval;
    
    public PacketSink() {
        this.writers = new ConcurrentHashMap<>();
    }
    
    @Override
    public EngineData configure(EngineData input) {
        String mimeType = input.getMimeType();
        String source = (String) input.getData();
        JSONObject data;
        
        if (mimeType.equalsIgnoreCase(JavaObjectType.JOBJ.mimeType()) || 
            mimeType.equalsIgnoreCase(EngineDataType.JSON.mimeType())) {
            data = new JSONObject(source);
            
            try {
                // Get output directory from configuration or use default
                String outputDirStr = data.optString("OUTPUT_DIR", "output");
                outputDir = Paths.get(outputDirStr).toAbsolutePath();
                
                LOGGER.info("Setting output directory to absolute path: " + outputDir);
                
                // Create output directory if it doesn't exist
                if (!Files.exists(outputDir)) {
                    LOGGER.info("Creating output directory: " + outputDir);
                    Files.createDirectories(outputDir);
                }
                
                // Verify directory is writable
                File outDir = outputDir.toFile();
                if (!outDir.canWrite()) {
                    LOGGER.severe("Cannot write to output directory: " + outputDir);
                    throw new IOException("Output directory is not writable: " + outputDir);
                }
                
                // Get configuration values with defaults
                flushInterval = data.optInt("FLUSH_INTERVAL", 1000);
                
                // Start the scheduler for periodic flushing
                scheduler = Executors.newSingleThreadScheduledExecutor();
                scheduler.scheduleAtFixedRate(this::flushWriters, flushInterval, flushInterval, TimeUnit.MILLISECONDS);
                
                LOGGER.info("Initialized packet sink with output directory: " + outputDir);
                
                // Return success with sfixed32 data type
                EngineData output = new EngineData();
                output.setData(EngineDataType.SFIXED32, 0);
                return output;
            } catch (IOException e) {
                LOGGER.severe("Failed to configure PacketSink: " + e.getMessage());
                e.printStackTrace();
                EngineData output = new EngineData();
                output.setData(EngineDataType.SFIXED32, -1);
                return output;
            }
        }
        LOGGER.warning("Invalid configuration mime type: " + mimeType);
        EngineData output = new EngineData();
        output.setData(EngineDataType.SFIXED32, -1);
        return output;
    }
    
    @Override
    public EngineData execute(EngineData input) {
        LOGGER.info("=== PacketSink execute method called ===");
        
        String mimeType = input.getMimeType();
        LOGGER.info("Received data with mime-type: " + mimeType);
        
        if (!mimeType.equalsIgnoreCase(JavaObjectType.JOBJ.mimeType()) && 
            !mimeType.equalsIgnoreCase(EngineDataType.JSON.mimeType())) {
            LOGGER.warning("Invalid input mime type: " + mimeType);
            return createErrorResponse("Invalid input mime type: " + mimeType);
        }
        
        String rawData = (String) input.getData();
        LOGGER.info("Raw data class: " + rawData.getClass().getName());
        
        try {
            JSONObject metadata = new JSONObject(rawData);
            LOGGER.info("Processing JSON metadata: " + metadata.toString());
            
            // Check if this is an error packet
            if (metadata.has("error")) {
                String errorMsg = metadata.getString("error");
                LOGGER.warning("Received error packet: " + errorMsg);
                return createErrorResponse(errorMsg);
            }
            
            // Extract required fields with defaults
            long timestamp = metadata.optLong("processed_timestamp", System.currentTimeMillis());
            int packetSize = metadata.optInt("packet_size", 0);
            int protocol = metadata.optInt("protocol", 0);
            String srcIp = metadata.optString("source_ip", "0.0.0.0");
            String dstIp = metadata.optString("destination_ip", "0.0.0.0");
            int srcPort = metadata.optInt("source_port", 0);
            int dstPort = metadata.optInt("destination_port", 0);
            int totalHeaderLength = metadata.optInt("total_header_length", 0);
            int payloadLength = metadata.optInt("payload_length", packetSize);
            
            // Skip writing if it's an unknown protocol packet
            if (protocol == 0) {
                LOGGER.warning("Skipping unknown protocol packet");
                return createErrorResponse("Unknown protocol packet");
            }
            
            // Write packet data to CSV
            writePacketData(srcIp, timestamp, packetSize, protocol, srcIp, dstIp, srcPort, dstPort, totalHeaderLength, payloadLength);
            
            return createSuccessResponse(timestamp, srcIp, dstIp, packetSize, protocol);
            
        } catch (Exception e) {
            LOGGER.severe("Failed to process metadata: " + e.getMessage());
            return createErrorResponse("Failed to process metadata: " + e.getMessage());
        }
    }
    
    private EngineData createSuccessResponse(long timestamp, String srcIp, String dstIp, int packetSize, int protocol) {
        EngineData output = new EngineData();
        output.setData(EngineDataType.SFIXED32, 1);
        return output;
    }
    
    private EngineData createErrorResponse(String message) {
        EngineData output = new EngineData();
        output.setData(EngineDataType.SFIXED32, -1);
        return output;
    }
    
    private void writePacketData(String ip, long timestamp, int packetSize, int protocol, 
                               String srcIp, String dstIp, int sourcePort, int destPort,
                               int totalHeaderSize, int payloadLength) {
        LOGGER.info("Attempting to write packet metadata for IP: " + ip);
        try {
            // Get or create writer for this IP
            FileWriter writer = writers.computeIfAbsent(ip, k -> {
                try {
                    Path csvPath = outputDir.resolve(k + "_packets.csv");
                    LOGGER.info("Creating new CSV file at absolute path: " + csvPath.toAbsolutePath());
                    
                    // Create parent directories if they don't exist
                    Files.createDirectories(csvPath.getParent());
                    
                    // Create new file or verify it exists
                    File csvFile = csvPath.toFile();
                    boolean isNewFile = csvFile.createNewFile();
                    LOGGER.info("CSV file " + (isNewFile ? "created" : "already exists") + " at: " + csvFile.getAbsolutePath());
                    
                    // Verify file is writable
                    if (!csvFile.canWrite()) {
                        LOGGER.severe("Cannot write to file: " + csvFile.getAbsolutePath());
                        return null;
                    }
                    
                    FileWriter newWriter = new FileWriter(csvFile, true); // append mode
                    // Write CSV header only if it's a new file
                    if (isNewFile) {
                        LOGGER.info("Writing CSV header to new file");
                        newWriter.write("Timestamp,PacketSize,Protocol,SourceIP,DestinationIP,SourcePort,DestinationPort,HeaderSize,PayloadLength\n");
                        newWriter.flush();
                    }
                    LOGGER.info("Successfully created writer for IP: " + k);
                    return newWriter;
                } catch (IOException e) {
                    LOGGER.severe("Failed to create writer for IP " + k + ": " + e.getMessage());
                    e.printStackTrace();
                    return null;
                }
            });
            
            if (writer != null) {
                String csvLine = String.format("%d,%d,%d,%s,%s,%d,%d,%d,%d\n",
                    timestamp,
                    packetSize,
                    protocol,
                    srcIp,
                    dstIp,
                    sourcePort,
                    destPort,
                    totalHeaderSize,
                    payloadLength
                );

                LOGGER.info("Writing CSV line to " + outputDir.resolve(ip + "_packets.csv").toAbsolutePath());
                LOGGER.info("CSV line content: " + csvLine);
                writer.write(csvLine);
                writer.flush();  // Force flush after each write
                LOGGER.info("Successfully wrote and flushed packet metadata for IP: " + ip);
            } else {
                LOGGER.severe("Writer is null for IP: " + ip + ". Cannot write packet metadata.");
            }
        } catch (IOException e) {
            LOGGER.severe("Failed to write packet metadata for IP " + ip + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void flushWriters() {
        for (FileWriter writer : writers.values()) {
            try {
                writer.flush();
            } catch (IOException e) {
                LOGGER.severe("Failed to flush writer: " + e.getMessage());
            }
        }
    }
    
    @Override
    public Set<EngineDataType> getInputDataTypes() {
        Set<EngineDataType> types = ErsapUtil.buildDataTypes(
            JavaObjectType.JOBJ,
            EngineDataType.JSON
        );
        LOGGER.info("Supported input types:");
        for (EngineDataType type : types) {
            LOGGER.info("- " + type.mimeType());
        }
        return types;
    }
    
    @Override
    public Set<EngineDataType> getOutputDataTypes() {
        Set<EngineDataType> types = ErsapUtil.buildDataTypes(EngineDataType.SFIXED32);
        LOGGER.info("Supported output types:");
        for (EngineDataType type : types) {
            LOGGER.info("- " + type.mimeType());
        }
        return types;
    }
    
    @Override
    public String getDescription() {
        return "Writes processed packet metadata to CSV files";
    }
    
    @Override
    public String getVersion() {
        return "1.0.0";
    }
    
    @Override
    public String getAuthor() {
        return "ERSAP Team";
    }
    
    @Override
    public void destroy() {
        // Stop the scheduler
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // Close all writers
        for (Map.Entry<String, FileWriter> entry : writers.entrySet()) {
            try {
                entry.getValue().close();
                LOGGER.info("Closed writer for IP: " + entry.getKey());
            } catch (IOException e) {
                LOGGER.severe("Failed to close writer for IP " + entry.getKey() + ": " + e.getMessage());
            }
        }
        writers.clear();
        LOGGER.info("Destroyed packet sink");
    }
    
    @Override
    public void reset() {
        // Stop the scheduler
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // Close all writers
        for (Map.Entry<String, FileWriter> entry : writers.entrySet()) {
            try {
                entry.getValue().close();
                LOGGER.info("Closed writer for IP: " + entry.getKey());
            } catch (IOException e) {
                LOGGER.severe("Failed to close writer for IP " + entry.getKey() + ": " + e.getMessage());
            }
        }
        writers.clear();
        
        // Reinitialize
        writers = new ConcurrentHashMap<>();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::flushWriters, flushInterval, flushInterval, TimeUnit.MILLISECONDS);
        
        LOGGER.info("Reset packet sink");
    }
} 