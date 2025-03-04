#!/bin/bash

cat > src/main/java/org/jlab/ersap/actor/pcap/standalone/PcapReader.java << 'EOF'
package org.jlab.ersap.actor.pcap.standalone;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.logging.Logger;

/**
 * A standalone PCAP file reader that doesn't depend on the ERSAP framework.
 * This class can read and analyze PCAP files, including the special format
 * used in the CLAS12 data.
 */
public class PcapReader implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(PcapReader.class.getName());

    // PCAP file header constants
    private static final int PCAP_HEADER_SIZE = 24;
    private static final int PACKET_HEADER_SIZE = 16;
    private static final int MAGIC_NUMBER = 0xa1b2c3d4;
    private static final int MAGIC_NUMBER_REVERSED = 0xd4c3b2a1;
    private static final int MAGIC_NUMBER_MODIFIED = 0x4d3cb2a1; // Special case for CLAS12 files

    // Ethernet header constants
    private static final int ETH_HEADER_SIZE = 14;
    private static final int ETH_TYPE_IPV4 = 0x0800;
    private static final int ETH_TYPE_ARP = 0x0806;
    private static final int ETH_TYPE_IPV6 = 0x86DD;

    // IP header constants
    private static final int IP_PROTOCOL_ICMP = 1;
    private static final int IP_PROTOCOL_TCP = 6;
    private static final int IP_PROTOCOL_UDP = 17;

    private final File pcapFile;
    private final FileInputStream fileInputStream;
    private final ByteOrder byteOrder;
    private final int versionMajor;
    private final int versionMinor;
    private final int snaplen;
    private final int network;

    private int packetCount = 0;
    private long totalBytes = 0;

    /**
     * Creates a new PcapReader for the specified PCAP file.
     * 
     * @param pcapFilePath the path to the PCAP file
     * @throws IOException              if an I/O error occurs
     * @throws IllegalArgumentException if the file is not a valid PCAP file
     */
    public PcapReader(String pcapFilePath) throws IOException {
        this.pcapFile = new File(pcapFilePath);

        if (!pcapFile.exists() || !pcapFile.isFile()) {
            throw new IllegalArgumentException("PCAP file does not exist or is not a regular file: " + pcapFilePath);
        }

        LOGGER.info("Opening PCAP file: " + pcapFilePath);
        LOGGER.info("File size: " + pcapFile.length() + " bytes");

        this.fileInputStream = new FileInputStream(pcapFile);

        // Read and parse the PCAP file header
        byte[] headerBytes = new byte[PCAP_HEADER_SIZE];
        int bytesRead = fileInputStream.read(headerBytes);

        if (bytesRead < PCAP_HEADER_SIZE) {
            throw new IllegalArgumentException("PCAP file is too small to contain a valid header");
        }

        ByteBuffer buffer = ByteBuffer.wrap(headerBytes);
        int magicNumber = buffer.getInt();

        LOGGER.info("Magic number: 0x" + Integer.toHexString(magicNumber));

        // Determine byte order based on magic number
        if (magicNumber == MAGIC_NUMBER) {
            this.byteOrder = ByteOrder.BIG_ENDIAN;
            LOGGER.info("PCAP file byte order: BIG_ENDIAN");
        } else if (magicNumber == MAGIC_NUMBER_REVERSED) {
            this.byteOrder = ByteOrder.LITTLE_ENDIAN;
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            LOGGER.info("PCAP file byte order: LITTLE_ENDIAN");
        } else if (magicNumber == MAGIC_NUMBER_MODIFIED) {
            // Special case for CLAS12 files
            this.byteOrder = ByteOrder.LITTLE_ENDIAN;
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            LOGGER.info("PCAP file byte order: LITTLE_ENDIAN (Modified format)");
        } else {
            LOGGER.warning("Unknown PCAP magic number: 0x" + Integer.toHexString(magicNumber));
            LOGGER.warning("Attempting to continue with LITTLE_ENDIAN byte order...");
            this.byteOrder = ByteOrder.LITTLE_ENDIAN;
            buffer.order(ByteOrder.LITTLE_ENDIAN);
        }

        // Read PCAP file header fields
        this.versionMajor = buffer.getShort(4) & 0xFFFF;
        this.versionMinor = buffer.getShort(6) & 0xFFFF;
        int timezone = buffer.getInt(8);
        int sigfigs = buffer.getInt(12);
        this.snaplen = buffer.getInt(16);
        this.network = buffer.getInt(20);

        LOGGER.info("PCAP version: " + versionMajor + "." + versionMinor);
        LOGGER.info("Snaplen: " + snaplen);
        LOGGER.info("Network: " + network);
    }

    /**
     * Reads the next packet from the PCAP file.
     * 
     * @return a byte array containing the packet data, or null if end of file is
     *         reached
     * @throws IOException if an I/O error occurs
     */
    public byte[] readNextPacket() throws IOException {
        // Read packet header (16 bytes)
        byte[] packetHeaderBytes = new byte[PACKET_HEADER_SIZE];
        int headerBytesRead = fileInputStream.read(packetHeaderBytes);

        if (headerBytesRead < PACKET_HEADER_SIZE) {
            // End of file reached
            return null;
        }

        ByteBuffer packetBuffer = ByteBuffer.wrap(packetHeaderBytes).order(byteOrder);

        // Skip timestamp fields (8 bytes)
        packetBuffer.position(8);

        // Read packet length
        int includedLength = packetBuffer.getInt();
        int originalLength = packetBuffer.getInt();

        // Sanity check on packet length
        if (includedLength < 0 || includedLength > snaplen) {
            LOGGER.warning("Invalid packet length: " + includedLength +
                    " at packet #" + (packetCount + 1) +
                    " (exceeds snaplen: " + snaplen + ")");
            return null;
        }

        // Read packet data
        byte[] packetData = new byte[includedLength];
        int dataBytesRead = fileInputStream.read(packetData);

        if (dataBytesRead < includedLength) {
            LOGGER.warning("Incomplete packet data: expected " + includedLength +
                    " bytes, got " + dataBytesRead + " bytes");
            return null;
        }

        // Update statistics
        packetCount++;
        totalBytes += includedLength;

        // Log progress periodically
        if (packetCount % 10000 == 0) {
            LOGGER.info("Processed " + packetCount + " packets (" + totalBytes + " bytes)");
        }

        return packetData;
    }

    /**
     * Analyzes an Ethernet packet and returns a string representation of its headers.
     * 
     * @param packetData the packet data
     * @return a string representation of the packet headers
     */
    public String analyzePacket(byte[] packetData) {
        if (packetData == null || packetData.length < ETH_HEADER_SIZE) {
            return "Packet too small to contain an Ethernet header";
        }

        StringBuilder sb = new StringBuilder();
        ByteBuffer buffer = ByteBuffer.wrap(packetData).order(byteOrder);

        // Parse Ethernet header
        byte[] destMac = new byte[6];
        byte[] srcMac = new byte[6];
        buffer.get(destMac);
        buffer.get(srcMac);
        
        // Read EtherType - note that Ethernet headers are always in big endian
        // regardless of the PCAP file's byte order
        int etherType = ((packetData[12] & 0xFF) << 8) | (packetData[13] & 0xFF);

        sb.append("Ethernet Header:\n");
        sb.append("  Destination MAC: ").append(formatMacAddress(destMac)).append("\n");
        sb.append("  Source MAC: ").append(formatMacAddress(srcMac)).append("\n");
        sb.append("  EtherType: 0x").append(String.format("%04X", etherType)).append(" (").append(getEtherTypeName(etherType)).append(")\n");

        // Parse IP header if this is an IPv4 packet
        if (etherType == ETH_TYPE_IPV4 && packetData.length >= ETH_HEADER_SIZE + 20) {
            int ipOffset = ETH_HEADER_SIZE;
            byte versionAndIhl = packetData[ipOffset];
            int version = (versionAndIhl >> 4) & 0xF;
            int ihl = (versionAndIhl & 0xF) * 4; // IHL is in 4-byte units
            
            if (version == 4 && ihl >= 20) {
                int tos = packetData[ipOffset + 1] & 0xFF;
                int totalLength = ((packetData[ipOffset + 2] & 0xFF) << 8) | (packetData[ipOffset + 3] & 0xFF);
                int identification = ((packetData[ipOffset + 4] & 0xFF) << 8) | (packetData[ipOffset + 5] & 0xFF);
                int flagsAndFragmentOffset = ((packetData[ipOffset + 6] & 0xFF) << 8) | (packetData[ipOffset + 7] & 0xFF);
                int ttl = packetData[ipOffset + 8] & 0xFF;
                int protocol = packetData[ipOffset + 9] & 0xFF;
                int checksum = ((packetData[ipOffset + 10] & 0xFF) << 8) | (packetData[ipOffset + 11] & 0xFF);
                
                byte[] srcIp = new byte[4];
                byte[] dstIp = new byte[4];
                System.arraycopy(packetData, ipOffset + 12, srcIp, 0, 4);
                System.arraycopy(packetData, ipOffset + 16, dstIp, 0, 4);
                
                sb.append("IPv4 Header:\n");
                sb.append("  Version: ").append(version).append("\n");
                sb.append("  Header Length: ").append(ihl).append(" bytes\n");
                sb.append("  Type of Service: 0x").append(String.format("%02X", tos)).append("\n");
                sb.append("  Total Length: ").append(totalLength).append(" bytes\n");
                sb.append("  Identification: 0x").append(String.format("%04X", identification)).append("\n");
                sb.append("  Flags: 0x").append(String.format("%01X", (flagsAndFragmentOffset >> 13) & 0x7)).append("\n");
                sb.append("  Fragment Offset: ").append(flagsAndFragmentOffset & 0x1FFF).append("\n");
                sb.append("  Time to Live: ").append(ttl).append("\n");
                sb.append("  Protocol: ").append(protocol).append(" (").append(getProtocolName(protocol)).append(")\n");
                sb.append("  Header Checksum: 0x").append(String.format("%04X", checksum)).append("\n");
                sb.append("  Source IP: ").append(formatIpAddress(srcIp)).append("\n");
                sb.append("  Destination IP: ").append(formatIpAddress(dstIp)).append("\n");
                
                // Parse TCP or UDP header
                if (protocol == IP_PROTOCOL_TCP && packetData.length >= ipOffset + ihl + 20) {
                    int tcpOffset = ipOffset + ihl;
                    int srcPort = ((packetData[tcpOffset] & 0xFF) << 8) | (packetData[tcpOffset + 1] & 0xFF);
                    int dstPort = ((packetData[tcpOffset + 2] & 0xFF) << 8) | (packetData[tcpOffset + 3] & 0xFF);
                    long seqNum = ((packetData[tcpOffset + 4] & 0xFFL) << 24) | 
                                 ((packetData[tcpOffset + 5] & 0xFFL) << 16) | 
                                 ((packetData[tcpOffset + 6] & 0xFFL) << 8) | 
                                  (packetData[tcpOffset + 7] & 0xFFL);
                    long ackNum = ((packetData[tcpOffset + 8] & 0xFFL) << 24) | 
                                 ((packetData[tcpOffset + 9] & 0xFFL) << 16) | 
                                 ((packetData[tcpOffset + 10] & 0xFFL) << 8) | 
                                  (packetData[tcpOffset + 11] & 0xFFL);
                    int dataOffset = ((packetData[tcpOffset + 12] >> 4) & 0xF) * 4; // Data offset in 4-byte units
                    int flags = ((packetData[tcpOffset + 12] & 0x1) << 8) | (packetData[tcpOffset + 13] & 0xFF);
                    int window = ((packetData[tcpOffset + 14] & 0xFF) << 8) | (packetData[tcpOffset + 15] & 0xFF);
                    int checksum2 = ((packetData[tcpOffset + 16] & 0xFF) << 8) | (packetData[tcpOffset + 17] & 0xFF);
                    int urgentPointer = ((packetData[tcpOffset + 18] & 0xFF) << 8) | (packetData[tcpOffset + 19] & 0xFF);
                    
                    sb.append("TCP Header:\n");
                    sb.append("  Source Port: ").append(srcPort).append("\n");
                    sb.append("  Destination Port: ").append(dstPort).append("\n");
                    sb.append("  Sequence Number: ").append(seqNum).append("\n");
                    sb.append("  Acknowledgment Number: ").append(ackNum).append("\n");
                    sb.append("  Data Offset: ").append(dataOffset).append(" bytes\n");
                    sb.append("  Flags: 0x").append(String.format("%03X", flags)).append(" (");
                    if ((flags & 0x01) != 0) sb.append("FIN ");
                    if ((flags & 0x02) != 0) sb.append("SYN ");
                    if ((flags & 0x04) != 0) sb.append("RST ");
                    if ((flags & 0x08) != 0) sb.append("PSH ");
                    if ((flags & 0x10) != 0) sb.append("ACK ");
                    if ((flags & 0x20) != 0) sb.append("URG ");
                    if ((flags & 0x40) != 0) sb.append("ECE ");
                    if ((flags & 0x80) != 0) sb.append("CWR ");
                    if ((flags & 0x100) != 0) sb.append("NS ");
                    sb.append(")\n");
                    sb.append("  Window: ").append(window).append("\n");
                    sb.append("  Checksum: 0x").append(String.format("%04X", checksum2)).append("\n");
                    sb.append("  Urgent Pointer: ").append(urgentPointer).append("\n");
                    
                    // Calculate payload size
                    int payloadOffset = tcpOffset + dataOffset;
                    int payloadSize = packetData.length - payloadOffset;
                    sb.append("  Payload Size: ").append(payloadSize).append(" bytes\n");
                } else if (protocol == IP_PROTOCOL_UDP && packetData.length >= ipOffset + ihl + 8) {
                    int udpOffset = ipOffset + ihl;
                    int srcPort = ((packetData[udpOffset] & 0xFF) << 8) | (packetData[udpOffset + 1] & 0xFF);
                    int dstPort = ((packetData[udpOffset + 2] & 0xFF) << 8) | (packetData[udpOffset + 3] & 0xFF);
                    int length = ((packetData[udpOffset + 4] & 0xFF) << 8) | (packetData[udpOffset + 5] & 0xFF);
                    int checksum2 = ((packetData[udpOffset + 6] & 0xFF) << 8) | (packetData[udpOffset + 7] & 0xFF);
                    
                    sb.append("UDP Header:\n");
                    sb.append("  Source Port: ").append(srcPort).append("\n");
                    sb.append("  Destination Port: ").append(dstPort).append("\n");
                    sb.append("  Length: ").append(length).append(" bytes\n");
                    sb.append("  Checksum: 0x").append(String.format("%04X", checksum2)).append("\n");
                    
                    // Calculate payload size
                    int payloadSize = length - 8; // UDP header is 8 bytes
                    sb.append("  Payload Size: ").append(payloadSize).append(" bytes\n");
                }
            }
        }
        
        return sb.toString();
    }

    /**
     * Formats a MAC address as a string.
     * 
     * @param mac the MAC address bytes
     * @return the formatted MAC address
     */
    private String formatMacAddress(byte[] mac) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mac.length; i++) {
            sb.append(String.format("%02X", mac[i] & 0xFF));
            if (i < mac.length - 1) {
                sb.append(":");
            }
        }
        return sb.toString();
    }

    /**
     * Formats an IP address as a string.
     * 
     * @param ip the IP address bytes
     * @return the formatted IP address
     */
    private String formatIpAddress(byte[] ip) {
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
     * Gets the name of an EtherType.
     * 
     * @param etherType the EtherType value
     * @return the name of the EtherType
     */
    private String getEtherTypeName(int etherType) {
        switch (etherType) {
            case ETH_TYPE_IPV4:
                return "IPv4";
            case ETH_TYPE_ARP:
                return "ARP";
            case ETH_TYPE_IPV6:
                return "IPv6";
            default:
                return "Unknown";
        }
    }

    /**
     * Gets the name of an IP protocol.
     * 
     * @param protocol the protocol value
     * @return the name of the protocol
     */
    private String getProtocolName(int protocol) {
        switch (protocol) {
            case IP_PROTOCOL_ICMP:
                return "ICMP";
            case IP_PROTOCOL_TCP:
                return "TCP";
            case IP_PROTOCOL_UDP:
                return "UDP";
            default:
                return "Unknown";
        }
    }

    /**
     * Gets the byte order of the PCAP file.
     * 
     * @return the byte order
     */
    public ByteOrder getByteOrder() {
        return byteOrder;
    }

    /**
     * Gets the total number of packets read so far.
     * 
     * @return the packet count
     */
    public int getPacketCount() {
        return packetCount;
    }

    /**
     * Gets the total number of bytes read so far.
     * 
     * @return the total bytes
     */
    public long getTotalBytes() {
        return totalBytes;
    }

    /**
     * Closes the PCAP file.
     * 
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void close() throws IOException {
        if (fileInputStream != null) {
            fileInputStream.close();
            LOGGER.info("PCAP file closed. Total packets: " + packetCount +
                    ", Total bytes: " + totalBytes);
        }
    }

    /**
     * Main method to demonstrate the functionality of the PcapReader.
     * 
     * @param args command-line arguments:
     *             args[0] - path to the PCAP file
     *             args[1] (optional) - maximum number of packets to process (0 for all)
     *             args[2] (optional) - number of packets to display in detail (0 for none)
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java PcapReader <pcap-file-path> [max-packets] [display-packets]");
            System.out.println("  pcap-file-path: Path to the PCAP file to read");
            System.out.println("  max-packets: Maximum number of packets to process (0 for all, default: 0)");
            System.out.println("  display-packets: Number of packets to display in detail (default: 5)");
            return;
        }

        String pcapFilePath = args[0];
        int maxPackets = 0; // 0 means process all packets
        int displayPackets = 5; // Default to showing 5 packets in detail
        
        if (args.length >= 2) {
            try {
                maxPackets = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid max-packets value. Using default (0 - all packets).");
            }
        }
        
        if (args.length >= 3) {
            try {
                displayPackets = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid display-packets value. Using default (5).");
            }
        }

        try (PcapReader reader = new PcapReader(pcapFilePath)) {
            System.out.println("Successfully opened PCAP file: " + pcapFilePath);
            System.out.println("Byte order: " + reader.getByteOrder());
            System.out.println();

            long startTime = System.currentTimeMillis();
            long packetCount = 0;
            long totalBytes = 0;
            
            byte[] packetData;
            while ((packetData = reader.readNextPacket()) != null) {
                packetCount++;
                totalBytes += packetData.length;
                
                // Print detailed analysis for the first N packets
                if (displayPackets > 0 && packetCount <= displayPackets) {
                    System.out.println("Packet #" + packetCount + " (" + packetData.length + " bytes)");
                    System.out.println(reader.analyzePacket(packetData));
                    System.out.println();
                }
                
                if (packetCount % 10000 == 0) {
                    long currentTime = System.currentTimeMillis();
                    double elapsedSeconds = (currentTime - startTime) / 1000.0;
                    double packetsPerSecond = packetCount / elapsedSeconds;
                    double mbProcessed = totalBytes / (1024.0 * 1024.0);
                    System.out.printf("Processed %d packets (%.2f MB) at %.2f packets/sec%n", 
                            packetCount, mbProcessed, packetsPerSecond);
                }
                
                if (maxPackets > 0 && packetCount >= maxPackets) {
                    break;
                }
            }
            
            long endTime = System.currentTimeMillis();
            double elapsedSeconds = (endTime - startTime) / 1000.0;
            double packetsPerSecond = packetCount / elapsedSeconds;
            double mbProcessed = totalBytes / (1024.0 * 1024.0);
            double avgPacketSize = (double) totalBytes / packetCount;
            
            System.out.println("\nProcessing complete:");
            System.out.println("  Total packets: " + packetCount);
            System.out.println("  Total data: " + mbProcessed + " MB");
            System.out.println("  Processing time: " + elapsedSeconds + " seconds");
            System.out.printf("  Processing rate: %.2f packets/second%n", packetsPerSecond);
            System.out.printf("  Average packet size: %.0f bytes%n", avgPacketSize);
        } catch (IOException e) {
            System.err.println("Error reading PCAP file: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid PCAP file: " + e.getMessage());
        }
    }
}
EOF
