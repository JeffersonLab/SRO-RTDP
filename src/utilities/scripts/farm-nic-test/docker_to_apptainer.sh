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
apptainer build $TMP_DIR/rtdp-farm-nic-test.sif docker://jlabtsai/rtdp-farm-nic-test

# Move the SIF file to the current directory
mv $TMP_DIR/rtdp-farm-nic-test.sif .

# Clean up
rm -rf $TMP_DIR

echo "Conversion complete!"
echo "Apptainer SIF file created: rtdp-farm-nic-test.sif"
echo ""
echo "Usage: apptainer run --network-args \"portmap=5201:5201/tcp\" rtdp-farm-nic-test.sif <receiver_ip>"
echo "Example: apptainer run --network-args \"portmap=5201:5201/tcp\" rtdp-farm-nic-test.sif 192.168.1.100" 