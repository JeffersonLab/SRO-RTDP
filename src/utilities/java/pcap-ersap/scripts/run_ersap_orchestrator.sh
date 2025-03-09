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

# Define the config file path
CONFIG_DIR="/workspace/src/utilities/java/pcap2streams/custom-config"
CONFIG_FILE="$CONFIG_DIR/ip-based-config.json"

# Create the custom-config directory if it doesn't exist
mkdir -p $CONFIG_DIR

# Check if pcap2streams is already running
PCAP2STREAMS_RUNNING=$(ps aux | grep -v grep | grep -v "run_ersap_orchestrator.sh" | grep "java.*Pcap2Streams")
if [ ! -z "$PCAP2STREAMS_RUNNING" ]; then
    echo "pcap2streams is already running"
    PCAP2STREAMS_STARTED=false
    
    # Check if the config file exists
    if [ -f "$CONFIG_FILE" ]; then
        echo "Configuration file exists: $CONFIG_FILE"
    else
        echo "Warning: Configuration file not found. ERSAP orchestrator may not work correctly."
        echo "Stopping existing pcap2streams to restart it properly..."
        pkill -f "java.*Pcap2Streams"
        sleep 2
        PCAP2STREAMS_STARTED=true
    fi
else
    echo "pcap2streams is not running"
    PCAP2STREAMS_STARTED=true
fi

# If we need to start pcap2streams or restart it
if [ "$PCAP2STREAMS_STARTED" = true ]; then
    echo "Starting pcap2streams..."
    
    # Use the PCAP file from /scratch/jeng-yuantsai/
    PCAP_FILE="/scratch/jeng-yuantsai/CLAS12_ECAL_PCAL_DC_2024-05-15_17-12-30.pcap"
    
    # Check if the file exists
    if [ ! -f "$PCAP_FILE" ]; then
        echo "PCAP file not found: $PCAP_FILE"
        echo "Using default test PCAP file"
        PCAP_FILE="/workspace/src/utilities/java/pcap2streams/test.pcap"
    else
        echo "Using PCAP file: $PCAP_FILE"
    fi
    
    # Remove any existing config file to ensure a fresh start
    if [ -f "$CONFIG_FILE" ]; then
        echo "Removing existing configuration file: $CONFIG_FILE"
        rm -f "$CONFIG_FILE"
    fi
    
    # Run pcap2streams using the run_pcap2streams.sh script
    echo "Changing to pcap2streams directory..."
    cd /workspace/src/utilities/java/pcap2streams
    
    # Make sure the script is executable
    chmod +x ./scripts/run_pcap2streams.sh
    
    # Modified: Run pcap2streams in the background
    echo "Running pcap2streams to generate configuration file and serve data..."
    ./scripts/run_pcap2streams.sh "$PCAP_FILE" &
    PCAP2STREAMS_PID=$!
    
    # Return to the original directory
    cd $ERSAP_USER_DATA
    
    # Wait for the configuration file to be created (with timeout)
    echo "Waiting for configuration file to be created..."
    MAX_WAIT=60  # Maximum wait time in seconds
    WAIT_COUNT=0
    
    while [ ! -f "$CONFIG_FILE" ] && [ $WAIT_COUNT -lt $MAX_WAIT ]; do
        sleep 1
        WAIT_COUNT=$((WAIT_COUNT + 1))
        echo -n "."
        
        # Check if pcap2streams is still running
        if ! ps -p $PCAP2STREAMS_PID > /dev/null; then
            echo "Error: pcap2streams process terminated unexpectedly"
            exit 1
        fi
    done
    echo ""
    
    # Check if the config file was created
    if [ -f "$CONFIG_FILE" ]; then
        echo "Configuration file created: $CONFIG_FILE"
        # Give pcap2streams a moment to set up the socket servers after creating the config
        echo "Waiting for socket servers to initialize..."
        sleep 5
    else
        echo "Error: Configuration file not created after $MAX_WAIT seconds. ERSAP orchestrator may not work correctly."
        echo "Killing pcap2streams process..."
        kill $PCAP2STREAMS_PID
        echo "Exiting..."
        exit 1
    fi
fi

# Create output directory if it doesn't exist
mkdir -p $ERSAP_USER_DATA/output

# Update the config file path in pcap-services.yaml
echo "Updating config file path in pcap-services.yaml..."
sed -i "s|config_file:.*|config_file: $CONFIG_FILE|g" $ERSAP_USER_DATA/config/pcap-services.yaml

# Compile the application
echo "Compiling application..."
cd $ERSAP_USER_DATA
gradle clean build -x test

# Verify JAR files
echo "Verifying JAR files..."
ls -la $ERSAP_HOME/lib/ersap/
ls -la $ERSAP_USER_DATA/lib/

# Start the ERSAP orchestrator with more memory
echo "Starting ERSAP orchestrator..."
JAVA_OPTS="-Xmx4g" $ERSAP_HOME/scripts/unix/ersap-orchestrator -f $ERSAP_USER_DATA/config/pcap-services.yaml

# Wait longer for processing to complete (5 minutes)
echo "Waiting for processing to complete..."
sleep 300

# Check output files
echo "Checking output files..."
ls -la $ERSAP_USER_DATA/output/

# Stop pcap2streams if we started it
if [ "$PCAP2STREAMS_STARTED" = true ]; then
    echo "Stopping pcap2streams..."
    kill $PCAP2STREAMS_PID 2>/dev/null || pkill -f "java.*Pcap2Streams"
fi

echo "ERSAP orchestrator completed successfully." 