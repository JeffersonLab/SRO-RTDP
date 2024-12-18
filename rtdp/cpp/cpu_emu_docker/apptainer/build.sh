#!/bin/bash

# Default values
SIF_NAME="cpu-emu.sif"
DOCKER_IMAGE="jlabtsai/rtdp-cpu_emu:latest"

# Help message
show_help() {
    echo "Usage: $0 [-o SIF_NAME]"
    echo "  -o SIF_NAME     Output SIF file name (default: cpu-emu.sif)"
    echo "  -h             Show this help message"
    echo
    echo "This script pulls the CPU emulator image from Docker Hub"
    echo "and converts it to Apptainer SIF format"
}

# Parse command line options
while getopts "o:h" opt; do
    case $opt in
        o) SIF_NAME="$OPTARG" ;;
        h) show_help; exit 0 ;;
        ?) show_help; exit 1 ;;
    esac
done

echo "Converting Docker image $DOCKER_IMAGE to Apptainer SIF format: $SIF_NAME"

# Convert Docker image to Apptainer SIF
apptainer pull $SIF_NAME docker://$DOCKER_IMAGE

if [ $? -eq 0 ]; then
    echo "Conversion complete. SIF file created: $SIF_NAME"
else
    echo "Error: Failed to convert Docker image to SIF format"
    exit 1
fi 