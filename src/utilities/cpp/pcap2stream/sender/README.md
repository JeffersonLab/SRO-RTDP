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

## Features

- Thread-safe packet queuing for each source IP
- Asynchronous transmission of payloads
- Automatic connection management
- Preserves packet timing information
- Graceful cleanup on exit

## Testing with Stream Server

1. Start the stream server first:
```bash
./stream_server 0.0.0.0 5000 3
```

2. Then run pcap2stream:
```bash
./pcap2stream capture.pcap 127.0.0.1 5000
```

## Notes

- The server must be running and accepting TCP connections before starting pcap2stream
- Each unique source IP will get its own dedicated TCP connection
- Make sure the server has enough ports allocated to handle all unique source IPs in your pcap file
- The program will automatically close connections when finished or if errors occur