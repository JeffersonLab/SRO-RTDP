#!/bin/bash

# Create sifs directory if it doesn't exist
mkdir -p sifs

# Build the Apptainer container
apptainer build sifs/cpu-emu.sif ../Dockerfile

# Make sure the container was built successfully
if [ $? -eq 0 ]; then
    echo "Successfully built cpu-emu.sif"
else
    echo "Failed to build cpu-emu.sif"
    exit 1
fi 