#!/bin/bash

# Exit on error
set -e

# Function to check if a command exists
check_command() {
    if ! command -v $1 &> /dev/null; then
        echo "Error: $1 is not installed"
        echo "Please install $1 and try again"
        exit 1
    fi
}

# Function to check if a file exists and is executable
check_executable() {
    if [ ! -f "$1" ]; then
        echo "Error: $1 does not exist"
        exit 1
    fi
    if [ ! -x "$1" ]; then
        echo "Error: $1 is not executable"
        echo "Please make it executable with: chmod +x $1"
        exit 1
    fi
}

# Function to check if a file exists
check_file() {
    if [ ! -f "$1" ]; then
        echo "Error: $1 does not exist"
        exit 1
    fi
}

echo "Checking prerequisites..."

# Check for required commands
check_command "jq"

# Check for pcap2streams directory and run script
PCAP2STREAMS_DIR="/workspaces/ersap-actors/src/utilities/java/pcap2streams"
RUN_SCRIPT="$PCAP2STREAMS_DIR/scripts/run_pcap2streams.sh"
check_executable "$RUN_SCRIPT"

# Use the PCAP file from /scratch/jeng-yuantsai/
PCAP_FILE="/scratch/jeng-yuantsai/CLAS12_ECAL_PCAL_DC_2024-05-15_17-12-30.pcap"
check_file "$PCAP_FILE"

# Set ERSAP_HOME to match build_ersap-java.sh
export ERSAP_HOME=$HOME/ersap-install

# Set LD_LIBRARY_PATH for native libraries
export LD_LIBRARY_PATH=/usr/local/lib:/usr/local/lib64

# Build and install the PCAP actors
echo "Building and installing PCAP actors..."
PCAP_ACTORS_DIR="/workspaces/ersap-actors/src/utilities/java/ersap-pcap/pcap-actors"
cd "$PCAP_ACTORS_DIR"
./gradlew clean build
ERSAP_HOME=$ERSAP_HOME ./gradlew install

echo "Starting pcap2streams..."
# Create a temporary file to store pcap2streams output
TEMP_LOG=$(mktemp)

# Start pcap2streams in the background and redirect output to temp file
cd "$PCAP2STREAMS_DIR"
./scripts/run_pcap2streams.sh "$PCAP_FILE" > "$TEMP_LOG" 2>&1 &
PCAP_PID=$!

# Wait for "Ctrl" to appear in the log file
echo "Waiting for pcap2streams to initialize..."
TIMEOUT=300000  # 60 seconds timeout
START_TIME=$(date +%s)
while ! grep -q "Ctrl" "$TEMP_LOG"; do
    sleep 1
    # Check if pcap2streams is still running
    if ! kill -0 $PCAP_PID 2>/dev/null; then
        echo "Error: pcap2streams failed to start properly"
        echo "Last 50 lines of output:"
        tail -n 50 "$TEMP_LOG"
        exit 1
    fi
    # Check for timeout
    CURRENT_TIME=$(date +%s)
    if [ $((CURRENT_TIME - START_TIME)) -ge $TIMEOUT ]; then
        echo "Error: pcap2streams failed to start within $TIMEOUT seconds"
        echo "Last 50 lines of output:"
        tail -n 50 "$TEMP_LOG"
        kill $PCAP_PID
        exit 1
    fi
done

# Wait a bit more to ensure all servers are fully initialized
sleep 2

echo "pcap2streams is ready!"

# Create input and output directories
echo "Preparing directories..."
mkdir -p "$PCAP_ACTORS_DIR/input"
mkdir -p "$PCAP_ACTORS_DIR/output"

# Transform JSON configuration into the expected text format
# Extract IP, host, and port from the JSON and format as "IP HOST PORT"
echo "Transforming configuration format..."
jq -r '.connections[] | "\(.ip) \(.host) \(.port)"' \
    "$PCAP2STREAMS_DIR/custom-config/ip-based-config.json" > \
    "$PCAP_ACTORS_DIR/input/pcap_sockets.txt"

# Verify the transformed file was created and has content
if [ ! -s "$PCAP_ACTORS_DIR/input/pcap_sockets.txt" ]; then
    echo "Error: Failed to create or transform pcap_sockets.txt"
    rm "$TEMP_LOG"
    exit 1
fi

echo "Running PCAP pipeline..."
# Change to the pcap-actors directory and run the pipeline
cd "$PCAP_ACTORS_DIR"
$HOME/ersap-install/bin/ersap-shell config/localhost/pcap_localhost.ersap > log.txt

# Cleanup: kill pcap2streams when the pipeline is done
echo "Cleaning up..."
kill $PCAP_PID
rm "$TEMP_LOG"

echo "PCAP pipeline execution completed!" 