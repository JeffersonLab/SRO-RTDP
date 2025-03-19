package org.jlab.epsci.ersap.actor.pcap.proc;

import java.nio.ByteBuffer;
import java.util.logging.Logger;

/**
 * A simple processor for PCAP packet data.
 * This processor extracts basic information from PCAP packets and can perform
 * filtering, transformation, or analysis on the packet data.
 */
public class PcapPacketProcessor implements IEProcessor {
    
    private static final Logger LOGGER = Logger.getLogger(PcapPacketProcessor.class.getName());
    
    // Configuration parameters
    private boolean extractHeaders = true;
    private boolean filterByProtocol = false;
    private int protocolFilter = -1; // -1 means no filter
    
    /**
     * Default constructor.
     */
    public PcapPacketProcessor() {
        // Default initialization
    }
    
    /**
     * Constructor with configuration parameters.
     * 
     * @param extractHeaders Whether to extract packet headers
     * @param filterByProtocol Whether to filter packets by protocol
     * @param protocolFilter Protocol number to filter by (-1 for no filter)
     */
    public PcapPacketProcessor(boolean extractHeaders, boolean filterByProtocol, int protocolFilter) {
        this.extractHeaders = extractHeaders;
        this.filterByProtocol = filterByProtocol;
        this.protocolFilter = protocolFilter;
    }
    
    @Override
    public Object process(Object data) {
        if (data == null) {
            LOGGER.warning("Received null data to process");
            return null;
        }
        
        if (!(data instanceof byte[])) {
            LOGGER.warning("Received non-byte array data: " + data.getClass().getName());
            return data; // Return unprocessed
        }
        
        byte[] packetData = (byte[]) data;
        
        // Create a processed packet object with extracted information
        ProcessedPacket processedPacket = new ProcessedPacket();
        processedPacket.setRawData(packetData);
        
        // Extract basic packet information if enabled
        if (extractHeaders && packetData.length >= 14) { // Minimum Ethernet frame size
            ByteBuffer buffer = ByteBuffer.wrap(packetData);
            
            // Extract Ethernet header (simplified)
            byte[] destMac = new byte[6];
            byte[] srcMac = new byte[6];
            buffer.get(destMac);
            buffer.get(srcMac);
            int etherType = buffer.getShort() & 0xFFFF;
            
            processedPacket.setDestMac(formatMac(destMac));
            processedPacket.setSrcMac(formatMac(srcMac));
            processedPacket.setEtherType(etherType);
            
            // Extract IP header if it's an IP packet (EtherType 0x0800 for IPv4)
            if (etherType == 0x0800 && packetData.length >= 34) {
                int ipVersion = (buffer.get() >> 4) & 0xF;
                buffer.position(buffer.position() + 8); // Skip to protocol field
                int protocol = buffer.get() & 0xFF;
                buffer.position(buffer.position() + 2); // Skip checksum
                byte[] srcIp = new byte[4];
                byte[] destIp = new byte[4];
                buffer.get(srcIp);
                buffer.get(destIp);
                
                processedPacket.setIpVersion(ipVersion);
                processedPacket.setProtocol(protocol);
                processedPacket.setSrcIp(formatIp(srcIp));
                processedPacket.setDestIp(formatIp(destIp));
                
                // Apply protocol filtering if enabled
                if (filterByProtocol && protocolFilter != -1 && protocol != protocolFilter) {
                    return null; // Filter out this packet
                }
            }
        }
        
        // Add timestamp
        processedPacket.setTimestamp(System.currentTimeMillis());
        
        return processedPacket;
    }
    
    @Override
    public void reset() {
        // Reset any state if needed
    }
    
    @Override
    public void destruct() {
        // Clean up resources if needed
    }
    
    /**
     * Format a MAC address as a string.
     */
    private String formatMac(byte[] mac) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mac.length; i++) {
            sb.append(String.format("%02X", mac[i]));
            if (i < mac.length - 1) {
                sb.append(":");
            }
        }
        return sb.toString();
    }
    
    /**
     * Format an IP address as a string.
     */
    private String formatIp(byte[] ip) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ip.length; i++) {
            sb.append(ip[i] & 0xFF);
            if (i < ip.length - 1) {
                sb.append(".");
            }
        }
        return sb.toString();
    }
    
    /**
     * Set whether to extract packet headers.
     */
    public void setExtractHeaders(boolean extractHeaders) {
        this.extractHeaders = extractHeaders;
    }
    
    /**
     * Set whether to filter packets by protocol.
     */
    public void setFilterByProtocol(boolean filterByProtocol) {
        this.filterByProtocol = filterByProtocol;
    }
    
    /**
     * Set the protocol number to filter by.
     */
    public void setProtocolFilter(int protocolFilter) {
        this.protocolFilter = protocolFilter;
    }
    
    /**
     * Class representing a processed packet with extracted information.
     */
    public static class ProcessedPacket {
        private byte[] rawData;
        private String srcMac;
        private String destMac;
        private int etherType;
        private int ipVersion;
        private int protocol;
        private String srcIp;
        private String destIp;
        private long timestamp;
        
        // Getters and setters
        public byte[] getRawData() { return rawData; }
        public void setRawData(byte[] rawData) { this.rawData = rawData; }
        
        public String getSrcMac() { return srcMac; }
        public void setSrcMac(String srcMac) { this.srcMac = srcMac; }
        
        public String getDestMac() { return destMac; }
        public void setDestMac(String destMac) { this.destMac = destMac; }
        
        public int getEtherType() { return etherType; }
        public void setEtherType(int etherType) { this.etherType = etherType; }
        
        public int getIpVersion() { return ipVersion; }
        public void setIpVersion(int ipVersion) { this.ipVersion = ipVersion; }
        
        public int getProtocol() { return protocol; }
        public void setProtocol(int protocol) { this.protocol = protocol; }
        
        public String getSrcIp() { return srcIp; }
        public void setSrcIp(String srcIp) { this.srcIp = srcIp; }
        
        public String getDestIp() { return destIp; }
        public void setDestIp(String destIp) { this.destIp = destIp; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
} 