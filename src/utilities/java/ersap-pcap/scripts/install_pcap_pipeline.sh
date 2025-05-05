#!/bin/bash

# Exit on error
set -e

# Function to check if a file exists and is executable
check_executable() {
    if [ ! -f "$1" ]; then
        echo "Error: $1 does not exist"
        exit 1
    fi
    if [ ! -x "$1" ]; then
        echo "Error: $1 is not executable"
        echo "Please make it executable with: chmod +x $1"
        exit 1
    fi
}

# Function to check if a directory exists
check_directory() {
    if [ ! -d "$1" ]; then
        echo "Error: Directory $1 does not exist"
        exit 1
    fi
}

echo "Checking prerequisites..."

# Set ERSAP_HOME to match build_ersap-java.sh
export ERSAP_HOME=$HOME/ersap-install

# Check if ERSAP_HOME exists
check_directory "$ERSAP_HOME"

echo "Installing PCAP pipeline..."

# Set the absolute path to pcap-actors directory
PCAP_ACTORS_DIR="/workspaces/SRO-RTDP/src/utilities/java/ersap-pcap/pcap-actors"
check_directory "$PCAP_ACTORS_DIR"

# Check if gradlew exists and is executable
check_executable "$PCAP_ACTORS_DIR/gradlew"

# Build and install the pcap-actors
cd "$PCAP_ACTORS_DIR"
echo "Building and installing pcap-actors..."
./gradlew clean build
ERSAP_HOME=$ERSAP_HOME ./gradlew install

# Create necessary directories
echo "Creating input and output directories..."
mkdir -p "$PCAP_ACTORS_DIR/input"
mkdir -p "$PCAP_ACTORS_DIR/output"

echo "PCAP pipeline installation completed successfully!" 