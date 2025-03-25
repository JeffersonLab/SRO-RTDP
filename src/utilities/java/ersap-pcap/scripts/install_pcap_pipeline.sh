#!/bin/bash

# Exit on error
set -e

# Set ERSAP_HOME to match build_ersap-java.sh
export ERSAP_HOME=$HOME/ersap-install

echo "Installing PCAP pipeline..."

# Build and install the pcap-actors
cd pcap-actors
ERSAP_HOME=$ERSAP_HOME ./gradlew install

echo "PCAP pipeline installation completed successfully!" 