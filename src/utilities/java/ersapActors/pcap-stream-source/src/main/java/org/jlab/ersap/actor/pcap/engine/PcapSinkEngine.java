package org.jlab.ersap.actor.pcap.engine;

import java.io.IOException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jlab.epsci.ersap.base.ErsapUtil;
import org.jlab.epsci.ersap.engine.Engine;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.engine.EngineStatus;
import org.jlab.ersap.actor.pcap.sink.FileSink;
import org.jlab.ersap.actor.pcap.sink.IESink;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * ERSAP sink engine that writes processed PCAP data to a destination.
 */
public class PcapSinkEngine implements Engine {

    private static final Logger LOGGER = Logger.getLogger(PcapSinkEngine.class.getName());
    
    private static final String CONFIG_OUTPUT_DIR = "output_directory";
    private static final String CONFIG_FILE_PREFIX = "file_prefix";
    private static final String CONFIG_FILE_EXTENSION = "file_extension";
    private static final String CONFIG_MAX_FILE_SIZE = "max_file_size";
    private static final String CONFIG_APPEND_TIMESTAMP = "append_timestamp";
    
    private static final String DEFAULT_OUTPUT_DIR = "output";
    private static final String DEFAULT_FILE_PREFIX = "pcap_processed";
    private static final String DEFAULT_FILE_EXTENSION = ".dat";
    private static final long DEFAULT_MAX_FILE_SIZE = 100 * 1024 * 1024; // 100 MB
    private static final boolean DEFAULT_APPEND_TIMESTAMP = true;
    
    private IESink sink;
    
    /**
     * Default constructor.
     */
    public PcapSinkEngine() {
        // Create default sink
        sink = new FileSink();
    }
    
    @Override
    public EngineData configure(EngineData input) {
        if (input.getMimeType().equals(EngineDataType.JSON.mimeType())) {
            String source = (String) input.getData();
            try {
                JSONObject config = new JSONObject(source);
                
                // Configure the sink
                if (sink instanceof FileSink) {
                    FileSink fileSink = (FileSink) sink;
                    
                    // Extract configuration parameters
                    String outputDir = config.optString(CONFIG_OUTPUT_DIR, DEFAULT_OUTPUT_DIR);
                    String filePrefix = config.optString(CONFIG_FILE_PREFIX, DEFAULT_FILE_PREFIX);
                    String fileExtension = config.optString(CONFIG_FILE_EXTENSION, DEFAULT_FILE_EXTENSION);
                    long maxFileSize = config.optLong(CONFIG_MAX_FILE_SIZE, DEFAULT_MAX_FILE_SIZE);
                    boolean appendTimestamp = config.optBoolean(CONFIG_APPEND_TIMESTAMP, DEFAULT_APPEND_TIMESTAMP);
                    
                    // Apply configuration
                    fileSink.setOutputDirectory(outputDir);
                    fileSink.setFilePrefix(filePrefix);
                    fileSink.setFileExtension(fileExtension);
                    fileSink.setMaxFileSize(maxFileSize);
                    fileSink.setAppendTimestamp(appendTimestamp);
                    
                    LOGGER.info("Configured PCAP sink with: outputDir=" + outputDir + 
                               ", filePrefix=" + filePrefix + 
                               ", fileExtension=" + fileExtension + 
                               ", maxFileSize=" + maxFileSize + 
                               ", appendTimestamp=" + appendTimestamp);
                }
                
                // Open the sink
                try {
                    if (!sink.isOpen()) {
                        sink.open();
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Error opening sink", e);
                    return buildErrorResponse("Error opening sink: " + e.getMessage());
                }
                
                return null;
            } catch (JSONException e) {
                LOGGER.severe("Error parsing configuration: " + e.getMessage());
                return buildErrorResponse("Invalid configuration format: " + e.getMessage());
            }
        }
        
        return buildErrorResponse("Invalid configuration data type. Expected JSON.");
    }
    
    @Override
    public EngineData execute(EngineData input) {
        try {
            // Get input data
            Object data = input.getData();
            
            // Write the data to the sink
            sink.write(data);
            
            // Return success
            return null;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error writing to sink", e);
            return buildErrorResponse("Error writing to sink: " + e.getMessage());
        }
    }
    
    @Override
    public EngineData executeGroup(Set<EngineData> inputs) {
        // Not implemented for this engine
        return null;
    }
    
    @Override
    public Set<EngineDataType> getInputDataTypes() {
        return ErsapUtil.buildDataTypes(
                EngineDataType.BYTES,
                EngineDataType.JSON
        );
    }
    
    @Override
    public Set<EngineDataType> getOutputDataTypes() {
        return ErsapUtil.buildDataTypes(
                EngineDataType.BYTES
        );
    }
    
    @Override
    public Set<String> getStates() {
        return null;
    }
    
    @Override
    public String getDescription() {
        return "Writes processed PCAP data to a destination.";
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
        try {
            if (sink.isOpen()) {
                sink.flush();
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error flushing sink during reset", e);
        }
    }
    
    @Override
    public void destroy() {
        try {
            if (sink.isOpen()) {
                sink.close();
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error closing sink during destroy", e);
        }
    }
    
    private EngineData buildErrorResponse(String message) {
        EngineData response = new EngineData();
        response.setStatus(EngineStatus.ERROR);
        response.setDescription(message);
        return response;
    }
} 