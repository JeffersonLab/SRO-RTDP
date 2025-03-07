#!/bin/bash

# Script to run the ERSAP test using Pcap2Streams as the data server
# This script replaces MockPcapServer with Pcap2Streams for more realistic IP-based streaming

# Set the project directories
INTEGRATION_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PCAP_STREAM_DIR="/workspace/src/utilities/java/ersapActors/pcap-stream-source"
PCAP2STREAMS_DIR="/workspace/src/utilities/java/pcap2streams"
SCRIPTS_DIR="$INTEGRATION_DIR/scripts"
CONFIG_DIR="$INTEGRATION_DIR/config"
RESULTS_DIR="$INTEGRATION_DIR/results"

# Default PCAP file location
PCAP_FILE="/scratch/jeng-yuantsai/CLAS12_ECAL_PCAL_DC_2024-05-15_17-12-30.pcap"
# For testing with a smaller file
TEST_PCAP_FILE="$PCAP_STREAM_DIR/test-data/test.pcap"

# Use test file by default for faster testing
DEFAULT_PCAP_FILE="$TEST_PCAP_FILE"

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --pcap)
            if [ -f "$2" ]; then
                PCAP_FILE="$2"
                USE_TEST_FILE=false  # Set USE_TEST_FILE to false when --pcap is used
                shift 2
            else
                echo "Error: PCAP file not found at $2"
                echo "Please provide a valid PCAP file path"
                exit 1
            fi
            ;;
        --test)
            USE_TEST_FILE=true
            shift
            ;;
        --full)
            USE_TEST_FILE=false
            shift
            ;;
        --help|-h)
            echo "Usage: $0 [options]"
            echo "Options:"
            echo "  --pcap FILE    Use specified PCAP file"
            echo "  --test         Use small test PCAP file (default)"
            echo "  --full         Use full PCAP file from /scratch"
            echo "  --help, -h     Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Determine which PCAP file to use
if [[ "$USE_TEST_FILE" == "false" ]]; then
    SELECTED_PCAP_FILE="$PCAP_FILE"
    echo "Using full PCAP file: $SELECTED_PCAP_FILE"
else
    SELECTED_PCAP_FILE="$DEFAULT_PCAP_FILE"
    echo "Using test PCAP file: $SELECTED_PCAP_FILE"
fi

# Check if the PCAP file exists
if [ ! -f "$SELECTED_PCAP_FILE" ]; then
    echo "Error: PCAP file not found at $SELECTED_PCAP_FILE"
    exit 1
fi

# Create directories if they don't exist
mkdir -p "$CONFIG_DIR" "$RESULTS_DIR"

# Function to clean up processes on exit
cleanup() {
    echo "Cleaning up processes..."
    # Kill Pcap2Streams process
    pkill -f "java.*Pcap2Streams" 2>/dev/null
    echo "Cleanup complete."
}

# Set up trap to ensure cleanup on script exit
trap cleanup EXIT

# Step 1: Start Pcap2Streams to analyze the PCAP file and create servers
echo "Starting Pcap2Streams with PCAP file: $SELECTED_PCAP_FILE"
cd "$PCAP2STREAMS_DIR"
./scripts/run_pcap2streams.sh "$SELECTED_PCAP_FILE" &
PCAP2STREAMS_PID=$!

# Wait for Pcap2Streams to initialize and generate the configuration
echo "Waiting for Pcap2Streams to initialize and generate configuration..."
sleep 10

# Step 2: Check if the IP-based configuration file was generated
PCAP2STREAMS_CONFIG="$PCAP2STREAMS_DIR/custom-config/ip-based-config.json"
if [ ! -f "$PCAP2STREAMS_CONFIG" ]; then
    echo "Error: Pcap2Streams configuration file not found at $PCAP2STREAMS_CONFIG"
    cleanup
    exit 1
fi

# Step 3: Create the configuration adapter script
echo "Creating configuration adapter..."
cat > "$SCRIPTS_DIR/config_adapter.py" << 'EOF'
#!/usr/bin/env python3
import json
import sys
import os

