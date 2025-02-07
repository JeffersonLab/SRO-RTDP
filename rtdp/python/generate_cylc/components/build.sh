#!/bin/bash

# Set -e to exit on error
set -e

# Get the directory of this script
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
CYLC_DIR="${SCRIPT_DIR}/../cylc"

# Create sifs directory if it doesn't exist
mkdir -p sifs

# Function to convert Docker image to SIF
convert_to_sif() {
    local docker_image=$1
    local sif_name=$2
    local description=$3
    
    echo "Converting $description Docker image to SIF format..."
    if [ ! -f "sifs/$sif_name" ]; then
        apptainer pull "sifs/$sif_name" "docker://$docker_image"
        if [ $? -ne 0 ]; then
            echo "Error: Failed to convert $description image"
            return 1
        fi
        echo "Successfully created sifs/$sif_name"
    else
        echo "SIF file sifs/$sif_name already exists, skipping conversion"
    fi
    return 0
}

# Function to copy files to Cylc directory
copy_to_cylc() {
    local cylc_dir=$1
    
    echo "Copying files to Cylc workflow directory..."
    
    # Create necessary directories
    mkdir -p "$cylc_dir/sifs"
    mkdir -p "$cylc_dir/share"
    
    # Copy SIF files
    cp sifs/*.sif "$cylc_dir/sifs/"
    
    # Copy configuration files
    if [ -d "../wf-generator/generated/share" ]; then
        cp ../wf-generator/generated/share/* "$cylc_dir/share/"
    fi
    
    echo "Files copied successfully to Cylc workflow directory"
}

# Step 1: Convert CPU emulator image
echo "Step 1: Converting CPU emulator image..."
convert_to_sif "jlabtsai/rtdp-cpu_emu:v0.1" "cpu-emu.sif" "CPU emulator" || {
    echo "Failed to convert CPU emulator image"
    exit 1
}

# Step 2: Convert components image
echo "Step 2: Converting components image..."
convert_to_sif "jlabtsai/rtdp-components:latest" "rtdp-components.sif" "RTDP components" || {
    echo "Failed to convert components image"
    exit 1
}

# Step 3: Verify both SIF files exist
echo "Step 3: Verifying SIF files..."
if [ ! -f "sifs/cpu-emu.sif" ]; then
    echo "Error: cpu-emu.sif not found!"
    exit 1
fi
if [ ! -f "sifs/rtdp-components.sif" ]; then
    echo "Error: rtdp-components.sif not found!"
    exit 1
fi

# Step 4: Copy to Cylc directory
echo "Step 4: Copying files to Cylc directory..."
if [ -d "$CYLC_DIR" ]; then
    copy_to_cylc "$CYLC_DIR"
else
    echo "Warning: Cylc workflow directory not found at $CYLC_DIR"
fi

# Print summary
echo
echo "Build process completed successfully!"
echo "Generated SIF files:"
ls -lh sifs/
echo
if [ -d "$CYLC_DIR" ]; then
    echo "Files copied to Cylc workflow directory:"
    echo "  - $CYLC_DIR/sifs/"
    ls -lh "$CYLC_DIR/sifs/"
    echo "  - $CYLC_DIR/share/"
    ls -lh "$CYLC_DIR/share/" 2>/dev/null || echo "    (No configuration files found)"
fi 