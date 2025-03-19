package org.jlab.epsci.ersap.actor.pcap.engine;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.engine.EngineStatus;
import org.jlab.epsci.ersap.std.services.AbstractService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

public class PcapStreamSourceEngine extends AbstractService {
    private List<Socket> sockets;
    private List<SocketConfig> socketConfigs;
    private String serviceName;
    private String serviceDescription;

    private static class SocketConfig {
        String host;
        int port;
        int connectionTimeout;
        int readTimeout;
        int bufferSize;
        int packetCount;
    }

    @Override
    public EngineData configure(EngineData input) {
        EngineData output = new EngineData();
        if (input == null) {
            ServiceUtils.setError(output, "Input data is null");
            return output;
        }

        String mimeType = input.getMimeType();
        String configStr;
        
        if (mimeType.equals(EngineDataType.JSON.mimeType())) {
            configStr = (String) input.getData();
        } else if (mimeType.equals(EngineDataType.STRING.mimeType())) {
            configStr = (String) input.getData();
        } else {
            ServiceUtils.setError(output, "Unsupported mime-type: " + mimeType);
            return output;
        }

        try {
            JSONObject config = new JSONObject(configStr);
            JSONArray connections = config.getJSONArray("connections");
            for (int i = 0; i < connections.length(); i++) {
                JSONObject conn = connections.getJSONObject(i);
                String host = conn.getString("host");
                int port = conn.getInt("port");
                int connectionTimeout = conn.getInt("connection_timeout");
                int readTimeout = conn.getInt("read_timeout");
                int bufferSize = conn.getInt("buffer_size");
                int packetCount = conn.getInt("packet_count");
                
                // Create socket connection
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), connectionTimeout);
                socket.setSoTimeout(readTimeout);
                
                // Store connection info
                connections.add(new ConnectionInfo(socket, bufferSize, packetCount));
            }
            
            output.setData(EngineDataType.STRING.mimeType(), "Configuration successful");
        } catch (Exception e) {
            ServiceUtils.setError(output, "Failed to configure: " + e.getMessage());
        }
        return output;
    }

    @Override
    public EngineData execute(EngineData input) {
        try {
            // Read from all sockets
            for (int i = 0; i < sockets.size(); i++) {
                Socket socket = sockets.get(i);
                SocketConfig config = socketConfigs.get(i);
                
                byte[] buffer = new byte[config.bufferSize];
                int bytesRead = socket.getInputStream().read(buffer);

                if (bytesRead > 0) {
                    ByteBuffer data = ByteBuffer.wrap(buffer, 0, bytesRead);
                    input.setData(EngineDataType.BYTES, data);
                    return input;
                }
            }
            return null;
        } catch (IOException e) {
            return buildErrorResponse("Failed to read data: " + e.getMessage());
        }
    }

    @Override
    public EngineData executeGroup(Set<EngineData> inputs) {
        return null;
    }

    @Override
    public Set<EngineDataType> getInputDataTypes() {
        return Set.of(EngineDataType.STRING, EngineDataType.JSON);
    }

    @Override
    public Set<EngineDataType> getOutputDataTypes() {
        return Set.of(EngineDataType.BYTES);
    }

    @Override
    public Set<String> getStates() {
        return null;
    }

    @Override
    public String getDescription() {
        return serviceDescription != null ? serviceDescription
                : "PCAP stream source engine that reads from multiple sockets";
    }

    @Override
    public String getName() {
        return serviceName != null ? serviceName : "PcapStreamSource";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public String getAuthor() {
        return "JLAB EPSCI Group";
    }

    private EngineData buildErrorResponse(String message) {
        EngineData error = new EngineData();
        error.setData(EngineDataType.BYTES, ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8)));
        error.setStatus(EngineStatus.ERROR);
        return error;
    }

    @Override
    public void reset() {
        try {
            if (sockets != null) {
                for (Socket socket : sockets) {
                    if (socket != null) {
                        socket.close();
                    }
                }
            }
        } catch (IOException e) {
            // Ignore
        }
    }

    @Override
    public void destroy() {
        reset();
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: PcapStreamSourceEngine <name> <description>");
            System.exit(1);
        }

        PcapStreamSourceEngine service = new PcapStreamSourceEngine();
        service.serviceName = args[0];
        service.serviceDescription = args[1];
    }
}
