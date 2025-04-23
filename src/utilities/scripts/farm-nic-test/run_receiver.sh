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

echo "Starting iperf2 server..."
echo "Press Ctrl+C to stop the server"
echo ""

# Run the server
apptainer run rtdp-farm-nic-test.sif iperf -s 