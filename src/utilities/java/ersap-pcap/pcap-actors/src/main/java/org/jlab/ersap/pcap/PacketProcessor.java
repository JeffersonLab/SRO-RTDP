package org.jlab.ersap.pcap;

import org.jlab.epsci.ersap.base.ErsapUtil;
import org.jlab.epsci.ersap.engine.Engine;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.ersap.actor.datatypes.JavaObjectType;
import org.json.JSONObject;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.HashSet;
import java.util.Set;

public class PacketProcessor implements Engine {
    
    private static final Logger LOGGER = Logger.getLogger(PacketProcessor.class.getName());
    
    private boolean enableProtocolAnalysis;
    private boolean enablePortAnalysis;
    private Level logLevel;
    
    @Override
    public EngineData configure(EngineData input) {
        String mimeType = input.getMimeType();
        if (mimeType.equalsIgnoreCase(JavaObjectType.JOBJ.mimeType()) || 
            mimeType.equalsIgnoreCase(EngineDataType.JSON.mimeType())) {
            String source = (String) input.getData();
            JSONObject data = new JSONObject(source);
            
            // Get configuration from options
            enableProtocolAnalysis = data.optBoolean("ENABLE_PROTOCOL_ANALYSIS", true);
            enablePortAnalysis = data.optBoolean("ENABLE_PORT_ANALYSIS", true);
            logLevel = Level.parse(data.optString("LOG_LEVEL", "FINE"));
            
            LOGGER.setLevel(logLevel);
            LOGGER.info("Initialized packet processor with protocol analysis: " + enableProtocolAnalysis + 
                       ", port analysis: " + enablePortAnalysis);
        }
        return null;
    }
    
