package org.jlab.ersap.actor.pcap.services;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jlab.epsci.ersap.base.EngineData;
import org.jlab.epsci.ersap.engine.IEngine;
import org.jlab.ersap.actor.pcap.data.PacketEvent;

/**
 * ERSAP engine that processes packet data.
 */
public class PcapProcessorEngine implements IEngine {

    private static final Logger LOGGER = Logger.getLogger(PcapProcessorEngine.class.getName());

    private static final String PROTOCOL_FILTER_KEY = "protocol_filter";
    private static final String IP_FILTER_KEY = "ip_filter";

    private String protocolFilter = "";
    private String ipFilter = "";

    @Override
    public EngineData configure(EngineData input) {
        LOGGER.log(Level.SEVERE, "PcapProcessorEngine: configure called");

        if (input.getMetadata() != null) {
            Map<String, Object> metadata = input.getMetadata();
            if (metadata.containsKey(PROTOCOL_FILTER_KEY)) {
                protocolFilter = (String) metadata.get(PROTOCOL_FILTER_KEY);
                LOGGER.log(Level.SEVERE, "PcapProcessorEngine: Using protocol filter: {0}", protocolFilter);
            }

            if (metadata.containsKey(IP_FILTER_KEY)) {
                ipFilter = (String) metadata.get(IP_FILTER_KEY);
                LOGGER.log(Level.SEVERE, "PcapProcessorEngine: Using IP filter: {0}", ipFilter);
            }
        }

        EngineData output = new EngineData();
        output.setStatus(0);
        output.setDescription("PcapProcessorEngine configured successfully");
        return output;
    }

    @Override
    public EngineData execute(EngineData input) {
        LOGGER.log(Level.FINE, "PcapProcessorEngine: execute called");

        if (input == null || input.getData() == null) {
            LOGGER.log(Level.WARNING, "PcapProcessorEngine: Received null input");
            EngineData output = new EngineData();
            output.setStatus(1);
            output.setDescription("Received null input");
            return output;
        }

        if (!(input.getData() instanceof PacketEvent)) {
            LOGGER.log(Level.WARNING, "PcapProcessorEngine: Received input of unexpected type: {0}",
                    input.getData().getClass().getName());
            EngineData output = new EngineData();
            output.setStatus(1);
            output.setDescription("Received input of unexpected type: " + input.getData().getClass().getName());
            return output;
        }

        PacketEvent event = (PacketEvent) input.getData();

        // Apply filters if configured
        if (shouldFilter(event)) {
            LOGGER.log(Level.FINE, "PcapProcessorEngine: Packet filtered out: {0}", event);
            EngineData output = new EngineData();
            output.setStatus(0);
            output.setDescription("Packet filtered out");
            return output;
        }

        // Process the packet (in this simple example, we just pass it through)
        LOGGER.log(Level.FINE, "PcapProcessorEngine: Processed packet: {0}", event);

        EngineData output = new EngineData();
        output.setData(input.getMimeType(), event);
        output.setStatus(0);
        output.setDescription("Packet processed successfully");
        return output;
    }

    @Override
    public EngineData executeGroup(EngineData input) {
        LOGGER.log(Level.SEVERE, "PcapProcessorEngine: executeGroup not implemented");
        EngineData output = new EngineData();
        output.setStatus(1);
        output.setDescription("executeGroup not implemented");
        return output;
    }

    @Override
    public void reset() {
        LOGGER.log(Level.SEVERE, "PcapProcessorEngine: reset called");
    }

    @Override
    public void destroy() {
        LOGGER.log(Level.SEVERE, "PcapProcessorEngine: destroy called");
    }

    @Override
    public String getDescription() {
        return "ERSAP engine that processes packet data";
    }

    @Override
    public String getName() {
        return "PcapProcessorEngine";
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
        // Apply protocol filter if configured
        if (!protocolFilter.isEmpty() && !protocolFilter.equals(event.getProtocol())) {
            return true;
        }

        // Apply IP filter if configured
        if (!ipFilter.isEmpty() && !ipFilter.equals(event.getSourceIp())
                && !ipFilter.equals(event.getDestinationIp())) {
            return true;
        }

        return false;
    }
}