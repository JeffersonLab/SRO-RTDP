#!/bin/bash

# Set -e to exit on error
set -e

# clean the sifs directory if it exists
rm -rf sifs

# Set fixed workflow name
WORKFLOW_NAME="cylc-cpu-emu-gen"

# Create necessary directories
mkdir -p sifs etc/config scripts

# build sif file
./build.sh -i jlabtsai/rtdp-cpu_emu:latest

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
echo "1. CPU emulator SIF file is copied to ./sifs/cpu-emu.sif"
echo "2. Configure environment variables in flow.cylc if needed"
echo ""
echo "To run the workflow:"
echo "cylc play ${WORKFLOW_NAME}"
echo ""
echo "Workflow installed at: ${CYLC_RUN_DIR}" 

cylc play cylc-cpu-emu-gen
