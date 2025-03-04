#!/bin/bash

# Script to check the ring buffer status of a running PCAP Stream Source

# Set the project directory
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPTS_DIR="$PROJECT_DIR/scripts"

# Check if ERSAP_HOME is set
if [ -z "$ERSAP_HOME" ]; then
    echo "Error: ERSAP_HOME environment variable is not set"
    echo "Please set ERSAP_HOME to the ERSAP installation directory"
    exit 1
fi

# Default values
CONTAINER_NAME="pcap-container"
SERVICE_NAME="pcap-source"
INTERVAL_SECONDS=5

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        -c|--container)
            CONTAINER_NAME="$2"
            shift 2
            ;;
        -s|--service)
            SERVICE_NAME="$2"
            shift 2
            ;;
        -i|--interval)
            INTERVAL_SECONDS="$2"
            shift 2
            ;;
        -h|--help)
            echo "Usage: $0 [options]"
            echo "Options:"
            echo "  -c, --container CONTAINER_NAME  Container name (default: pcap-container)"
            echo "  -s, --service SERVICE_NAME      Service name (default: pcap-source)"
            echo "  -i, --interval SECONDS          Update interval in seconds (default: 5)"
            echo "  -h, --help                      Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

echo "ERSAP_HOME: $ERSAP_HOME"
echo "Container name: $CONTAINER_NAME"
echo "Service name: $SERVICE_NAME"
echo "Update interval: $INTERVAL_SECONDS seconds"

# Compile the ring buffer status checker
echo "Compiling the ring buffer status checker..."
cd "$SCRIPTS_DIR"
javac -cp "$ERSAP_HOME/lib/*" RingBufferStatusChecker.java

# Run the ring buffer status checker
echo "Running the ring buffer status checker..."
java -cp ".:$ERSAP_HOME/lib/*" RingBufferStatusChecker "$CONTAINER_NAME" "$SERVICE_NAME" "$INTERVAL_SECONDS" 