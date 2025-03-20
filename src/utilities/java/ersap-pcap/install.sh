#!/bin/bash

# Exit on error
set -e

# Set ERSAP_HOME
export ERSAP_HOME=~/ersap-install

# Create ERSAP installation directory if it doesn't exist
mkdir -p $ERSAP_HOME
mkdir -p $ERSAP_HOME/lib
mkdir -p $ERSAP_HOME/bin

echo "Installing ERSAP core..."
# Install ERSAP core
cd /workspace/ERSAP/ersap-java
./gradlew clean deploy
./gradlew publishToMavenLocal

echo "Building ERSAP PCAP service..."
# Build and install PCAP service
cd /workspace/src/utilities/java/ersap-pcap
./gradlew clean build

# Copy the built jar to ERSAP installation
cp build/libs/ersap-pcap-1.0-SNAPSHOT.jar $ERSAP_HOME/lib/

echo "Creating necessary directories..."
# Create necessary directories
mkdir -p input output log

echo "Setting up file permissions..."
# Ensure scripts are executable
chmod +x run_pipeline.sh
chmod +x run_ersap.sh
chmod +x get_help.sh
chmod +x get_run_help.sh

echo "Installation complete!"
echo "ERSAP_HOME is set to: $ERSAP_HOME"
echo ""
echo "To run the pipeline:"
echo "1. Place your PCAP files in the input/ directory"
echo "2. Run: ./run_pipeline.sh" 