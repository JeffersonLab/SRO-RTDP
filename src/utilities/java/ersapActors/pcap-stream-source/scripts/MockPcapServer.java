import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A mock server that reads a PCAP file and streams packets to clients.
 */
public class MockPcapServer {
    private static final int DEFAULT_PORT = 9000;
    private static final int PCAP_HEADER_SIZE = 24;
    private static final int PACKET_HEADER_SIZE = 16;

    private final int port;
    private final String pcapFile;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public MockPcapServer(String pcapFile, int port) {
        this.pcapFile = pcapFile;
        this.port = port;
    }

    public void start() {
        System.out.println("Starting MockPcapServer on port " + port);
        System.out.println("Reading PCAP file: " + pcapFile);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started. Waiting for connections...");

            // Add shutdown hook to stop the server gracefully
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down server...");
                running.set(false);
            }));

            while (running.get()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Client connected: " + clientSocket.getInetAddress());

                    // Handle client in a new thread
                    new Thread(() -> handleClient(clientSocket)).start();
                } catch (IOException e) {
                    if (running.get()) {
                        System.err.println("Error accepting client connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
        }
    }

    private void handleClient(Socket clientSocket) {
        System.out.println("Handling client: " + clientSocket.getInetAddress());

        try (DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
                FileInputStream fileIn = new FileInputStream(pcapFile);
                DataInputStream pcapIn = new DataInputStream(fileIn)) {

            // Read PCAP file header
            byte[] pcapHeader = new byte[PCAP_HEADER_SIZE];
            int bytesRead = pcapIn.read(pcapHeader);

            if (bytesRead != PCAP_HEADER_SIZE) {
                System.err.println("Error reading PCAP header");
                return;
            }

            // Parse magic number to determine byte order
            ByteBuffer buffer = ByteBuffer.wrap(pcapHeader);
            int magicNumber = buffer.getInt();
            ByteOrder byteOrder = ByteOrder.BIG_ENDIAN;

            if (magicNumber == 0xd4c3b2a1) {
                byteOrder = ByteOrder.LITTLE_ENDIAN;
                System.out.println("PCAP file is in little-endian format");
            } else if (magicNumber == 0xa1b2c3d4) {
                System.out.println("PCAP file is in big-endian format");
            } else if (magicNumber == 0x4d3cb2a1) {
                byteOrder = ByteOrder.LITTLE_ENDIAN;
                System.out.println("PCAP file is in modified little-endian format (CLAS12)");
            } else {
                System.err.println("Invalid PCAP file format: " + Integer.toHexString(magicNumber));
                return;
            }

            // Read packets and send to client
            int packetCount = 0;
            long totalBytes = 0;
            long startTime = System.currentTimeMillis();

            while (running.get() && clientSocket.isConnected()) {
                try {
                    // Read packet header
                    byte[] packetHeader = new byte[PACKET_HEADER_SIZE];
                    bytesRead = pcapIn.read(packetHeader);

                    if (bytesRead != PACKET_HEADER_SIZE) {
                        System.out.println("End of PCAP file reached. Restarting from beginning.");
                        fileIn.getChannel().position(PCAP_HEADER_SIZE);
                        continue;
                    }

                    // Parse packet length
                    buffer = ByteBuffer.wrap(packetHeader);
                    buffer.order(byteOrder);

                    // Skip timestamp fields
                    buffer.position(8);

                    // Get captured length
                    int capturedLength = buffer.getInt();

                    System.out.println("Packet " + packetCount + ": length = " + capturedLength);

                    // Read packet data
                    byte[] packetData = new byte[capturedLength];
                    bytesRead = pcapIn.read(packetData);

                    if (bytesRead != capturedLength) {
                        System.err.println(
                                "Error reading packet data: expected " + capturedLength + " bytes, got " + bytesRead);
                        continue;
                    }

                    // Send packet length and data to client
                    out.writeInt(capturedLength);
                    out.write(packetData);
                    out.flush();

                    packetCount++;
                    totalBytes += capturedLength;

                    if (packetCount % 10 == 0) {
                        long elapsedTime = System.currentTimeMillis() - startTime;
                        double mbps = (totalBytes * 8.0 / 1000000.0) / (elapsedTime / 1000.0);
                        System.out.printf("Sent %d packets, %d bytes (%.2f Mbps)%n",
                                packetCount, totalBytes, mbps);
                    }

                    // Add a small delay to simulate realistic packet rates
                    Thread.sleep(10);

                } catch (IOException e) {
                    System.err.println("Error reading/sending packet: " + e.getMessage());
                    break;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            System.out.println("Client disconnected. Sent " + packetCount + " packets.");

        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    public void stop() {
        running.set(false);
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java MockPcapServer <pcap_file> [port]");
            System.exit(1);
        }

        String pcapFile = args[0];
        int port = DEFAULT_PORT;

        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[1]);
                System.exit(1);
            }
        }

        // Check if the PCAP file exists
        File file = new File(pcapFile);
        if (!file.exists() || !file.isFile()) {
            System.err.println("PCAP file not found: " + pcapFile);
            System.exit(1);
        }

        MockPcapServer server = new MockPcapServer(pcapFile, port);
        server.start();
    }
}