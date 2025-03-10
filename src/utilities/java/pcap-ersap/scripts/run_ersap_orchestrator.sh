#!/bin/bash

# Default configuration
SOCKET_BUFFER_SIZE=16384
RING_BUFFER_SIZE=1024
PCAP_FILE="/scratch/jeng-yuantsai/CLAS12_ECAL_PCAL_DC_2024-05-15_17-12-30.pcap"

# Parse command line arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --socket-buffer-size=*)
      SOCKET_BUFFER_SIZE="${1#*=}"
      shift
      ;;
    --ring-buffer-size=*)
      RING_BUFFER_SIZE="${1#*=}"
      shift
      ;;
    --pcap-file=*)
      PCAP_FILE="${1#*=}"
      shift
      ;;
    *)
      echo "Unknown option: $1"
      echo "Usage: $0 [--socket-buffer-size=SIZE] [--ring-buffer-size=SIZE] [--pcap-file=PATH]"
      exit 1
      ;;
  esac
done

echo "Using socket buffer size: $SOCKET_BUFFER_SIZE"
echo "Using ring buffer size: $RING_BUFFER_SIZE"
echo "Using PCAP file: $PCAP_FILE"

# Set environment variables
export ERSAP_HOME=/workspace/src/utilities/java/ersapActors/ersap-java
export ERSAP_USER_DATA=/workspace/src/utilities/java/pcap-ersap

# Create directories if they don't exist
mkdir -p $ERSAP_USER_DATA/config
mkdir -p $ERSAP_USER_DATA/output

# Copy services.yaml to config directory
echo "Copying services.yaml to config directory..."
cp $ERSAP_USER_DATA/src/main/resources/org/jlab/ersap/actor/pcap/services.yaml $ERSAP_USER_DATA/config/

# Check if pcap2streams is running
if pgrep -f "pcap2streams" > /dev/null; then
    echo "pcap2streams is already running"
    # Check if configuration file exists
    if [ -f "/workspace/src/utilities/java/pcap2streams/custom-config/ip-based-config.json" ]; then
        echo "Configuration file exists: /workspace/src/utilities/java/pcap2streams/custom-config/ip-based-config.json"
    else
        echo "Configuration file does not exist"
    fi
else
    echo "pcap2streams is not running"
    # Check if PCAP file exists
    if [ -f "$PCAP_FILE" ]; then
        echo "Starting pcap2streams with PCAP file: $PCAP_FILE"
        cd /workspace/src/utilities/java/pcap2streams && ./pcap2streams "$PCAP_FILE" &
        STARTED_PCAP2STREAMS=true
        sleep 2
    else
        echo "PCAP file does not exist: $PCAP_FILE"
        echo "Checking for default PCAP file..."
        if [ -f "/workspace/src/utilities/java/pcap2streams/pcap/ersap_test.pcap" ]; then
            echo "Using default PCAP file: /workspace/src/utilities/java/pcap2streams/pcap/ersap_test.pcap"
            cd /workspace/src/utilities/java/pcap2streams && ./pcap2streams /workspace/src/utilities/java/pcap2streams/pcap/ersap_test.pcap &
            STARTED_PCAP2STREAMS=true
            sleep 2
        else
            echo "Default PCAP file not found either. Exiting."
            exit 1
        fi
    fi
fi

# Compile and deploy the application
echo "Compiling application..."
cd $ERSAP_USER_DATA && ./gradlew build
echo "Deploying application..."
cd $ERSAP_USER_DATA && ./gradlew deploy

# Run the orchestrator directly using our RunOrchestrator class
echo "Starting ERSAP orchestrator..."
cd $ERSAP_USER_DATA && java -DsocketBufferSize=$SOCKET_BUFFER_SIZE -DringBufferSize=$RING_BUFFER_SIZE -cp "build/libs/*:lib/*:$ERSAP_HOME/lib/*" org.jlab.ersap.actor.pcap.RunOrchestrator

# Check output files
echo "Checking output files..."
ls -la $ERSAP_USER_DATA/output

# Stop pcap2streams if it was started by this script
if [ "$STARTED_PCAP2STREAMS" = true ]; then
    echo "Stopping pcap2streams..."
    pkill -f "pcap2streams"
fi

echo "ERSAP orchestrator completed successfully." 