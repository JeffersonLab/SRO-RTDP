#!/bin/bash

# Script to run the ERSAP test application with the mock server

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

# Compile the server
echo "Compiling the mock server..."
cd "$SCRIPTS_DIR"
javac MockPcapServer.java

# Create a simple ERSAP test application
echo "Creating ERSAP test application..."
cat > PcapStreamSourceTest.java << 'EOF'
import org.jlab.epsci.ersap.base.Core;
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
        int port = 9000;
        
        if (args.length >= 1) {
            host = args[0];
        }
        
        if (args.length >= 2) {
            port = Integer.parseInt(args[1]);
        }
        
        System.out.println("Starting ERSAP test application");
        System.out.println("Host: " + host);
        System.out.println("Port: " + port);
        
        // Create ERSAP core
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
        
        String serviceAddress = ErsapUtil.getServiceAddress(containerName, serviceName);
        core.configure(serviceAddress, input);
        
        // Keep the application running
        System.out.println("Services started. Press Ctrl+C to exit.");
        while (true) {
            Thread.sleep(1000);
        }
    }
}
EOF

# Compile the ERSAP test application
echo "Compiling ERSAP test application..."
javac -cp "$ERSAP_HOME/lib/*" PcapStreamSourceTest.java

# Kill any existing MockPcapServer processes
echo "Killing any existing MockPcapServer processes..."
pkill -f MockPcapServer 2>/dev/null

# Start the server in the background
echo "Starting the mock server..."
java -cp "$SCRIPTS_DIR" MockPcapServer "$PCAP_FILE" 9000 &
SERVER_PID=$!

# Wait for the server to start
echo "Waiting for server to start..."
sleep 3

# Run the ERSAP test application
echo "Running ERSAP test application..."
java -cp ".:$ERSAP_HOME/lib/*" PcapStreamSourceTest

# Clean up
echo "Cleaning up..."
kill $SERVER_PID 2>/dev/null

echo "Test completed." 