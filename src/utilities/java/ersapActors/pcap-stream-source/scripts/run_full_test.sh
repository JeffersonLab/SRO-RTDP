#!/bin/bash

# Comprehensive test script for PCAP Stream Source with ring buffer monitoring

# Set the project directory
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPTS_DIR="$PROJECT_DIR/scripts"

# Use the real PCAP file from /scratch/jeng-yuantsai
PCAP_FILE="/scratch/jeng-yuantsai/CLAS12_ECAL_PCAL_DC_2024-05-15_17-12-30.pcap"

# Check if the PCAP file exists
if [ ! -f "$PCAP_FILE" ]; then
    echo "Error: PCAP file not found at $PCAP_FILE"
    exit 1
fi

# Check if ERSAP_HOME is set
if [ -z "$ERSAP_HOME" ]; then
    echo "Error: ERSAP_HOME environment variable is not set"
    echo "Please set ERSAP_HOME to the ERSAP installation directory"
    exit 1
fi

echo "Using PCAP file: $PCAP_FILE"
echo "ERSAP_HOME: $ERSAP_HOME"

# Kill any existing processes
echo "Killing any existing processes..."
pkill -f MockPcapServer 2>/dev/null
pkill -f PcapStreamSourceTest 2>/dev/null
pkill -f RingBufferStatusChecker 2>/dev/null

# Compile all Java files
echo "Compiling Java files..."
cd "$SCRIPTS_DIR"
javac MockPcapServer.java
javac -cp "$ERSAP_HOME/lib/*" RingBufferStatusChecker.java

# Create a simple ERSAP test application
echo "Creating ERSAP test application..."
cat > PcapStreamSourceTest.java << 'EOF'
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
EOF

# Compile the ERSAP test application
echo "Compiling ERSAP test application..."
javac -cp "$ERSAP_HOME/lib/*" PcapStreamSourceTest.java

# Start the mock server in the background
echo "Starting the mock server..."
java -cp "$SCRIPTS_DIR" MockPcapServer "$PCAP_FILE" 9000 > mock_server.log 2>&1 &
SERVER_PID=$!

# Wait for the server to start
echo "Waiting for server to start..."
sleep 3

# Start the ERSAP application in the background
echo "Starting the ERSAP application..."
java -cp ".:$ERSAP_HOME/lib/*" PcapStreamSourceTest localhost 9000 2048 > ersap_app.log 2>&1 &
APP_PID=$!

# Wait for the ERSAP application to start
echo "Waiting for ERSAP application to start..."
sleep 5

# Start the ring buffer status checker
echo "Starting the ring buffer status checker..."
echo "Press Ctrl+C to stop the test."
java -cp ".:$ERSAP_HOME/lib/*" RingBufferStatusChecker pcap-container pcap-source 3

# Clean up
echo "Cleaning up..."
kill $SERVER_PID 2>/dev/null
kill $APP_PID 2>/dev/null

echo "Test completed." 