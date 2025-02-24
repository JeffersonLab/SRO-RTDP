#!/bin/bash

# Default values
THREADS=5
LATENCY=100
MEM_FOOTPRINT=10
OUTPUT_SIZE=0.01
RECV_PORT=8888
DEST_PORT=8888
DEST_IP="127.0.0.1"
SLEEP_MODE=0
VERBOSE=0
TERMINAL=0

# Help message
show_help() {
    echo "Usage: $0 [options]"
    echo "Options:"
    echo "  -t THREADS      Number of threads (default: 5)"
    echo "  -b LATENCY      Seconds thread latency per GB input (default: 100)"
    echo "  -m MEM          Thread memory footprint in GB (default: 10)"
    echo "  -o OUTPUT       Output size in GB (default: 0.01)"
    echo "  -r RECV_PORT    Receive port (default: 8888)"
    echo "  -p DEST_PORT    Destination port (default: 8888)"
    echo "  -i DEST_IP      Destination IP (default: 127.0.0.1)"
    echo "  -s              Use sleep mode instead of CPU burn"
    echo "  -v              Enable verbose mode"
    echo "  -z              Act as terminal node (don't forward data)"
    echo "  -h              Show this help message"
}

# Parse command line options
while getopts "t:b:m:o:r:p:i:svzh" opt; do
    case $opt in
        t) THREADS="$OPTARG" ;;
        b) LATENCY="$OPTARG" ;;
        m) MEM_FOOTPRINT="$OPTARG" ;;
        o) OUTPUT_SIZE="$OPTARG" ;;
        r) RECV_PORT="$OPTARG" ;;
        p) DEST_PORT="$OPTARG" ;;
        i) DEST_IP="$OPTARG" ;;
        s) SLEEP_MODE=1 ;;
        v) VERBOSE=1 ;;
        z) TERMINAL=1 ;;
        h) show_help; exit 0 ;;
        ?) show_help; exit 1 ;;
    esac
done

# Create output directory if it doesn't exist
OUTPUT_DIR="./output"
mkdir -p "$OUTPUT_DIR"

# Create YAML configuration file
CONFIG_FILE="$OUTPUT_DIR/cpu_emu.yaml"
cat > "$CONFIG_FILE" << EOL
destination: "$DEST_IP"
dst_port: $DEST_PORT
rcv_port: $RECV_PORT
sleep: $SLEEP_MODE
threads: $THREADS
latency: $LATENCY
mem_footprint: $MEM_FOOTPRINT
output_size: $OUTPUT_SIZE
verbose: $VERBOSE
terminal: $TERMINAL
EOL

# Construct the command with explicit port mapping
CMD="docker run -i --rm \
    --network host \
    -v $OUTPUT_DIR:/output \
    cpu-emu --output-dir /output \
    -y /output/cpu_emu.yaml"

echo "Starting CPU emulator with configuration:"
cat "$CONFIG_FILE"
echo -e "\nCommand:"
echo "$CMD"
eval "$CMD" 