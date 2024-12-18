#!/bin/bash

# Default values
PORT=50080
OUTPUT_FILE="received_data.bin"

# Help message
show_help() {
    echo "Usage: $0 [-p PORT] [-o OUTPUT_FILE]"
    echo "  -p PORT         Port to listen on (default: 50080)"
    echo "  -o OUTPUT_FILE  File to save received data (default: received_data.bin)"
    echo "  -h             Show this help message"
}

# Parse command line options
while getopts "p:o:h" opt; do
    case $opt in
        p) PORT="$OPTARG" ;;
        o) OUTPUT_FILE="$OPTARG" ;;
        h) show_help; exit 0 ;;
        ?) show_help; exit 1 ;;
    esac
done

echo "Starting receiver on port $PORT, saving to $OUTPUT_FILE"

# Run the container with tty allocation to prevent immediate exit
docker run -i --rm --network host cpu-emu receive "$PORT" > "$OUTPUT_FILE" 