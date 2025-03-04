#!/bin/bash

# Script to run the mock PCAP server

# Set the project directory
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Check if a PCAP file was provided
if [ $# -lt 1 ]; then
    echo "Usage: $0 <pcap_file> [port]"
    echo "Example: $0 /path/to/capture.pcap 9000"
    exit 1
fi

PCAP_FILE="$1"
PORT="${2:-9000}"

# Check if the PCAP file exists
if [ ! -f "$PCAP_FILE" ]; then
    echo "Error: PCAP file not found: $PCAP_FILE"
    exit 1
fi

# Compile the mock server
echo "Compiling the mock server..."
cd "$PROJECT_DIR/scripts"
javac MockPcapServer.java

# Check if compilation was successful
if [ $? -ne 0 ]; then
    echo "Compilation failed. Please check the error messages."
    exit 1
fi

# Run the mock server
echo "Starting the mock PCAP server..."
java -cp "$PROJECT_DIR/scripts" MockPcapServer "$PCAP_FILE" "$PORT" 