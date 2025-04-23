#!/bin/bash

# Check if apptainer is installed
if ! command -v apptainer &> /dev/null; then
    echo "Error: apptainer is not installed. Please install apptainer first."
    exit 1
fi

# Check if SIF file exists
if [ ! -f "rtdp-farm-nic-test.sif" ]; then
    echo "Error: rtdp-farm-nic-test.sif not found. Please run ./docker_to_apptainer.sh first."
    exit 1
fi

# Check if receiver IP is provided
if [ -z "$1" ]; then
    echo "Error: Please provide the receiver's IP address"
    echo "Usage: ./run_sender.sh <receiver_ip>"
    echo "Example: ./run_sender.sh 192.168.1.100"
    exit 1
fi

echo "Running NIC test with receiver IP: $1"
echo ""

# Run the test
apptainer run rtdp-farm-nic-test.sif /usr/local/bin/nic_test.py "$1" 