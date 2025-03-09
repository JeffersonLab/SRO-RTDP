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
