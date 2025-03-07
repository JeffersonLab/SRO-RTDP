#!/bin/bash

# Check if ERSAP_HOME is set
if [ -z "$ERSAP_HOME" ]; then
    echo "Error: ERSAP_HOME environment variable is not set."
    echo "Please set ERSAP_HOME to the root directory of your ERSAP installation."
    exit 1
fi

# Check if ERSAP_USER_DATA is set
if [ -z "$ERSAP_USER_DATA" ]; then
    echo "Error: ERSAP_USER_DATA environment variable is not set."
    echo "Please set ERSAP_USER_DATA to the directory where you want to store ERSAP data."
    exit 1
fi

# Create necessary directories if they don't exist
mkdir -p $ERSAP_USER_DATA/config
mkdir -p $ERSAP_USER_DATA/data/input
mkdir -p $ERSAP_USER_DATA/data/output
mkdir -p $ERSAP_USER_DATA/log

# Copy the services.yaml file to the config directory
cp $(dirname "$0")/../src/main/resources/services.yaml $ERSAP_USER_DATA/config/

# Start the mock PCAP server
echo "Starting mock PCAP server..."
java -cp "../build/libs/pcap-stream-source-1.0-SNAPSHOT.jar" org.jlab.ersap.actor.pcap.scripts.MockPcapServer test-data/example.pcap 9000 &
SERVER_PID=$!

# Wait for the server to start
sleep 2

# Start the ERSAP orchestrator
echo "Starting ERSAP orchestrator..."
$ERSAP_HOME/bin/ersap-orchestrator -f $ERSAP_USER_DATA/config/services.yaml

# Clean up
echo "Stopping mock PCAP server..."
kill $SERVER_PID

echo "Workflow completed." 