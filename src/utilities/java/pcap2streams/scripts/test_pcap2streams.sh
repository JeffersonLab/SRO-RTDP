#!/bin/bash

# Exit on error
set -e

echo "Testing pcap2streams..."

# Check if pcap2streams directory exists
PCAP2STREAMS_DIR="/workspaces/ersap-actors/src/utilities/java/pcap2streams"
if [ ! -d "$PCAP2STREAMS_DIR" ]; then
    echo "Error: pcap2streams directory not found at $PCAP2STREAMS_DIR"
    exit 1
fi

# Check if run script exists and is executable
RUN_SCRIPT="$PCAP2STREAMS_DIR/scripts/run_pcap2streams.sh"
if [ ! -f "$RUN_SCRIPT" ]; then
    echo "Error: run_pcap2streams.sh not found at $RUN_SCRIPT"
    exit 1
fi

if [ ! -x "$RUN_SCRIPT" ]; then
    echo "Error: run_pcap2streams.sh is not executable"
    echo "Please make it executable with: chmod +x $RUN_SCRIPT"
    exit 1
fi

# Use the PCAP file from /scratch/jeng-yuantsai/
PCAP_FILE="/scratch/jeng-yuantsai/CLAS12_ECAL_PCAL_DC_2024-05-15_17-12-30.pcap"
if [ ! -f "$PCAP_FILE" ]; then
    echo "Error: PCAP file not found at $PCAP_FILE"
    exit 1
fi

echo "Starting pcap2streams with $PCAP_FILE..."
cd "$PCAP2STREAMS_DIR"
./scripts/run_pcap2streams.sh "$PCAP_FILE"

# Note: This will run in the foreground so we can see any errors
# Press Ctrl+C to stop the test 