#!/bin/bash

# This script tests the entire ERSAP actors pipeline

# Set environment variables if not already set
if [ -z "$ERSAP_HOME" ]; then
    export ERSAP_HOME=/opt/ersap
    echo "Setting ERSAP_HOME to $ERSAP_HOME"
fi

if [ -z "$ERSAP_USER_DATA" ]; then
    export ERSAP_USER_DATA=/workspace/ersap-data
    echo "Setting ERSAP_USER_DATA to $ERSAP_USER_DATA"
fi

# Create ERSAP_USER_DATA directory structure if it doesn't exist
mkdir -p $ERSAP_USER_DATA/config
mkdir -p $ERSAP_USER_DATA/data/input
mkdir -p $ERSAP_USER_DATA/data/output
mkdir -p $ERSAP_USER_DATA/log

# Copy services.yaml to the config directory
cp $(dirname "$0")/main/resources/services.yaml $ERSAP_USER_DATA/config/

# Build the ERSAP actors project
echo "Building ERSAP actors project..."
cd $(dirname "$0")
gradle deploy

# Check if we have a test PCAP file, generate one if not
if [ ! -f "$(dirname "$0")/samples/test.pcap" ]; then
    echo "Generating test PCAP file..."
    $(dirname "$0")/samples/generate_test_pcap.sh
fi

# Check if pcap2stream utilities exist
if [ ! -d "/workspace/pcap2stream" ]; then
    echo "Warning: pcap2stream directory not found!"
    echo "The full pipeline test cannot be run without pcap2stream."
    echo "Please make sure the pcap2stream directory is properly mounted in the devcontainer."
    echo "You can still build and deploy the ERSAP actors, but the full pipeline test will be skipped."
    exit 0
fi

# Build pcap2stream tools if they're not already built
if [ ! -f "/workspace/pcap2stream/server/build/stream_server" ]; then
    echo "Building pcap2stream server..."
    cd /workspace/pcap2stream/server
    mkdir -p build && cd build
    cmake ..
    make
fi

if [ ! -f "/workspace/pcap2stream/sender/build/pcap2stream" ]; then
    echo "Building pcap2stream sender..."
    cd /workspace/pcap2stream/sender
    mkdir -p build && cd build
    cmake ..
    make
fi

# Start the stream server in the background
echo "Starting stream server..."
cd /workspace/pcap2stream/server/build
./stream_server 0.0.0.0 5000 3 > /tmp/stream_server.log 2>&1 &
SERVER_PID=$!

# Wait for the server to start
sleep 2

# Start the ERSAP shell in the background
echo "Starting ERSAP shell..."
$ERSAP_HOME/bin/ersap-shell > /tmp/ersap_shell.log 2>&1 &
ERSAP_PID=$!

# Wait for ERSAP to start
sleep 2

# Send PCAP data to the server
echo "Sending PCAP data to the server..."
cd /workspace/pcap2stream/sender/build
./pcap2stream /workspace/samples/test.pcap 127.0.0.1 5000

# Wait for processing to complete
sleep 5

# Clean up
echo "Cleaning up..."
kill $SERVER_PID
kill $ERSAP_PID

echo "Test complete!"
echo "Check the logs for details:"
echo "  - Stream server log: /tmp/stream_server.log"
echo "  - ERSAP shell log: /tmp/ersap_shell.log" 