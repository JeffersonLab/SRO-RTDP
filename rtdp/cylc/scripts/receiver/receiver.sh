#!/bin/bash

# Receiver script for RTDP workflows
# This script handles data reception and validation

set -e  # Exit on error

# Configuration
RECV_PORT=${RECV_PORT:-5000}  # Default port if not specified
DATA_DIR=${DATA_DIR:-"$CYLC_WORKFLOW_RUN_DIR/data"}
LOG_DIR=${LOG_DIR:-"$CYLC_WORKFLOW_RUN_DIR/logs"}

# Create necessary directories
mkdir -p "$DATA_DIR" "$LOG_DIR"

# Log file setup
LOG_FILE="$LOG_DIR/receiver_$(date +%Y%m%d_%H%M%S).log"
exec 1> >(tee -a "$LOG_FILE")
exec 2>&1

echo "Starting receiver on port $RECV_PORT"
echo "Data directory: $DATA_DIR"
echo "Log directory: $LOG_DIR"

# Function to validate received data
validate_data() {
    local file=$1
    if [ ! -f "$file" ]; then
        echo "Error: File $file not found"
        return 1
    fi
    # Add more validation as needed
    return 0
}

# Main reception loop
while true; do
    echo "Waiting for data on port $RECV_PORT..."
    
    # TODO: Implement actual data reception logic
    # This could be using netcat, custom protocol, etc.
    
    # For now, create a dummy file to test the workflow
    touch "$DATA_DIR/received_data_$(date +%Y%m%d_%H%M%S).dat"
    
    # Validate received data
    if validate_data "$DATA_DIR/received_data_*.dat"; then
        echo "Data received and validated successfully"
        # Signal ready for next component
        touch "$CYLC_WORKFLOW_RUN_DIR/ready"
        break
    else
        echo "Data validation failed"
        exit 1
    fi
done

echo "Receiver completed successfully" 