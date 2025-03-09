# PCAP Processing Workflow

This project implements a workflow for processing PCAP (Packet Capture) files using ERSAP (Event-based Reconstruction Software Architecture for Physics Analysis).

## Overview

The workflow reads packets from a PCAP file using the `pcap2streams` tool, which creates socket servers for each unique IP address found in the PCAP file. The ERSAP orchestrator then connects to these servers, reads the packets, and processes them.

## Components

1. **pcap2streams**: A tool that reads a PCAP file and creates socket servers for each unique IP address.
2. **ERSAP Services**:
   - `PcapSourceService`: Connects to the socket servers and reads packets.
   - `PcapProcessorService`: Processes and filters packets based on protocol and IP address.
   - `PcapSinkService`: Writes processed packets to output files.
3. **ERSAP Orchestrator**: Manages the execution of ERSAP services and coordinates the data flow between them.

## How It Works

1. The `pcap2streams` tool reads a PCAP file and creates socket servers for each unique IP address.
2. The ERSAP Orchestrator starts and manages the services defined in the configuration file.
3. The `PcapSourceService` connects to the socket servers and reads packets.
4. The packets are processed by the `PcapProcessorService` and filtered based on protocol and IP address.
5. The processed packets are written to output files by the `PcapSinkService`.

## Running the Workflow

### Using ERSAP Orchestration

We've created several scripts to simplify running the ERSAP orchestration workflow:

```bash
cd /workspace/src/utilities/java/pcap-ersap
chmod +x scripts/rebuild_ersap.sh scripts/run_ersap_orchestrator.sh scripts/fix_package_structure.sh scripts/fix_imports.sh
./scripts/run_ersap_orchestrator.sh
```

This script performs the following steps:

1. Sets up the ERSAP environment variables
2. Rebuilds the ERSAP environment using `rebuild_ersap.sh`
3. Fixes package structure and imports using `fix_package_structure.sh` and `fix_imports.sh`
4. Checks if `pcap2streams` is running and starts it if needed
5. Compiles the application using Gradle
6. Verifies the JAR files
7. Starts the ERSAP orchestrator with the configuration file
8. Waits for processing to complete
9. Checks the output files
10. Stops `pcap2streams` if it was started by the script

#### Script Details

- **rebuild_ersap.sh**: Rebuilds the ERSAP environment from scratch, including:
  - Creating necessary classes and interfaces (Orchestrator, IEngine, EngineData)
  - Compiling Java files
  - Creating JAR files
  - Setting up the environment

- **fix_package_structure.sh**: Fixes the package structure of Java files, ensuring they are in the correct directories based on their package declarations.

- **fix_imports.sh**: Fixes imports in service classes to ensure they use the correct packages.

- **run_ersap_orchestrator.sh**: The main script that orchestrates the entire workflow.

## Configuration

The workflow is configured using the following files:

- `/workspace/src/utilities/java/pcap2streams/custom-config/ip-based-config.json`: Configures the `pcap2streams` tool.
- `/workspace/src/utilities/java/pcap-ersap/config/pcap-services.yaml`: Configures the ERSAP services.

### ERSAP Orchestrator Configuration

The ERSAP Orchestrator is configured to:

1. Connect to `pcap2streams` socket servers for each IP address (default ports 9000, 9001, 9002)
2. Process packets from these connections
3. Write packet data to output files in the `/workspace/src/utilities/java/pcap-ersap/output` directory

## Output

The workflow generates output files in the `/workspace/src/utilities/java/pcap-ersap/output` directory. Each file contains information about the packets processed, including:

- Packet ID
- Source IP
- Destination IP
- Protocol
- EtherType
- Data length
- Timestamp

Example output:
```
PacketEvent{packetId=0, sourceIp='192.168.10.1', destinationIp='unknown', protocol='unknown', etherType=0x0, dataLength=16384, timestamp=1741548286471}
```

## Troubleshooting

- **"Invalid packet length" warnings**: This is normal. The `pcap2streams` tool may send data that doesn't match the expected packet format. These packets are skipped by the workflow.
- **No output files**: Check that the `pcap2streams` tool is running and that the configuration files are correct.
- **ERSAP orchestrator fails to start**: Check that all the necessary JAR files are present in the correct locations.
- **Compilation errors**: Run the `fix_package_structure.sh` and `fix_imports.sh` scripts to ensure the code is structured correctly.
- **Socket connection errors**: Ensure that `pcap2streams` is running and listening on the expected ports.

## Monitoring

You can monitor the ERSAP orchestrator by:

1. Checking the log output for messages from the Orchestrator
2. Examining the output directory for new files
3. Using system tools to monitor network connections (e.g., `netstat -an | grep 900`)

## Documentation

For more detailed information about the ERSAP orchestration architecture and scripts, please refer to the following documentation:

- [ERSAP Orchestration Architecture](docs/ERSAP_ORCHESTRATION.md): Detailed explanation of the ERSAP orchestration architecture.
- [Script Reference](docs/SCRIPT_REFERENCE.md): Quick reference guide for the scripts used in the ERSAP orchestration workflow.

## Future Work

- Improve packet parsing to handle different packet formats
- Implement a full service chain where packets flow through multiple services
- Add more configuration options to the orchestrator
- Improve error handling and recovery mechanisms
- Add monitoring and metrics collection
- Support for real-time packet processing 