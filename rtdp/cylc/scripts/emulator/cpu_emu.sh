#!/bin/bash

# CPU Emulator script for RTDP workflows
# This script handles data processing on CPU

set -e  # Exit on error

# Configuration
IN_PORT=${IN_PORT:-5001}  # Default input port
OUT_PORT=${OUT_PORT:-5002}  # Default output port
DATA_DIR=${DATA_DIR:-"$CYLC_WORKFLOW_RUN_DIR/data"}
LOG_DIR=${LOG_DIR:-"$CYLC_WORKFLOW_RUN_DIR/logs"}
NUM_THREADS=${NUM_THREADS:-4}  # Default number of threads
LATENCY=${LATENCY:-0}  # Default latency in milliseconds

# Create necessary directories
mkdir -p "$DATA_DIR" "$LOG_DIR"

# Log file setup
LOG_FILE="$LOG_DIR/cpu_emu_$(date +%Y%m%d_%H%M%S).log"
exec 1> >(tee -a "$LOG_FILE")
exec 2>&1

echo "Starting CPU emulator"
echo "Input port: $IN_PORT"
echo "Output port: $OUT_PORT"
echo "Data directory: $DATA_DIR"
echo "Log directory: $LOG_DIR"
echo "Number of threads: $NUM_THREADS"
echo "Latency: $LATENCY ms"

# Function to process data on CPU
process_data() {
    local input_file=$1
    local output_file=$2
    
    echo "Processing $input_file on CPU with $NUM_THREADS threads"
    
    # TODO: Implement actual CPU processing logic
    # This could be using OpenMP, custom CPU code, etc.
    
    # Simulate processing time based on latency
    if [ $LATENCY -gt 0 ]; then
        sleep $(echo "scale=3; $LATENCY/1000" | bc)
    fi
    
    # For now, just copy the file to simulate processing
    cp "$input_file" "$output_file"
    
    if [ $? -eq 0 ]; then
        echo "Processing completed successfully"
        return 0
    else
        echo "Processing failed"
        return 1
    fi
}

# Main processing loop
while true; do
    echo "Waiting for input data on port $IN_PORT..."
    
    # TODO: Implement actual data reception logic
    # For now, process any existing files
    for input_file in "$DATA_DIR/received_data_*_processed.dat"; do
        if [ -f "$input_file" ]; then
            output_file="${input_file%.dat}_emulated.dat"
            if process_data "$input_file" "$output_file"; then
                echo "Data processed successfully"
                # Signal ready for next component
                touch "$CYLC_WORKFLOW_RUN_DIR/ready"
                break
            else
                echo "Data processing failed"
                exit 1
            fi
        fi
    done
    break
done

echo "CPU emulator completed successfully" 