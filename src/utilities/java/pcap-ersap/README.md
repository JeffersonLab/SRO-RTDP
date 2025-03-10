# PCAP-ERSAP

This project provides a framework for implementing **ERSAP**-compliant data streaming applications for PCAP (Packet Capture) data. The project leverages ERSAP to structure computation into **source**, **processor**, and **sink** actors, each represented by an engine. By composing these actors, users can build scalable, streaming data pipelines.

## Repository Structure

The codebase is organized under the `pcap` directory and is divided into several primary subdirectories:

- **engine/**  
  Contains implementations of ERSAP engines. These engines serve as building blocks (actors) within the ERSAP framework. They are specialized into three major categories:
    1. **Source Engine**: Implements a socket-based data source actor that feeds streaming data into the pipeline.
    2. **Processor Engine**: Provides a processor engine that processes PCAP data.
    3. **Sink Engine**: Provides a sink engine that writes processed data to files.

- **proc/**  
  Contains the processing logic for PCAP data. The processor engines use classes implementing the `IEProcessor` interface to encapsulate the processing logic.

- **source/**  
  Contains the concrete implementations of source actors and their underlying connection-handling logic.

- **data/**  
  Contains data classes used throughout the application, such as `PacketEvent`.

## ERSAP Actor Model and Engine Interfaces

ERSAP categorizes actors into three types:

- **Source Actors**: Data producers that feed data into the pipeline.
- **Processor Actors**: Intermediate computation units that transform or analyze the incoming data stream.
- **Sink Actors**: Data consumers that handle output or storage operations.

## Engine Implementations

- **PcapSourceEngine**:  
  Implements the `Engine` interface, allowing ERSAP to present this engine as a source actor. It reads PCAP data from a socket connection and passes it to the next actor in the pipeline.

- **PcapProcessorEngine**:  
  Implements the `Engine` interface, allowing ERSAP to present this engine as a processor actor. It processes PCAP data and passes the processed data to the next actor in the pipeline.

- **PcapSinkEngine**:  
  Implements the `Engine` interface, allowing ERSAP to present this engine as a sink actor. It writes processed data to files.

## Source Components

- **AbstractConnectionHandler**:  
  Abstract base class for connection handlers. It provides the basic structure for handling connections to data sources and publishing events to a ring buffer.

- **SocketConnectionHandler**:  
  Concrete implementation of `AbstractConnectionHandler` that handles socket connections. It reads data from a socket and publishes it to a ring buffer.

- **Event**:  
  Class used by the LMAX Disruptor ring buffer. It represents an event in the ring buffer and holds the data payload for the event.

- **PcapStreamReceiver**:  
  Implements the `IESource` interface and provides a way to receive PCAP data from a socket connection.

## Configuration

The application is configured using a YAML file (`services.yaml`) that specifies the engines to use and their configuration parameters. The configuration parameters include:

- **streamHost**: The host name or IP address of the PCAP data source.
- **streamPort**: The port number of the PCAP data source.
- **ringBufferSize**: The size of the ring buffer used for passing events between threads.
- **socketBufferSize**: The size of the socket buffer used for reading data from the socket.
- **connectionTimeout**: The connection timeout in milliseconds.
- **readTimeout**: The read timeout in milliseconds.
- **outputDir**: The directory where output files will be written.

## Building and Running

1. Build the project using Gradle:

```
./gradlew build
```

2. Run the application using the ERSAP shell:

```
$ERSAP_HOME/bin/ersap-shell
```

3. Deploy the services:

```
ersap> deploy config.yaml
```

4. Start the services:

```
ersap> start
```

## Dependencies

- ERSAP: The ERSAP framework provides the infrastructure for building streaming data pipelines.
- LMAX Disruptor: Used for high-performance inter-thread communication.
- JSON: Used for parsing configuration files.
- SnakeYAML: Used for parsing YAML configuration files.

## License

This project is licensed under the Apache License 2.0. 