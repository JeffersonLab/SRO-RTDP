#!/bin/bash

# Check if apptainer is installed
if ! command -v apptainer &> /dev/null; then
    echo "Error: apptainer is not installed. Please install apptainer first."
    exit 1
fi

# Create a temporary directory for the build
TMP_DIR=$(mktemp -d)
echo "Using temporary directory: $TMP_DIR"

# Convert Docker image to Apptainer SIF
echo "Converting Docker image to Apptainer SIF..."
apptainer build $TMP_DIR/rtdp-farm-nic-test.sif docker-daemon://farm-nic-test:latest

# Move the SIF file to the current directory
mv $TMP_DIR/rtdp-farm-nic-test.sif .

# Clean up
rm -rf $TMP_DIR

echo "Conversion complete!"
echo "Apptainer SIF file created: rtdp-farm-nic-test.sif"
echo ""
echo "=== Instructions for both sender and receiver ==="
echo ""
echo "1. Receiver Side (Run this first):"
echo "   apptainer run rtdp-farm-nic-test.sif iperf -s"
echo ""
echo "2. Sender Side (Run after receiver is ready):"
echo "   apptainer run rtdp-farm-nic-test.sif /usr/local/bin/nic_test.py <receiver_ip>"
echo "   Example: apptainer run rtdp-farm-nic-test.sif /usr/local/bin/nic_test.py 192.168.1.100"
echo ""
echo "Note: Make sure to run the receiver side first and keep it running"
echo "      while performing the test from the sender side." 