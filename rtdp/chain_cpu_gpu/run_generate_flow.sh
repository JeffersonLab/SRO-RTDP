#!/bin/bash

# Get the directory where the script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

# Check if config file is provided
if [ $# -ne 1 ]; then
    echo "Usage: $0 <chain_config.yaml>"
    exit 1
fi

CONFIG_FILE="$1"

# Check if config file exists
if [ ! -f "$CONFIG_FILE" ]; then
    echo "Error: Config file $CONFIG_FILE does not exist"
    exit 1
fi

# Build the Docker image
echo "Building Docker image..."
docker build -t generate-flow .

# Run the container
echo "Running container..."
docker run --rm \
    -v "$SCRIPT_DIR/generate_flow.py:/app/generate_flow.py" \
    -v "$SCRIPT_DIR/cylc:/app/cylc" \
    -v "$(realpath "$CONFIG_FILE"):/app/chain_config.yaml" \
    generate-flow /app/chain_config.yaml

# Check if the flow.cylc file was generated
if [ -f "$SCRIPT_DIR/cylc/flow.cylc" ]; then
    echo "Successfully generated flow.cylc in $SCRIPT_DIR/cylc/"
else
    echo "Error: Failed to generate flow.cylc"
    exit 1
fi 