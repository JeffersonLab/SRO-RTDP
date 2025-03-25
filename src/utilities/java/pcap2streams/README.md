# Pcap2Streams

Pcap2Streams is a Java application that analyzes a PCAP file, identifies unique IP addresses, and creates separate socket servers for each IP address. Each server streams only the packets related to its assigned IP address.

## Overview

The application consists of the following components:

1. **PcapIPAnalyzer**: Analyzes a PCAP file to identify unique IP addresses and their associated packets.
2. **IPBasedPcapServer**: A server that streams PCAP packets for a specific IP address.
3. **Pcap2Streams**: The main application that orchestrates the analysis and server creation.
4. **IPBasedStreamClient**: A client that connects to the IP-based servers and receives packets.
5. **PcapPacketAnalyzer**: Handles detailed packet analysis and processing.

## Project Structure

```
pcap2streams/
├── src/
│   └── main/
│       └── java/
│           └── org/
│               └── jlab/
│                   └── ersap/
│                       └── actor/
│                           └── pcap2streams/
│                               ├── Pcap2Streams.java
│                               ├── PcapIPAnalyzer.java
│                               ├── IPBasedPcapServer.java
│                               ├── IPBasedStreamClient.java
│                               └── PcapPacketAnalyzer.java
├── lib/
│   ├── json-20231013.jar
│   ├── disruptor-3.4.4.jar
│   └── snakeyaml-2.0.jar
├── scripts/
├── custom-config/
├── output/
└── pcap_analysis/
```

## Prerequisites

- Java 11 or higher
- A PCAP file containing network packet data
- Required dependencies (included in `lib/` directory):
  - JSON library (json-20231013.jar)
  - Disruptor framework (disruptor-3.4.4.jar)
  - SnakeYAML (snakeyaml-2.0.jar)

## Building

To build the project, run:

```bash
cd pcap2streams
javac -d build/classes/java/main -cp "lib/json-20231013.jar:lib/disruptor-3.4.4.jar:lib/snakeyaml-2.0.jar" src/main/java/org/jlab/ersap/actor/pcap2streams/*.java
```

## Running

### Starting the Servers

To start the servers, run:

```bash
./scripts/run_pcap2streams.sh [pcap_file]
```

If no PCAP file is specified, it will use the default file path.

This will:
1. Analyze the PCAP file to identify unique IP addresses
2. Create a separate server for each IP address
3. Generate a configuration file at `custom-config/ip-based-config.json`

### Running the Client

To run the client, run:

```bash
./scripts/run_client.sh [config_file]
```

If no configuration file is specified, it will use the default file at `custom-config/ip-based-config.json`.

The client will:
1. Connect to all servers specified in the configuration file
2. Receive packets from each server
3. Process the packets using the PcapPacketAnalyzer
4. Run for 60 seconds and then display statistics

## Configuration

The configuration file is a JSON file with the following structure:

```json
{
  "connections": [
    {
      "ip": "192.168.1.1",
      "host": "localhost",
      "port": 9000,
      "connection_timeout": 5000,
      "read_timeout": 30000,
      "buffer_size": 1024,
      "packet_count": 1000
    }
  ]
}
```

## Features

- Efficient packet analysis using the Disruptor framework for high-performance processing
- YAML configuration support for flexible deployment
- Detailed packet analysis with PcapPacketAnalyzer
- Separate output and analysis directories for better organization
- Configurable connection parameters for each IP-based server

## ERSAP Integration

This application is designed to integrate with the ERSAP framework:

1. Uses the standard ERSAP actor package structure
2. Generates configuration files compatible with ERSAP services
3. Provides packet streaming capabilities for ERSAP data processing pipelines

## Troubleshooting

### Connection Issues

If you encounter connection issues:

1. Ensure that the servers are running
2. Check that the ports are not being used by other applications
3. Verify that the configuration file has the correct host and port information
4. Check the output directory for any error logs

### PCAP File Issues

If you encounter issues with the PCAP file:

1. Ensure that the file exists and is readable
2. Verify that the file is a valid PCAP file
3. Check that the file contains IP packets (IPv4)
4. Review the pcap_analysis directory for detailed analysis results

## License

This project is licensed under the MIT License - see the LICENSE file for details. 