package org.jlab.ersap.actor.pcap2streams;

import java.io.IOException;
import java.net.Socket;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public class IPBasedStreamClient {
    private final String ip;
    private final int port;
    private Socket socket;
    private InputStream inputStream;
    private final AtomicBoolean isRunning;
    
    public IPBasedStreamClient(String ip, int port) {
        this.ip = ip;
        this.port = port;
        this.isRunning = new AtomicBoolean(false);
    }
    
    public void connect() throws IOException {
        socket = new Socket(ip, port);
        inputStream = socket.getInputStream();
        isRunning.set(true);
    }
    
    public void disconnect() {
        isRunning.set(false);
        try {
            if (inputStream != null) {
                inputStream.close();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public byte[] readPacket() throws IOException {
        if (!isRunning.get()) {
            return null;
        }
        
        // Read packet size (4 bytes)
        byte[] sizeBytes = new byte[4];
        int bytesRead = inputStream.read(sizeBytes);
        if (bytesRead != 4) {
            return null;
        }
        
        // Read packet size in little-endian order
        int packetSize = (sizeBytes[3] & 0xFF) << 24 |
                        (sizeBytes[2] & 0xFF) << 16 |
                        (sizeBytes[1] & 0xFF) << 8 |
                        (sizeBytes[0] & 0xFF);
        
        // Read packet data
        byte[] packet = new byte[packetSize];
        bytesRead = inputStream.read(packet);
        if (bytesRead != packetSize) {
            return null;
        }
        
        return packet;
    }
    
    public boolean isConnected() {
        return socket != null && socket.isConnected();
    }
} 