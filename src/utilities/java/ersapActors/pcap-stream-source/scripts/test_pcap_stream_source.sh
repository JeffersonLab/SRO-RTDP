#!/bin/bash

# Test script for PCAP Stream Source

# Set the ERSAP_HOME environment variable if not already set
if [ -z "$ERSAP_HOME" ]; then
    echo "ERSAP_HOME is not set. Please set it to the ERSAP installation directory."
    exit 1
fi

# Set the project directory
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Build the project
echo "Building the project..."
cd "$PROJECT_DIR"
./gradlew build

# Check if the build was successful
if [ $? -ne 0 ]; then
    echo "Build failed. Please check the error messages."
    exit 1
fi

# Set the classpath
CLASSPATH="$PROJECT_DIR/build/libs/*:$ERSAP_HOME/lib/*"

# Create a simple ERSAP application
echo "Creating a simple ERSAP application..."
cat > "$PROJECT_DIR/scripts/PcapStreamSourceTest.java" << 'EOF'
import org.jlab.epsci.ersap.base.ContainerRegistrationData;
import org.jlab.epsci.ersap.base.ErsapLauncher;
import org.jlab.epsci.ersap.base.ErsapUtil;
import org.jlab.epsci.ersap.base.ServiceRegistrationData;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.json.JSONObject;

public class PcapStreamSourceTest {
    public static void main(String[] args) throws Exception {
        // Parse command-line arguments
        String host = "localhost";
        int port = 9000;
        
        if (args.length >= 1) {
            host = args[0];
        }
        if (args.length >= 2) {
            port = Integer.parseInt(args[1]);
        }
        
        // Create ERSAP launcher
        ErsapLauncher launcher = new ErsapLauncher();
        
        // Register container
        ContainerRegistrationData container = new ContainerRegistrationData("pcap-container", "localhost");
        launcher.registerContainer(container);
        
        // Register services
        ServiceRegistrationData sourceService = new ServiceRegistrationData(
                "PcapStreamSource",
                "org.jlab.ersap.actor.pcap.engine.PcapStreamSourceEngine",
                "PCAP stream source service");
        launcher.registerService(container.name(), sourceService);
        
        // Configure source service
        JSONObject config = new JSONObject();
        config.put("host", host);
        config.put("port", port);
        
        EngineData input = new EngineData();
        input.setData(EngineDataType.JSON, config.toString());
        
        launcher.configureService(container.name(), sourceService.name(), input);
        
        // Start services
        launcher.startServices(container.name());
        
        System.out.println("PCAP Stream Source is running and connected to " + host + ":" + port);
        System.out.println("Press Ctrl+C to stop");
        
        // Keep the application running
        while (true) {
            Thread.sleep(1000);
        }
    }
}
EOF

# Compile the test application
echo "Compiling the test application..."
javac -cp "$CLASSPATH" "$PROJECT_DIR/scripts/PcapStreamSourceTest.java"

# Make the script executable
chmod +x "$PROJECT_DIR/scripts/test_pcap_stream_source.sh"

echo "Test script created successfully."
echo "To run the test, use: ./scripts/test_pcap_stream_source.sh [host] [port]"
echo "Default host: localhost"
echo "Default port: 9000"
echo ""
echo "Note: Make sure the pcap2stream server is running and streaming data to the specified host and port." 