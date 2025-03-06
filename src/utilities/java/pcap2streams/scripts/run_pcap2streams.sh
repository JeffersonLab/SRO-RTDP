#!/bin/bash

# Script to run the Pcap2Streams application

# Set the project directory
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPTS_DIR="$PROJECT_DIR/scripts"
CONFIG_DIR="$PROJECT_DIR/custom-config"

# Default PCAP file location
DEFAULT_PCAP_FILE="/scratch/jeng-yuantsai/CLAS12_ECAL_PCAL_DC_2024-05-15_17-12-30.pcap"

# Parse command line arguments
PCAP_FILE=${1:-$DEFAULT_PCAP_FILE}

# Check if the PCAP file exists
if [ ! -f "$PCAP_FILE" ]; then
    echo "Error: PCAP file not found at $PCAP_FILE"
    echo "Usage: $0 [pcap_file]"
    exit 1
fi

# Create config directory if it doesn't exist
mkdir -p "$CONFIG_DIR"

# Compile the application
echo "Compiling the Pcap2Streams application..."
cd "$PROJECT_DIR"
javac -d build/classes/java/main -cp "lib/json-20231013.jar:lib/disruptor-3.4.4.jar:lib/snakeyaml-2.0.jar" src/main/java/org/jlab/ersap/actor/pcap2streams/*.java

# Kill any existing Pcap2Streams processes
echo "Killing any existing Pcap2Streams processes..."
pkill -f "java.*Pcap2Streams" 2>/dev/null

# Run the Pcap2Streams application
echo "Starting Pcap2Streams with PCAP file: $PCAP_FILE"
java -cp "build/classes/java/main:lib/json-20231013.jar:lib/disruptor-3.4.4.jar:lib/snakeyaml-2.0.jar" org.jlab.ersap.actor.pcap2streams.Pcap2Streams "$PCAP_FILE" "$CONFIG_DIR" 