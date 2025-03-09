# ERSAP Orchestration Architecture

This document provides a detailed explanation of the ERSAP (Event-based Reconstruction Software Architecture for Physics Analysis) orchestration architecture used in the PCAP processing workflow.

## Overview

ERSAP is a service-oriented architecture designed for high-performance data processing. It provides a framework for building distributed data processing applications using a service-oriented approach. The ERSAP orchestration architecture consists of the following components:

1. **Orchestrator**: Manages the execution of services and coordinates the data flow between them.
2. **Services**: Perform specific data processing tasks.
3. **Data Flow**: Defines how data moves between services.
4. **Configuration**: Specifies the services to run and how they are connected.

## Orchestrator

The Orchestrator is the central component of the ERSAP architecture. It is responsible for:

- Starting and stopping services
- Managing the data flow between services
- Monitoring the execution of services
- Handling errors and exceptions

In our implementation, the Orchestrator is implemented in the `org.jlab.epsci.ersap.sys.Orchestrator` class. It reads a configuration file that specifies the services to run and how they are connected, and then starts the services and manages the data flow between them.

### Implementation Details

The Orchestrator implementation includes:

- A thread pool for executing services concurrently
- A connection manager for handling socket connections to data sources
- A packet processing pipeline for processing packets
- A writer manager for writing processed packets to output files

## Services

ERSAP services are the building blocks of the data processing workflow. Each service performs a specific task, such as reading data from a source, processing data, or writing data to a destination.

In our PCAP processing workflow, we have the following services:

1. **PcapSourceService**: Connects to socket servers and reads packets.
2. **PcapProcessorService**: Processes and filters packets based on protocol and IP address.
3. **PcapSinkService**: Writes processed packets to output files.

### Service Interface

All ERSAP services implement the `IEngine` interface, which defines the following methods:

- `configure(EngineData input)`: Configures the service with the provided input data.
- `execute(EngineData input)`: Executes the service with the provided input data.
- `executeGroup(EngineData input)`: Executes the service with a group of input data.
- `reset()`: Resets the service to its initial state.
- `destroy()`: Cleans up resources used by the service.
- `getDescription()`: Returns a description of the service.
- `getName()`: Returns the name of the service.
- `getVersion()`: Returns the version of the service.
- `getAuthor()`: Returns the author of the service.

### Data Flow

Data flows between services using the `EngineData` class, which encapsulates the data being processed and metadata about the data. The `EngineData` class includes:

- The data being processed
- The MIME type of the data
- Metadata about the data
- Status information
- A description of the data

## Configuration

The ERSAP orchestration is configured using a YAML file that specifies the services to run and how they are connected. The configuration file includes:

- The services to run
- The connections between services
- The configuration parameters for each service

### Example Configuration

```yaml
services:
  - class: org.jlab.ersap.actor.pcap.services.PcapSourceService
    name: PcapSource
    inputs:
      - config
    outputs:
      - packets
    configuration:
      ip: 192.168.10.1
      port: 9000

  - class: org.jlab.ersap.actor.pcap.services.PcapProcessorService
    name: PcapProcessor
    inputs:
      - packets
    outputs:
      - processed_packets
    configuration:
      filter: "ip.src == 192.168.10.1"

  - class: org.jlab.ersap.actor.pcap.services.PcapSinkService
    name: PcapSink
    inputs:
      - processed_packets
    outputs:
      - output
    configuration:
      output_dir: /workspace/src/utilities/java/pcap-ersap/output
```

## Execution Flow

The execution flow of the ERSAP orchestration is as follows:

1. The Orchestrator reads the configuration file and creates the services.
2. The Orchestrator establishes connections between services based on the configuration.
3. The Orchestrator starts the services.
4. Data flows from the source services to the processing services to the sink services.
5. The Orchestrator monitors the execution of services and handles errors and exceptions.
6. When the processing is complete, the Orchestrator stops the services and cleans up resources.

## Packet Processing

In our PCAP processing workflow, packets are processed as follows:

1. The `PcapSourceService` connects to socket servers and reads packets.
2. The packets are encapsulated in `EngineData` objects and sent to the `PcapProcessorService`.
3. The `PcapProcessorService` processes and filters the packets based on protocol and IP address.
4. The processed packets are encapsulated in `EngineData` objects and sent to the `PcapSinkService`.
5. The `PcapSinkService` writes the processed packets to output files.

## Error Handling

The ERSAP orchestration includes error handling mechanisms to handle errors and exceptions that occur during execution. These include:

- Logging errors and exceptions
- Retrying failed operations
- Skipping invalid data
- Gracefully shutting down services when errors occur

## Monitoring

The ERSAP orchestration includes monitoring mechanisms to monitor the execution of services. These include:

- Logging service execution
- Tracking the number of packets processed
- Monitoring resource usage
- Detecting and reporting errors and exceptions

## Conclusion

The ERSAP orchestration architecture provides a flexible and scalable framework for building distributed data processing applications. It allows for the composition of services into complex workflows, and provides mechanisms for managing the execution of services, handling errors and exceptions, and monitoring the execution of services.

In our PCAP processing workflow, the ERSAP orchestration architecture enables the efficient processing of packets from multiple sources, with the ability to filter and transform the packets, and write the processed packets to output files. 