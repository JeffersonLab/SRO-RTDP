#!/bin/bash

# Default values
SIF_NAME="cpu-emu.sif"

# Help message
show_help() {
    echo "Usage: $0 -i DOCKER_IMAGE [-o SIF_NAME]"
    echo "  -i DOCKER_IMAGE  Docker image to convert (e.g., 'username/cpu-emu:latest')"
    echo "  -o SIF_NAME     Output SIF file name (default: cpu-emu.sif)"
    echo "  -h             Show this help message"
    echo
    echo "Example:"
    echo "  $0 -i myregistry.azurecr.io/cpu-emu:latest"
    echo "  $0 -i docker.io/username/cpu-emu:v1.0"
}

# Check if no arguments were provided
if [ $# -eq 0 ]; then
    show_help
    exit 1
fi

# Parse command line options
while getopts "i:o:h" opt; do
    case $opt in
        i) DOCKER_IMAGE="$OPTARG" ;;
        o) SIF_NAME="$OPTARG" ;;
        h) show_help; exit 0 ;;
        ?) show_help; exit 1 ;;
    esac
done

# Check if Docker image is provided
if [ -z "$DOCKER_IMAGE" ]; then
    echo "Error: Docker image must be provided with -i flag"
    show_help
    exit 1
fi

echo "Converting Docker image $DOCKER_IMAGE to Apptainer SIF format: $SIF_NAME"

# Convert Docker image to Apptainer SIF
apptainer pull $SIF_NAME docker://$DOCKER_IMAGE

if [ $? -eq 0 ]; then
    echo "Conversion complete. SIF file created: $SIF_NAME"
else
    echo "Error: Failed to convert Docker image to SIF format"
    exit 1
fi 