#!/bin/bash

# Default values
INPUT_FILE=""
CPU_EMU_HOST="127.0.0.1"
PORT=18888
CONTAINER_PATH="cpu_emu.sif"

# Parse command line arguments
while getopts "f:h:p:c:?" opt; do
    case $opt in
        f) INPUT_FILE="$OPTARG" ;;
        h) CPU_EMU_HOST="$OPTARG" ;;
        p) PORT="$OPTARG" ;;
        c) CONTAINER_PATH="$OPTARG" ;;
        ?) echo "Usage: $0 -f input_file [-h cpu_emu_host] [-p port] [-c container_path]"
           echo "  -f: Input file to send (required)"
           echo "  -h: CPU emulator host (default: 127.0.0.1)"
           echo "  -p: Port to send to (default: 18888)"
           echo "  -c: Path to Apptainer container (default: cpu_emu.sif)"
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
apptainer exec \
    --bind $(pwd):/data \
    --pwd /data \
    ${CONTAINER_PATH} \
    sh -c "cat /data/$(basename ${INPUT_FILE}) | nc -N -q 0 ${CPU_EMU_HOST} ${PORT}" 