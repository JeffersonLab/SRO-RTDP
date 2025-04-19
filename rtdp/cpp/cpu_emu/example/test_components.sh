#!/bin/bash

# Configuration
RECEIVER_PORT=3000
EMULATOR_PORT=3001
SENDER_PORT=3002

# Function to check if a port is available
check_port() {
    local port=$1
    if lsof -i :$port > /dev/null 2>&1; then
        echo "Port $port is already in use"
        return 1
    fi
    return 0
}

# Check all ports before starting
for port in $RECEIVER_PORT $EMULATOR_PORT $SENDER_PORT; do
    if ! check_port $port; then
        exit 1
    fi
done

# Clean up any existing containers
echo "Cleaning up existing containers..."
docker ps -a | grep cpu-emu | awk '{print $1}' | xargs -r docker rm -f

# Create a temporary YAML file for the emulator
cat > emulator_config.yaml << EOF
destination: "127.0.0.1"
dst_port: $RECEIVER_PORT
rcv_port: $EMULATOR_PORT
sleep: 0
threads: 1
latency: 500
mem_footprint: 0.05
output_size: 0.001
verbose: 1
terminal: 0
EOF

# Function to run a command in the background and capture its output
run_component() {
    local name=$1
    local cmd=$2
    echo "Starting $name..."
    echo "Command: $cmd" > "${name}_output.log"
    # Run the command and save its output to a log file
    $cmd >> "${name}_output.log" 2>&1 &
    echo $! > "${name}_pid.txt"
    # Start a background process to monitor the log file
    tail -f "${name}_output.log" &
    echo $! > "${name}_tail_pid.txt"
}

# Start the receiver (terminal node)
run_component "Receiver" "docker run --rm --network host cpu-emu receiver -z -r $RECEIVER_PORT -v 1"

# Wait for receiver to start
sleep 2

# Start the emulator
run_component "Emulator" "docker run --rm --network host -v $(pwd)/emulator_config.yaml:/app/emulator_config.yaml cpu-emu emulator -y emulator_config.yaml"

# Wait for emulator to start
sleep 2

# Start the sender
run_component "Sender" "docker run --rm --network host cpu-emu sender -i 127.0.0.1 -p $EMULATOR_PORT -c 5 -s 10"

echo "Test components started:"
echo "- Receiver listening on port $RECEIVER_PORT"
echo "- Emulator listening on port $EMULATOR_PORT"
echo "- Sender sending to port $EMULATOR_PORT"
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
    kill $(cat *_tail_pid.txt 2>/dev/null) 2>/dev/null
    # Stop all components
    kill $(cat *_pid.txt 2>/dev/null) 2>/dev/null
    # Remove temporary files
    rm -f *_pid.txt *_tail_pid.txt *_output.log emulator_config.yaml
    # Clean up Docker containers
    docker ps -a | grep cpu-emu | awk '{print $1}' | xargs -r docker rm -f
    exit
}

# Set up cleanup on script exit
trap cleanup INT TERM EXIT

# Wait for all background processes
wait 