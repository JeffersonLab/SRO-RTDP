#!/bin/bash

# Set -e to exit on error
set -e

# Set fixed workflow name
WORKFLOW_NAME="gpu-proxy"

# Remove workflow run directory
echo "Removing workflow run directory..."
rm -rf ~/cylc-run/${WORKFLOW_NAME}

# Remove local directories
echo "Removing local directories..."
rm -rf sifs
rm -rf etc
rm -rf scripts

echo "Cleanup completed successfully." 