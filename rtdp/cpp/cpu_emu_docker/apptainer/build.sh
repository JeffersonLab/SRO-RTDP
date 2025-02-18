#!/bin/bash

# Default values
SIF_NAME="cpu-emu.sif"
DEF_FILE="cpu-emu.def"

# Help message
show_help() {
    echo "Usage: $0 [-o SIF_NAME] [-d DEF_FILE]"
    echo "  -o SIF_NAME     Output SIF file name (default: cpu-emu.sif)"
    echo "  -d DEF_FILE     Definition file path (default: cpu-emu.def)"
    echo "  -h             Show this help message"
    echo
    echo "Example:"
    echo "  $0 -o cpu-emu.sif -d cpu-emu.def"
}

# Parse command line options
while getopts "o:d:h" opt; do
    case $opt in
        o) SIF_NAME="$OPTARG" ;;
        d) DEF_FILE="$OPTARG" ;;
        h) show_help; exit 0 ;;
        ?) show_help; exit 1 ;;
    esac
done

# Check if definition file exists
if [ ! -f "$DEF_FILE" ]; then
    echo "Error: Definition file '$DEF_FILE' not found"
    exit 1
fi

echo "Building Apptainer image from definition file: $DEF_FILE"
echo "Output SIF file will be: $SIF_NAME"

# Build the SIF file from definition file using fakeroot
apptainer build --fakeroot "$SIF_NAME" "$DEF_FILE"

if [ $? -eq 0 ]; then
    echo "Build complete. SIF file created: $SIF_NAME"
else
    echo "Error: Failed to build SIF file"
    exit 1
fi 