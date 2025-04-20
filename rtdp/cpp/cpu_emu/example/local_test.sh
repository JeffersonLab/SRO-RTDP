#!/bin/bash

# Function to clean up any existing processes and ports
cleanup_ports() {
    echo "Cleaning up any existing processes..."
    # Kill any existing processes
    pkill -f "apptainer.*receiver" || true
    pkill -f "apptainer.*emulator" || true
    pkill -f "apptainer.*sender" || true
    
    # Wait for processes to fully terminate
    sleep 3
    
    # Force kill any remaining processes
    pkill -9 -f "apptainer.*receiver" || true
    pkill -9 -f "apptainer.*emulator" || true
    pkill -9 -f "apptainer.*sender" || true
    
    # Additional cleanup for ZMQ sockets
    fuser -k -n tcp $RECEIVER_PORT 2>/dev/null || true
    fuser -k -n tcp $EMULATOR_RCV_PORT 2>/dev/null || true
    fuser -k -n tcp $EMULATOR_SND_PORT 2>/dev/null || true
    fuser -k -n tcp $SENDER_PORT 2>/dev/null || true
    
    # Final wait to ensure cleanup is complete
    sleep 2
}

# Configuration
RECEIVER_PORT=55559
EMULATOR_RCV_PORT=55558
EMULATOR_SND_PORT=55559
SENDER_PORT=55558

# Function to check if a port is available
check_port() {
    local port=$1
    if netstat -tuln | grep -q ":$port"; then
        echo "Port $port is already in use"
        return 1
    fi
    return 0
}

# Function to build the SIF file
build_sif() {
    local sif_file=$1
    
    echo "Building SIF file..."
    
    # Check if apptainer is installed
    if ! command -v apptainer &> /dev/null; then
        echo "Error: apptainer is not installed"
        exit 1
    fi
    
    # Create sifs directory if it doesn't exist
    mkdir -p "$(dirname "$sif_file")"
    
    # Build the SIF file from Docker Hub
    echo "Building SIF file from docker://jlabtsai/rtdp-cpu_emu:latest..."
    apptainer build --force "$sif_file" docker://jlabtsai/rtdp-cpu_emu:latest
    
    if [ ! -f "$sif_file" ]; then
        echo "Error: Failed to build SIF file"
        exit 1
    fi
}

# Check all ports before starting
for port in $RECEIVER_PORT $EMULATOR_RCV_PORT $EMULATOR_SND_PORT $SENDER_PORT; do
    if ! check_port $port; then
        exit 1
    fi
done

# Get the absolute paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SIF_FILE="$SCRIPT_DIR/../../sifs/cpu-emu.sif"

# Build the SIF file
build_sif "$SIF_FILE"

# Create log directory
LOG_DIR="$SCRIPT_DIR/logs"
mkdir -p $LOG_DIR

# Function to run a command in the background and capture its output
run_component() {
    local name=$1
    local cmd=$2
    echo "Starting $name..."
    echo "Command: $cmd" > "${LOG_DIR}/${name}_output.log"
    # Run the command and save its output to a log file
    $cmd >> "${LOG_DIR}/${name}_output.log" 2>&1 &
    echo $! > "${LOG_DIR}/${name}_pid.txt"
    # Start a background process to monitor the log file
    tail -f "${LOG_DIR}/${name}_output.log" &
    echo $! > "${LOG_DIR}/${name}_tail_pid.txt"
}

# Clean up before starting
cleanup_ports

# Start the receiver
run_component "Receiver" "apptainer run --pwd /app $SIF_FILE receiver -z -i 127.0.0.1 -p $RECEIVER_PORT"

# Wait longer for receiver to start and bind socket
sleep 5

# Start the emulator
run_component "Emulator" "apptainer run --pwd /app $SIF_FILE emulator -i 127.0.0.1 -p $EMULATOR_SND_PORT -r $EMULATOR_RCV_PORT -t 1 -b 100 -m 0.01 -o 0.001 -s 1 -v 1"

# Wait longer for emulator to start
sleep 5

# Start the sender
run_component "Sender" "apptainer run --pwd /app $SIF_FILE sender -i 127.0.0.1 -p $EMULATOR_RCV_PORT -c 10 -s 1"

echo "Test components started:"
echo "- Receiver listening on port $RECEIVER_PORT"
echo "- Emulator receiving on port $EMULATOR_RCV_PORT and sending on port $EMULATOR_SND_PORT"
echo "- Sender sending to port $EMULATOR_RCV_PORT"
echo ""
echo "To verify data flow:"
echo "1. Check Receiver output for 'Received request' messages"
echo "2. Check Emulator output for 'Received request' and 'Forwarding to destination' messages"
echo "3. Check Sender output for 'Sending' and 'Received' messages"
echo ""
echo "Press Ctrl+C to stop all components"

# Cleanup function
cleanup() {
    echo "Cleaning up..."
    # Stop all tail processes
    kill $(cat ${LOG_DIR}/*_tail_pid.txt 2>/dev/null) 2>/dev/null
    # Stop all components
    kill $(cat ${LOG_DIR}/*_pid.txt 2>/dev/null) 2>/dev/null
    # Remove temporary files
    rm -f ${LOG_DIR}/*_pid.txt ${LOG_DIR}/*_tail_pid.txt
    exit
}

# Set up cleanup on script exit
trap cleanup INT TERM EXIT

# Wait for all background processes
wait 