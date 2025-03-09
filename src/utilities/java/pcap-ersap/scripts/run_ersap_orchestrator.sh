#!/bin/bash

# This script runs the ERSAP orchestrator with our rebuilt environment

# Set environment variables
export ERSAP_HOME="/workspace/src/utilities/java/ersapActors/ersap-java"
export ERSAP_USER_DATA="/workspace/src/utilities/java/pcap-ersap"

# Rebuild the ERSAP environment
echo "Rebuilding ERSAP environment..."
./scripts/rebuild_ersap.sh

# Fix package structure and imports
echo "Fixing package structure..."
./scripts/fix_package_structure.sh
echo "Fixing imports..."
./scripts/fix_imports.sh

# Check if pcap2streams is running
if ! pgrep -f "pcap2streams" > /dev/null; then
    echo "Starting pcap2streams..."
    cd /workspace/src/utilities/java/pcap2streams
    ./pcap2streams -f /workspace/src/utilities/java/pcap2streams/pcap/test.pcap -c /workspace/src/utilities/java/pcap2streams/custom-config/ip-based-config.json &
    PCAP2STREAMS_PID=$!
    cd $ERSAP_USER_DATA
    # Wait for pcap2streams to start
    sleep 30
else
    echo "Pcap2Streams is already running"
    PCAP2STREAMS_PID=""
fi

# Create output directory if it doesn't exist
mkdir -p $ERSAP_USER_DATA/output

# Compile the application
echo "Compiling application..."
cd $ERSAP_USER_DATA
gradle clean build -x test

# Verify JAR files
echo "Verifying JAR files..."
ls -la $ERSAP_HOME/lib/ersap/
ls -la $ERSAP_USER_DATA/lib/

# Start the ERSAP orchestrator
echo "Starting ERSAP orchestrator..."
JAVA_OPTS="-Xmx4g" $ERSAP_HOME/scripts/unix/ersap-orchestrator -f $ERSAP_USER_DATA/config/pcap-services.yaml

# Wait for processing to complete
echo "Waiting for processing to complete..."
sleep 300

# Check output files
echo "Checking output files..."
ls -la $ERSAP_USER_DATA/output/

# Stop the orchestrator and pcap2streams if we started it
if [ ! -z "$PCAP2STREAMS_PID" ]; then
    echo "Stopping pcap2streams..."
    kill $PCAP2STREAMS_PID
fi

echo "ERSAP orchestrator completed successfully." 