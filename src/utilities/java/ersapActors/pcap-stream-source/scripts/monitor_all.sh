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

# Function to display usage
usage() {
    echo "Usage: $0 [pcap_file] [port] [buffer_size] [monitor_interval_ms]"
    echo
    echo "This script provides comprehensive monitoring for the PCAP Stream Source."
    echo "It starts a mock PCAP server, a test client with built-in monitoring,"
    echo "and displays buffer status information."
    echo
    echo "Options:"
    echo "  pcap_file             Path to the PCAP file (default: $DEFAULT_PCAP_FILE)"
    echo "  port                  Port to use for the server (default: $PORT)"
    echo "  buffer_size           Size of the ring buffer (default: $BUFFER_SIZE)"
    echo "  monitor_interval_ms   Monitoring interval in milliseconds (default: $MONITOR_INTERVAL)"
    echo
    echo "Examples:"
    echo "  $0"
    echo "  $0 /path/to/file.pcap 9000 2048 500"
    exit 1
}

# Check if help is requested
if [ "$1" == "-h" ] || [ "$1" == "--help" ]; then
    usage
fi

# Check if the PCAP file exists
if [ ! -f "$PCAP_FILE" ]; then
    echo "Error: PCAP file not found at $PCAP_FILE"
    usage
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

# Start the client with monitoring in the background
echo "Starting test client with buffer size: $BUFFER_SIZE and monitor interval: $MONITOR_INTERVAL ms"
java TestClientWithMonitoring localhost "$PORT" "$BUFFER_SIZE" "$MONITOR_INTERVAL" &
CLIENT_PID=$!

# Wait for the client to start
echo "Waiting for client to start..."
sleep 2

# Check if the client is running
if ! ps -p $CLIENT_PID > /dev/null; then
    echo "Error: Failed to start the test client"
    kill $SERVER_PID
    exit 1
fi

# Display monitoring information
echo
echo "=== Monitoring Information ==="
echo "Server PID: $SERVER_PID"
echo "Client PID: $CLIENT_PID"
echo
echo "To view detailed buffer status, run:"
echo "  ./check_buffer_status.sh"
echo
echo "Press Ctrl+C to stop monitoring and clean up"
echo

# Cleanup function
cleanup() {
    echo "Cleaning up..."
    kill $SERVER_PID 2>/dev/null || true
    kill $CLIENT_PID 2>/dev/null || true
    echo "Monitoring stopped."
}

# Register the cleanup function to be called on exit
trap cleanup EXIT

# Wait for user to press Ctrl+C
while true; do
    # Display basic status
    echo "----------------------------------------"
    echo "Status at $(date)"
    echo "----------------------------------------"
    echo "Server running: $(ps -p $SERVER_PID > /dev/null && echo "Yes" || echo "No")"
    echo "Client running: $(ps -p $CLIENT_PID > /dev/null && echo "Yes" || echo "No")"
    
    # Get client output if available
    if ps -p $CLIENT_PID > /dev/null; then
        # Try to get buffer status
        BUFFER_INFO=$(ps -o args= -p $CLIENT_PID | grep -o "Buffer.*" || echo "")
        if [ -n "$BUFFER_INFO" ]; then
            echo "Buffer info: $BUFFER_INFO"
        fi
        
        # Try to get throughput
        THROUGHPUT=$(ps -o args= -p $CLIENT_PID | grep -o "Throughput.*" || echo "")
        if [ -n "$THROUGHPUT" ]; then
            echo "Throughput: $THROUGHPUT"
        fi
    fi
    
    echo
    sleep 5
done 