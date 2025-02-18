# pcap2stream (Sender)

A utility that reads pcap files and streams the network payloads to TCP endpoints.

## Overview

This utility processes pcap files by:
- Identifying unique source IP addresses in the pcap file
- Creating TCP connections to a server for each unique source IP
- Extracting network payloads from the pcap file
- Streaming the payloads to the corresponding server endpoints

## Building

### Prerequisites

- CMake (version 3.10 or higher)
- C++ compiler with C++20 support
- libpcap development library
- pthread library

### Installing Dependencies

On Ubuntu/Debian:
```bash
sudo apt-get install libpcap-dev
```

### Build Steps

```bash
# Create build directory
mkdir build
cd build

# Generate build files
cmake ..

# Build the sender
make
```

## Usage

```bash
./pcap2stream <pcap_file> <server_ip> <base_port>
```

### Parameters

- `pcap_file`: Path to the input pcap file to process
- `server_ip`: IP address of the server to connect to
- `base_port`: Base port number. For each unique source IP, the program will use (base_port + N) where N is the Nth unique source IP encountered

### Example

```bash
./pcap2stream capture.pcap 127.0.0.1 5000
```

This will:
1. Read `capture.pcap`
2. For each unique source IP found in the pcap:
   - Create a TCP connection to 127.0.0.1 on port 5000+N
   - Stream the payloads associated with that source IP to the connection

## Container Support

### Using Docker

1. Build the Docker image:
```bash
docker build -t pcap2stream -f .container/Dockerfile .
```

2. Run with Docker:
```bash
# Mount a directory containing your pcap files
docker run -v /path/to/pcap/dir:/app/pcap pcap2stream /app/pcap/capture.pcap stream-server 5000
```

3. Using docker-compose:
```bash
# First, ensure your pcap files are in the ./pcap directory
docker-compose run pcap-sender /app/pcap/capture.pcap stream-server 5000
```

### Using Apptainer/Singularity

1. Build the Apptainer image:
```bash
# Using the build script from parent directory
../build_apptainer.sh sender

# Or build directly
apptainer build pcap2stream.sif .apptainer/pcap2stream.def
```

2. Run with Apptainer:
```bash
# For files in your home directory, bind it with write permissions
apptainer run --bind $HOME pcap2stream.sif /path/to/capture.pcap 127.0.0.1 5000

# For files in other locations, bind the specific directory
apptainer run --bind /path/to/pcap/dir pcap2stream.sif /path/to/pcap/dir/capture.pcap 127.0.0.1 5000

# If you need write access to the bound directory
apptainer run --bind /path/to/pcap/dir:/path/to/pcap/dir pcap2stream.sif /path/to/pcap/dir/capture.pcap 127.0.0.1 5000
```

Note: When using Apptainer, you need to:
- Use absolute paths for your pcap files
- Bind any directories containing your pcap files
- The bound directories will be available at the same path inside the container

Example with a pcap file in your home directory:
```bash
# If your pcap is at ~/data/capture.pcap
apptainer run --bind $HOME pcap2stream.sif $HOME/data/capture.pcap 127.0.0.1 5000
```

## Features

- Thread-safe packet queuing for each source IP
- Asynchronous transmission of payloads
- Automatic connection management
- Preserves packet timing information
- Graceful cleanup on exit

## Testing with Stream Server

1. Start the stream server first:
```bash
# Native
./stream_server 0.0.0.0 5000 3

# Docker
docker-compose up stream-server

# Apptainer
apptainer run ../server/stream_server.sif 0.0.0.0 5000 3
```

2. Then run pcap2stream:
```bash
# Native
./pcap2stream capture.pcap 127.0.0.1 5000

# Docker
docker-compose run pcap-sender /app/pcap/capture.pcap stream-server 5000

# Apptainer
apptainer run pcap2stream.sif capture.pcap 127.0.0.1 5000
```

## Notes

- The server must be running and accepting TCP connections before starting pcap2stream
- Each unique source IP will get its own dedicated TCP connection
- Make sure the server has enough ports allocated to handle all unique source IPs in your pcap file
- The program will automatically close connections when finished or if errors occur