package scripts;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A mock PCAP server that reads a PCAP file and sends its contents over a socket.
 * This is used for testing the MultiSocketSourceEngine.
 */
public class MockPcapServer {
    
    private static final Logger LOGGER = Logger.getLogger(MockPcapServer.class.getName());
    private static final int BUFFER_SIZE = 65536; // 64KB buffer
    private static final int PCAP_HEADER_SIZE = 24; // PCAP global header size
    private static final int PACKET_HEADER_SIZE = 16; // PCAP packet header size
    private static final int MAX_PACKET_SIZE = 1024; // Maximum packet size to send
    private static final int HEADER_SIZE = 24; // Global header size
    
    private final String pcapFile;
    private final int port;
    private boolean running;
    private ServerSocket serverSocket;
    
    /**
     * Creates a new MockPcapServer.
     * 
     * @param port the port to listen on
     * @param pcapFile the PCAP file to read
     */
    public MockPcapServer(int port, String pcapFile) {
        this.port = port;
        this.pcapFile = pcapFile;
        this.running = false;
    }
    
    /**
     * Starts the server.
     */
    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            LOGGER.info("Server started on port " + port);
            
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    LOGGER.info("Client connected from " + clientSocket.getRemoteSocketAddress());
                    
                    // Handle client in a new thread
                    Thread clientThread = new Thread(() -> handleClient(clientSocket));
                    clientThread.setDaemon(true);
                    clientThread.start();
                } catch (IOException e) {
                    if (running) {
                        LOGGER.log(Level.WARNING, "Error accepting client connection", e);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error starting server", e);
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
        try (InputStream pcapStream = new FileInputStream(pcapFile);
             DataOutputStream clientOut = new DataOutputStream(clientSocket.getOutputStream())) {
            
            LOGGER.info("Waiting before sending data...");
            Thread.sleep(1000); // Wait 1 second before sending data
            
            // Skip the global header (24 bytes)
            if (pcapStream.skip(HEADER_SIZE) != HEADER_SIZE) {
                LOGGER.warning("Failed to skip global header");
                return;
            }
            
            byte[] packetHeader = new byte[PACKET_HEADER_SIZE];
            byte[] packetData;
            
            while (!clientSocket.isClosed() && pcapStream.available() > 0) {
                // Read packet header (16 bytes)
                int headerBytesRead = pcapStream.read(packetHeader);
                if (headerBytesRead < PACKET_HEADER_SIZE) {
                    LOGGER.warning("Incomplete packet header, skipping");
                    break;
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
                int dataBytesRead = pcapStream.read(packetData, 0, actualLength);
                
                if (dataBytesRead < actualLength) {
                    LOGGER.warning("Incomplete packet data, skipping");
                    // Skip the rest of this packet if we couldn't read it all
                    if (dataBytesRead > 0 && packetLength > dataBytesRead) {
                        pcapStream.skip(packetLength - dataBytesRead);
                    }
                    continue;
                }
                
                // Skip the rest of the packet if we limited the size
                if (packetLength > actualLength) {
                    pcapStream.skip(packetLength - actualLength);
                }
                
                try {
                    // First send the packet length (4 bytes)
                    clientOut.writeInt(actualLength);
                    
                    // Then send the packet data
                    clientOut.write(packetData, 0, actualLength);
                    clientOut.flush();
                    packetCount++;
                    
                    if (packetCount % 100 == 0) {
                        LOGGER.info("Sent " + packetCount + " packets");
                    }
                    
                    // Small delay between packets to avoid overwhelming the client
                    Thread.sleep(10);
                } catch (SocketException e) {
                    LOGGER.info("Client disconnected: " + e.getMessage());
                    break;
                } catch (SocketTimeoutException e) {
                    LOGGER.info("Socket timeout: " + e.getMessage());
                    break;
                }
            }
            
            if (pcapStream.available() <= 0) {
                LOGGER.info("Reached end of PCAP file");
            }
            
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error handling client", e);
        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, "Thread interrupted", e);
            Thread.currentThread().interrupt();
        } finally {
            try {
                if (!clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error closing client socket", e);
            }
            LOGGER.info("Client handler finished after sending " + packetCount + " packets");
        }
    }
    
    /**
     * Stops the server.
     */
    public void stop() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error closing server socket", e);
            }
        }
    }
    
    /**
     * Main method to start the server.
     * 
     * @param args command line arguments: port pcapFile
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: MockPcapServer <port> <pcap_file>");
            System.exit(1);
        }
        
        int port = Integer.parseInt(args[0]);
        String pcapFile = args[1];
        
        MockPcapServer server = new MockPcapServer(port, pcapFile);
        server.start();
    }
}