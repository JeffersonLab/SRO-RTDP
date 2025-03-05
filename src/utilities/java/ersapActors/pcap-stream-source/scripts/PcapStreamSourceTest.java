import org.jlab.epsci.ersap.base.ErsapUtil;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.std.services.EventReaderException;

import org.json.JSONObject;

import java.util.Set;
import java.util.concurrent.TimeUnit;

public class PcapStreamSourceTest {
    public static void main(String[] args) throws Exception {
        String host = "localhost";
        int port = 9005;
        
        if (args.length >= 1) {
            host = args[0];
        }
        
        if (args.length >= 2) {
            port = Integer.parseInt(args[1]);
        }
        
        System.out.println("Starting ERSAP test application");
        System.out.println("Host: " + host);
        System.out.println("Port: " + port);
        
        // Create ERSAP core using local Core class
        Core core = new Core();
        
        // Register container
        System.out.println("Registering container...");
        String containerName = "pcap-container";
        core.startContainer(containerName);
        
        // Register service
        System.out.println("Registering service...");
        String serviceName = "pcap-source";
        String serviceClass = "org.jlab.ersap.actor.pcap.engine.PcapStreamSourceEngine";
        core.startService(containerName, serviceClass, serviceName);
        
        // Configure service
        System.out.println("Configuring service...");
        JSONObject config = new JSONObject();
        config.put("host", host);
        config.put("port", port);
        config.put("connection_timeout", 10000);
        config.put("read_timeout", 60000);
        config.put("buffer_size", 2048);
        
        EngineData input = new EngineData();
        input.setData(EngineDataType.JSON, config.toString());
        
        String serviceAddress = containerName + ":" + serviceName;
        core.configure(serviceAddress, input);
        
        // Keep the application running
        System.out.println("Services started. Press Ctrl+C to exit.");
        while (true) {
            Thread.sleep(1000);
        }
    }
}
