package org.jlab.ersap.actor.pcap2streams;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.util.logging.Logger;

/**
 * Analyzes PCAP packets and extracts detailed information into CSV format.
 */
public class PcapPacketAnalyzer {
    private static final Logger LOGGER = Logger.getLogger(PcapPacketAnalyzer.class.getName());

    // Header size constants
    private static final int PCAP_HEADER_SIZE = 24; // Global header size
    private static final int PACKET_HEADER_SIZE = 16; // Per-packet header size
    private static final int ETHERNET_HEADER_SIZE = 14; // Ethernet header size
    private static final int IP_HEADER_SIZE = 20; // Minimum IP header size
    private static final int TCP_HEADER_SIZE = 20; // Base TCP header size
    private static final int UDP_HEADER_SIZE = 8; // UDP header size

    // Offset constants
    private static final int IP_PROTOCOL_OFFSET = 9; // Offset to IP protocol field
    private static final int IP_HEADER_SRC_ADDR_OFFSET = 12; // Offset to source IP in IP header
    private static final int IP_HEADER_DST_ADDR_OFFSET = 16; // Offset to destination IP in IP header

    // Size limits
    private static final int MAX_PACKET_SIZE = 65535; // Maximum packet size
    private static final int MIN_PACKET_SIZE = 60; // Minimum packet size

    private final String pcapFile;
    private final String csvFile;
    private FileWriter csvWriter;

    public PcapPacketAnalyzer(String pcapFile, String csvFile) {
        this.pcapFile = pcapFile;
        this.csvFile = csvFile;
    }

