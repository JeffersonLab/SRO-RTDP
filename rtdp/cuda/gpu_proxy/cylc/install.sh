#!/bin/bash

# Set -e to exit on error
set -e

# Set fixed workflow name
WORKFLOW_NAME="gpu-proxy"

# Create necessary directories
mkdir -p sifs etc/config scripts


# Install the workflow using Cylc
echo "Installing workflow..."
cylc install --workflow-name=${WORKFLOW_NAME}

# Validate the workflow
echo "Validating workflow..."
cylc validate .

echo "Workflow installed and validated."
echo "Before running, please ensure:"
echo "1. GPU proxy SIF file is copied to ./sifs/gpu-proxy.sif"
echo "2. Configure environment variables in flow.cylc if needed"
echo ""
echo "To run the workflow:"
echo "cylc play ${WORKFLOW_NAME}"
echo ""
echo "Workflow installed at: ${CYLC_RUN_DIR}" 