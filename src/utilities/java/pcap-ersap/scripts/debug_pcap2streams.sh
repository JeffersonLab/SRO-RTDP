#!/bin/bash

# This script debugs the pcap2streams application to see why ip-based-config.json isn't being created

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

# Kill any existing pcap2streams processes
echo "Killing any existing pcap2streams processes..."
pkill -f "java.*Pcap2Streams"
sleep 2

# Use the PCAP file from /scratch/jeng-yuantsai/
PCAP_FILE="/scratch/jeng-yuantsai/CLAS12_ECAL_PCAL_DC_2024-05-15_17-12-30.pcap"

# Check if the file exists
if [ ! -f "$PCAP_FILE" ]; then
    echo "PCAP file not found: $PCAP_FILE"
    echo "Trying default test PCAP file..."
    PCAP_FILE="/workspace/src/utilities/java/pcap2streams/test.pcap"
    
    if [ ! -f "$PCAP_FILE" ]; then
        echo "Default test PCAP file not found either. Exiting."
        exit 1
    fi
fi

echo "Using PCAP file: $PCAP_FILE"
echo "Configuration file should be created at: $CONFIG_FILE"

# Change to the pcap2streams directory
cd /workspace/src/utilities/java/pcap2streams

# Check if the run_pcap2streams.sh script exists
if [ ! -f "./scripts/run_pcap2streams.sh" ]; then
    echo "Error: run_pcap2streams.sh script not found."
    echo "Current directory: $(pwd)"
    echo "Contents of scripts directory:"
    ls -la ./scripts/
    exit 1
fi

# Make sure the script is executable
chmod +x ./scripts/run_pcap2streams.sh

# Check the content of the run_pcap2streams.sh script
echo "Content of run_pcap2streams.sh:"
cat ./scripts/run_pcap2streams.sh

# Run pcap2streams directly with verbose output
echo "Running pcap2streams directly..."
./scripts/run_pcap2streams.sh "$PCAP_FILE"

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
    echo "Contents of custom-config directory:"
    ls -la $CONFIG_DIR
fi

# Stop pcap2streams
echo "Stopping pcap2streams..."
pkill -f "java.*Pcap2Streams"

echo "Debug completed." 