def adapt_config(input_file, output_file):
    """
    Adapt the Pcap2Streams IP-based configuration to the format expected by SimpleMultiSocketTest
    """
    try:
        with open(input_file, 'r') as f:
            ip_config = json.load(f)
        
        # Create the multi-socket configuration format
        multi_socket_config = {"connections": []}
        
        # Convert each IP-based connection to the multi-socket format
        for conn in ip_config.get("connections", []):
            multi_socket_config["connections"].append({
                "host": conn.get("host", "localhost"),
                "port": conn.get("port", 9000),
                "buffer_size": conn.get("buffer_size", 8192),
                "read_timeout": conn.get("read_timeout", 1000),
                "connection_timeout": conn.get("connection_timeout", 1000)
            })
        
        # Write the adapted configuration
        with open(output_file, 'w') as f:
            json.dump(multi_socket_config, f, indent=2)
        
        print(f"Successfully adapted configuration from {input_file} to {output_file}")
        print(f"Created configuration with {len(multi_socket_config['connections'])} connections")
        return True
    
    except Exception as e:
        print(f"Error adapting configuration: {str(e)}")
        return False

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python config_adapter.py <input_config> <output_config>")
        sys.exit(1)
    
    input_file = sys.argv[1]
    output_file = sys.argv[2]
    
    if not os.path.exists(input_file):
        print(f"Error: Input file {input_file} not found")
        sys.exit(1)
    
    success = adapt_config(input_file, output_file)
    sys.exit(0 if success else 1)
EOF

# Make the adapter script executable
chmod +x "$SCRIPTS_DIR/config_adapter.py"

# Step 4: Run the configuration adapter to create the multi-socket configuration
MULTI_SOCKET_CONFIG="$CONFIG_DIR/multi-socket-config.json"
echo "Adapting Pcap2Streams configuration to multi-socket format..."
python3 "$SCRIPTS_DIR/config_adapter.py" "$PCAP2STREAMS_CONFIG" "$MULTI_SOCKET_CONFIG"

if [ ! -f "$MULTI_SOCKET_CONFIG" ]; then
    echo "Error: Failed to create multi-socket configuration"
    cleanup
    exit 1
fi

echo "Using adapted configuration file: $MULTI_SOCKET_CONFIG"

# Step 5: Compile the test client
echo "Compiling the test client..."
cd "$PCAP_STREAM_DIR"

# First, make sure the source directory exists
mkdir -p build/classes/java/scripts

# Copy the SimpleMultiSocketTest.java file to the new location if it doesn't exist
if [ ! -f "$INTEGRATION_DIR/scripts/SimpleMultiSocketTest.java" ]; then
    echo "Copying SimpleMultiSocketTest.java to integration directory..."
    cp "$PCAP_STREAM_DIR/scripts/SimpleMultiSocketTest.java" "$INTEGRATION_DIR/scripts/"
fi

# Compile with the correct classpath
javac -d build/classes/java/scripts -cp "build/classes/java/main:lib/json-20231013.jar:lib/disruptor-3.4.4.jar:lib/snakeyaml-2.0.jar:src/main/java" scripts/SimpleMultiSocketTest.java

# Step 6: Run the SimpleMultiSocketTest with the adapted configuration
echo "Running SimpleMultiSocketTest with the adapted configuration..."
TEST_DURATION=60  # seconds
java -cp "build/classes/java/scripts:build/classes/java/main:lib/json-20231013.jar:lib/disruptor-3.4.4.jar:lib/snakeyaml-2.0.jar:src/main/java" scripts.SimpleMultiSocketTest "$MULTI_SOCKET_CONFIG" $TEST_DURATION

# Step 7: Save test results
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
RESULT_FILE="$RESULTS_DIR/test_results_$TIMESTAMP.log"
echo "Test completed at $(date)" > "$RESULT_FILE"
echo "PCAP file used: $SELECTED_PCAP_FILE" >> "$RESULT_FILE"
echo "Configuration:" >> "$RESULT_FILE"
cat "$MULTI_SOCKET_CONFIG" >> "$RESULT_FILE"
echo "Test duration: $TEST_DURATION seconds" >> "$RESULT_FILE"

echo "Test completed. Results saved to $RESULT_FILE"
echo "Cleaning up..."

# Cleanup is handled by the trap 