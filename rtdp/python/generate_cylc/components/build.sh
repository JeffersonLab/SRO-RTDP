#!/bin/bash

# Create sifs directory if it doesn't exist
mkdir -p sifs

# Function to convert Docker image to SIF
convert_to_sif() {
    local docker_image=$1
    local sif_name=$2
    local description=$3
    
    echo "Converting $description Docker image to SIF format..."
    apptainer pull "sifs/$sif_name" "docker://$docker_image"
    
    if [ $? -ne 0 ]; then
        echo "Error: Failed to convert $description image"
        return 1
    fi
    echo "Successfully created sifs/$sif_name"
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
convert_to_sif "jlabtsai/rtdp-cpu_emu:v0.1" "cpu-emu.sif" "CPU emulator" || exit 1

# Step 2: Build and convert components image
echo "Building components Docker image..."
docker build -t rtdp-components:latest -f Dockerfile .
if [ $? -ne 0 ]; then
    echo "Error: Failed to build components Docker image"
    exit 1
fi

# Tag for Docker Hub
docker tag rtdp-components:latest jlabtsai/rtdp-components:latest

# Optional: Push to Docker Hub (commented out by default)
# docker push jlabtsai/rtdp-components:latest

# Convert components image
convert_to_sif "jlabtsai/rtdp-components:latest" "rtdp-components.sif" "RTDP components" || exit 1

# Step 3: Copy to Cylc directory if it exists
CYLC_DIR="../cylc"
if [ -d "$CYLC_DIR" ]; then
    copy_to_cylc "$CYLC_DIR"
else
    echo "Warning: Cylc workflow directory not found at $CYLC_DIR"
fi

# Print summary
echo
echo "Build process completed successfully!"
echo "Generated SIF files:"
echo "  - sifs/cpu-emu.sif"
echo "  - sifs/rtdp-components.sif"

if [ -d "$CYLC_DIR" ]; then
    echo
    echo "Files copied to Cylc workflow directory:"
    echo "  - $CYLC_DIR/sifs/"
    echo "  - $CYLC_DIR/share/"
fi 