#!/bin/bash

# Default values
HOST="localhost"
PORT=50888
INPUT_FILE=""
SIZE="10M"

# Help message
show_help() {
    echo "Usage: $0 [-h HOST] [-p PORT] [-f INPUT_FILE] [-s SIZE]"
    echo "  -h HOST        Target host (default: localhost)"
    echo "  -p PORT        Target port (default: 50888)"
    echo "  -f FILE        Input file to send (optional)"
    echo "  -s SIZE        Size of random data to generate if no input file (default: 10M)"
    echo "  -? | --help    Show this help message"
}

# Parse command line options
while getopts "h:p:f:s:?" opt; do
    case $opt in
        h) HOST="$OPTARG" ;;
        p) PORT="$OPTARG" ;;
        f) INPUT_FILE="$OPTARG" ;;
        s) SIZE="$OPTARG" ;;
        ?) show_help; exit 0 ;;
    esac
done

# Create temporary directory for input files if needed
TEMP_DIR="./input"
mkdir -p "$TEMP_DIR"

if [ -z "$INPUT_FILE" ]; then
    echo "Generating random data of size $SIZE and sending to $HOST:$PORT"
    INPUT_FILE="$TEMP_DIR/random_data.bin"
    dd if=/dev/urandom bs=$SIZE count=1 of="$INPUT_FILE" 2>/dev/null
fi

if [ ! -f "$INPUT_FILE" ]; then
    echo "Error: Input file $INPUT_FILE does not exist"
    exit 1
fi

echo "Sending $INPUT_FILE to $HOST:$PORT"
docker run -i --rm --network host \
    -v "$(dirname "$INPUT_FILE"):/data" \
    cpu-emu send "/data/$(basename "$INPUT_FILE")" "$HOST" "$PORT"

# Clean up temporary files if we generated them
if [[ "$INPUT_FILE" == "$TEMP_DIR"* ]]; then
    rm -f "$INPUT_FILE"
fi 