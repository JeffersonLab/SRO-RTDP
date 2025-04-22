#!/bin/bash

# Set -e to exit on error
set -e

# Create sifs directory if it doesn't exist
mkdir -p sifs

# Docker sources
CPU_EMU_SOURCE="jlabtsai/rtdp-cpu_emu:latest"
GPU_PROXY_SOURCE="jlabtsai/rtdp-gpu_proxy:latest"

# Build CPU emulator container
echo "Building CPU emulator container from ${CPU_EMU_SOURCE}..."
apptainer build sifs/cpu-emu.sif docker://${CPU_EMU_SOURCE}

# Build GPU proxy container
echo "Building GPU proxy container from ${GPU_PROXY_SOURCE}..."
apptainer build sifs/gpu-proxy.sif docker://${GPU_PROXY_SOURCE}

# Verify both containers were built successfully
if [ $? -eq 0 ]; then
    echo "Successfully built both containers:"
    echo "- cpu-emu.sif"
    echo "- gpu-proxy.sif"
else
    echo "Failed to build one or more containers"
    exit 1
fi

# Install yq for YAML parsing in the workflow directory
if ! command -v yq &> /dev/null; then
    echo "Installing yq..."
    mkdir -p bin
    wget https://github.com/mikefarah/yq/releases/download/v4.30.8/yq_linux_amd64 -O bin/yq
    chmod +x bin/yq
    export PATH=$PATH:$(pwd)/bin
fi

# Make scripts executable
chmod +x $CYLC_WORKFLOW_RUN_DIR/flow.cylc 