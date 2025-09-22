#!/bin/bash

# Default values
PORT=18080
OUTPUT_FILE="received_data"

# Parse command line arguments
while getopts "p:o:h" opt; do
    case $opt in
        p) PORT="$OPTARG" ;;
        o) OUTPUT_FILE="$OPTARG" ;;
        h) echo "Usage: $0 [-p port] [-o output_file]"
           echo "  -p: Port to listen on (default: 18080)"
           echo "  -o: Output file name (default: received_data)"
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
docker run -p ${PORT}:${PORT} \
    -v "$(pwd):/data" \
    --workdir /data \
    cpu_emu \
    sh -c "nc -l ${PORT} > /data/$(basename ${OUTPUT_FILE})"