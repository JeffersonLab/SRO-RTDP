#!/bin/bash

# Set the project directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Default PCAP file path - update this to your actual PCAP file path
DEFAULT_PCAP_FILE="/scratch/jeng-yuantsai/CLAS12_ECAL_PCAL_DC_2024-05-15_17-12-30.pcap"
PCAP_FILE=${1:-$DEFAULT_PCAP_FILE}

# Default port
PORT=${2:-9000}

# Default buffer size
BUFFER_SIZE=${3:-1024}

# Default monitor interval in milliseconds
MONITOR_INTERVAL=${4:-1000}

# Check if the PCAP file exists
if [ ! -f "$PCAP_FILE" ]; then
    echo "Error: PCAP file not found at $PCAP_FILE"
    echo "Usage: $0 [pcap_file] [port] [buffer_size] [monitor_interval_ms]"
    exit 1
fi

# Kill any existing processes
echo "Cleaning up any existing processes..."
pkill -f "java.*MockPcapServer" || true
pkill -f "java.*TestClientWithMonitoring" || true

# Compile the Java files
echo "Compiling Java files..."
cd "$SCRIPT_DIR"
javac MockPcapServer.java
if [ $? -ne 0 ]; then
    echo "Error: Failed to compile MockPcapServer.java"
    exit 1
fi

javac TestClientWithMonitoring.java
if [ $? -ne 0 ]; then
    echo "Error: Failed to compile TestClientWithMonitoring.java"
    exit 1
fi

# Start the mock server
echo "Starting mock PCAP server with file: $PCAP_FILE on port: $PORT"
java MockPcapServer "$PCAP_FILE" "$PORT" &
SERVER_PID=$!

# Wait for the server to start
echo "Waiting for server to start..."
sleep 2

# Check if the server is running
if ! ps -p $SERVER_PID > /dev/null; then
    echo "Error: Failed to start the mock server"
    exit 1
fi

# Start the client with monitoring
echo "Starting test client with buffer size: $BUFFER_SIZE and monitor interval: $MONITOR_INTERVAL ms"
java TestClientWithMonitoring localhost "$PORT" "$BUFFER_SIZE" "$MONITOR_INTERVAL"

# The client will run until the user presses Ctrl+C
# When the user presses Ctrl+C, the shutdown hook in the client will handle cleanup

# Cleanup function
cleanup() {
    echo "Cleaning up..."
    kill $SERVER_PID 2>/dev/null || true
    echo "Test completed."
}

# Register the cleanup function to be called on exit
trap cleanup EXIT

# Wait for the client to finish (this will only happen if the client exits on its own)
wait 