#!/bin/bash

# Set -e to exit on error, -x for debug output
set -ex

# Set fixed workflow name (must match install.sh)
WORKFLOW_NAME="cpu-emu"

echo "Cleaning up SLURM jobs and data..."

# Cancel any running jobs with matching names
echo "Canceling running jobs..."
scancel --name=cpu-emu-recv
scancel --name=cpu-emu
scancel --name=cpu-emu-send

# Stop any running workflows
echo "Stopping Cylc workflows..."
if cylc scan "${WORKFLOW_NAME}" 2>/dev/null; then
    # Get the host where the workflow is running
    HOST=$(cylc scan "${WORKFLOW_NAME}" | awk '{print $2}')
    if [ -n "${HOST}" ]; then
        echo "Workflow running on ${HOST}"
        # Stop the workflow on the correct host
        ssh "${HOST}" "cylc stop --now ${WORKFLOW_NAME}"
    else
        cylc stop --now "${WORKFLOW_NAME}" || true
    fi
fi

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