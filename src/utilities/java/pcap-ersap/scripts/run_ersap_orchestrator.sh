#!/bin/bash

# Default configurations
SOCKET_BUFFER_SIZE=11534336  # Increased to 11MB to handle large packets
RING_BUFFER_SIZE=1024
PCAP_FILE="/scratch/jeng-yuantsai/CLAS12_ECAL_PCAL_DC_2024-05-15_17-12-30.pcap"

# Parse command-line arguments
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
export ERSAP_HOME="/workspace/src/utilities/java/ersapActors/ersap-java"
export ERSAP_USER_DATA="/workspace/src/utilities/java/pcap-ersap"

# Create directories for configuration and output
mkdir -p $ERSAP_USER_DATA/config
mkdir -p $ERSAP_USER_DATA/output

# Copy services.yaml to config directory
echo "Copying services.yaml to config directory..."
cp $ERSAP_USER_DATA/config/services.yaml $ERSAP_USER_DATA/config/services.yaml.bak 2>/dev/null || true
cp $ERSAP_USER_DATA/src/main/resources/org/jlab/ersap/actor/pcap/services.yaml $ERSAP_USER_DATA/config/

# Check if pcap2streams is running
if pgrep -f "java.*Pcap2Streams" > /dev/null; then
    echo "pcap2streams is already running"
    PCAP2STREAMS_STARTED=false
    
    # Check if configuration file exists
    if [ -f "/workspace/src/utilities/java/pcap2streams/custom-config/ip-based-config.json" ]; then
        echo "Configuration file exists"
    else
        echo "Configuration file does not exist"
    fi
else
    # Check if PCAP file exists
    if [ -f "$PCAP_FILE" ]; then
        echo "Starting pcap2streams with PCAP file: $PCAP_FILE"
        cd /workspace/src/utilities/java/pcap2streams
        # Use the run_pcap2streams.sh script
        if [ -f "./scripts/run_pcap2streams.sh" ]; then
            ./scripts/run_pcap2streams.sh "$PCAP_FILE" &
            PCAP2STREAMS_STARTED=true
            # Wait a moment for pcap2streams to start
            sleep 2
        else
            echo "Error: run_pcap2streams.sh script not found in $(pwd)/scripts"
            exit 1
        fi
        cd $ERSAP_USER_DATA
    else
        echo "PCAP file not found: $PCAP_FILE"
        echo "Checking for default PCAP file..."
        if [ -f "/workspace/src/utilities/java/pcap2streams/test.pcap" ]; then
            echo "Using default PCAP file: /workspace/src/utilities/java/pcap2streams/test.pcap"
            cd /workspace/src/utilities/java/pcap2streams
            if [ -f "./scripts/run_pcap2streams.sh" ]; then
                ./scripts/run_pcap2streams.sh "./test.pcap" &
                PCAP2STREAMS_STARTED=true
                # Wait a moment for pcap2streams to start
                sleep 2
            else
                echo "Error: run_pcap2streams.sh script not found in $(pwd)/scripts"
                exit 1
            fi
            cd $ERSAP_USER_DATA
        else
            echo "Default PCAP file not found. Cannot start pcap2streams."
            exit 1
        fi
    fi
fi

# Wait for pcap2streams to finish analyzing the PCAP file
echo "Waiting for pcap2streams to finish analyzing the PCAP file..."
while ! [ -f "/workspace/src/utilities/java/pcap2streams/custom-config/ip-based-config.json" ]; do
    echo "Waiting for configuration file to be created..."
    sleep 2
done
echo "Configuration file found. Continuing..."

# Compile and deploy the application
echo "Compiling application..."
cd $ERSAP_USER_DATA
./gradlew compileJava

echo "Deploying application..."
./gradlew deploy

# Wait for pcap2streams servers to be ready
echo "Waiting for pcap2streams servers to be ready..."
# Extract ports from the configuration file
PORTS=$(grep -o '"port": [0-9]*' /workspace/src/utilities/java/pcap2streams/custom-config/ip-based-config.json | awk '{print $2}')

# Wait for all ports to be listening
for PORT in $PORTS; do
    echo "Waiting for port $PORT to be ready..."
    while ! netstat -tuln | grep -q ":$PORT "; do
        echo "Port $PORT not ready yet, waiting..."
        sleep 2
    done
    echo "Port $PORT is ready!"
done
echo "All pcap2streams servers are ready!"

# Set system properties for socket buffer size
echo "Setting system properties for buffer sizes..."
export JAVA_OPTS="-Xmx4g -DsocketBufferSize=$SOCKET_BUFFER_SIZE -DringBufferSize=$RING_BUFFER_SIZE"

# Run the orchestrator directly
echo "Starting ERSAP orchestrator..."
cd $ERSAP_USER_DATA
java $JAVA_OPTS -cp "build/libs/*:lib/*:$ERSAP_HOME/lib/*" org.jlab.ersap.actor.pcap.RunOrchestrator

# Check output files
echo "Checking output files..."
ls -la $ERSAP_USER_DATA/output/

# Stop pcap2streams if we started it
if [ "$PCAP2STREAMS_STARTED" = true ]; then
    echo "Stopping pcap2streams..."
    pkill -f "java.*Pcap2Streams"
fi

echo "ERSAP orchestrator completed." 