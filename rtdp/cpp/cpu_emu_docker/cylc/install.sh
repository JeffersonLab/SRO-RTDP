#!/bin/bash

# Get the current directory name
WORKFLOW_NAME=$(basename $(pwd))

# Create necessary directories
mkdir -p sifs
mkdir -p etc/config
mkdir -p scripts

# Install the workflow using Cylc
cylc install

# Validate the workflow
cylc validate .

echo "Workflow installed and validated."
echo "Before running, please ensure:"
echo "1. CPU emulator SIF file is copied to ./sifs/cpu-emu.sif"
echo "2. Configure environment variables in flow.cylc if needed"
echo ""
echo "To run the workflow:"
echo "cylc play ${WORKFLOW_NAME}" 