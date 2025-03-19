package test;

import java.net.Socket;

public class PortTest {
    public static void main(String[] args) {
        int[] ports = {9000, 9001, 9002, 9003};
        for (int port : ports) {
            try {
                Socket socket = new Socket("localhost", port);
                System.out.println("Successfully connected to port " + port);
                socket.close();
            } catch (Exception e) {
                System.out.println("Failed to connect to port " + port + ": " + e.getMessage());
            }
        }
    }
} 