# PCAP Processing Project Summary

## Overview

This project implements a system for processing PCAP (Packet Capture) data, with two main approaches:

1. **Actor-based System**: A system that connects to socket servers created by the `pcap2streams` tool, processes the packet data, and writes the results to output files.
2. **Direct PCAP Processing**: A direct PCAP file processor that can read and process PCAP files without requiring the `pcap2streams` tool.

## Components

### Actor-based System

The actor-based system consists of three main components:

1. **Source Actor**: Connects to socket servers created by `pcap2streams` and reads packet data from multiple IP addresses.
2. **Processor Actor**: Processes the raw packet data to extract Ethernet and IP information, and applies optional filtering.
3. **Sink Actor**: Writes the processed event data to output files.

### Direct PCAP Processing

The direct PCAP processor:

1. Reads a PCAP file directly
2. Processes packets to extract Ethernet and IP information
3. Writes packet information to output files

## Challenges and Solutions

During the development of this project, we encountered several challenges:

1. **Socket Communication**: The `pcap2streams` tool creates socket servers for each IP address, but the communication protocol was not well-documented. We had to experiment with different approaches to read the data correctly.

   **Solution**: We implemented a test client that could connect to the socket servers and read the data directly, which helped us understand the protocol.

2. **PCAP File Format**: The PCAP file format is complex, and we encountered issues with parsing the file correctly.

   **Solution**: We implemented a direct PCAP processor that could read the file and extract the packet information, which helped us understand the format.

3. **Output Generation**: We had issues with generating output files from the actor-based system.

   **Solution**: We implemented a direct approach to writing packet information to files, which helped us verify that the packet processing logic was working correctly.

## Results

We successfully implemented both approaches:

1. **Actor-based System**: The system can connect to socket servers created by `pcap2streams`, process the packet data, and write the results to output files.
2. **Direct PCAP Processing**: The system can read a PCAP file directly, process the packets, and write the information to output files.

The direct PCAP processor successfully processed packets from the PCAP file and wrote them to output files, which contain detailed information about each packet, including:

- Source and destination MAC addresses
- EtherType
- Packet size
- Timestamp

## Future Work

1. **Improve Error Handling**: The current implementation has basic error handling, but it could be improved to handle more edge cases.
2. **Add More Packet Types**: The current implementation focuses on Ethernet and IPv4 packets, but it could be extended to handle more packet types.
3. **Implement Full ERSAP Integration**: The current implementation is a simplified version of the actor system, but it could be extended to use the full ERSAP framework.
4. **Add Visualization**: The current implementation writes packet information to files, but it could be extended to visualize the packet data in real-time.
5. **Distributed Processing**: The current implementation runs on a single machine, but it could be extended to run on multiple machines for distributed processing.

## Conclusion

This project demonstrates two approaches to processing PCAP data: an actor-based system that connects to socket servers, and a direct PCAP processor that reads PCAP files directly. Both approaches have their advantages and can be used depending on the specific requirements of the application. 