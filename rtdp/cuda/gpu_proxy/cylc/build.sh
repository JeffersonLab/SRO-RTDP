#!/bin/bash

# Exit on error
set -e

# Create containers directory if it doesn't exist
mkdir -p containers

# Build Apptainer container from Docker image
apptainer build containers/gpu-proxy.sif docker://${CYLC_WORKFLOW_PARAM_containers_gpu_proxy_docker_source}

echo "Container build completed successfully." 