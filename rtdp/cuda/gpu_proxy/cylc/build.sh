#!/bin/bash

# Exit on error
set -e

# Create containers directory if it doesn't exist
mkdir -p containers

# Build Apptainer container from Docker image
apptainer build containers/gpu-proxy.sif docker://jlabtsai/gpu-proxy:latest

echo "Container build completed successfully." 