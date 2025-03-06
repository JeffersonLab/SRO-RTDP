# Pcap2Streams

Pcap2Streams is a Java application that analyzes a PCAP file, identifies unique IP addresses, and creates separate socket servers for each IP address. Each server streams only the packets related to its assigned IP address.

## Overview

The application consists of the following components:

1. **PcapIPAnalyzer**: Analyzes a PCAP file to identify unique IP addresses and their associated packets.
2. **IPBasedPcapServer**: A server that streams PCAP packets for a specific IP address.
3. **Pcap2Streams**: The main application that orchestrates the analysis and server creation.
4. **IPBasedStreamClient**: A client that connects to the IP-based servers and receives packets.

## How It Works

1. The application reads a PCAP file and analyzes it to identify all unique IP addresses (both source and destination).
2. For each unique IP address, it creates a separate socket server on a different port.
3. Each server streams only the packets related to its assigned IP address.
4. The application generates a configuration file that maps IP addresses to ports.
5. Clients can connect to specific servers to receive packets for specific IP addresses.

## Prerequisites

- Java 11 or higher
- A PCAP file containing network packet data

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
3. Process the packets (currently just counting them)
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
    },
    {
      "ip": "192.168.1.2",
      "host": "localhost",
      "port": 9001,
      "connection_timeout": 5000,
      "read_timeout": 30000,
      "buffer_size": 1024,
      "packet_count": 500
    }
  ]
}
```

## ERSAP Integration

This application can be integrated with the ERSAP framework by:

1. Using the generated configuration file to connect to the IP-based servers
2. Creating an ERSAP service that uses the `IPBasedStreamClient` to receive packets
3. Processing the packets within the ERSAP service

## Troubleshooting

### Connection Issues

If you encounter connection issues:

1. Ensure that the servers are running
2. Check that the ports are not being used by other applications
3. Verify that the configuration file has the correct host and port information

### PCAP File Issues

If you encounter issues with the PCAP file:

1. Ensure that the file exists and is readable
2. Verify that the file is a valid PCAP file
3. Check that the file contains IP packets (IPv4)

## License

This project is licensed under the MIT License - see the LICENSE file for details. 