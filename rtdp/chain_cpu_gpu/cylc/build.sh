#!/bin/bash

# Set -e to exit on error
set -e

# Create sifs directory if it doesn't exist
mkdir -p sifs

# Docker sources
CPU_EMU_SOURCE="jlabtsai/rtdp-cpu_emu:latest"
GPU_PROXY_SOURCE="jlabtsai/rtdp-gpu_proxy:latest"

# Check and build CPU emulator container if needed
if [ ! -f "sifs/cpu-emu.sif" ]; then
    echo "Building CPU emulator container from ${CPU_EMU_SOURCE}..."
    apptainer build sifs/cpu-emu.sif docker://${CPU_EMU_SOURCE}
    if [ $? -eq 0 ]; then
        echo "Successfully built cpu-emu.sif"
    else
        echo "Failed to build cpu-emu.sif"
        exit 1
    fi
else
    echo "CPU emulator container already exists at sifs/cpu-emu.sif"
fi

# Check and build GPU proxy container if needed
if [ ! -f "sifs/gpu-proxy.sif" ]; then
    echo "Building GPU proxy container from ${GPU_PROXY_SOURCE}..."
    apptainer build sifs/gpu-proxy.sif docker://${GPU_PROXY_SOURCE}
    if [ $? -eq 0 ]; then
        echo "Successfully built gpu-proxy.sif"
    else
        echo "Failed to build gpu-proxy.sif"
        exit 1
    fi
else
    echo "GPU proxy container already exists at sifs/gpu-proxy.sif"
fi

# Create local bin directory for tools
mkdir -p bin

# Install yq for YAML parsing in the workflow directory
if [ ! -f "bin/yq" ]; then
    echo "Installing yq..."
    wget https://github.com/mikefarah/yq/releases/download/v4.30.8/yq_linux_amd64 -O bin/yq
    chmod +x bin/yq
fi

# Add local bin to PATH
export PATH=$(pwd)/bin:$PATH 