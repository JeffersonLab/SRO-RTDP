#!/bin/bash

# Set -e to exit on error
set -e

# Set fixed workflow name
WORKFLOW_NAME="rtdp-workflow"

# Clean existing directories
echo "Cleaning up existing directories..."
rm -rf sifs etc/config scripts

# Create necessary directories
echo "Creating directories..."
mkdir -p sifs etc/config scripts share

# Build container images and convert to SIF format
echo "Building and converting container images..."
./build.sh

# Make sure we're in the correct directory
CYLC_RUN_DIR=~/cylc-run/${WORKFLOW_NAME}
mkdir -p ${CYLC_RUN_DIR}

# Install the workflow using Cylc
echo "Installing workflow..."
cylc install --workflow-name=${WORKFLOW_NAME}

# Validate the workflow
echo "Validating workflow..."
cylc validate .

echo "Workflow installed and validated."
echo "Before running, please ensure:"
echo "1. Both SIF files are present in ./sifs/:"
echo "   - cpu-emu.sif"
echo "   - rtdp-components.sif"
echo "2. Component configuration files are in ./share/"
echo "3. Configure environment variables in flow.cylc if needed"
echo ""
echo "To run the workflow:"
echo "cylc play ${WORKFLOW_NAME}"
echo ""
echo "To monitor the workflow:"
echo "cylc tui ${WORKFLOW_NAME}"
echo ""
echo "To stop the workflow:"
echo "cylc stop ${WORKFLOW_NAME}"
echo ""
echo "Workflow installed at: ${CYLC_RUN_DIR}"

# Optionally start the workflow (commented out by default)
# cylc play ${WORKFLOW_NAME} 