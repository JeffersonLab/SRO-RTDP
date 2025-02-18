#!/bin/bash

# Default values
DEFAULT_SERVER_IP="127.0.0.1"
DEFAULT_BASE_PORT=5000

# Function to print usage
print_usage() {
    echo "Usage: $0 <pcap_file> [options]"
    echo "Arguments:"
    echo "  pcap_file               Path to the PCAP file to process"
    echo "Options:"
    echo "  -i, --ip <ip>          Server IP address (default: $DEFAULT_SERVER_IP)"
    echo "  -p, --port <port>      Base port number (default: $DEFAULT_BASE_PORT)"
    echo "                         Note: This should match the server's base port"
    echo "  -h, --help             Show this help message"
    echo
    echo "Examples:"
    echo "  $0 capture.pcap                     # Use defaults"
    echo "  $0 capture.pcap -p 6000            # Use port 6000 as base port"
    echo "  $0 capture.pcap -i 192.168.1.100   # Connect to specific server"
    echo "  $0 capture.pcap -i 10.0.0.1 -p 7000 # Custom IP and port"
}

# Parse command line arguments
SERVER_IP=$DEFAULT_SERVER_IP
BASE_PORT=$DEFAULT_BASE_PORT
PCAP_FILE=""

# First argument should be the pcap file
if [ $# -eq 0 ]; then
    print_usage
    exit 1
fi

PCAP_FILE="$1"
shift

while [[ $# -gt 0 ]]; do
    case $1 in
        -i|--ip)
            if [ -z "$2" ]; then
                echo "Error: IP address not provided"
                exit 1
            fi
            SERVER_IP="$2"
            shift 2
            ;;
        -p|--port)
            if [ -z "$2" ]; then
                echo "Error: Port number not provided"
                exit 1
            fi
            if ! [[ "$2" =~ ^[0-9]+$ ]]; then
                echo "Error: Port must be a number"
                exit 1
            fi
            if [ "$2" -lt 1024 ] || [ "$2" -gt 65535 ]; then
                echo "Error: Port must be between 1024 and 65535"
                exit 1
            fi
            BASE_PORT="$2"
            shift 2
            ;;
        -h|--help)
            print_usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            print_usage
            exit 1
            ;;
    esac
done

# Check if PCAP file exists
if [ ! -f "$PCAP_FILE" ]; then
    echo "Error: PCAP file not found: $PCAP_FILE"
    exit 1
fi

# Get absolute path of PCAP file
PCAP_FILE=$(realpath "$PCAP_FILE")

# Get the directory containing the PCAP file
PCAP_DIR=$(dirname "$PCAP_FILE")

# Check if the sender image exists
if [ ! -f "sender/pcap2stream.sif" ]; then
    echo "Sender image not found. Building it now..."
    ./build_apptainer.sh sender
    if [ $? -ne 0 ]; then
        echo "Error: Failed to build sender image"
        exit 1
    fi
fi

echo "Starting pcap2stream sender..."
echo "PCAP File: $PCAP_FILE"
echo "Server IP: $SERVER_IP"
echo "Base Port: $BASE_PORT"
echo "Note: Make sure the server is running and using the same base port"
echo

# Run the sender with the PCAP directory bound
apptainer run --bind "$PCAP_DIR" sender/pcap2stream.sif "$PCAP_FILE" "$SERVER_IP" "$BASE_PORT"