#!/bin/bash

# Script to run a standalone test client with the mock server

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

echo "Using PCAP file: $PCAP_FILE"

# Compile the server and client
echo "Compiling the server and client..."
cd "$SCRIPTS_DIR"
javac MockPcapServer.java
javac TestClient.java

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

# Run the client
echo "Running the test client..."
java -cp "$SCRIPTS_DIR" TestClient

# Clean up
echo "Cleaning up..."
kill $SERVER_PID 2>/dev/null

echo "Test completed." 