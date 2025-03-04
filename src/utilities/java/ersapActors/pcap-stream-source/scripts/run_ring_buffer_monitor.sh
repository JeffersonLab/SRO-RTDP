#!/bin/bash

# Set the project directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Default object name pattern
DEFAULT_PATTERN="org.jlab.ersap:type=RingBuffer,*"
PATTERN=${1:-$DEFAULT_PATTERN}

# Default interval in seconds
DEFAULT_INTERVAL=1
INTERVAL=${2:-$DEFAULT_INTERVAL}

# Compile the Java file
echo "Compiling RingBufferMonitor.java..."
cd "$SCRIPT_DIR"
javac RingBufferMonitor.java
if [ $? -ne 0 ]; then
    echo "Error: Failed to compile RingBufferMonitor.java"
    exit 1
fi

# Run the monitor
echo "Starting ring buffer monitor..."
echo "Object name pattern: $PATTERN"
echo "Interval: $INTERVAL seconds"
java RingBufferMonitor "$PATTERN" "$INTERVAL" 