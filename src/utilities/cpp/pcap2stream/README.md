# PCAP Stream Processing Tools

This project contains two complementary tools for processing and streaming PCAP file contents:

1. **pcap2stream (sender)** - A utility that reads pcap files and streams network payloads
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

## Building

Both components need to be built separately:

### Building the Server
```bash
cd server
mkdir build && cd build
cmake ..
make
```

### Building the Sender
```bash
cd sender
mkdir build && cd build
cmake ..
make
```

## Quick Start

1. Start the server:
```bash
cd server/build
./stream_server 0.0.0.0 5000 3
```

2. In another terminal, run the sender:
```bash
cd sender/build
./pcap2stream capture.pcap 127.0.0.1 5000
```

## Documentation

For detailed information about each component:
- See [sender/README.md](sender/README.md) for pcap2stream documentation
- See [server/README.md](server/README.md) for stream_server documentation