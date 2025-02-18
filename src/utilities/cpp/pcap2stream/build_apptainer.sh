#!/bin/bash

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
    cd sender
    apptainer build pcap2stream.sif .apptainer/pcap2stream.def
    cd ..
}

# Function to build server image
build_server() {
    echo "Building stream server image..."
    cd server
    apptainer build stream_server.sif .apptainer/stream_server.def
    cd ..
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
        build_server
        ;;
    *)
        print_usage
        exit 1
        ;;
esac