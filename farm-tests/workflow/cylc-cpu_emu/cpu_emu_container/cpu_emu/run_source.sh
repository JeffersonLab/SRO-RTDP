#!/bin/bash

# Default values
INPUT_FILE=""
CPU_EMU_HOST="localhost"
PORT=18888  # This should match the RECEIVE_PORT in run_sender.sh

# Parse command line arguments
while getopts "f:h:p:?" opt; do
    case $opt in
        f) INPUT_FILE="$OPTARG" ;;
        h) CPU_EMU_HOST="$OPTARG" ;;
        p) PORT="$OPTARG" ;;
        ?) echo "Usage: $0 -f input_file [-h cpu_emu_host] [-p port]"
           echo "  -f: Input file to send (required)"
           echo "  -h: CPU emulator host (default: localhost)"
           echo "  -p: Port to send to (default: 8888)"
           exit 1
           ;;
    esac
done

# Check if input file is provided
if [ -z "$INPUT_FILE" ]; then
    echo "Error: Input file (-f) is required"
    exit 1
fi

# Check if input file exists
if [ ! -f "$INPUT_FILE" ]; then
    echo "Error: Input file '$INPUT_FILE' does not exist"
    exit 1
fi

echo "Sending file '$INPUT_FILE' to ${CPU_EMU_HOST}:${PORT}"
docker run \
    -v "$(pwd):/data" \
    --network host \
    cpu_emu \
    sh -c "cat /data/$(basename ${INPUT_FILE}) | nc -N -q 0 ${CPU_EMU_HOST} ${PORT}" 