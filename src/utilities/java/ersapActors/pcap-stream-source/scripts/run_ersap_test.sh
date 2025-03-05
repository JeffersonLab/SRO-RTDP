#!/bin/bash

# Script to run the multi-socket test with 24 mock servers for multi-socket connections

# Set the project directory
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPTS_DIR="$PROJECT_DIR/scripts"
CONFIG_DIR="$PROJECT_DIR/custom-config"

# Use the real PCAP file from /scratch/jeng-yuantsai
PCAP_FILE="/scratch/jeng-yuantsai/CLAS12_ECAL_PCAL_DC_2024-05-15_17-12-30.pcap"

# Check if the PCAP file exists
if [ ! -f "$PCAP_FILE" ]; then
    echo "Error: PCAP file not found at $PCAP_FILE"
    exit 1
fi

# Create config directory if it doesn't exist
mkdir -p "$CONFIG_DIR"

# Verify the JSON configuration file exists
if [ ! -f "$CONFIG_DIR/multi-socket-config.json" ]; then
    echo "Error: JSON configuration file not found at $CONFIG_DIR/multi-socket-config.json"
    exit 1
fi

echo "Using JSON configuration file: $CONFIG_DIR/multi-socket-config.json"

# Compile the server and test client
echo "Compiling the mock server and test client..."
cd "$PROJECT_DIR"
javac -d build/classes/java/scripts scripts/MockPcapServer.java
javac -d build/classes/java/scripts -cp "build/classes/java/main:lib/json-20231013.jar:lib/disruptor-3.4.4.jar:lib/snakeyaml-2.0.jar" scripts/SimpleMultiSocketTest.java

# Kill any existing MockPcapServer processes
echo "Killing any existing MockPcapServer processes..."
pkill -f MockPcapServer 2>/dev/null

# Start 24 mock servers in the background
echo "Starting 24 mock servers on ports 9000-9023..."
SERVER_PIDS=()

for i in {0..23}; do
    port=$((9000 + i))
    echo "Starting server on port $port..."
    java -cp "build/classes/java/scripts" scripts.MockPcapServer $port "$PCAP_FILE" &
    SERVER_PIDS+=($!)
    # Small delay to avoid overwhelming the system
    sleep 0.5
done

# Wait for servers to start
echo "Waiting for all servers to start..."
sleep 10

# Run the SimpleMultiSocketTest
echo "Running SimpleMultiSocketTest with 24 connections..."
java -cp "build/classes/java/scripts:build/classes/java/main:lib/json-20231013.jar:lib/disruptor-3.4.4.jar:lib/snakeyaml-2.0.jar" scripts.SimpleMultiSocketTest "$CONFIG_DIR/multi-socket-config.json" 60

# Clean up
echo "Cleaning up..."
for pid in "${SERVER_PIDS[@]}"; do
    kill $pid 2>/dev/null
done

echo "Test completed." 