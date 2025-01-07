#!/bin/bash

# Set -e to exit on error, -x for debug output
set -ex

echo "Cleaning up SLURM job outputs and generated data..."

# Remove SLURM output logs
rm -f slurm_*_*.log

# Remove memory monitoring logs
rm -f memory_monitor.log

# Remove input/output data
rm -f received_data.bin
rm -rf input/
rm -rf output/

# Remove any core dumps if they exist
rm -f core.*

echo "Cleanup complete!"

# List remaining files for verification
echo -e "\nRemaining files in directory:"
ls -la 