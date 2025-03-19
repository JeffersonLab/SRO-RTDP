#!/bin/bash

# Check if ERSAP_HOME is set
if [ -z "$ERSAP_HOME" ]; then
    echo "Error: ERSAP_HOME environment variable is not set"
    exit 1
fi

# Create necessary directories
mkdir -p $ERSAP_HOME/lib
mkdir -p $ERSAP_HOME/data/output

# Set LD_LIBRARY_PATH
export LD_LIBRARY_PATH=/usr/local/lib:/usr/local/lib64:$LD_LIBRARY_PATH

# Run the ERSAP shell with the configuration
$HOME/ersap-install/bin/ersap-shell ersap_script.ersap
