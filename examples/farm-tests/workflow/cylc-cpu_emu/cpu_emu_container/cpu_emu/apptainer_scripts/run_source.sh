#!/bin/bash

# Default values
INPUT_FILE=""
PORT=18888
CONTAINER_PATH="cpu_emu.sif"

# Parse command line arguments
while getopts "f:p:c:?" opt; do
    case $opt in
        f) INPUT_FILE="$OPTARG" ;;
        p) PORT="$OPTARG" ;;
        c) CONTAINER_PATH="$OPTARG" ;;
        ?) echo "Usage: $0 -f input_file [-p port] [-c container_path]"
           echo "  -f: Input file to send (required)"
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


echo -e "\nAttempting to send file '${INPUT_FILE}' to localhost:${PORT}"
apptainer exec \
    --bind $(pwd):/data \
    --pwd /data \
    ${CONTAINER_PATH} \
    sh -c "cat /data/$(basename ${INPUT_FILE}) | nc -v -N -q 0 127.0.0.1 ${PORT}"

status=$?
if [ $status -eq 0 ]; then
    echo "File sent successfully"
else
    echo "Error: Failed to send file (exit code: $status)"
    echo "Troubleshooting tips:"
    echo "1. Verify that cpu_emu sender is running"
    echo "2. Check if port ${PORT} is open: nc -zv 127.0.0.1 ${PORT}"
    echo "3. Make sure all components are running on this machine"
fi