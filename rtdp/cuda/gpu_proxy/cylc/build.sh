#!/bin/bash

# Set -e to exit on error
set -e

# Create sifs directory if it doesn't exist
mkdir -p sifs

# Read docker source from flow.cylc
DOCKER_SOURCE=$(grep "DOCKER_SOURCE =" flow.cylc | awk -F'"' '{print $2}')
SIF_FILE=$(grep "SIF_FILE =" flow.cylc | awk -F'"' '{print $2}')

# Build Apptainer container from Docker image
echo "Building GPU proxy container from ${DOCKER_SOURCE}..."
apptainer build sifs/${SIF_FILE} docker://${DOCKER_SOURCE}

echo "Container build completed successfully."
echo "Container is available at: sifs/${SIF_FILE}" 