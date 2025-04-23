#!/bin/bash

# Check if apptainer is installed
if ! command -v apptainer &> /dev/null; then
    echo "Error: apptainer is not installed. Please install apptainer first."
    exit 1
fi

# Convert Docker image to Apptainer SIF
echo "Converting Docker image to Apptainer SIF..."
apptainer build rtdp-farm-nic-test.sif docker://jlabtsai/rtdp-farm-nic-test

echo "Conversion complete!"
echo "Apptainer SIF file created: rtdp-farm-nic-test.sif"
echo ""
echo "=== Instructions for both sender and receiver ==="
echo ""
echo "1. Receiver Side (Run this first):"
echo "   ./run_receiver.sh"
echo ""
echo "2. Sender Side (Run after receiver is ready):"
echo "   ./run_sender.sh <receiver_ip>"
echo "   Example: ./run_sender.sh 192.168.1.100"
echo ""
echo "Note: Make sure to run the receiver side first and keep it running"
echo "      while performing the test from the sender side." 