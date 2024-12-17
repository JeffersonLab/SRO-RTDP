#!/bin/bash

# Default values
PORT=18080
OUTPUT_FILE="received_data"
CONTAINER_PATH="cpu_emu.sif"

# Parse command line arguments
while getopts "p:o:c:h" opt; do
    case $opt in
        p) PORT="$OPTARG" ;;
        o) OUTPUT_FILE="$OPTARG" ;;
        c) CONTAINER_PATH="$OPTARG" ;;
        h) echo "Usage: $0 [-p port] [-o output_file] [-c container_path]"
           echo "  -p: Port to listen on (default: 18080)"
           echo "  -o: Output file name (default: received_data)"
           echo "  -c: Path to Apptainer container (default: cpu_emu.sif)"
           exit 0
           ;;
        ?) echo "Invalid option. Use -h for help."
           exit 1
           ;;
    esac
done

# Create output directory if it doesn't exist
mkdir -p $(dirname ${OUTPUT_FILE})

echo "Starting receiver on port $PORT, output will be saved to $OUTPUT_FILE"
apptainer exec \
    --bind $(pwd):/data \
    --pwd /data \
    ${CONTAINER_PATH} \
    nc -l ${PORT} > ${OUTPUT_FILE} 