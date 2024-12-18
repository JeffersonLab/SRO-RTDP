#!/bin/bash

# Default values
PORT=50080
OUTPUT_FILE="received_data.bin"
BIND_IP="0.0.0.0"
SIF_PATH="../cpu-emu.sif"

# Help message
show_help() {
    echo "Usage: $0 [-p PORT] [-o OUTPUT_FILE] [-b BIND_IP] [-f SIF_PATH]"
    echo "  -p PORT         Port to listen on (default: 50080)"
    echo "  -o OUTPUT_FILE  File to save received data (default: received_data.bin)"
    echo "  -b BIND_IP      IP address to bind to (default: 0.0.0.0)"
    echo "  -f SIF_PATH    Path to the SIF file (default: ../cpu-emu.sif)"
    echo "  -h             Show this help message"
}

# Parse command line options
while getopts "p:o:b:f:h" opt; do
    case $opt in
        p) PORT="$OPTARG" ;;
        o) OUTPUT_FILE="$OPTARG" ;;
        b) BIND_IP="$OPTARG" ;;
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

echo "Starting receiver on $BIND_IP:$PORT, saving to $OUTPUT_FILE"
apptainer run --net --network-args "portmap=$PORT:$PORT/tcp" \
    "$SIF_PATH" receive "$PORT" "$BIND_IP" > "$OUTPUT_FILE" 