#!/bin/bash

# Default values
HOST="localhost"
PORT=50888
SIF_PATH="../cpu-emu.sif"
TEST_DEF="docker://ubuntu:22.04"

# Help message
show_help() {
    echo "Usage: $0 [-h HOST] [-p PORT] [-i SIF_PATH] [-t TEST_IMAGE]"
    echo "Tests network connectivity from inside the Apptainer container"
    echo ""
    echo "Options:"
    echo "  -h HOST        Target host to test (default: localhost)"
    echo "  -p PORT        Target port to test (default: 50888)"
    echo "  -i SIF_PATH    Path to the SIF file (default: ../cpu-emu.sif)"
    echo "  -t TEST_IMAGE  Test container image (default: docker://ubuntu:22.04)"
    echo "  -? | --help    Show this help message"
}

# Parse command line options
while getopts "h:p:i:t:?" opt; do
    case $opt in
        h) HOST="$OPTARG" ;;
        p) PORT="$OPTARG" ;;
        i) SIF_PATH="$OPTARG" ;;
        t) TEST_DEF="$OPTARG" ;;
        ?) show_help; exit 0 ;;
    esac
done

# Check if SIF file exists (only if we're testing against it)
if [ "$TEST_DEF" = "$SIF_PATH" ] && [ ! -f "$SIF_PATH" ]; then
    echo "Error: SIF file $SIF_PATH does not exist"
    exit 1
fi

echo "Testing connectivity to $HOST:$PORT from inside container..."
echo "Using test image: $TEST_DEF"

# Install required packages first
echo "Installing required packages..."
if ! apptainer exec --writable-tmpfs "$TEST_DEF" bash -c "
    export DEBIAN_FRONTEND=noninteractive && \
    echo 'Creating temporary directories...' && \
    mkdir -p /tmp/apt/partial /tmp/apt/lists/partial && \
    echo 'Running apt-get update...' && \
    apt-get -o Dir::Cache=/tmp/apt -o Dir::State::Lists=/tmp/apt/lists update && \
    echo 'Installing packages...' && \
    apt-get -o Dir::Cache=/tmp/apt -o Dir::State::Lists=/tmp/apt/lists install -y iputils-ping netcat-openbsd
    "; then
    echo "Warning: Failed to install required packages. Check error messages above."
    exit 1
fi

# Use standard paths for ping and nc
PING_PATH="/usr/bin/ping"
NC_PATH="/usr/bin/nc.openbsd"

if [ "$HOST" != "localhost" ] && [ "$HOST" != "127.0.0.1" ]; then
    echo "Testing ping to $HOST..."
    if ! apptainer exec --writable-tmpfs "$TEST_DEF" "$PING_PATH" -c 1 -W 2 "$HOST" 2>&1; then
        echo "Warning: Unable to ping $HOST"
        echo "Note: This might be normal if ICMP is blocked"
    else
        echo "Ping to $HOST successful"
    fi
fi

# Test TCP connectivity using netcat
echo "Testing TCP connection to $HOST:$PORT..."
if apptainer exec --writable-tmpfs "$TEST_DEF" "$NC_PATH" -zv "$HOST" "$PORT" 2>&1; then
    echo "Success: TCP connection to $HOST:$PORT is possible"
    exit 0
else
    echo "Error: Cannot establish TCP connection to $HOST:$PORT"
    echo "Possible issues:"
    echo "  - Port is not open on target host"
    echo "  - Firewall is blocking the connection"
    echo "  - Target service is not running"
    echo "  - Network routing issues"
    exit 1
fi