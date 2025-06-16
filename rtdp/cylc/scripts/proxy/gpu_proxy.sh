#!/bin/bash

# GPU Proxy script for RTDP workflows
# This script handles data processing on GPU

set -e  # Exit on error

# Configuration
IN_PORT=${IN_PORT:-5000}  # Default input port
OUT_PORT=${OUT_PORT:-5001}  # Default output port
DATA_DIR=${DATA_DIR:-"$CYLC_WORKFLOW_RUN_DIR/data"}
LOG_DIR=${LOG_DIR:-"$CYLC_WORKFLOW_RUN_DIR/logs"}
GPU_ID=${GPU_ID:-0}  # Default GPU ID

# Create necessary directories
mkdir -p "$DATA_DIR" "$LOG_DIR"

# Log file setup
LOG_FILE="$LOG_DIR/gpu_proxy_${GPU_ID}_$(date +%Y%m%d_%H%M%S).log"
exec 1> >(tee -a "$LOG_FILE")
exec 2>&1

echo "Starting GPU proxy on GPU $GPU_ID"
echo "Input port: $IN_PORT"
echo "Output port: $OUT_PORT"
echo "Data directory: $DATA_DIR"
echo "Log directory: $LOG_DIR"

# Function to check GPU availability
check_gpu() {
    if ! nvidia-smi -i $GPU_ID > /dev/null 2>&1; then
        echo "Error: GPU $GPU_ID not available"
        return 1
    fi
    return 0
}

# Function to process data on GPU
process_data() {
    local input_file=$1
    local output_file=$2
    
    echo "Processing $input_file on GPU $GPU_ID"
    
    # TODO: Implement actual GPU processing logic
    # This could be using CUDA, custom GPU code, etc.
    
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
    
    # Check GPU availability
    if ! check_gpu; then
        echo "GPU not available, exiting"
        exit 1
    fi
    
    # TODO: Implement actual data reception logic
    # For now, process any existing files
    for input_file in "$DATA_DIR/received_data_*.dat"; do
        if [ -f "$input_file" ]; then
            output_file="${input_file%.dat}_processed.dat"
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

echo "GPU proxy completed successfully" 