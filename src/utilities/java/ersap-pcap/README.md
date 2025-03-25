# ERSAP PCAP Pipeline

This project implements an ERSAP pipeline for processing multiple data streams from pcap2streams. The pipeline consists of:

1. Source: Reads data from multiple pcap2streams sockets
2. Processor: Simple data processing component
3. Sink: Data storage/visualization component

## Prerequisites

- Docker and Docker Compose
- Visual Studio Code with Remote - Containers extension
- pcap2streams running and configured

## Installation

### 1. Set Up Development Environment

First, set up the development environment using VS Code DevContainer:

1. Open VS Code
2. Navigate to and open the `SRO-RTDP` directory
3. Press F1 and type "Dev Containers: Open Folder in Container"

The container will build and set up the environment automatically, including:
- Java 17 (OpenJDK)
- Gradle 8.1.1
- All required dependencies

> **Note**: The devcontainer configuration is referenced from the jupyter notebook in [ersap-e2sar](https://github.com/JeffersonLab/ersap-e2sar) project.

### 2. Install ERSAP Java Framework

After the container is ready, install the ERSAP Java framework:

```bash
cd /workspaces/ersap-actors/src/utilities/java
chmod +x build_ersap-java.sh
./build_ersap-java.sh
```

The script will:
- Set up ERSAP_HOME in your home directory
- Clone required repositories (ersap-java and ersap-actor)
- Build and install both repositories
- Copy necessary JAR files to ERSAP_HOME/lib

### 3. Install PCAP Pipeline

Finally, install the PCAP pipeline:

```bash
cd ersap-pcap
chmod +x scripts/install_pcap_pipeline.sh
./scripts/install_pcap_pipeline.sh
```

## Usage

### 1. Test the Setup

Before running the full pipeline, you can test the setup using the test script:

```bash
./scripts/test_pcap2streams.sh
```

This script will:
- Verify the ERSAP installation
- Test basic connectivity
- Validate configuration files

### 2. Run the PCAP Pipeline

To run the full PCAP pipeline:

```bash
./scripts/run_pcap_pipeline.sh
```

The script will:
- Start pcap2streams which creates ip-based-config.json
- Generate pcap_sockets.txt from the pcap2streams configuration
- Start the ERSAP actors in the correct order
- Monitor the pipeline status
- Handle logging and error reporting

### Configuration Files

The pipeline uses two important configuration files:

1. `pcap2streams/ip-based-config.json`
   - Created by pcap2streams during runtime
   - Contains the mapping between IP addresses and their corresponding socket configurations

2. `pcap-actors/input/pcap_sockets.txt`
   - Generated from ip-based-config.json
   - Contains the list of socket connections for the ERSAP actors
   - Used by PcapSource to connect to the pcap2streams sockets
   - Format: One socket configuration per line (IP:PORT)

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
src/utilities/java/
├── pcap2streams/         # PCAP to streams conversion layer
│   ├── ip-based-config.json  # Runtime configuration for pcap2streams
│   └── src/             # Source code for pcap2streams
├── ersap-pcap/          # ERSAP actors implementation
│   ├── pcap-actors/     # Main actors implementation
│   │   ├── input/      # Input configurations
│   │   │   └── pcap_sockets.txt  # Generated socket configurations
│   │   └── src/       # Source code for ERSAP actors
│   ├── scripts/        # Build and deployment scripts
│   │   ├── install_pcap_pipeline.sh  # Installation script
│   │   ├── run_pcap_pipeline.sh     # Main pipeline runner
│   │   └── test_pcap2streams.sh     # Test script
│   └── README.md      # This file
└── build_ersap-java.sh # ERSAP Java framework build script
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