import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Simple client to test the connection to the mock PCAP server.
 */
public class TestClient {
    public static void main(String[] args) {
        String host = "localhost";
        int port = 9000;
        
        if (args.length >= 1) {
            host = args[0];
        }
        
        if (args.length >= 2) {
            port = Integer.parseInt(args[1]);
        }
        
        System.out.println("Connecting to " + host + ":" + port);
        
        try (Socket socket = new Socket(host, port);
             DataInputStream in = new DataInputStream(socket.getInputStream())) {
            
            System.out.println("Connected to server");
            
            // Read packets
            int packetCount = 0;
            long totalBytes = 0;
            long startTime = System.currentTimeMillis();
            
            try {
                while (true) {
                    // Read packet length
                    int packetLength = in.readInt();
                    
                    // Read packet data
                    byte[] packetData = new byte[packetLength];
                    in.readFully(packetData);
                    
                    packetCount++;
                    totalBytes += packetLength;
                    
                    if (packetCount % 10 == 0) {
                        long elapsedTime = System.currentTimeMillis() - startTime;
                        double mbps = (totalBytes * 8.0 / 1000000.0) / (elapsedTime / 1000.0);
                        System.out.printf("Received %d packets, %d bytes (%.2f Mbps)%n", 
                                         packetCount, totalBytes, mbps);
                    }
                }
            } catch (IOException e) {
                // End of stream
                System.out.println("End of stream reached");
            }
            
            long elapsedTime = System.currentTimeMillis() - startTime;
            System.out.printf("Total: %d packets, %d bytes in %d ms%n", 
                             packetCount, totalBytes, elapsedTime);
            
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 