# PCAP Source Actor for ERSAP

This project provides an ERSAP source actor for reading PCAP files and integrating them into ERSAP data processing pipelines.

## Overview

The PCAP Source Actor reads network packet data from PCAP files and makes it available to downstream ERSAP processing actors. It supports standard PCAP files as well as the modified format used in CLAS12 data.

## Components

### PcapFileReader

The `PcapFileReader` class is responsible for reading and parsing PCAP files. It handles:

- PCAP file header parsing
- Packet reading with proper byte order handling
- Support for standard and CLAS12-specific PCAP formats
- Resource management and statistics tracking

### PcapFileSourceEngine

The `PcapFileSourceEngine` class is an ERSAP source engine that integrates the `PcapFileReader` with the ERSAP framework. It extends `AbstractEventReaderService` and implements the necessary methods to:

- Create and manage the reader
- Read packets from the PCAP file
- Provide byte order information
- Convert packets to the appropriate ERSAP data type

## Prerequisites

- Java 11 or higher
- Gradle 7.0 or higher
- ERSAP framework

## Building

```bash
gradle build
```

## Running

### Testing the PcapFileReader

To test the PcapFileReader functionality without the ERSAP framework:

```bash
gradle runTest -PpcapFile=/path/to/your/file.pcap -PmaxPackets=10
```

This will read the specified PCAP file and display information about the first 10 packets.

### As an ERSAP Source Engine

```bash
gradle runSourceEngine -PpcapFile=/path/to/your/file.pcap
```

## Integration with ERSAP

To use this source actor in an ERSAP data processing pipeline:

1. Add the actor to your ERSAP service configuration:

```json
{
  "services": [
    {
      "class": "org.jlab.ersap.actor.pcap.engine.PcapFileSourceEngine",
      "name": "PcapSource"
    },
    {
      "class": "your.processing.actor.Class",
      "name": "Processor"
    },
    {
      "class": "org.jlab.epsci.ersap.std.services.StreamWriter",
      "name": "Writer"
    }
  ],
  "io-services": [
    {
      "class": "org.jlab.ersap.actor.pcap.engine.PcapFileSourceEngine",
      "name": "PcapSource"
    }
  ]
}
```

2. Connect the source actor to your processing actors in the ERSAP orchestrator.

## Data Flow

1. The `PcapFileSourceEngine` reads packets from the PCAP file using the `PcapFileReader`.
2. Each packet is converted to a byte array and published as an ERSAP event.
3. Downstream ERSAP actors can process these events according to their implementation.

## License

This project is licensed under the Apache License 2.0. 