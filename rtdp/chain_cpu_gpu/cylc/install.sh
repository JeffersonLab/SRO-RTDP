#!/bin/bash

# Set -e to exit on error
set -e

# Set fixed workflow name
WORKFLOW_NAME="chain-cpu-gpu"

# Create necessary directories
mkdir -p sifs etc/config scripts

# Make sure we're in the correct directory
CYLC_RUN_DIR=~/jeng/cylc-run/${WORKFLOW_NAME}
mkdir -p ${CYLC_RUN_DIR}

# Install the workflow using Cylc
echo "Installing workflow..."
cylc install --workflow-name=${WORKFLOW_NAME}

# Validate the workflow
echo "Validating workflow..."
cylc validate .

echo "Workflow installed and validated."
echo "Before running, please ensure:"
echo "1. CPU emulator SIF file is built in ./sifs/cpu-emu.sif"
echo "2. GPU proxy SIF file is built in ./sifs/gpu-proxy.sif"
echo "3. Configure chain_config.yaml with your desired chain configuration"
echo "4. Configure environment variables in flow.cylc if needed"
echo ""
echo "To run the workflow:"
echo "cylc play ${WORKFLOW_NAME}"
echo ""
echo "Workflow installed at: ${CYLC_RUN_DIR}" 