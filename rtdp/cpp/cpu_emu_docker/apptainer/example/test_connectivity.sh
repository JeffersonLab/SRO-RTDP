#!/bin/bash

# Default values
HOST="localhost"
PORT=50888
SIF_PATH="../cpu-emu.sif"

# Help message
show_help() {
    echo "Usage: $0 [-h HOST] [-p PORT] [-i SIF_PATH]"
    echo "Tests network connectivity from inside the Apptainer container"
    echo ""
    echo "Options:"
    echo "  -h HOST        Target host to test (default: localhost)"
    echo "  -p PORT        Target port to test (default: 50888)"
    echo "  -i SIF_PATH    Path to the SIF file (default: ../cpu-emu.sif)"
    echo "  -? | --help    Show this help message"
}

# Parse command line options
while getopts "h:p:i:?" opt; do
    case $opt in
        h) HOST="$OPTARG" ;;
        p) PORT="$OPTARG" ;;
        i) SIF_PATH="$OPTARG" ;;
        ?) show_help; exit 0 ;;
    esac
done

# Check if SIF file exists
if [ ! -f "$SIF_PATH" ]; then
    echo "Error: SIF file $SIF_PATH does not exist"
    exit 1
fi

echo "Testing connectivity to $HOST:$PORT from inside container..."

# Test using ping first (if host is not localhost)
if [ "$HOST" != "localhost" ] && [ "$HOST" != "127.0.0.1" ]; then
    echo "Testing ping to $HOST..."
    if ! apptainer exec "$SIF_PATH" ping -c 1 -W 2 "$HOST" > /dev/null 2>&1; then
        echo "Warning: Unable to ping $HOST"
        echo "Note: This might be normal if ICMP is blocked"
    else
        echo "Ping to $HOST successful"
    fi
fi

# Test TCP connectivity using netcat
echo "Testing TCP connection to $HOST:$PORT..."
if apptainer exec "$SIF_PATH" timeout 5 nc -zv "$HOST" "$PORT" 2>&1; then
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