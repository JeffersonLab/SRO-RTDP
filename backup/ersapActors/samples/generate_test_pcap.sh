#!/bin/bash

# This script generates a test PCAP file for testing the ERSAP actors

# Check if tcpdump is installed
if ! command -v tcpdump &> /dev/null; then
    echo "Error: tcpdump is not installed. Please install it first."
    exit 1
fi

# Create output directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="$SCRIPT_DIR"
mkdir -p "$OUTPUT_DIR"

# Generate a test PCAP file with 10 ICMP packets
echo "Generating test PCAP file..."
echo "Capturing 10 ICMP packets..."

# Start tcpdump in the background
tcpdump -c 10 -w "$OUTPUT_DIR/test.pcap" icmp &
TCPDUMP_PID=$!

# Generate some ICMP traffic
ping -c 15 localhost > /dev/null 2>&1

# Wait for tcpdump to finish
wait $TCPDUMP_PID

# Check if the file was created
if [ -f "$OUTPUT_DIR/test.pcap" ]; then
    FILE_SIZE=$(du -h "$OUTPUT_DIR/test.pcap" | cut -f1)
    echo "Test PCAP file created successfully: $OUTPUT_DIR/test.pcap (Size: $FILE_SIZE)"
    
    # Create a README file with information about the sample files
    cat > "$OUTPUT_DIR/README.md" << EOF
# Sample PCAP Files

This directory contains sample PCAP files for testing the ERSAP actors.

## test.pcap

A simple PCAP file containing 10 ICMP packets (ping to localhost).

### Generation

This file was generated using the \`generate_test_pcap.sh\` script:

\`\`\`bash
./generate_test_pcap.sh
\`\`\`

### Usage

You can use this file with the pcap2stream utility:

\`\`\`bash
cd /workspace/pcap2stream/sender/build
./pcap2stream /workspace/samples/test.pcap 127.0.0.1 5000
\`\`\`

This will send the packets to the stream server running on localhost port 5000.
EOF
    
    echo "README file created: $OUTPUT_DIR/README.md"
    
    # Create a copy in the workspace samples directory if it exists
    if [ -d "/workspace" ]; then
        mkdir -p "/workspace/samples"
        cp "$OUTPUT_DIR/test.pcap" "/workspace/samples/test.pcap"
        echo "Copy created: /workspace/samples/test.pcap"
    fi
    
    exit 0
else
    echo "Error: Failed to create test PCAP file."
    exit 1
fi 