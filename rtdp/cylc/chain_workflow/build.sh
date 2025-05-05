#!/bin/bash

# Set -e to exit on error
set -e

# Create sifs directory if it doesn't exist
mkdir -p sifs

# Build CPU emulator container
echo "Building CPU emulator container..."
apptainer build sifs/cpu-emu.sif docker://jlabtsai/rtdp-cpu_emu:latest

# Build GPU proxy container
echo "Building GPU proxy container..."
apptainer build sifs/gpu-proxy.sif docker://jlabtsai/rtdp-gpu_proxy:latest

echo "Container builds completed successfully."
echo "Containers are available in: sifs/" 