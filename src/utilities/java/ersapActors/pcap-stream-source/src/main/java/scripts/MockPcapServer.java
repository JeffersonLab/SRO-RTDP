package scripts;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class MockPcapServer {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: MockPcapServer <pcap-file> <port>");
            System.exit(1);
        }

        String pcapFile = args[0];
        int port = Integer.parseInt(args[1]);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server listening on port " + port);
            
            while (true) {
                try (Socket clientSocket = serverSocket.accept();
                     FileInputStream fis = new FileInputStream(new File(pcapFile));
                     DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {
                    
                    System.out.println("Client connected from " + clientSocket.getInetAddress());
                    
                    // Read and send the PCAP file in chunks
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        // Write the length of the chunk (4 bytes)
                        out.writeInt(bytesRead);
                        // Write the chunk data
                        out.write(buffer, 0, bytesRead);
                        out.flush();
                        Thread.sleep(10); // Add a small delay to simulate real-time streaming
                    }
                } catch (IOException | InterruptedException e) {
                    System.err.println("Error handling client: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
            System.exit(1);
        }
    }
} 