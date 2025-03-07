package org.jlab.ersap.actor.pcap.engine;

import java.util.Set;
import java.util.logging.Logger;

import org.jlab.epsci.ersap.base.ErsapUtil;
import org.jlab.epsci.ersap.engine.Engine;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.ersap.actor.pcap.proc.IEProcessor;
import org.jlab.ersap.actor.pcap.proc.PcapPacketProcessor;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * ERSAP processor engine that processes PCAP data.
 */
public class PcapProcessorEngine implements Engine {

    private static final Logger LOGGER = Logger.getLogger(PcapProcessorEngine.class.getName());
    
    private static final String CONFIG_EXTRACT_HEADERS = "extract_headers";
    private static final String CONFIG_FILTER_BY_PROTOCOL = "filter_by_protocol";
    private static final String CONFIG_PROTOCOL_FILTER = "protocol_filter";
    
    private static final boolean DEFAULT_EXTRACT_HEADERS = true;
    private static final boolean DEFAULT_FILTER_BY_PROTOCOL = false;
    private static final int DEFAULT_PROTOCOL_FILTER = -1;
    
    private IEProcessor processor;
    
    /**
     * Default constructor.
     */
    public PcapProcessorEngine() {
        // Create default processor
        processor = new PcapPacketProcessor();
    }
    
    @Override
    public EngineData configure(EngineData input) {
        if (input.getMimeType().equals(EngineDataType.JSON.mimeType())) {
            String source = (String) input.getData();
            try {
                JSONObject config = new JSONObject(source);
                
                // Configure the processor
                PcapPacketProcessor packetProcessor = (PcapPacketProcessor) processor;
                
                // Extract configuration parameters
                boolean extractHeaders = config.optBoolean(CONFIG_EXTRACT_HEADERS, DEFAULT_EXTRACT_HEADERS);
                boolean filterByProtocol = config.optBoolean(CONFIG_FILTER_BY_PROTOCOL, DEFAULT_FILTER_BY_PROTOCOL);
                int protocolFilter = config.optInt(CONFIG_PROTOCOL_FILTER, DEFAULT_PROTOCOL_FILTER);
                
                // Apply configuration
                packetProcessor.setExtractHeaders(extractHeaders);
                packetProcessor.setFilterByProtocol(filterByProtocol);
                packetProcessor.setProtocolFilter(protocolFilter);
                
                LOGGER.info("Configured PCAP processor with: extractHeaders=" + extractHeaders + 
                           ", filterByProtocol=" + filterByProtocol + 
                           ", protocolFilter=" + protocolFilter);
                
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
        EngineData output = new EngineData();
        
        try {
            // Get input data
            Object data = input.getData();
            
            // Process the data
            Object processedData = processor.process(data);
            
            if (processedData == null) {
                // Packet was filtered out or processing failed
                return null;
            }
            
            // Set the output data
            output.setData(EngineDataType.BYTES.mimeType(), processedData);
            
            return output;
        } catch (Exception e) {
            LOGGER.severe("Error processing data: " + e.getMessage());
            return buildErrorResponse("Error processing data: " + e.getMessage());
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
        return "Processes PCAP packet data, extracting information and applying filters.";
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
        processor.reset();
    }
    
    @Override
    public void destroy() {
        processor.destruct();
    }
    
    private EngineData buildErrorResponse(String message) {
        EngineData response = new EngineData();
        response.setStatus(EngineStatus.ERROR);
        response.setDescription(message);
        return response;
    }
} 