package org.jlab.ersap.actor.pcap.engine;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jlab.epsci.ersap.engine.Engine;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.ersap.actor.pcap.proc.PcapProcessor;
import org.json.JSONObject;

/**
 * ERSAP engine for processing PCAP data.
 * This engine implements the Engine interface and provides
 * a way to process PCAP data within the ERSAP framework.
 */
public class PcapProcessorEngine implements Engine {

    private static final Logger LOGGER = Logger.getLogger(PcapProcessorEngine.class.getName());

    // Define custom EngineDataType for text and bytes
    private static final EngineDataType TEXT = new EngineDataType("text/plain", null);
    private static final EngineDataType BYTES = new EngineDataType("binary/bytes", null);
    private static final EngineDataType JOBJ = new EngineDataType("binary/data-jobj", null);

    private PcapProcessor processor;
    private boolean initialized = false;

    @Override
    public EngineData configure(EngineData engineData) {
        if (engineData.getMimeType().equals("application/json")) {
            String jsonString = (String) engineData.getData();
            JSONObject jsonObject = new JSONObject(jsonString);

            try {
                // Initialize the processor
                processor = new PcapProcessor();
                initialized = true;

                EngineData result = new EngineData();
                result.setData(TEXT, "PcapProcessorEngine configured successfully");
                return result;
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error configuring PcapProcessorEngine", e);
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
                result.setData(TEXT, "Error: No data to process");
                return result;
            }

            // Process the data
            Object processedData = processor.process(data);
            if (processedData == null) {
                EngineData result = new EngineData();
                result.setData(TEXT, "Error: Processing returned null");
                return result;
            }

            EngineData result = new EngineData();
            result.setData(JOBJ, processedData);
            return result;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error processing data", e);
            EngineData result = new EngineData();
            result.setData(TEXT, "Error: " + e.getMessage());
            return result;
        }
    }

    @Override
    public EngineData executeGroup(Set<EngineData> set) {
        // Not implemented for this engine
        return null;
    }

    @Override
    public Set<EngineDataType> getInputDataTypes() {
        Set<EngineDataType> types = new HashSet<>();
        types.add(BYTES);
        return types;
    }

    @Override
    public Set<EngineDataType> getOutputDataTypes() {
        Set<EngineDataType> types = new HashSet<>();
        types.add(JOBJ);
        return types;
    }

    @Override
    public Set<String> getStates() {
        return new HashSet<>();
    }

    @Override
    public String getDescription() {
        return "PCAP Processor Engine: Processes PCAP data";
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
        if (processor != null) {
            processor.reset();
        }
    }

    @Override
    public void destroy() {
        if (processor != null) {
            processor.destruct();
        }
    }
}