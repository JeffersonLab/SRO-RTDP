#!/bin/bash

# This script tests if pcap2streams creates the configuration file correctly

# Define the config file path
CONFIG_DIR="/workspace/src/utilities/java/pcap2streams/custom-config"
CONFIG_FILE="$CONFIG_DIR/ip-based-config.json"

# Create the custom-config directory if it doesn't exist
mkdir -p $CONFIG_DIR

# Remove any existing config file
if [ -f "$CONFIG_FILE" ]; then
    echo "Removing existing configuration file: $CONFIG_FILE"
    rm -f "$CONFIG_FILE"
fi

# Check if pcap2streams is already running
PCAP2STREAMS_RUNNING=$(ps aux | grep -v grep | grep -v "test_pcap2streams.sh" | grep "java.*Pcap2Streams")
if [ ! -z "$PCAP2STREAMS_RUNNING" ]; then
    echo "pcap2streams is already running. Please stop it before running this test."
    echo "Running processes:"
    echo "$PCAP2STREAMS_RUNNING"
    exit 1
fi

# Use the PCAP file from /scratch/jeng-yuantsai/
PCAP_FILE="/scratch/jeng-yuantsai/CLAS12_ECAL_PCAL_DC_2024-05-15_17-12-30.pcap"

# Check if the file exists
if [ ! -f "$PCAP_FILE" ]; then
    echo "PCAP file not found: $PCAP_FILE"
    echo "Please provide a valid PCAP file path."
    exit 1
fi

echo "Starting pcap2streams with PCAP file: $PCAP_FILE"
echo "Configuration file will be created at: $CONFIG_FILE"

# Change to the pcap2streams directory
cd /workspace/src/utilities/java/pcap2streams

# Run pcap2streams using the run_pcap2streams.sh script
./scripts/run_pcap2streams.sh "$PCAP_FILE" &
PCAP2STREAMS_PID=$!

# Wait for pcap2streams to start and create the config file
echo "Waiting for pcap2streams to initialize and create the config file..."
sleep 10

# Check if the config file was created
if [ -f "$CONFIG_FILE" ]; then
    echo "Configuration file created successfully: $CONFIG_FILE"
    echo "Contents of the configuration file:"
    cat "$CONFIG_FILE"
else
    echo "Error: Configuration file was not created."
fi

# Stop pcap2streams
echo "Stopping pcap2streams..."
pkill -f "java.*Pcap2Streams"

echo "Test completed." 