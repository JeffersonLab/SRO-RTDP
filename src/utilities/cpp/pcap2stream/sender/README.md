# pcap2stream (Sender)

A utility that reads pcap files and streams the network payloads to TCP endpoints.

## Overview

This utility processes pcap files by:
- Identifying unique source IP addresses in the pcap file
- Creating TCP connections to a server for each unique source IP
- Extracting network payloads from the pcap file
- Streaming the payloads to the corresponding server endpoints

## Building and Running

There are three ways to build and run this utility:
1. Native binary
2. Docker container
3. Apptainer/Singularity container

### 1. Native Binary

#### Prerequisites
- CMake (version 3.10 or higher)
- C++ compiler with C++20 support
- libpcap development library
- pthread library

#### Installing Dependencies
On Ubuntu/Debian:
```bash
sudo apt-get install libpcap-dev
```

#### Building
```bash
# Create build directory
mkdir build
cd build

# Generate build files
cmake ..

# Build the sender
make
```

#### Running
```bash
# Basic usage
./pcap2stream <pcap_file> <server_ip> <base_port>

# Example
./pcap2stream capture.pcap 127.0.0.1 5000
```

### 2. Docker Container

#### Building
```bash
# Build the image
docker build -t pcap2stream -f .container/Dockerfile .
```

#### Running
```bash
# Basic usage
docker run -v /path/to/pcap/dir:/app/pcap pcap2stream /app/pcap/capture.pcap <server_ip> <base_port>

# Example with docker-compose
docker-compose run pcap-sender /app/pcap/capture.pcap stream-server 5000

# Example with specific paths and ports
docker run -v $(pwd)/pcap:/app/pcap pcap2stream /app/pcap/capture.pcap 192.168.1.100 6000
```

### 3. Apptainer/Singularity Container

#### Building
```bash
# Using the build script
../build_apptainer.sh sender

# Or build directly
apptainer build pcap2stream.sif .apptainer/pcap2stream.def
```

#### Running

##### Using the Helper Script
```bash
# The script provides an easy way to run with Apptainer
../run_sender_apptainer.sh <pcap_file> [options]

# Examples
../run_sender_apptainer.sh capture.pcap                     # Use defaults
../run_sender_apptainer.sh capture.pcap -p 6000            # Custom port
../run_sender_apptainer.sh capture.pcap -i 192.168.1.100   # Custom IP
```

##### Direct Apptainer Usage
```bash
# Basic usage
apptainer run pcap2stream.sif <pcap_file> <server_ip> <base_port>

# Example with home directory binding
apptainer run --bind $HOME pcap2stream.sif ~/data/capture.pcap 127.0.0.1 5000

# Example with specific directory binding
apptainer run --bind /path/to/pcap/dir pcap2stream.sif /path/to/pcap/dir/capture.pcap 192.168.1.100 6000
```

## Common Usage Patterns

### 1. Local Testing
```bash
# Native
./build/pcap2stream capture.pcap 127.0.0.1 5000

# Docker
docker run -v $(pwd)/pcap:/app/pcap pcap2stream /app/pcap/capture.pcap 127.0.0.1 5000

# Apptainer
./run_sender_apptainer.sh capture.pcap
```

### 2. Remote Server
```bash
# Native
./build/pcap2stream capture.pcap 192.168.1.100 5000

# Docker
docker run -v $(pwd)/pcap:/app/pcap pcap2stream /app/pcap/capture.pcap 192.168.1.100 5000

# Apptainer
./run_sender_apptainer.sh capture.pcap -i 192.168.1.100
```

### 3. Custom Port
```bash
# Native
./build/pcap2stream capture.pcap 127.0.0.1 6000

# Docker
docker run -v $(pwd)/pcap:/app/pcap pcap2stream /app/pcap/capture.pcap 127.0.0.1 6000

# Apptainer
./run_sender_apptainer.sh capture.pcap -p 6000
```

## Notes

- The server must be running and accepting TCP connections before starting pcap2stream
- Each unique source IP will get its own dedicated TCP connection
- Make sure the server has enough ports allocated to handle all unique source IPs in your pcap file
- The program will automatically close connections when finished or if errors occur
- When using containers, ensure proper volume mounting for accessing PCAP files
- Base port number should match the server's configuration