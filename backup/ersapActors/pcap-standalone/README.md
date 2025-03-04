# PCAP Standalone Reader

A standalone Java application for reading and analyzing PCAP (Packet Capture) files, specifically designed to work with CLAS12 data formats.

## Overview

This project provides a simple, efficient PCAP file reader that doesn't depend on the ERSAP framework. It can:

- Read standard PCAP files
- Handle special CLAS12 PCAP file formats
- Analyze Ethernet, IPv4, TCP, and UDP headers
- Process packets at high speed (150,000+ packets per second)
- Display detailed packet information

## Prerequisites

- Java 11 or higher
- Gradle 7.0+ (or use the included Gradle wrapper)

## Building the Project

To build the project, run:

```bash
./gradlew build
```

## Running the Application

You can run the application using Gradle:

```bash
./gradlew runPcapReader
```

By default, it will try to read the PCAP file at `/scratch/jeng-yuantsai/CLAS12_ECAL_PCAL_DC_2024-05-15_17-12-30.pcap`.

### Command-line Options

You can customize the execution with the following parameters:

- `-Ppcap=<path>`: Specify the path to the PCAP file
- `-Pmax=<number>`: Maximum number of packets to process (0 for all)
- `-Pdisplay=<number>`: Number of packets to display in detail (0 for none)

Example:

```bash
./gradlew runPcapReader -Ppcap=/path/to/your/file.pcap -Pmax=1000 -Pdisplay=5
```

## Features

### Packet Analysis

The PcapReader can analyze:

- Ethernet headers (source/destination MAC, EtherType)
- IPv4 headers (version, length, protocol, source/destination IP)
- TCP headers (ports, sequence numbers, flags, window size)
- UDP headers (ports, length)

### Performance

The reader is optimized for performance, capable of processing hundreds of thousands of packets per second on modern hardware.

## Implementation Details

The main class is `PcapReader`, which implements `AutoCloseable` for proper resource management. Key methods include:

- `readNextPacket()`: Reads the next packet from the PCAP file
- `analyzePacket()`: Analyzes a packet and returns a string representation of its headers
- `main()`: Entry point that demonstrates the functionality

## License

This project is provided as-is with no warranty. Use at your own risk. 