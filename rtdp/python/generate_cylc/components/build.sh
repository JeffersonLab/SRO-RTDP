#!/bin/bash

# Create sifs directory if it doesn't exist
mkdir -p sifs

# Build and convert CPU emulator image
echo "Converting CPU emulator Docker image to SIF format..."
apptainer pull sifs/cpu-emu.sif docker://jlabtsai/rtdp-cpu_emu:v0.1
if [ $? -ne 0 ]; then
    echo "Error: Failed to convert CPU emulator image"
    exit 1
fi

# Build components Docker image
echo "Building components Docker image..."
docker build -t rtdp-components:latest -f Dockerfile .
if [ $? -ne 0 ]; then
    echo "Error: Failed to build components Docker image"
    exit 1
fi

# Tag the components image for Docker Hub
docker tag rtdp-components:latest jlabtsai/rtdp-components:latest

# Push to Docker Hub (optional, commented out by default)
# docker push jlabtsai/rtdp-components:latest

# Convert components image to SIF
echo "Converting components Docker image to SIF format..."
apptainer pull sifs/rtdp-components.sif docker://jlabtsai/rtdp-components:latest
if [ $? -ne 0 ]; then
    echo "Error: Failed to convert components image"
    exit 1
fi

echo "Build process completed successfully!"
echo "Generated SIF files:"
echo "  - sifs/cpu-emu.sif"
echo "  - sifs/rtdp-components.sif" 