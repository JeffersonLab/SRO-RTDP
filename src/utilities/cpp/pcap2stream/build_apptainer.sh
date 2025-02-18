#!/bin/bash

# Get the script's directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Function to print usage
print_usage() {
    echo "Usage: $0 [sender|server|all]"
    echo "  sender: Build only the pcap2stream sender image"
    echo "  server: Build only the stream server image"
    echo "  all: Build both images"
}

# Function to build sender image
build_sender() {
    echo "Building pcap2stream sender image..."
    cd "${SCRIPT_DIR}/sender"
    apptainer build pcap2stream.sif .apptainer/pcap2stream.def
    if [ $? -eq 0 ]; then
        echo "Successfully built pcap2stream sender image"
    else
        echo "Failed to build pcap2stream sender image"
        exit 1
    fi
}

# Function to build server image
build_server() {
    echo "Building stream server image..."
    cd "${SCRIPT_DIR}/server"
    apptainer build stream_server.sif .apptainer/stream_server.def
    if [ $? -eq 0 ]; then
        echo "Successfully built stream server image"
    else
        echo "Failed to build stream server image"
        exit 1
    fi
}

# Check command line arguments
if [ $# -ne 1 ]; then
    print_usage
    exit 1
fi

case "$1" in
    "sender")
        build_sender
        ;;
    "server")
        build_server
        ;;
    "all")
        build_sender
        cd "${SCRIPT_DIR}"  # Return to original directory
        build_server
        ;;
    *)
        print_usage
        exit 1
        ;;
esac