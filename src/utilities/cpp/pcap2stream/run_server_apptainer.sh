#!/bin/bash

# Default values
DEFAULT_IP="0.0.0.0"
DEFAULT_BASE_PORT=5000
DEFAULT_NUM_PORTS=3
DEFAULT_OUTPUT_DIR="$HOME/pcap_output"

# Function to print usage
print_usage() {
    echo "Usage: $0 [options]"
    echo "Options:"
    echo "  -i, --ip <ip>          IP address to bind to (default: $DEFAULT_IP)"
    echo "  -p, --port <port>      Base port number (default: $DEFAULT_BASE_PORT)"
    echo "  -n, --num-ports <num>  Number of ports to use (default: $DEFAULT_NUM_PORTS)"
    echo "  -o, --output <dir>     Output directory (default: $DEFAULT_OUTPUT_DIR)"
    echo "  -h, --help             Show this help message"
}

# Parse command line arguments
IP_ADDR=$DEFAULT_IP
BASE_PORT=$DEFAULT_BASE_PORT
NUM_PORTS=$DEFAULT_NUM_PORTS
OUTPUT_DIR=$DEFAULT_OUTPUT_DIR

while [[ $# -gt 0 ]]; do
    case $1 in
        -i|--ip)
            IP_ADDR="$2"
            shift 2
            ;;
        -p|--port)
            BASE_PORT="$2"
            shift 2
            ;;
        -n|--num-ports)
            NUM_PORTS="$2"
            shift 2
            ;;
        -o|--output)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        -h|--help)
            print_usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            print_usage
            exit 1
            ;;
    esac
done

# Convert output directory to absolute path
OUTPUT_DIR=$(realpath "$OUTPUT_DIR")

# Create output directory with proper permissions
mkdir -p "$OUTPUT_DIR"
if [ $? -ne 0 ]; then
    echo "Error: Failed to create output directory: $OUTPUT_DIR"
    exit 1
fi

# Ensure output directory is writable
if [ ! -w "$OUTPUT_DIR" ]; then
    echo "Error: Output directory is not writable: $OUTPUT_DIR"
    exit 1
fi

# Set proper permissions for the output directory
chmod 755 "$OUTPUT_DIR"

# Check if the server image exists
if [ ! -f "server/stream_server.sif" ]; then
    echo "Server image not found. Building it now..."
    ./build_apptainer.sh server
    if [ $? -ne 0 ]; then
        echo "Error: Failed to build server image"
        exit 1
    fi
fi

echo "Starting stream server..."
echo "IP Address: $IP_ADDR"
echo "Base Port: $BASE_PORT"
echo "Number of Ports: $NUM_PORTS"
echo "Output Directory: $OUTPUT_DIR"
echo "Ports that will be used: $BASE_PORT to $((BASE_PORT + NUM_PORTS - 1))"
echo

# Create a subdirectory for this run with timestamp
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RUN_DIR="$OUTPUT_DIR/run_$TIMESTAMP"
mkdir -p "$RUN_DIR"

echo "Output files will be saved in: $RUN_DIR"
echo "You can monitor the output files with:"
echo "  ls -l $RUN_DIR"
echo

# Run the server with proper binding
apptainer run \
    --bind "$RUN_DIR":/app/output \
    --pwd /app \
    server/stream_server.sif "$IP_ADDR" "$BASE_PORT" "$NUM_PORTS"