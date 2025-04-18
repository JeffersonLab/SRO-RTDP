#!/bin/bash

# Default values
THREADS=10
LATENCY=100
MEM_FOOTPRINT=0.1
OUTPUT_SIZE=0.001
RECV_PORT=50888
DEST_PORT=50080
DEST_IP="127.0.0.1"
SLEEP_MODE=0
VERBOSE=0
SIF_PATH="../cpu-emu.sif"

# Help message
show_help() {
    echo "Usage: $0 [options]"
    echo "Options:"
    echo "  -t THREADS       Number of threads (default: 10)"
    echo "  -b LATENCY      Seconds thread latency per GB input (default: 100)"
    echo "  -m MEM          Thread memory footprint in GB (default: 0.1)"
    echo "  -o OUTPUT       Output size in GB (default: 0.001)"
    echo "  -r RECV_PORT    Receive port (default: 50888)"
    echo "  -p DEST_PORT    Destination port (default: 50080)"
    echo "  -i DEST_IP      Destination IP (default: 127.0.0.1)"
    echo "  -f SIF_PATH     Path to the SIF file (default: ../cpu-emu.sif)"
    echo "  -s             Use sleep mode instead of CPU burn"
    echo "  -v             Enable verbose mode"
    echo "  -h             Show this help message"
}

# Parse command line options
while getopts "t:b:m:o:r:p:i:f:svh" opt; do
    case $opt in
        t) THREADS="$OPTARG" ;;
        b) LATENCY="$OPTARG" ;;
        m) MEM_FOOTPRINT="$OPTARG" ;;
        o) OUTPUT_SIZE="$OPTARG" ;;
        r) RECV_PORT="$OPTARG" ;;
        p) DEST_PORT="$OPTARG" ;;
        i) DEST_IP="$OPTARG" ;;
        f) SIF_PATH="$OPTARG" ;;
        s) SLEEP_MODE=1 ;;
        v) VERBOSE=1 ;;
        h) show_help; exit 0 ;;
        ?) show_help; exit 1 ;;
    esac
done

# Check if SIF file exists
if [ ! -f "$SIF_PATH" ]; then
    echo "Error: SIF file not found at $SIF_PATH"
    echo "Please build the SIF file first using ../build.sh"
    exit 1
fi

# Create output directory
OUTPUT_DIR="./output"
mkdir -p "$OUTPUT_DIR"

# Construct the command
CMD="apptainer run"

# Add environment variables for debugging
if [ $VERBOSE -eq 1 ]; then
    CMD="$CMD --env VERBOSE=1 --env DEBUG=1"
fi

# Add the SIF file and output directory
CMD="$CMD $SIF_PATH --output-dir /output"

# Add required parameters
CMD="$CMD -t $THREADS -b $LATENCY -m $MEM_FOOTPRINT -o $OUTPUT_SIZE"
CMD="$CMD -r $RECV_PORT -p $DEST_PORT -i $DEST_IP"

# Add optional flags
if [ $SLEEP_MODE -eq 1 ]; then
    CMD="$CMD -s"
fi

if [ $VERBOSE -eq 1 ]; then
    CMD="$CMD -v 1"
fi

echo "Starting CPU emulator with command:"
echo "$CMD"
eval "$CMD" 