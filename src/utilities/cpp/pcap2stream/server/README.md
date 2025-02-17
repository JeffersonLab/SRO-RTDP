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
./stream_server 0.0.0.0 5000 3
```

2. Then run pcap2stream with matching parameters:
```bash
./pcap2stream capture.pcap 127.0.0.1 5000
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