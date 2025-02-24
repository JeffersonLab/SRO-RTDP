#!/bin/bash

# Set -e to exit on error, -x for debug output
set -ex

# Set fixed workflow name (must match install.sh)
WORKFLOW_NAME="cpu-emu"

echo "Cleaning up SLURM jobs and data..."

# Function to print messages with timestamps
log() {
    echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] $1"
}

# Cancel any running jobs with matching names
echo "Canceling running jobs..."
scancel --name=cpu-emu-recv
scancel --name=cpu-emu
scancel --name=cpu-emu-send

# Stop any running workflows
log "Stopping Cylc workflows..."
cylc scan --states=running | grep "cpu-emu" | cut -d' ' -f1 | xargs -r cylc stop

# Wait a moment for jobs to clean up
sleep 2

# Remove SLURM output logs
echo "Removing log files..."
rm -f slurm_*_*.log

# Remove memory monitoring logs
rm -f memory_monitor.log

# Remove input/output data
rm -f received_data.bin
rm -rf input/
rm -rf output/

# Remove node info file
rm -f node_info.txt

# Remove any core dumps if they exist
rm -f core.*

# Uninstall the Cylc workflow
echo "Uninstalling Cylc workflow..."
# Try to clean on the running host if available
HOST=$(cylc scan "${WORKFLOW_NAME}" | awk '{print $2}')
if [ -n "${HOST}" ]; then
    ssh "${HOST}" "cylc clean ${WORKFLOW_NAME}" || cylc clean "${WORKFLOW_NAME}" || true
else
    cylc clean "${WORKFLOW_NAME}" || true
fi

echo "Cleanup complete!"

# Show running jobs (if any)
echo -e "\nChecking for any remaining jobs:"
squeue --name=cpu-emu-recv
squeue --name=cpu-emu
squeue --name=cpu-emu-send

# List remaining files for verification
echo -e "\nRemaining files in directory:"
ls -la

# Clean up workflow installation
log "Cleaning up workflow installation..."
cylc clean "cpu-emu"

# Remove SIF files
log "Removing Apptainer SIF files..."
rm -f sifs/*.sif

# Clean up Apptainer cache
log "Cleaning Apptainer cache..."
apptainer cache clean -f

# Remove log and data directories
log "Removing log and data directories..."
rm -rf logs/* output/* input/*

log "Cleanup completed successfully" 