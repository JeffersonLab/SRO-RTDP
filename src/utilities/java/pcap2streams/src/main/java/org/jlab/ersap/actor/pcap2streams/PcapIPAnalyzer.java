package org.jlab.ersap.actor.pcap2streams;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Analyzes a PCAP file to identify unique IP addresses and their associated
 * packets.
 * This class scans a PCAP file and builds a mapping of IP addresses to packet
 * positions.
 */
public class PcapIPAnalyzer {

    private static final Logger LOGGER = Logger.getLogger(PcapIPAnalyzer.class.getName());

    private static final int PCAP_HEADER_SIZE = 24; // Global header size
    private static final int PACKET_HEADER_SIZE = 16; // Per-packet header size
    private static final int ETHERNET_HEADER_SIZE = 14; // Ethernet header size
    private static final int IP_PROTOCOL_OFFSET = 9; // Offset to IP protocol field
    private static final int IP_HEADER_SRC_ADDR_OFFSET = 12; // Offset to source IP in IP header
    private static final int IP_HEADER_DST_ADDR_OFFSET = 16; // Offset to destination IP in IP header
    private static final int MAX_PACKET_SIZE = 65535; // Maximum packet size (65535 bytes)
    private static final int MIN_PACKET_SIZE = 60; // Minimum packet size (60 bytes)

    private final String pcapFile;
    private final Map<String, Set<Long>> ipToPacketPositions;
    private final Set<String> uniqueIPs;

    /**
     * Creates a new PcapIPAnalyzer for the specified PCAP file.
     * 
     * @param pcapFile the path to the PCAP file to analyze
     */
    public PcapIPAnalyzer(String pcapFile) {
        this.pcapFile = pcapFile;
        this.ipToPacketPositions = new HashMap<>();
        this.uniqueIPs = new HashSet<>();
    }

    /**
     * Analyzes the PCAP file to identify unique IP addresses and their packet
     * positions.
     * 
     * @return a set of unique IP addresses found in the PCAP file
     * @throws IOException if an error occurs reading the PCAP file
     */
    public Set<String> analyze() throws IOException {
        LOGGER.info("Analyzing PCAP file: " + pcapFile);

        try (FileInputStream fis = new FileInputStream(pcapFile)) {
            // Skip the global header
            if (fis.skip(PCAP_HEADER_SIZE) != PCAP_HEADER_SIZE) {
                throw new IOException("Failed to skip global header");
            }

            byte[] packetHeader = new byte[PACKET_HEADER_SIZE];
            byte[] ethernetHeader = new byte[ETHERNET_HEADER_SIZE];
            byte[] ipHeader = new byte[20]; // Minimum IP header size

            long position = PCAP_HEADER_SIZE;
            int packetCount = 0;

            while (fis.available() > 0) {
                // Remember the position of this packet
                long packetPosition = position;

                // Read packet header
                int headerBytesRead = fis.read(packetHeader);
                if (headerBytesRead < PACKET_HEADER_SIZE) {
                    LOGGER.warning("Incomplete packet header at position " + position);
                    break;
                }
                position += PACKET_HEADER_SIZE;

                // Extract packet length from header (bytes 8-11, little-endian)
                int packetLength = ((packetHeader[8] & 0xFF) |
                        ((packetHeader[9] & 0xFF) << 8) |
                        ((packetHeader[10] & 0xFF) << 16) |
                        ((packetHeader[11] & 0xFF) << 24));

                // Validate packet length
                if (packetLength < MIN_PACKET_SIZE) {
                    LOGGER.warning("Invalid packet length: " + packetLength + " at position " + position + 
                                 ". Must be at least " + MIN_PACKET_SIZE + " bytes.");
                    // Skip this packet
                    fis.skip(packetLength);
                    position += packetLength;
                    continue;
                }

                if (packetLength > MAX_PACKET_SIZE) {
                    LOGGER.info("Large packet detected: " + packetLength + " bytes at position " + position + 
                              ". Will be truncated to " + MAX_PACKET_SIZE + " bytes.");
                }

                // Read Ethernet header
                int ethernetBytesRead = fis.read(ethernetHeader);
                if (ethernetBytesRead < ETHERNET_HEADER_SIZE) {
                    LOGGER.warning("Incomplete Ethernet header at position " + position);
                    // Skip the rest of this packet
                    fis.skip(packetLength - ethernetBytesRead);
                    position += packetLength;
                    continue;
                }
                position += ETHERNET_HEADER_SIZE;

                // Check if it's an IP packet (EtherType 0x0800 for IPv4)
                int etherType = ((ethernetHeader[12] & 0xFF) << 8) | (ethernetHeader[13] & 0xFF);
                if (etherType != 0x0800) {
                    // Not an IPv4 packet, skip it
                    fis.skip(packetLength - ETHERNET_HEADER_SIZE);
                    position += (packetLength - ETHERNET_HEADER_SIZE);
                    continue;
                }

                // Read IP header (at least the first 20 bytes)
                int ipBytesRead = fis.read(ipHeader);
                if (ipBytesRead < 20) {
                    LOGGER.warning("Incomplete IP header at position " + position);
                    // Skip the rest of this packet
                    fis.skip(packetLength - ETHERNET_HEADER_SIZE - ipBytesRead);
                    position += (packetLength - ETHERNET_HEADER_SIZE);
                    continue;
                }

                // Extract source and destination IP addresses
                byte[] srcIp = new byte[4];
                byte[] dstIp = new byte[4];
                System.arraycopy(ipHeader, IP_HEADER_SRC_ADDR_OFFSET, srcIp, 0, 4);
                System.arraycopy(ipHeader, IP_HEADER_DST_ADDR_OFFSET, dstIp, 0, 4);

                String srcIpStr = InetAddress.getByAddress(srcIp).getHostAddress();
                String dstIpStr = InetAddress.getByAddress(dstIp).getHostAddress();

                // Add IPs to the unique set
                uniqueIPs.add(srcIpStr);
                uniqueIPs.add(dstIpStr);

                // Map packet position to both source and destination IPs
                ipToPacketPositions.computeIfAbsent(srcIpStr, k -> new HashSet<>()).add(packetPosition);
                ipToPacketPositions.computeIfAbsent(dstIpStr, k -> new HashSet<>()).add(packetPosition);

                // Skip the rest of this packet
                fis.skip(packetLength - ETHERNET_HEADER_SIZE - ipBytesRead);
                position += (packetLength - ETHERNET_HEADER_SIZE - ipBytesRead);

                packetCount++;
                if (packetCount % 10000 == 0) {
                    LOGGER.info("Processed " + packetCount + " packets, found " + uniqueIPs.size() + " unique IPs");
                }
            }

            LOGGER.info("PCAP analysis complete. Processed " + packetCount + " packets, found " + uniqueIPs.size()
                    + " unique IPs");
        }

        return uniqueIPs;
    }

    /**
     * Gets the mapping of IP addresses to packet positions.
     * 
     * @return a map where keys are IP addresses and values are sets of packet
     *         positions
     */
    public Map<String, Set<Long>> getIpToPacketPositions() {
        return ipToPacketPositions;
    }

    /**
     * Gets the set of unique IP addresses found in the PCAP file.
     * 
     * @return a set of unique IP addresses
     */
    public Set<String> getUniqueIPs() {
        return uniqueIPs;
    }
}