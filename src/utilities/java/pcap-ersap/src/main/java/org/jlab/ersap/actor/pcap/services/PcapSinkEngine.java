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
 * ERSAP engine that writes packet data to files.
 */
public class PcapSinkEngine implements IEngine {

    private static final Logger LOGGER = Logger.getLogger(PcapSinkEngine.class.getName());

    private static final String OUTPUT_DIR_KEY = "output_dir";
    private static final String DEFAULT_OUTPUT_DIR = "/workspace/src/utilities/java/pcap-ersap/output";

    private final Map<String, BufferedWriter> writers = new ConcurrentHashMap<>();
    private String outputDir = DEFAULT_OUTPUT_DIR;
    private int packetCount = 0;

    @Override
    public EngineData configure(EngineData input) {
        LOGGER.log(Level.SEVERE, "PcapSinkEngine: configure called");

        if (input.getMetadata() != null) {
            Map<String, Object> metadata = input.getMetadata();
            if (metadata.containsKey(OUTPUT_DIR_KEY)) {
                outputDir = (String) metadata.get(OUTPUT_DIR_KEY);
                LOGGER.log(Level.SEVERE, "PcapSinkEngine: Using output directory: {0}", outputDir);
            }
        }

        // Create output directory if it doesn't exist
        Path outputPath = Paths.get(outputDir);
        if (!Files.exists(outputPath)) {
            try {
                Files.createDirectories(outputPath);
                LOGGER.log(Level.SEVERE, "PcapSinkEngine: Created output directory: {0}", outputDir);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "PcapSinkEngine: Error creating output directory: {0}", e.getMessage());
                EngineData output = new EngineData();
                output.setStatus(1);
                output.setDescription("Error creating output directory: " + e.getMessage());
                return output;
            }
        } else {
            LOGGER.log(Level.SEVERE, "PcapSinkEngine: Using existing output directory: {0}", outputDir);
        }

        EngineData output = new EngineData();
        output.setStatus(0);
        output.setDescription("PcapSinkEngine configured successfully");
        return output;
    }

    @Override
    public EngineData execute(EngineData input) {
        LOGGER.log(Level.FINE, "PcapSinkEngine: execute called");

        if (input == null || input.getData() == null) {
            LOGGER.log(Level.WARNING, "PcapSinkEngine: Received null input");
            EngineData output = new EngineData();
            output.setStatus(1);
            output.setDescription("Received null input");
            return output;
        }

        if (!(input.getData() instanceof PacketEvent)) {
            LOGGER.log(Level.WARNING, "PcapSinkEngine: Received input of unexpected type: {0}",
                    input.getData().getClass().getName());
            EngineData output = new EngineData();
            output.setStatus(1);
            output.setDescription("Received input of unexpected type: " + input.getData().getClass().getName());
            return output;
        }

        PacketEvent event = (PacketEvent) input.getData();

        try {
            writePacket(event);
            packetCount++;

            LOGGER.log(Level.FINE, "PcapSinkEngine: Wrote packet #{0} to file", event.getPacketId());

            EngineData output = new EngineData();
            output.setStatus(0);
            output.setDescription("Packet written to file successfully");
            return output;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "PcapSinkEngine: Error writing packet to file: {0}", e.getMessage());
            EngineData output = new EngineData();
            output.setStatus(1);
            output.setDescription("Error writing packet to file: " + e.getMessage());
            return output;
        }
    }

    @Override
    public EngineData executeGroup(EngineData input) {
        LOGGER.log(Level.SEVERE, "PcapSinkEngine: executeGroup not implemented");
        EngineData output = new EngineData();
        output.setStatus(1);
        output.setDescription("executeGroup not implemented");
        return output;
    }

    @Override
    public void reset() {
        LOGGER.log(Level.SEVERE, "PcapSinkEngine: reset called, total packets written: {0}", packetCount);
        closeWriters();
        packetCount = 0;
    }

    @Override
    public void destroy() {
        LOGGER.log(Level.SEVERE, "PcapSinkEngine: destroy called, total packets written: {0}", packetCount);
        closeWriters();
    }

    @Override
    public String getDescription() {
        return "ERSAP engine that writes packet data to files";
    }

    @Override
    public String getName() {
        return "PcapSinkEngine";
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