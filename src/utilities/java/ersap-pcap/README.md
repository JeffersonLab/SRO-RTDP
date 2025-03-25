# ERSAP PCAP Pipeline

This project implements an ERSAP pipeline for processing multiple data streams from pcap2streams. The pipeline consists of:

1. Source: Reads data from multiple pcap2streams sockets
2. Processor: Simple data processing component
3. Sink: Data storage/visualization component

## Service Descriptions

### PcapSource
- Connects to multiple pcap2streams sockets based on configuration
- Reads raw packet data from each socket
- Handles socket connection management and reconnection
- Supports configuration of socket IPs, ports, and timeouts
- Outputs raw packet data in binary format

### PacketProcessor
- Processes raw packet data from PcapSource
- Performs protocol analysis (TCP/UDP)
- Extracts packet metadata (timestamps, sizes, ports)
- Supports packet filtering and analysis
- Outputs processed packet data with metadata

### PacketSink
- Receives processed packet data from PacketProcessor
- Writes packet data to CSV files organized by source IP
- CSV files include:
  - Timestamp
  - Packet size
  - Protocol (TCP/UDP)
  - Source and destination IPs
  - Source and destination ports
  - Header and payload sizes
- Supports automatic file rotation and flushing
- Handles multiple concurrent writers for different IPs

### Socket Processing Model
The PcapSource uses a single-threaded, round-robin approach for processing multiple sockets:
- Single thread sequentially processes all configured sockets
- Reads from one socket at a time in a rotating fashion
- If no packet is available from current socket, moves to next socket
- After checking all sockets, waits 100ms before retrying
- Maintains only one active connection at a time

This design has the following characteristics:
- Pros:
  - Simple implementation
  - Lower resource usage
  - No thread synchronization needed
- Cons:
  - Potential for missed packets if processing one socket takes too long
  - Higher latency when switching between sockets
  - Less parallel processing capability

### MIME Types and Data Flow

#### PcapSource
Input MIME Types (Configuration):
- `binary/data-jobj`: Java object configuration
- `application/json`: JSON configuration
- `text/string`: String configuration

Output MIME Types:
- `binary/bytes`: Raw packet data
- `sfixed32`: For event count requests

#### PacketProcessor
Input MIME Types:
- `binary/bytes`: Raw packet data from PcapSource
- `binary/data-jobj`: Java object configuration
- `application/json`: JSON configuration

Output MIME Types:
- `binary/data-jobj`: Processed packet metadata in JSON format

#### PacketSink
Input MIME Types:
- `binary/data-jobj`: Processed packet metadata
- `application/json`: JSON metadata

Output:
- CSV files with packet metadata organized by source IP

## Project Structure

```
ersap-pcap/
├── pcap-actors/           # Main actors implementation
├── scripts/              # Build and deployment scripts
└── README.md            # This file
```

## Prerequisites

- Java 11 or later
- Gradle 7.x or later
- ERSAP Java framework
- pcap2streams running and configured

## Building the Project

1. Ensure ERSAP_HOME is set in your environment
2. Run the build script:
   ```bash
   ./scripts/build.sh
   ```

## Configuration

The pipeline can be configured through the ERSAP configuration system. See the configuration files in the `config` directory for examples.

### Configuration Parameters

#### IO Services Configuration

##### PcapSource (reader)
- `SOCKETS_FILE`: Path to file containing pcap2streams socket configurations
- `CONNECTION_TIMEOUT`: Timeout in milliseconds for socket connections (default: 5000)
- `READ_TIMEOUT`: Timeout in milliseconds for socket read operations (default: 30000)
- `BUFFER_SIZE`: Size of the read buffer in bytes (default: 1024)

##### PacketSink (writer)
- `OUTPUT_DIR`: Directory where CSV files will be written (default: "output")
- `FLUSH_INTERVAL`: Interval in milliseconds between file buffer flushes (default: 1000)

#### PacketProcessor Configuration
- `ENABLE_PROTOCOL_ANALYSIS`: Enable/disable protocol analysis (default: true)
- `ENABLE_PORT_ANALYSIS`: Enable/disable port analysis (default: true)
- `LOG_LEVEL`: Logging level (default: FINE)

#### Supported MIME Types
- `binary/bytes`: Raw packet data
- `binary/data-jobj`: Java object data
- `sfixed32`: 32-bit fixed-point data

## Usage

1. Start pcap2streams with the desired configuration
2. Deploy the ERSAP actors using the deployment script
3. Monitor the pipeline through the ERSAP dashboard 