    @Override
    public EngineData execute(EngineData input) {
        EngineData output = new EngineData();
        
        if (input.getMimeType().equalsIgnoreCase(EngineDataType.BYTES.mimeType())) {
            byte[] packet = (byte[]) input.getData();
            
            // Process the packet
            JSONObject result = new JSONObject();
            result.put("processed_timestamp", System.currentTimeMillis());
            result.put("packet_size", packet.length);
            
            // Initialize default values for required fields
            result.put("protocol", 0);  // Unknown protocol
            result.put("source_ip", "0.0.0.0");
            result.put("destination_ip", "0.0.0.0");
            result.put("source_port", 0);
            result.put("destination_port", 0);
            result.put("total_header_length", 0);
            result.put("payload_length", packet.length);
            
            // Extract IP header information
            if (packet.length >= 34) { // Minimum size for Ethernet + IP header
                // Log the first 50 bytes of the packet for analysis
                StringBuilder headerHex = new StringBuilder("Packet header bytes: ");
                for (int i = 0; i < Math.min(50, packet.length); i++) {
                    headerHex.append(String.format("%02X ", packet[i] & 0xFF));
                    if ((i + 1) % 16 == 0) {
                        headerHex.append("\n");
                    }
                }
                LOGGER.info(headerHex.toString());
                
                // Look for IP header signature (0x45) in the first 50 bytes
                int ipHeaderOffset = -1;
                for (int i = 0; i < Math.min(50, packet.length - 20); i++) {
                    if ((packet[i] & 0xFF) == 0x45) {
                        ipHeaderOffset = i;
                        LOGGER.info("Found potential IP header at offset: " + i);
                        break;
                    }
                }
                
                if (ipHeaderOffset == -1) {
                    LOGGER.warning("Could not find IP header signature (0x45)");
                    result.put("error", "IP header not found");
                    output.setData(EngineDataType.JSON, result.toString());
                    return output;
                }
                
                // Verify IP version (should be 4)
                int versionAndIHL = packet[ipHeaderOffset] & 0xFF;
                int version = (versionAndIHL >> 4) & 0x0F;
                int headerLength = (versionAndIHL & 0x0F) * 4;
                
                LOGGER.info(String.format("IP header found at offset %d, version: %d, header length: %d bytes", 
                    ipHeaderOffset, version, headerLength));
                
                if (version == 4) {
                    // Extract IP addresses using the found offset
                    String srcIp = extractIpAddress(packet, ipHeaderOffset + 12);
                    String dstIp = extractIpAddress(packet, ipHeaderOffset + 16);
                    
                    // Log the raw bytes for debugging
                    LOGGER.info(String.format("Source IP bytes: %02X %02X %02X %02X -> %s",
                        packet[ipHeaderOffset + 12] & 0xFF, packet[ipHeaderOffset + 13] & 0xFF,
                        packet[ipHeaderOffset + 14] & 0xFF, packet[ipHeaderOffset + 15] & 0xFF, srcIp));
                    LOGGER.info(String.format("Destination IP bytes: %02X %02X %02X %02X -> %s",
                        packet[ipHeaderOffset + 16] & 0xFF, packet[ipHeaderOffset + 17] & 0xFF,
                        packet[ipHeaderOffset + 18] & 0xFF, packet[ipHeaderOffset + 19] & 0xFF, dstIp));
                    
                    result.put("source_ip", srcIp);
                    result.put("destination_ip", dstIp);
                    
                    // Extract protocol (9 bytes into IP header)
                    int protocol = packet[ipHeaderOffset + 9] & 0xFF;
                    result.put("protocol", protocol);
                    
                    // Extract transport layer information
                    if (packet.length >= ipHeaderOffset + headerLength + 8) {
                        int transportStart = ipHeaderOffset + headerLength;
                        int sourcePort = ((packet[transportStart] & 0xFF) << 8) | (packet[transportStart + 1] & 0xFF);
                        int destPort = ((packet[transportStart + 2] & 0xFF) << 8) | (packet[transportStart + 3] & 0xFF);
                        result.put("source_port", sourcePort);
                        result.put("destination_port", destPort);
                        
                        // Calculate transport header size
                        int transportHeaderSize = protocol == 6 ? // TCP
                            ((packet[transportStart + 12] & 0xF0) >> 4) * 4 : // TCP header size
                            8; // UDP header size
                        result.put("transport_header_length", transportHeaderSize);
                        
                        // Calculate total header size and payload length
                        int totalHeaderSize = headerLength + transportHeaderSize;
                        int payloadLength = packet.length - (ipHeaderOffset + totalHeaderSize);
                        result.put("total_header_length", totalHeaderSize);
                        result.put("payload_length", payloadLength);
                    }
                } else {
                    LOGGER.warning("Non-IPv4 packet detected (version: " + version + ")");
                    result.put("error", "Non-IPv4 packet");
                }
            } else {
                LOGGER.warning("Packet too short: " + packet.length + " bytes");
                result.put("error", "Packet too short");
            }
            
            LOGGER.log(logLevel, "Processed packet metadata: " + result.toString());
            
            output.setData(EngineDataType.JSON, result.toString());
        } else {
            output.setData(EngineDataType.JSON, "Invalid input type");
        }
        
        return output;
    }
    
    private String extractIpAddress(byte[] packet, int offset) {
        // Add detailed logging of each byte
        LOGGER.info(String.format("Extracting IP from bytes at offset %d: %02X.%02X.%02X.%02X",
            offset,
            packet[offset] & 0xFF,
            packet[offset + 1] & 0xFF,
            packet[offset + 2] & 0xFF,
            packet[offset + 3] & 0xFF));

        // Read bytes in little-endian order to match IPBasedStreamClient
        String ip = String.format("%d.%d.%d.%d",
            packet[offset + 3] & 0xFF,  // Most significant byte
            packet[offset + 2] & 0xFF,
            packet[offset + 1] & 0xFF,
            packet[offset] & 0xFF);     // Least significant byte
        
        LOGGER.info("Extracted IP: " + ip);
        return ip;
    }
    
    @Override
    public EngineData executeGroup(Set<EngineData> inputs) {
        return null;
    }
    
    @Override
    public Set<EngineDataType> getInputDataTypes() {
        return ErsapUtil.buildDataTypes(EngineDataType.BYTES, JavaObjectType.JOBJ, EngineDataType.JSON);
    }
    
    @Override
    public Set<EngineDataType> getOutputDataTypes() {
        return ErsapUtil.buildDataTypes(JavaObjectType.JOBJ);
    }
    
    @Override
    public Set<String> getStates() {
        return null;
    }
    
    @Override
    public String getDescription() {
        return "Processes network packets and extracts protocol and port information";
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
    public void reset() {
        LOGGER.info("Resetting packet processor");
    }
    
    @Override
    public void destroy() {
        LOGGER.info("Destroying packet processor");
    }
} 