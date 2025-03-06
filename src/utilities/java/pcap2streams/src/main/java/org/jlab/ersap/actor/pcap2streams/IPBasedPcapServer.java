package org.jlab.ersap.actor.pcap2streams;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
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
    private static final int MAX_PACKET_SIZE = 65536; // Maximum packet size

    private final String pcapFile;
    private final String ipAddress;
    private final int port;
    private final Set<Long> packetPositions;
    private final AtomicBoolean running;
    private ServerSocket serverSocket;

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
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error closing server socket for IP " + ipAddress, e);
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
     * Handles a client connection.
     * 
     * @param clientSocket the client socket
     */
    private void handleClient(Socket clientSocket) {
        int packetCount = 0;

        try (RandomAccessFile pcapRaf = new RandomAccessFile(pcapFile, "r");
                DataOutputStream clientOut = new DataOutputStream(clientSocket.getOutputStream())) {

            LOGGER.info("Waiting before sending data for IP " + ipAddress + "...");
            Thread.sleep(1000); // Wait 1 second before sending data

            byte[] packetHeader = new byte[PACKET_HEADER_SIZE];
            byte[] packetData;

            // Process each packet position for this IP
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
                int packetLength = ((packetHeader[8] & 0xFF) |
                        ((packetHeader[9] & 0xFF) << 8) |
                        ((packetHeader[10] & 0xFF) << 16) |
                        ((packetHeader[11] & 0xFF) << 24));

                // Limit packet size to avoid overwhelming the client
                int actualLength = Math.min(packetLength, MAX_PACKET_SIZE);

                // Read packet data
                packetData = new byte[actualLength];
                int dataBytesRead = pcapRaf.read(packetData, 0, actualLength);

                if (dataBytesRead < actualLength) {
                    LOGGER.warning("Incomplete packet data at position " + position);
                    continue;
                }

                try {
                    // First send the packet length (4 bytes)
                    clientOut.writeInt(actualLength);

                    // Then send the packet data
                    clientOut.write(packetData, 0, actualLength);
                    clientOut.flush();
                    packetCount++;

                    if (packetCount % 100 == 0) {
                        LOGGER.info("Sent " + packetCount + " packets for IP " + ipAddress);
                    }

                    // Small delay between packets to avoid overwhelming the client
                    Thread.sleep(10);
                } catch (SocketException e) {
                    LOGGER.info("Client disconnected from IP " + ipAddress + " server: " + e.getMessage());
                    break;
                } catch (SocketTimeoutException e) {
                    LOGGER.info("Socket timeout for IP " + ipAddress + ": " + e.getMessage());
                    break;
                }
            }

            LOGGER.info("Finished sending packets for IP " + ipAddress);

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