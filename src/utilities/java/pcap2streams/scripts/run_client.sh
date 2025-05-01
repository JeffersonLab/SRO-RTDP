#!/bin/bash

# Script to run the IPBasedStreamClient

# Set the project directory
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPTS_DIR="$PROJECT_DIR/scripts"
CONFIG_DIR="$PROJECT_DIR/custom-config"

# Default config file
CONFIG_FILE="$CONFIG_DIR/ip-based-config.json"

# Parse command line arguments
if [ $# -gt 0 ]; then
    CONFIG_FILE="$1"
fi

# Check if the config file exists
if [ ! -f "$CONFIG_FILE" ]; then
    echo "Error: Configuration file not found at $CONFIG_FILE"
    echo "Usage: $0 [config_file]"
    echo "Default config file: $CONFIG_DIR/ip-based-config.json"
    exit 1
fi

# Compile the client
echo "Compiling the IPBasedStreamClient..."
cd "$PROJECT_DIR"
javac -d build/classes/java/main -cp "lib/json-20231013.jar:lib/disruptor-3.4.4.jar:lib/snakeyaml-2.0.jar" src/main/java/org/jlab/ersap/actor/pcap2streams/*.java

# Run the client
echo "Starting IPBasedStreamClient with config file: $CONFIG_FILE"
java -cp "build/classes/java/main:lib/json-20231013.jar:lib/disruptor-3.4.4.jar:lib/snakeyaml-2.0.jar" org.jlab.ersap.actor.pcap2streams.IPBasedStreamClient "$CONFIG_FILE" 