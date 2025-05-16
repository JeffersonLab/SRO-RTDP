package org.jlab.ersap.actor.pcap2streams;

import java.io.DataOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.InetAddress;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A server that streams PCAP packets for a specific IP address.
 * This server reads packets from a PCAP file and streams only those
 * related to a specific IP address to connected clients.
 */
public class IPBasedPcapServer implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(IPBasedPcapServer.class.getName());

    private static final int PCAP_HEADER_SIZE = 24; // Global header size
    private static final int PACKET_HEADER_SIZE = 16; // Per-packet header size
    private static final int MAX_PACKET_SIZE = 9000; // Maximum packet size (Jumbo frame size)
    private static final int MIN_PACKET_SIZE = 64; // Minimum packet size (Ethernet minimum)
    private static final int CHUNK_SIZE = 8192; // Size of chunks when sending data (8KB)
    private static final int PACKET_DELAY_MS = 1; // Delay between packets in milliseconds

    // Packet truncation constants
    private static final int ETHERNET_HEADER_SIZE = 14; // Ethernet header size
    private static final int IP_HEADER_SIZE = 20; // Minimum IP header size
    private static final int TCP_HEADER_SIZE = 20; // Base TCP header size
    private static final int TCP_OPTIONS_MAX_SIZE = 40; // Maximum TCP options size
    private static final int UDP_HEADER_SIZE = 8; // UDP header size
    private static final int HEADERS_MAX_SIZE = ETHERNET_HEADER_SIZE + IP_HEADER_SIZE + TCP_HEADER_SIZE; // ~54 bytes
    private static final int MAX_PAYLOAD_SIZE = MAX_PACKET_SIZE - HEADERS_MAX_SIZE; // Maximum payload size after headers
    private static final int MIN_PAYLOAD_SIZE = 64; // Minimum payload size to keep

    private static final int IP_HEADER_SRC_ADDR_OFFSET = 12; // IP header source address offset
    private static final int IP_HEADER_DST_ADDR_OFFSET = 16; // IP header destination address offset

    private final String pcapFile;
    private final String ipAddress;
    private final int port;
    private final Set<Long> packetPositions;
    private final AtomicBoolean running;
    private ServerSocket serverSocket;
    private FileWriter csvWriter;  // Add CSV writer

    /**
     * Creates a new IP-based PCAP server.
     * 
     * @param pcapFile        the path to the PCAP file
     * @param ipAddress       the IP address to filter packets for
     * @param port            the port to listen on
     * @param packetPositions the set of packet positions in the PCAP file for this
     *                        IP
     */
    public IPBasedPcapServer(String pcapFile, String ipAddress, int port, Set<Long> packetPositions) {
        this.pcapFile = pcapFile;
        this.ipAddress = ipAddress;
        this.port = port;
        this.packetPositions = packetPositions;
        this.running = new AtomicBoolean(false);
        
        // Initialize CSV writer
        try {
            String csvFile = String.format("/workspaces/ersap-actors/src/utilities/java/pcap2streams/output/header_%s.csv", 
                                         ipAddress.replace('.', '_'));
            LOGGER.info("Creating CSV file: " + csvFile);
            csvWriter = new FileWriter(csvFile);
            // Write CSV header
            String header = "Packet#,Position,Protocol,TotalLength,HeaderLength,PayloadLength,IsTruncated," +
                          "EthernetHeader,IPHeader,TransportHeader,SourceIP,DestIP,SourcePort,DestPort\n";
            csvWriter.write(header);
            csvWriter.flush();
            LOGGER.info("Successfully created CSV file and wrote header");
        } catch (IOException e) {
            LOGGER.severe("Failed to create CSV file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Starts the server.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            Thread serverThread = new Thread(this);
            serverThread.setDaemon(true);
            serverThread.start();
            LOGGER.info("Started server for IP " + ipAddress + " on port " + port + " with " +
                    packetPositions.size() + " packets");
        }
    }

    /**
     * Stops the server.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
                if (csvWriter != null) {
                    csvWriter.close();
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error closing resources for IP " + ipAddress, e);
            }
            LOGGER.info("Stopped server for IP " + ipAddress + " on port " + port);
        }
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setSoTimeout(1000); // 1 second timeout for accept()

            while (running.get()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    LOGGER.info("Client connected to IP " + ipAddress + " server from " +
                            clientSocket.getRemoteSocketAddress());

                    // Handle client in a new thread
                    Thread clientThread = new Thread(() -> handleClient(clientSocket));
                    clientThread.setDaemon(true);
                    clientThread.start();
                } catch (SocketTimeoutException e) {
                    // This is expected due to the timeout on accept()
                    continue;
                } catch (IOException e) {
                    if (running.get()) {
                        LOGGER.log(Level.WARNING, "Error accepting client connection for IP " + ipAddress, e);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error starting server for IP " + ipAddress, e);
        } finally {
            stop();
        }
    }

    /**
     * Truncates a packet while preserving essential header information and initial payload.
     * Ensures the final packet size is <= MAX_PACKET_SIZE (9000 bytes).
     * 
     * @param originalPacket the original packet data
     * @return the truncated packet containing headers and initial payload
     */
    private byte[] truncatePacket(byte[] originalPacket) {
        if (originalPacket.length <= MAX_PACKET_SIZE) {
            return originalPacket;
        }

        // Extract protocol from IP header (offset 9 in IP header)
        int protocol = originalPacket[ETHERNET_HEADER_SIZE + 9] & 0xFF;
        
        // Determine header sizes based on protocol
        int transportHeaderSize;
        if (protocol == 6) { // TCP
            // Get TCP header length (including options) from the Data Offset field
            int tcpDataOffset = ((originalPacket[ETHERNET_HEADER_SIZE + IP_HEADER_SIZE + 12] & 0xF0) >> 4) * 4;
            transportHeaderSize = Math.min(tcpDataOffset, TCP_HEADER_SIZE + TCP_OPTIONS_MAX_SIZE);
        } else if (protocol == 17) { // UDP
            transportHeaderSize = UDP_HEADER_SIZE;
        } else {
            LOGGER.warning("Unknown protocol: " + protocol + ". Using TCP header size as default.");
            transportHeaderSize = TCP_HEADER_SIZE;
        }
        
        // Calculate total header size
        int totalHeaderSize = ETHERNET_HEADER_SIZE + IP_HEADER_SIZE + transportHeaderSize;
        
        // Log packet structure for debugging
        if (LOGGER.isLoggable(Level.FINE)) {
            StringBuilder packetInfo = new StringBuilder();
            packetInfo.append("Packet structure:\n");
            packetInfo.append("Ethernet header (14 bytes): ");
            for (int i = 0; i < ETHERNET_HEADER_SIZE; i++) {
                packetInfo.append(String.format("%02X ", originalPacket[i] & 0xFF));
            }
            packetInfo.append("\nIP header (20 bytes): ");
            for (int i = ETHERNET_HEADER_SIZE; i < ETHERNET_HEADER_SIZE + IP_HEADER_SIZE; i++) {
                packetInfo.append(String.format("%02X ", originalPacket[i] & 0xFF));
            }
            packetInfo.append("\nTransport header (").append(transportHeaderSize).append(" bytes): ");
            for (int i = ETHERNET_HEADER_SIZE + IP_HEADER_SIZE; 
                 i < ETHERNET_HEADER_SIZE + IP_HEADER_SIZE + transportHeaderSize; i++) {
                packetInfo.append(String.format("%02X ", originalPacket[i] & 0xFF));
            }
            LOGGER.fine(packetInfo.toString());
        }
        
        // Calculate how much payload we can keep
        int maxPayloadSize = MAX_PACKET_SIZE - totalHeaderSize;
        int payloadToCopy = Math.min(maxPayloadSize, originalPacket.length - totalHeaderSize);
        if (payloadToCopy < 0) {
            payloadToCopy = 0;
        }
        
        // Create truncated packet with headers + payload
        byte[] truncatedPacket = new byte[totalHeaderSize + payloadToCopy];
        
        // Copy all headers
        System.arraycopy(originalPacket, 0, truncatedPacket, 0, totalHeaderSize);
        
        // Copy payload if available
        if (payloadToCopy > 0) {
            System.arraycopy(originalPacket, totalHeaderSize, 
                           truncatedPacket, totalHeaderSize, payloadToCopy);
        }
        
        // Verify final size
        if (truncatedPacket.length > MAX_PACKET_SIZE) {
            LOGGER.warning("Truncated packet size (" + truncatedPacket.length + 
                         " bytes) exceeds maximum size (" + MAX_PACKET_SIZE + 
                         " bytes). This should never happen!");
        }
        
        return truncatedPacket;
    }

    /**
     * Handles a client connection.
     * 
     * @param clientSocket the client socket
     */
    private void handleClient(Socket clientSocket) {
        int packetCount = 0;
        int successCount = 0;
        int truncatedCount = 0;

        try (RandomAccessFile pcapRaf = new RandomAccessFile(pcapFile, "r");
                DataOutputStream clientOut = new DataOutputStream(clientSocket.getOutputStream())) {

            LOGGER.info("Waiting before sending data for IP " + ipAddress + "...");
            Thread.sleep(1000); // Wait 1 second before sending data

            byte[] packetHeader = new byte[PACKET_HEADER_SIZE];
            byte[] packetData;

            // Loop indefinitely, sending packets in packetPositions repeatedly
            while (running.get() && !clientSocket.isClosed()) {
                for (Long position : packetPositions) {
                    if (!running.get() || clientSocket.isClosed()) {
                        break;
                    }

                    // Seek to the packet position
                    pcapRaf.seek(position);

                    // Read packet header
                    int headerBytesRead = pcapRaf.read(packetHeader);
                    if (headerBytesRead < PACKET_HEADER_SIZE) {
                        LOGGER.warning("Incomplete packet header at position " + position);
                        continue;
                    }

                    // Extract packet length from header (bytes 8-11, little-endian)
                    long packetLengthLong = ((packetHeader[8] & 0xFFL) |
                            ((packetHeader[9] & 0xFFL) << 8) |
                            ((packetHeader[10] & 0xFFL) << 16) |
                            ((packetHeader[11] & 0xFFL) << 24));

                    // Validate minimum packet size
                    if (packetLengthLong < MIN_PACKET_SIZE) {
                        LOGGER.warning("Packet too small: " + packetLengthLong + " bytes at position " + position + 
                                     ". Minimum size is " + MIN_PACKET_SIZE + " bytes. Skipping packet.");
                        continue;
                    }

                    int actualLength = (int) packetLengthLong;
                    boolean isTruncated = false;

                    // Read packet data
                    try {
                        packetData = new byte[actualLength];
                    } catch (NegativeArraySizeException e) {
                        LOGGER.warning("Invalid packet length (negative): " + actualLength + " at position " + position
                                + ". Skipping packet.");
                        continue;
                    }
                    int dataBytesRead = pcapRaf.read(packetData, 0, actualLength);

                    if (dataBytesRead < actualLength) {
                        LOGGER.warning("Incomplete packet data at position " + position + 
                                     ". Expected " + actualLength + " bytes, got " + dataBytesRead);
                        continue;
                    }

                    // Extract protocol and header information
                    int protocol = packetData[ETHERNET_HEADER_SIZE + 9] & 0xFF;
                    int transportHeaderSize;
                    if (protocol == 6) { // TCP
                        int tcpDataOffset = ((packetData[ETHERNET_HEADER_SIZE + IP_HEADER_SIZE + 12] & 0xF0) >> 4) * 4;
                        transportHeaderSize = Math.min(tcpDataOffset, TCP_HEADER_SIZE + TCP_OPTIONS_MAX_SIZE);
                    } else if (protocol == 17) { // UDP
                        transportHeaderSize = UDP_HEADER_SIZE;
                    } else {
                        transportHeaderSize = TCP_HEADER_SIZE;
                    }
                    
                    int totalHeaderSize = ETHERNET_HEADER_SIZE + IP_HEADER_SIZE + transportHeaderSize;
                    
                    // Extract IP addresses and ports
                    byte[] srcIp = new byte[4];
                    byte[] dstIp = new byte[4];
                    System.arraycopy(packetData, ETHERNET_HEADER_SIZE + 12, srcIp, 0, 4);
                    System.arraycopy(packetData, ETHERNET_HEADER_SIZE + 16, dstIp, 0, 4);

                    String sourceIP = InetAddress.getByAddress(srcIp).getHostAddress();
                    String destIP = InetAddress.getByAddress(dstIp).getHostAddress();
                    
                    int sourcePort = ((packetData[ETHERNET_HEADER_SIZE + IP_HEADER_SIZE] & 0xFF) << 8) |
                                   (packetData[ETHERNET_HEADER_SIZE + IP_HEADER_SIZE + 1] & 0xFF);
                    int destPort = ((packetData[ETHERNET_HEADER_SIZE + IP_HEADER_SIZE + 2] & 0xFF) << 8) |
                                 (packetData[ETHERNET_HEADER_SIZE + IP_HEADER_SIZE + 3] & 0xFF);

                    if (actualLength > MAX_PACKET_SIZE) {
                        packetData = truncatePacket(packetData);
                        actualLength = packetData.length;
                        isTruncated = true;
                        truncatedCount++;
                    }

                    if (packetCount % 10 == 0) {
                        LOGGER.info("Processing packet #" + packetCount + " for IP " + ipAddress +
                                ", length=" + actualLength +
                                ", position=" + position +
                                (isTruncated ? " (truncated)" : ""));
                        StringBuilder headerHex = new StringBuilder("Header bytes: ");
                        for (int i = 0; i < PACKET_HEADER_SIZE; i++) {
                            headerHex.append(String.format("%02X ", packetHeader[i] & 0xFF));
                        }
                        LOGGER.info(headerHex.toString());
                        StringBuilder dataHex = new StringBuilder("Data bytes (tcpdump format):\n");
                        int bytesToLog = Math.min(actualLength, 64);
                        for (int i = 0; i < bytesToLog; i += 16) {
                            dataHex.append(String.format("0x%04x:  ", i));
                            for (int j = 0; j < 16 && (i + j) < bytesToLog; j++) {
                                dataHex.append(String.format("%02x ", packetData[i + j] & 0xFF));
                            }
                            dataHex.append("\n");
                        }
                        LOGGER.info(dataHex.toString());
                        StringBuilder ipHex = new StringBuilder("IP addresses in hex:\n");
                        ipHex.append("Source IP bytes: ");
                        for (int i = 0; i < 4; i++) {
                            ipHex.append(String.format("%02x ", srcIp[i] & 0xFF));
                        }
                        ipHex.append("\nDest IP bytes: ");
                        for (int i = 0; i < 4; i++) {
                            ipHex.append(String.format("%02x ", dstIp[i] & 0xFF));
                        }
                        LOGGER.info(ipHex.toString());
                        LOGGER.info("Attempting to write to CSV: " + String.format("%d,%d,%d,%d,%d,%d,%b,%d,%d,%d,%s,%s,%d,%d\n",
                            packetCount, position, protocol, actualLength, totalHeaderSize,
                            actualLength - totalHeaderSize, isTruncated,
                            ETHERNET_HEADER_SIZE, IP_HEADER_SIZE, transportHeaderSize,
                            sourceIP, destIP, sourcePort, destPort));
                    }

                    try {
                        if (csvWriter != null) {
                            String csvLine = String.format("%d,%d,%d,%d,%d,%d,%b,%d,%d,%d,%s,%s,%d,%d\n",
                                packetCount, position, protocol, actualLength, totalHeaderSize,
                                actualLength - totalHeaderSize, isTruncated,
                                ETHERNET_HEADER_SIZE, IP_HEADER_SIZE, transportHeaderSize,
                                sourceIP, destIP, sourcePort, destPort);
                            LOGGER.info("Writing to CSV: " + csvLine);
                            csvWriter.write(csvLine);
                            csvWriter.flush();
                            LOGGER.info("Successfully wrote to CSV");
                        } else {
                            LOGGER.severe("CSV writer is null! Cannot write to CSV file");
                        }
                    } catch (IOException e) {
                        LOGGER.severe("Failed to write to CSV file: " + e.getMessage());
                        e.printStackTrace();
                    }

                    try {
                        clientOut.writeInt(Integer.reverseBytes(actualLength) >>> 32);
                        clientOut.flush();
                        int offset = 0;
                        while (offset < actualLength) {
                            int chunkLength = Math.min(CHUNK_SIZE, actualLength - offset);
                            clientOut.write(packetData, offset, chunkLength);
                            clientOut.flush();
                            offset += chunkLength;
                        }
                        packetCount++;
                        successCount++;
                        if (packetCount % 100 == 0) {
                            LOGGER.info("Sent " + packetCount + " packets for IP " + ipAddress + 
                                      " (" + truncatedCount + " truncated)");
                        }
                        Thread.sleep(PACKET_DELAY_MS);
                    } catch (SocketException e) {
                        LOGGER.info("Client disconnected from IP " + ipAddress + " server: " + e.getMessage());
                        return;
                    } catch (SocketTimeoutException e) {
                        LOGGER.info("Socket timeout for IP " + ipAddress + ": " + e.getMessage());
                        return;
                    }
                }
            }

            LOGGER.info("Finished sending packets for IP " + ipAddress +
                    ". Total packets: " + packetCount +
                    ", Successfully sent: " + successCount +
                    ", Truncated: " + truncatedCount);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error handling client for IP " + ipAddress, e);
        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, "Thread interrupted for IP " + ipAddress, e);
            Thread.currentThread().interrupt();
        } finally {
            try {
                if (!clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error closing client socket for IP " + ipAddress, e);
            }
            LOGGER.info("Client handler finished after sending " + packetCount + " packets for IP " + ipAddress);
        }
    }

    /**
     * Gets the IP address this server is handling.
     * 
     * @return the IP address
     */
    public String getIpAddress() {
        return ipAddress;
    }

    /**
     * Gets the port this server is listening on.
     * 
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * Gets the number of packets this server will stream.
     * 
     * @return the number of packets
     */
    public int getPacketCount() {
        return packetPositions.size();
    }
}