    /**
     * Analyzes the PCAP file and writes packet information to CSV.
     */
    public void analyze() throws IOException {
        LOGGER.info("Analyzing PCAP file: " + pcapFile);

        // Initialize CSV writer with headers
        csvWriter = new FileWriter(csvFile);
        writeCSVHeader();

        try (FileInputStream fis = new FileInputStream(pcapFile)) {
            // Skip the global header
            if (fis.skip(PCAP_HEADER_SIZE) != PCAP_HEADER_SIZE) {
                throw new IOException("Failed to skip global header");
            }

            byte[] packetHeader = new byte[PACKET_HEADER_SIZE];
            byte[] ethernetHeader = new byte[ETHERNET_HEADER_SIZE];
            byte[] ipHeader = new byte[IP_HEADER_SIZE];

            long position = PCAP_HEADER_SIZE;
            int packetCount = 0;

            while (fis.available() > 0) {
                // Read packet header
                int headerBytesRead = fis.read(packetHeader);
                if (headerBytesRead < PACKET_HEADER_SIZE) {
                    LOGGER.warning("Incomplete packet header at position " + position);
                    break;
                }

                // Extract packet length and timestamp
                long timestamp = ((packetHeader[0] & 0xFFL) |
                        ((packetHeader[1] & 0xFFL) << 8) |
                        ((packetHeader[2] & 0xFFL) << 16) |
                        ((packetHeader[3] & 0xFFL) << 24));
                
                int packetLength = ((packetHeader[8] & 0xFF) |
                        ((packetHeader[9] & 0xFF) << 8) |
                        ((packetHeader[10] & 0xFF) << 16) |
                        ((packetHeader[11] & 0xFF) << 24));

                position += PACKET_HEADER_SIZE;

                // Validate packet length
                if (packetLength < MIN_PACKET_SIZE || packetLength > MAX_PACKET_SIZE) {
                    LOGGER.warning("Invalid packet length: " + packetLength + " at position " + position);
                    fis.skip(packetLength);
                    position += packetLength;
                    continue;
                }

                // Read Ethernet header
                int ethernetBytesRead = fis.read(ethernetHeader);
                if (ethernetBytesRead < ETHERNET_HEADER_SIZE) {
                    LOGGER.warning("Incomplete Ethernet header at position " + position);
                    fis.skip(packetLength - ethernetBytesRead);
                    position += packetLength;
                    continue;
                }
                position += ETHERNET_HEADER_SIZE;

                // Check if it's an IP packet (EtherType 0x0800 for IPv4)
                int etherType = ((ethernetHeader[12] & 0xFF) << 8) | (ethernetHeader[13] & 0xFF);
                if (etherType != 0x0800) {
                    fis.skip(packetLength - ETHERNET_HEADER_SIZE);
                    position += (packetLength - ETHERNET_HEADER_SIZE);
                    continue;
                }

                // Read IP header
                int ipBytesRead = fis.read(ipHeader);
                if (ipBytesRead < IP_HEADER_SIZE) {
                    LOGGER.warning("Incomplete IP header at position " + position);
                    fis.skip(packetLength - ETHERNET_HEADER_SIZE - ipBytesRead);
                    position += (packetLength - ETHERNET_HEADER_SIZE);
                    continue;
                }

                // Extract protocol
                int protocol = ipHeader[IP_PROTOCOL_OFFSET] & 0xFF;

                // Extract source and destination IP addresses
                byte[] srcIp = new byte[4];
                byte[] dstIp = new byte[4];
                System.arraycopy(ipHeader, IP_HEADER_SRC_ADDR_OFFSET, srcIp, 0, 4);
                System.arraycopy(ipHeader, IP_HEADER_DST_ADDR_OFFSET, dstIp, 0, 4);

                String srcIpStr = InetAddress.getByAddress(srcIp).getHostAddress();
                String dstIpStr = InetAddress.getByAddress(dstIp).getHostAddress();

                // Extract ports based on protocol
                int sourcePort = 0;
                int destPort = 0;
                int transportHeaderSize = 0;

                byte[] transportHeader = new byte[20]; // Max size between TCP and UDP
                int transportBytesRead = fis.read(transportHeader);

                if (protocol == 6) { // TCP
                    if (transportBytesRead >= TCP_HEADER_SIZE) {
                        sourcePort = ((transportHeader[0] & 0xFF) << 8) | (transportHeader[1] & 0xFF);
                        destPort = ((transportHeader[2] & 0xFF) << 8) | (transportHeader[3] & 0xFF);
                        transportHeaderSize = ((transportHeader[12] & 0xF0) >> 4) * 4;
                    }
                } else if (protocol == 17) { // UDP
                    if (transportBytesRead >= UDP_HEADER_SIZE) {
                        sourcePort = ((transportHeader[0] & 0xFF) << 8) | (transportHeader[1] & 0xFF);
                        destPort = ((transportHeader[2] & 0xFF) << 8) | (transportHeader[3] & 0xFF);
                        transportHeaderSize = UDP_HEADER_SIZE;
                    }
                }

                // Calculate total header size and payload length
                int totalHeaderSize = ETHERNET_HEADER_SIZE + IP_HEADER_SIZE + transportHeaderSize;
                int payloadLength = packetLength - totalHeaderSize;

                // Write packet data to CSV
                writePacketToCSV(packetCount, position, timestamp, protocol, packetLength,
                        totalHeaderSize, payloadLength, srcIpStr, dstIpStr, sourcePort, destPort);

                // Skip the rest of the packet
                int remainingBytes = packetLength - ETHERNET_HEADER_SIZE - IP_HEADER_SIZE - transportBytesRead;
                if (remainingBytes > 0) {
                    fis.skip(remainingBytes);
                }
                position += (packetLength - ETHERNET_HEADER_SIZE - IP_HEADER_SIZE);

                packetCount++;
                if (packetCount % 10000 == 0) {
                    LOGGER.info("Processed " + packetCount + " packets");
                    csvWriter.flush();
                }
            }

            LOGGER.info("Analysis complete. Processed " + packetCount + " packets");
            csvWriter.flush();
        } finally {
            if (csvWriter != null) {
                csvWriter.close();
            }
        }
    }

    private void writeCSVHeader() throws IOException {
        csvWriter.write("PacketNumber,Position,Timestamp,Protocol,TotalLength,HeaderSize,PayloadLength," +
                "SourceIP,DestinationIP,SourcePort,DestinationPort\n");
    }

    private void writePacketToCSV(int packetNumber, long position, long timestamp, int protocol,
            int totalLength, int headerSize, int payloadLength, String sourceIP, String destIP,
            int sourcePort, int destPort) throws IOException {
        csvWriter.write(String.format("%d,%d,%d,%d,%d,%d,%d,%s,%s,%d,%d\n",
                packetNumber, position, timestamp, protocol, totalLength, headerSize, payloadLength,
                sourceIP, destIP, sourcePort, destPort));
    }

    /**
     * Main method to run the analyzer from command line.
     * 
     * @param args command line arguments: [pcapFile] [csvFile]
     */
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: PcapPacketAnalyzer <pcap_file> <csv_file>");
            System.exit(1);
        }

        String pcapFile = args[0];
        String csvFile = args[1];

        try {
            PcapPacketAnalyzer analyzer = new PcapPacketAnalyzer(pcapFile, csvFile);
            analyzer.analyze();
        } catch (IOException e) {
            System.err.println("Error analyzing PCAP file: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
} 