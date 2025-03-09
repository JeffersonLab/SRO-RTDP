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
