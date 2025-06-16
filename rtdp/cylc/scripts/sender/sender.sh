#!/bin/bash

# Sender script for RTDP workflows
# This script handles data transmission

set -e  # Exit on error

# Configuration
SEND_PORT=${SEND_PORT:-5002}  # Default port if not specified
DATA_DIR=${DATA_DIR:-"$CYLC_WORKFLOW_RUN_DIR/data"}
LOG_DIR=${LOG_DIR:-"$CYLC_WORKFLOW_RUN_DIR/logs"}
DEST_HOST=${DEST_HOST:-"localhost"}  # Default destination host

# Create necessary directories
mkdir -p "$DATA_DIR" "$LOG_DIR"

# Log file setup
LOG_FILE="$LOG_DIR/sender_$(date +%Y%m%d_%H%M%S).log"
exec 1> >(tee -a "$LOG_FILE")
exec 2>&1

echo "Starting sender to $DEST_HOST:$SEND_PORT"
echo "Data directory: $DATA_DIR"
echo "Log directory: $LOG_DIR"

# Function to validate data before sending
validate_data() {
    local file=$1
    if [ ! -f "$file" ]; then
        echo "Error: File $file not found"
        return 1
    fi
    # Add more validation as needed
    return 0
}

# Function to send data
send_data() {
    local file=$1
    
    echo "Sending $file to $DEST_HOST:$SEND_PORT"
    
    # TODO: Implement actual data transmission logic
    # This could be using netcat, custom protocol, etc.
    
    # For now, just move the file to simulate sending
    mv "$file" "$DATA_DIR/sent_$(basename "$file")"
    
    if [ $? -eq 0 ]; then
        echo "Data sent successfully"
        return 0
    else
        echo "Data transmission failed"
        return 1
    fi
}

# Main sending loop
while true; do
    echo "Waiting for processed data..."
    
    # Look for processed files
    for input_file in "$DATA_DIR/received_data_*_emulated.dat"; do
        if [ -f "$input_file" ]; then
            # Validate data
            if validate_data "$input_file"; then
                # Send data
                if send_data "$input_file"; then
                    echo "Data transmission completed successfully"
                    # Signal completion
                    touch "$CYLC_WORKFLOW_RUN_DIR/done"
                    break
                else
                    echo "Data transmission failed"
                    exit 1
                fi
            else
                echo "Data validation failed"
                exit 1
            fi
        fi
    done
    break
done

echo "Sender completed successfully" 