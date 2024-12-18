#!/bin/bash

# Default values
PORT=50080
OUTPUT_FILE="received_data.bin"
SIF_PATH="../cpu-emu.sif"

# Help message
show_help() {
    echo "Usage: $0 [-p PORT] [-o OUTPUT_FILE] [-f SIF_PATH]"
    echo "  -p PORT         Port to listen on (default: 50080)"
    echo "  -o OUTPUT_FILE  File to save received data (default: received_data.bin)"
    echo "  -f SIF_PATH    Path to the SIF file (default: ../cpu-emu.sif)"
    echo "  -h             Show this help message"
}

# Parse command line options
while getopts "p:o:f:h" opt; do
    case $opt in
        p) PORT="$OPTARG" ;;
        o) OUTPUT_FILE="$OPTARG" ;;
        f) SIF_PATH="$OPTARG" ;;
        h) show_help; exit 0 ;;
        ?) show_help; exit 1 ;;
    esac
done

# Check if SIF file exists
if [ ! -f "$SIF_PATH" ]; then
    echo "Error: SIF file not found at $SIF_PATH"
    echo "Please build the SIF file first using ../build.sh"
    exit 1
fi

echo "Starting receiver on port $PORT, saving to $OUTPUT_FILE"
apptainer run "$SIF_PATH" receive "$PORT" > "$OUTPUT_FILE" 