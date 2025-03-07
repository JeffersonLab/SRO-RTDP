# PCAP Stream Processing Workflow

This project implements a complete ERSAP-based workflow for processing PCAP data streams.

## Components

1. **Source Engine**: Reads PCAP data from a socket stream
2. **Processor Engine**: Processes the PCAP data, extracting information
3. **Sink Engine**: Writes the processed data to a destination

## Building

```bash
./gradlew build
```

## Running

```bash
./scripts/run_pcap_workflow.sh
```

See the full documentation for more details.