import org.jlab.epsci.ersap.base.Core;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

public class PcapStreamSourceTest {
    public static void main(String[] args) throws Exception {
        String host = "localhost";
        int port = 9000;
        int bufferSize = 1024;
        
        if (args.length >= 1) {
            host = args[0];
        }
        
        if (args.length >= 2) {
            port = Integer.parseInt(args[1]);
        }
        
        if (args.length >= 3) {
            bufferSize = Integer.parseInt(args[2]);
        }
        
        System.out.println("Starting ERSAP test application");
        System.out.println("Host: " + host);
        System.out.println("Port: " + port);
        System.out.println("Buffer size: " + bufferSize);
        
        // Create ERSAP core
        Core core = new Core();
        
        // Start container
        String containerName = "pcap-container";
        System.out.println("Starting container: " + containerName);
        core.startContainer(containerName);
        
        // Start service
        String serviceName = "pcap-source";
        String serviceClass = "org.jlab.ersap.actor.pcap.engine.PcapStreamSourceEngine";
        System.out.println("Starting service: " + serviceName + " (" + serviceClass + ")");
        core.startService(containerName, serviceClass, serviceName);
        
        // Configure service
        JSONObject config = new JSONObject();
        config.put("host", host);
        config.put("port", port);
        config.put("connection_timeout", 10000);
        config.put("read_timeout", 60000);
        config.put("buffer_size", bufferSize);
        
        EngineData input = new EngineData();
        input.setData(EngineDataType.JSON, config.toString());
        
        String serviceAddress = containerName + ":" + serviceName;
        System.out.println("Configuring service: " + serviceAddress);
        core.configure(serviceAddress, input);
        
        // Keep the application running
        System.out.println("ERSAP application started. Press Ctrl+C to exit.");
        while (true) {
            Thread.sleep(1000);
        }
    }
}
