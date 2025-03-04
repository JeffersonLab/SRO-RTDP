#!/bin/bash

# Script to run both the mock server and test client

# Set the project directory
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPTS_DIR="$PROJECT_DIR/scripts"
TEST_DATA_DIR="$PROJECT_DIR/test-data"

# Check if the test PCAP file exists, if not, create it
TEST_PCAP="$TEST_DATA_DIR/test.pcap"
if [ ! -f "$TEST_PCAP" ]; then
    echo "Test PCAP file not found. Creating it..."
    mkdir -p "$TEST_DATA_DIR"
    javac "$SCRIPTS_DIR/GenerateTestPcap.java"
    java -cp "$SCRIPTS_DIR" GenerateTestPcap "$TEST_PCAP" 100
fi

# Compile the server and client
echo "Compiling the server and client..."
cd "$SCRIPTS_DIR"
javac MockPcapServer.java
javac TestClient.java

# Kill any existing MockPcapServer processes
pkill -f MockPcapServer 2>/dev/null

# Start the server in the background
echo "Starting the mock server..."
java -cp "$SCRIPTS_DIR" MockPcapServer "$TEST_PCAP" 9000 &
SERVER_PID=$!

# Wait for the server to start
sleep 2

# Run the client
echo "Running the test client..."
java -cp "$SCRIPTS_DIR" TestClient

# Clean up
echo "Cleaning up..."
kill $SERVER_PID 2>/dev/null

echo "Test completed." 