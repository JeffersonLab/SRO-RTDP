package org.jlab.ersap.actor.pcap.engine;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jlab.epsci.ersap.engine.Engine;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.ersap.actor.pcap.data.PacketEvent;
import org.json.JSONObject;

/**
 * ERSAP engine for writing PCAP data to files.
 * This engine implements the Engine interface and provides
 * a way to write PCAP data to files within the ERSAP framework.
 */
public class PcapSinkEngine implements Engine {

    private static final Logger LOGGER = Logger.getLogger(PcapSinkEngine.class.getName());

    // Define custom EngineDataType for text and Java objects
    private static final EngineDataType TEXT = new EngineDataType("text/plain", null);
    private static final EngineDataType JOBJ = new EngineDataType("binary/data-jobj", null);

    private String outputDir;
    private boolean initialized = false;
    private int fileCounter = 0;

    @Override
    public EngineData configure(EngineData engineData) {
        if (engineData.getMimeType().equals("application/json")) {
            String jsonString = (String) engineData.getData();
            JSONObject jsonObject = new JSONObject(jsonString);

            try {
                // Set default output directory
                outputDir = "output";

                // Override with values from the configuration
                if (jsonObject.has("outputDir")) {
                    outputDir = jsonObject.getString("outputDir");
                }

                // Create the output directory if it doesn't exist
                Path outputPath = Paths.get(outputDir);
                if (!Files.exists(outputPath)) {
                    Files.createDirectories(outputPath);
                }

                LOGGER.log(Level.INFO, "PcapSinkEngine configured with output directory: {0}", outputDir);

                initialized = true;

                EngineData result = new EngineData();
                result.setData(TEXT, "PcapSinkEngine configured successfully");
                return result;
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error configuring PcapSinkEngine", e);
                EngineData result = new EngineData();
                result.setData(TEXT, "Error: " + e.getMessage());
                return result;
            }
        } else {
            LOGGER.warning("Invalid configuration data type: " + engineData.getMimeType());
            EngineData result = new EngineData();
            result.setData(TEXT, "Error: Invalid configuration data type");
            return result;
        }
    }

    @Override
    public EngineData execute(EngineData engineData) {
        if (!initialized) {
            EngineData result = new EngineData();
            result.setData(TEXT, "Error: Engine not initialized");
            return result;
        }

        try {
            Object data = engineData.getData();
            if (data == null) {
                EngineData result = new EngineData();
                result.setData(TEXT, "Error: No data to write");
                return result;
            }

            // Write the data to a file
            String filename = writeToFile(data);

            EngineData result = new EngineData();
            result.setData(TEXT, "Data written to file: " + filename);
            return result;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error writing data to file", e);
            EngineData result = new EngineData();
            result.setData(TEXT, "Error: " + e.getMessage());
            return result;
        }
    }

    /**
     * Writes the data to a file.
     *
     * @param data The data to write
     * @return The filename
     * @throws IOException If an I/O error occurs
     */
    private String writeToFile(Object data) throws IOException {
        String filename;

        if (data instanceof PacketEvent) {
            PacketEvent event = (PacketEvent) data;
            filename = String.format("%s/packet_%d.txt", outputDir, event.getPacketId());

            // Write the packet event to a text file
            try (FileOutputStream fos = new FileOutputStream(filename)) {
                fos.write(event.toString().getBytes());
            }
        } else {
            // For other types of data, use a counter for the filename
            filename = String.format("%s/data_%d.bin", outputDir, fileCounter++);

            // Write the data to a binary file
            try (FileOutputStream fos = new FileOutputStream(filename);
                    ObjectOutputStream oos = new ObjectOutputStream(fos)) {
                oos.writeObject(data);
            }
        }

        LOGGER.log(Level.FINE, "Wrote data to file: {0}", filename);
        return filename;
    }

    @Override
    public EngineData executeGroup(Set<EngineData> set) {
        // Not implemented for this engine
        return null;
    }

    @Override
    public Set<EngineDataType> getInputDataTypes() {
        Set<EngineDataType> types = new HashSet<>();
        types.add(JOBJ);
        return types;
    }

    @Override
    public Set<EngineDataType> getOutputDataTypes() {
        Set<EngineDataType> types = new HashSet<>();
        types.add(TEXT);
        return types;
    }

    @Override
    public Set<String> getStates() {
        return new HashSet<>();
    }

    @Override
    public String getDescription() {
        return "PCAP Sink Engine: Writes PCAP data to files";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public String getAuthor() {
        return "ERSAP Team";
    }

    @Override
    public void reset() {
        fileCounter = 0;
    }

    @Override
    public void destroy() {
        // Nothing to destroy
    }
}