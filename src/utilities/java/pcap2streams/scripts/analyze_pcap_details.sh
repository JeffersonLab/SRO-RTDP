#!/bin/bash

# Exit on error
set -e

# Set the project directory
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Default PCAP file location
DEFAULT_PCAP_FILE="/scratch/jeng-yuantsai/CLAS12_ECAL_PCAL_DC_2024-05-15_17-12-30.pcap"
PCAP_FILE=${1:-$DEFAULT_PCAP_FILE}

# Create output directory if it doesn't exist
OUTPUT_DIR="$PROJECT_DIR/output"
mkdir -p "$OUTPUT_DIR"

# Output CSV file
CSV_FILE="$OUTPUT_DIR/packet_details.csv"

# Check if the PCAP file exists
if [ ! -f "$PCAP_FILE" ]; then
    echo "Error: PCAP file not found at $PCAP_FILE"
    echo "Usage: $0 [pcap_file]"
    exit 1
fi

# Compile the Java code
echo "Compiling Java code..."
cd "$PROJECT_DIR"
javac -cp "lib/json-20231013.jar" -d build/classes/java/main src/main/java/org/jlab/ersap/actor/pcap2streams/*.java

# Run the analyzer
echo "Analyzing PCAP file: $PCAP_FILE"
echo "Output will be written to: $CSV_FILE"

java -cp "build/classes/java/main:lib/json-20231013.jar" org.jlab.ersap.actor.pcap2streams.PcapPacketAnalyzer "$PCAP_FILE" "$CSV_FILE"

echo "Analysis complete. Results are in $CSV_FILE" 