#!/bin/bash

# Default values
SIF_NAME="cpu-emu.sif"
DEF_FILE="../apptainer/cpu-emu.def"

# Parse command line arguments
while getopts "o:d:" opt; do
    case $opt in
        o) SIF_NAME="$OPTARG" ;;
        d) DEF_FILE="$OPTARG" ;;
        \?) echo "Invalid option: -$OPTARG" >&2; exit 1 ;;
    esac
done

# Ensure output directory exists
mkdir -p sifs

# Build the Apptainer container
echo "Building Apptainer container..."
apptainer build --fakeroot sifs/${SIF_NAME} ${DEF_FILE}

if [ $? -eq 0 ]; then
    echo "Successfully built ${SIF_NAME}"
    echo "Container location: sifs/${SIF_NAME}"
else
    echo "Failed to build container" >&2
    exit 1
fi 