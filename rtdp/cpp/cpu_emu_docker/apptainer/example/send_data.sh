#!/bin/bash

# Default values
HOST="localhost"
PORT=50888
INPUT_FILE=""
SIZE="10M"
SIF_PATH="../cpu-emu.sif"
VERBOSE=0

# Help message
show_help() {
    echo "Usage: $0 [-h HOST] [-p PORT] [-f INPUT_FILE] [-s SIZE] [-i SIF_PATH] [-v]"
    echo "  -h HOST        Target host (default: localhost)"
    echo "  -p PORT        Target port (default: 50888)"
    echo "  -f FILE        Input file to send (optional)"
    echo "  -s SIZE        Size of random data to generate if no input file (default: 10M)"
    echo "  -i SIF_PATH    Path to the SIF file (default: ../cpu-emu.sif)"
    echo "  -v            Enable verbose output"
    echo "  -? | --help    Show this help message"
}

# Parse command line options
while getopts "h:p:f:s:i:v?" opt; do
    case $opt in
        h) HOST="$OPTARG" ;;
        p) PORT="$OPTARG" ;;
        f) INPUT_FILE="$OPTARG" ;;
        s) SIZE="$OPTARG" ;;
        i) SIF_PATH="$OPTARG" ;;
        v) VERBOSE=1 ;;
        ?) show_help; exit 0 ;;
    esac
done

# Check if SIF file exists
if [ ! -f "$SIF_PATH" ]; then
    echo "Error: SIF file not found at $SIF_PATH"
    echo "Please build the SIF file first using ../build.sh"
    exit 1
fi

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

# Get actual file size
ACTUAL_SIZE=$(stat -f %z "$INPUT_FILE")
echo "Actual data size: $ACTUAL_SIZE bytes"

echo "Sending $INPUT_FILE to $HOST:$PORT"
if [ $VERBOSE -eq 1 ]; then
    # Send with debug output
    cat "$INPUT_FILE" | apptainer run --env VERBOSE=1 "$SIF_PATH" send "$HOST" "$PORT"
else
    cat "$INPUT_FILE" | apptainer run "$SIF_PATH" send "$HOST" "$PORT"
fi

# Clean up temporary files if we generated them
if [[ "$INPUT_FILE" == "$TEMP_DIR"* ]]; then
    rm -f "$INPUT_FILE"
fi 