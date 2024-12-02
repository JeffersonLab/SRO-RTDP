#!/bin/bash

# Get the current directory name
WORKFLOW_NAME=$(basename $(pwd))

# Create necessary directories
mkdir -p etc/config
mkdir -p sifs
mkdir -p scripts

# Install the workflow using Cylc
cylc install

# Validate the workflow
cylc validate .

echo "Workflow installed and validated."
echo "Before running, please ensure:"
echo "1. Container images are copied to ./sifs/"
echo "   - process-exporter.sif"
echo "   - prom.sif"
echo "2. IPERF3_PATH in flow.cylc points to your iperf3 binary"
echo ""
echo "To run the workflow:"
echo "cylc play ${WORKFLOW_NAME}"