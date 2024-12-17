#!/bin/bash

# Default values
RECEIVE_PORT=18888
DEST_IP="127.0.0.1"
DEST_PORT=18080
NUM_THREADS=10
VERBOSITY=0
THREAD_LATENCY=1     # seconds thread latency per GB input
MEMORY_FOOTPRINT=1   # thread memory footprint in GB
OUTPUT_SIZE=1        # output size in GB
SLEEP_MODE=0         # 0 for CPU burn, 1 for sleep
CONTAINER_PATH="cpu_emu.sif"

# Parse command line arguments
while getopts "r:i:p:t:v:b:m:o:s:c:h" opt; do
    case $opt in
        r) RECEIVE_PORT="$OPTARG" ;;
        i) DEST_IP="$OPTARG" ;;
        p) DEST_PORT="$OPTARG" ;;
        t) NUM_THREADS="$OPTARG" ;;
        v) VERBOSITY="$OPTARG" ;;
        b) THREAD_LATENCY="$OPTARG" ;;
        m) MEMORY_FOOTPRINT="$OPTARG" ;;
        o) OUTPUT_SIZE="$OPTARG" ;;
        s) SLEEP_MODE="$OPTARG" ;;
        c) CONTAINER_PATH="$OPTARG" ;;
        h) echo "Usage: $0 [-r receive_port] [-i dest_ip] [-p dest_port] [-t num_threads] [-v verbosity] [-b thread_latency] [-m memory_footprint] [-o output_size] [-s sleep_mode] [-c container_path]"
           echo "  -r: Receive port (default: 18888)"
           echo "  -i: Destination IP address (default: 127.0.0.1)"
           echo "  -p: Destination port (default: 18080)"
           echo "  -t: Number of threads (default: 10)"
           echo "  -v: Verbosity (0/1, default: 0)"
           echo "  -b: Thread latency in seconds per GB input (default: 1)"
           echo "  -m: Thread memory footprint in GB (default: 1)"
           echo "  -o: Output size in GB (default: 1)"
           echo "  -s: Sleep mode (0=CPU burn, 1=sleep, default: 0)"
           echo "  -c: Path to Apptainer container (default: cpu_emu.sif)"
           exit 0
           ;;
        ?) echo "Invalid option. Use -h for help."
           exit 1
           ;;
    esac
done

echo "Starting cpu_emu sender with:"
echo "  Receive port: $RECEIVE_PORT"
echo "  Destination IP: $DEST_IP"
echo "  Destination port: $DEST_PORT"
echo "  Number of threads: $NUM_THREADS"
echo "  Verbosity: $VERBOSITY"
echo "  Thread latency: $THREAD_LATENCY seconds/GB"
echo "  Memory footprint: $MEMORY_FOOTPRINT GB"
echo "  Output size: $OUTPUT_SIZE GB"
echo "  Sleep mode: $SLEEP_MODE"

apptainer exec \
    ${CONTAINER_PATH} \
    cpu_emu \
    -r ${RECEIVE_PORT} \
    -i "${DEST_IP}" \
    -p ${DEST_PORT} \
    -t ${NUM_THREADS} \
    -v ${VERBOSITY} \
    -b ${THREAD_LATENCY} \
    -m ${MEMORY_FOOTPRINT} \
    -o ${OUTPUT_SIZE} \
    ${SLEEP_MODE:+-s} 