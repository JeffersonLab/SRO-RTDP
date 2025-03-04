#!/bin/bash

# Set the project directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Check if the test client is running
CLIENT_PID=$(pgrep -f "java.*TestClientWithMonitoring")
if [ -z "$CLIENT_PID" ]; then
    echo "Error: Test client is not running"
    echo "Please run the test client first using: ./run_simple_test.sh"
    exit 1
fi

# Function to get the buffer status
get_buffer_status() {
    # Get the buffer status from the test client's output
    local buffer_info=$(ps -p $CLIENT_PID -o args= | grep -o "Buffer.*")
    if [ -z "$buffer_info" ]; then
        echo "Waiting for buffer status..."
        return 1
    fi
    
    echo "$buffer_info"
    return 0
}

# Function to get throughput information
get_throughput() {
    # Get the throughput from the test client's output
    local throughput=$(ps -p $CLIENT_PID -o args= | grep -o "Throughput.*")
    if [ -z "$throughput" ]; then
        echo "Waiting for throughput information..."
        return 1
    fi
    
    echo "$throughput"
    return 0
}

# Main loop
echo "Checking buffer status (press Ctrl+C to exit)..."
while true; do
    echo "----------------------------------------"
    echo "Buffer Status at $(date)"
    echo "----------------------------------------"
    
    # Get and display buffer status
    get_buffer_status
    
    # Get and display throughput
    get_throughput
    
    # Wait before checking again
    sleep 2
done 