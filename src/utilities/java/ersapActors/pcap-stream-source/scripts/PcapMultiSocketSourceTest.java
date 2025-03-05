import org.jlab.epsci.ersap.base.core.ErsapBase;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;

import org.json.JSONObject;
import org.json.JSONArray;

import java.util.concurrent.TimeUnit;

public class PcapMultiSocketSourceTest {
    public static void main(String[] args) throws Exception {
        String host = "localhost";
        int basePort = 9000;
        int numConnections = 24;
        
        System.out.println("Starting ERSAP test application with " + numConnections + " socket connections");
        
        // Create ERSAP base
        ErsapBase base = new ErsapBase();
        
        // Register container
        System.out.println("Registering container...");
        String containerName = "pcap-container";
        base.startContainer(containerName);
        
        // Register service
        System.out.println("Registering service...");
        String serviceName = "pcap-multi-source";
        String serviceClass = "org.jlab.ersap.actor.pcap.engine.MultiSocketSourceEngine";
        base.startService(containerName, serviceClass, serviceName);
        
        // Configure service with 24 connections
        System.out.println("Configuring service with " + numConnections + " connections...");
        JSONObject config = new JSONObject();
        JSONArray connections = new JSONArray();
        
        for (int i = 0; i < numConnections; i++) {
            JSONObject conn = new JSONObject();
            conn.put("host", host);
            conn.put("port", basePort + i);
            conn.put("connection_timeout", 10000);
            conn.put("read_timeout", 60000);
            conn.put("buffer_size", 2048);
            connections.put(conn);
        }
        
        config.put("connections", connections);
        
        EngineData input = new EngineData();
        input.setData(EngineDataType.JSON, config.toString());
        
        String serviceAddress = containerName + ":" + serviceName;
        base.configure(serviceAddress, input);
        
        // Keep the application running
        System.out.println("Services started. Press Ctrl+C to exit.");
        while (true) {
            Thread.sleep(1000);
        }
    }
}
