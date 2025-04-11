# PCAP Stream Processing Tools

This project contains two complementary tools for processing and streaming PCAP file contents:

1. **pcap2stream (sender)** - A utility that reads pcap files and streams network payloads to TCP endpoints
2. **stream_server** - A multi-port TCP server that receives and saves network streams

## Project Structure

```
.
├── sender/             # pcap2stream (sender) component
│   ├── pcap2stream.cc # Main sender implementation
│   ├── CMakeLists.txt # Build configuration for sender
│   └── README.md      # Sender documentation
│
└── server/            # stream_server component
    ├── stream_server.cc # Main server implementation
    ├── CMakeLists.txt  # Build configuration for server
    └── README.md       # Server documentation
```

## Building and Running

There are three ways to build and run each component:
1. Native binary
2. Docker container
3. Apptainer/Singularity container

### 1. Native Binary

#### Building the Server
```bash
cd server
mkdir build && cd build
cmake ..
make
```

#### Building the Sender
```bash
cd sender
mkdir build && cd build
cmake ..
make
```

### 2. Docker Container

Use docker-compose for the easiest setup:
```bash
# Start the server
docker-compose up stream-server

# In another terminal, run the sender
docker-compose run pcap-sender /app/pcap/capture.pcap stream-server 5000
```

### 3. Apptainer/Singularity Container

Use the provided build script:
```bash
# Build both components
./build_apptainer.sh all

# Or build individually
./build_apptainer.sh server
./build_apptainer.sh sender
```

## Quick Start

1. Start the server:
```bash
# Native
cd server/build
./stream_server 0.0.0.0 5000 3

# Docker
docker-compose up stream-server

# Apptainer
apptainer run server/stream_server.sif 0.0.0.0 5000 3
```

2. In another terminal, run the sender:
```bash
# Native
cd sender/build
./pcap2stream capture.pcap 127.0.0.1 5000

# Docker
docker-compose run pcap-sender /app/pcap/capture.pcap stream-server 5000

# Apptainer
apptainer run sender/pcap2stream.sif capture.pcap 127.0.0.1 5000
```

## Features

### Sender (pcap2stream)
- Processes PCAP files and extracts network payloads
- Creates separate TCP connections for each unique source IP
- Preserves packet timing information
- Thread-safe packet queuing
- Graceful shutdown handling

### Server (stream_server)
- Multi-port TCP server for receiving network streams
- Handles multiple concurrent connections
- Saves received data to separate binary files
- Real-time connection status logging
- Automatic port management
- Clean shutdown with resource cleanup

## Documentation

For detailed information about each component:
- See [sender/README.md](sender/README.md) for pcap2stream documentation
- See [server/README.md](server/README.md) for stream_server documentation

## Notes

- The server must be running before starting pcap2stream
- Each unique source IP will get its own dedicated TCP connection
- Make sure the server has enough ports allocated for all unique source IPs
- When using containers, ensure proper volume mounting for accessing files
- Base port number should match between server and sender configuration