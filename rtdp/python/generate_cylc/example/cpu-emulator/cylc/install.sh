#!/bin/bash

# Set -e to exit on error
set -e

# Set fixed workflow name
WORKFLOW_NAME="rtdp-workflow"

# Get the directory of this script
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

# Clean up existing directories
echo "Cleaning up existing directories..."
rm -rf sifs share

# Create necessary directories
echo "Creating directories..."
mkdir -p sifs share

# Convert both Docker images to SIF format
echo "Converting CPU emulator image..."
apptainer pull sifs/cpu-emu.sif docker://jlabtsai/rtdp-cpu_emu:v0.1
if [ $? -ne 0 ]; then
    echo "Error: Failed to convert CPU emulator image"
    exit 1
fi

echo "Converting RTDP components image..."
apptainer pull sifs/rtdp-components.sif docker://jlabtsai/rtdp-components:latest
if [ $? -ne 0 ]; then
    echo "Error: Failed to convert RTDP components image"
    exit 1
fi

# Copy configuration files from generated directory if they exist
WF_GENERATOR_DIR="${SCRIPT_DIR}/../../wf-generator"
if [ -d "${WF_GENERATOR_DIR}/generated/share" ]; then
    echo "Copying configuration files..."
    cp ${WF_GENERATOR_DIR}/generated/share/* share/
else
    echo "Warning: No configuration files found in ${WF_GENERATOR_DIR}/generated/share"
    echo "Please run the workflow generator first"
    exit 1
fi

# Make sure we're in the correct directory
CYLC_RUN_DIR=~/cylc-run/${WORKFLOW_NAME}
mkdir -p ${CYLC_RUN_DIR}

# Install the workflow using Cylc
echo "Installing workflow..."
cylc install --workflow-name=${WORKFLOW_NAME}

# Validate the workflow
echo "Validating workflow..."
cylc validate .

echo "Workflow setup completed!"
echo
echo "Generated SIF files:"
ls -lh sifs/
echo
echo "Configuration files:"
ls -lh share/
echo
echo "Starting workflow..."
cylc play ${WORKFLOW_NAME} 