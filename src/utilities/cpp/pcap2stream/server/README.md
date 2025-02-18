# Stream Server

A multi-port TCP server designed to receive and save network streams from pcap2stream utility.

## Overview

This server is specifically designed to work with the pcap2stream utility. It:
- Listens on multiple consecutive ports simultaneously
- Handles multiple concurrent connections
- Saves received data streams to separate binary files
- Provides real-time connection status logging
- Supports graceful shutdown

## Building

### Prerequisites

- CMake (version 3.10 or higher)
- C++ compiler with C++20 support
- pthread library

### Build Steps

```bash
# Create build directory
mkdir build
cd build

# Generate build files
cmake ..

# Build the server
make
```

## Usage

```bash
./stream_server <ip_address> <base_port> <num_ports>
```

### Parameters

- `ip_address`: IP address to bind to (use "0.0.0.0" to listen on all interfaces)
- `base_port`: Starting port number
- `num_ports`: Number of consecutive ports to listen on

### Example

To start the server listening on ports 5000, 5001, and 5002 on all interfaces:
```bash
./stream_server 0.0.0.0 5000 3
```

## Container Support

### Using Docker

1. Build the Docker image:
```bash
docker build -t stream-server -f .container/Dockerfile .
```

2. Run with Docker:
```bash
# Mount a directory for output files
docker run -p 5000-5002:5000-5002 -v /path/to/output:/app/output stream-server 0.0.0.0 5000 3
```

3. Using docker-compose:
```bash
# Output files will be saved in ./output directory
docker-compose up stream-server
```

### Using Apptainer/Singularity

1. Build the Apptainer image:
```bash
# Using the build script from parent directory
../build_apptainer.sh server

# Or build directly
apptainer build stream_server.sif .apptainer/stream_server.def
```

2. Run with Apptainer:
```bash
# Basic run (creates output in current directory)
mkdir -p output  # Create output directory first
apptainer run stream_server.sif 0.0.0.0 5000 3
# Output files will be in ./output/

# Run with output to your home directory
mkdir -p ~/pcap_output  # Create output directory first
apptainer run --bind $HOME/pcap_output:/app/output stream_server.sif 0.0.0.0 5000 3
# Output files will be in ~/pcap_output/

# Run with output to a specific directory
mkdir -p /path/to/output  # Create output directory first
apptainer run --bind /path/to/output:/app/output stream_server.sif 0.0.0.0 5000 3
# Output files will be in /path/to/output/
```

3. Run as an instance (background service):
```bash
# Create output directory first
mkdir -p /path/to/output

# Start instance with output to a specific directory
apptainer instance start --bind /path/to/output:/app/output stream_server.sif stream_server

# Stop instance
apptainer instance stop stream_server
```

Note: When using Apptainer, you need to:
1. Create the output directory before running the container
2. Choose one of these output options:
   - Use current directory: Files will be in `./output/`
   - Use home directory: Files will be in `~/pcap_output/` (or your chosen path)
   - Use custom directory: Files will be in your specified path
3. Ensure the output directory has write permissions
4. Use absolute paths for bound directories

Example directory setups:

1. Using current directory:
```bash
# Setup
mkdir -p output
chmod 755 output

# Run
apptainer run stream_server.sif 0.0.0.0 5000 3
```

2. Using home directory:
```bash
# Setup
mkdir -p ~/pcap_output
chmod 755 ~/pcap_output

# Run
apptainer run --bind ~/pcap_output:/app/output stream_server.sif 0.0.0.0 5000 3
```

3. Using a specific directory:
```bash
# Setup
sudo mkdir -p /data/pcap_output
sudo chmod 777 /data/pcap_output

# Run
apptainer run --bind /data/pcap_output:/app/output stream_server.sif 0.0.0.0 5000 3
```

Output File Structure:
```
output/
├── stream_5000_20240315_123456.bin  # Data from port 5000
├── stream_5001_20240315_123456.bin  # Data from port 5001
└── stream_5002_20240315_123456.bin  # Data from port 5002
```

## Output Files

- All received data is saved in the `output` directory
- Files are named using the format: `stream_<port>_<timestamp>.bin`
  - Example: `stream_5000_20240315_123456.bin`
- Each port's data is saved to a separate file
- New files are created with timestamps to prevent overwriting

## Features

- Thread-safe file handling
- Automatic port management
- Clean shutdown with Ctrl+C
- Real-time connection status logging
- Binary file output with timestamps
- Support for multiple simultaneous connections

## Testing with pcap2stream

1. Start the server first:
```bash
# Native
./stream_server 0.0.0.0 5000 3

# Docker
docker-compose up stream-server

# Apptainer
apptainer run stream_server.sif 0.0.0.0 5000 3
```

2. Then run pcap2stream with matching parameters:
```bash
# Native
./pcap2stream capture.pcap 127.0.0.1 5000

# Docker
docker-compose run pcap-sender /app/pcap/capture.pcap stream-server 5000

# Apptainer
apptainer run ../sender/pcap2stream.sif capture.pcap 127.0.0.1 5000
```

The server will:
1. Create an output file for each port when a connection is received
2. Save all received data to the corresponding file
3. Log connection events and file creation
4. Gracefully handle client disconnections

## Shutdown

- Press Ctrl+C to gracefully stop the server
- The server will:
  1. Stop accepting new connections
  2. Close existing connections
  3. Properly close all output files
  4. Clean up resources

## Notes

- Make sure the number of ports specified matches or exceeds the number of unique source IPs in your pcap file
- The server must be running before starting pcap2stream
- Each port can handle multiple connections over time, but data from all connections to the same port goes to the same output file
- Files are opened in binary mode to preserve exact data
- When using containers, ensure the ports are properly exposed and mapped
- Output directory permissions must allow writing when using containers