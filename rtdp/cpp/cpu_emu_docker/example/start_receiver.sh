#!/bin/bash

# Default values
PORT=8888
OUTPUT_FILE="received_data.bin"
BIND_IP="*"

# Help message
show_help() {
    echo "Usage: $0 [-p PORT] [-o OUTPUT_FILE] [-b BIND_IP]"
    echo "  -p PORT         Port to listen on (default: 8888)"
    echo "  -o OUTPUT_FILE  File to save received data (default: received_data.bin)"
    echo "  -b BIND_IP      IP address to bind to (default: *)"
    echo "  -h             Show this help message"
}

# Parse command line options
while getopts "p:o:b:h" opt; do
    case $opt in
        p) PORT="$OPTARG" ;;
        o) OUTPUT_FILE="$OPTARG" ;;
        b) BIND_IP="$OPTARG" ;;
        h) show_help; exit 0 ;;
        ?) show_help; exit 1 ;;
    esac
done

echo "Starting ZMQ receiver on $BIND_IP:$PORT, saving to $OUTPUT_FILE"

# Run the container with host networking
docker run -i --rm \
    --network host \
    cpu-emu receive "$PORT" "$BIND_IP" > "$OUTPUT_FILE"