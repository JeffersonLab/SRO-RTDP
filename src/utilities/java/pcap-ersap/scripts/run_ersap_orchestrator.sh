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
export ERSAP_HOME="$PWD/../ersapActors/ersap-java"
export ERSAP_USER_DATA="$PWD"

# Create directories for configuration and output
mkdir -p $ERSAP_USER_DATA/config
mkdir -p $ERSAP_USER_DATA/output

# Copy services.yaml to config directory
echo "Copying services.yaml to config directory..."
cp $ERSAP_USER_DATA/config/services.yaml $ERSAP_USER_DATA/config/services.yaml.bak 2>/dev/null || true
cp $ERSAP_USER_DATA/src/main/resources/org/jlab/ersap/actor/pcap/services.yaml $ERSAP_USER_DATA/config/

# Check if pcap2streams is running
if pgrep -x "pcap2streams" > /dev/null; then
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
        ./pcap2streams --pcap-file="$PCAP_FILE" --ip-based-config=true &
        PCAP2STREAMS_STARTED=true
        cd - > /dev/null
    else
        echo "PCAP file not found: $PCAP_FILE"
        echo "Checking for default PCAP file..."
        if [ -f "/workspace/src/utilities/java/pcap2streams/pcap/ersap_test.pcap" ]; then
            echo "Using default PCAP file: /workspace/src/utilities/java/pcap2streams/pcap/ersap_test.pcap"
            cd /workspace/src/utilities/java/pcap2streams
            ./pcap2streams --pcap-file=pcap/ersap_test.pcap --ip-based-config=true &
            PCAP2STREAMS_STARTED=true
            cd - > /dev/null
        else
            echo "Default PCAP file not found. Cannot start pcap2streams."
            exit 1
        fi
    fi
fi

# Compile and deploy the application
echo "Compiling application..."
./gradlew compileJava

echo "Deploying application..."
./gradlew deploy

# Create a temporary ERSAP shell script
echo "Starting ERSAP orchestrator..."
cat > $ERSAP_USER_DATA/run_ersap.ersap << EOF
set socketBufferSize $SOCKET_BUFFER_SIZE
set ringBufferSize $RING_BUFFER_SIZE
run org.jlab.ersap.actor.pcap.RunOrchestrator
EOF

# Start the ERSAP orchestrator
JAVA_OPTS="-Xmx4g" $ERSAP_HOME/scripts/unix/ersap-shell $ERSAP_USER_DATA/run_ersap.ersap

# Check output files
echo "Checking output files..."
ls -la $ERSAP_USER_DATA/output/

# Stop pcap2streams if we started it
if [ "$PCAP2STREAMS_STARTED" = true ]; then
    echo "Stopping pcap2streams..."
    pkill -f pcap2streams
fi

echo "ERSAP orchestrator completed." 