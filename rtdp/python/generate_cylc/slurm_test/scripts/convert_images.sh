#!/bin/bash

# Set -e to exit on error
set -e

# Create sifs directory if it doesn't exist
mkdir -p ../sifs

# Convert CPU emulator image
echo "Converting CPU emulator image..."
apptainer pull ../sifs/cpu-emu.sif docker://jlabtsai/rtdp-cpu_emu:v0.1

# Convert RTDP components image
echo "Converting RTDP components image..."
apptainer pull ../sifs/rtdp-components.sif docker://jlabtsai/rtdp-components:latest

echo "Conversion complete. SIF files created in ../sifs/"
ls -lh ../sifs/ 