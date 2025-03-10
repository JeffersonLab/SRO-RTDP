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
  - Responsible for connecting to external data sources (socket servers in this case)
  - Handle multiple concurrent connections to different data sources
  - Read raw packet data from network sockets
  - Convert raw data into standardized packet events
  - Manage connection timeouts and reconnection strategies
  - Buffer incoming data using ring buffers for efficient processing
  - Pass data downstream using the MIME type `binary/bytes`

- **Processor Actors**: Intermediate computation units that transform or analyze the incoming data stream.
  - Receive packet data from source actors
  - Apply filtering based on configurable criteria (IP addresses, protocols, etc.)
  - Process and analyze packet contents
  - Extract metadata from packets
  - Perform protocol-specific analysis
  - Transform data into a format suitable for downstream consumption
  - Maintain processing statistics
  - Pass processed data to sink actors using the MIME type `binary/bytes`

- **Sink Actors**: Data consumers that handle output or storage operations.
  - Receive processed data from processor actors
  - Write data to output files, organized by IP address or other criteria
  - Manage file handles and resources
  - Handle file rotation and naming conventions
  - Provide status information using the MIME type `text/plain`
  - Ensure proper cleanup of resources when the application terminates

## Data Flow Between Actors

The ERSAP framework orchestrates the flow of data between actors:

1. **Source → Processor**: 
   - Source actors read from multiple socket connections in parallel
   - Data from all connections converges into a single processing pipeline
   - Packets are passed as binary data with MIME type `binary/bytes`

2. **Processor → Sink**:
   - Processed packets are passed to the sink actor
   - The sink may write to different output files based on packet attributes
   - All processing occurs in a single logical stream

## Data Aggregation Approach

PCAP-ERSAP uses an **asynchronous independent processing** approach for handling multiple data streams, which differs from other ERSAP implementations like PET-SRO that use synchronous aggregation.

### Asynchronous Independent Processing (PCAP-ERSAP)

```
┌─────────────────────────────────────────────────────────────┐
│                     PcapSourceEngine                         │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                 Independent Thread Creation                  │
└─────────────────────────────────────────────────────────────┘
          │                   │                   │
          ▼                   ▼                   ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│  Connection 1 │    │  Connection 2 │    │  Connection 3 │  ...
│    Thread     │    │    Thread     │    │    Thread     │
└───────────────┘    └───────────────┘    └───────────────┘
          │                   │                   │
          ▼                   ▼                   ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│  readPackets  │    │  readPackets  │    │  readPackets  │  ...
└───────────────┘    └───────────────┘    └───────────────┘
          │                   │                   │
          │                   │                   │
          ▼                   ▼                   ▼
     ┌─────────┐         ┌─────────┐         ┌─────────┐
     │ Event A │         │ Event B │         │ Event C │  ...
     └─────────┘         └─────────┘         └─────────┘
          │                   │                   │
          │                   │                   │
          ▼                   ▼                   ▼
┌─────────────────────────────────────────────────────────────┐
│                    ERSAP Framework                           │
│                  (Event Distribution)                        │
└─────────────────────────────────────────────────────────────┘
          │                   │                   │
          ▼                   ▼                   ▼
┌─────────────────────────────────────────────────────────────┐
│                    Processor Engine                          │
└─────────────────────────────────────────────────────────────┘
```

**Key Characteristics:**
- Each connection has its own independent thread
- Events are processed as they arrive
- No waiting for other connections
- Events flow independently to the processor
- Fast connections aren't slowed by slow ones

### Timing Behavior

```
┌─────────────────────────────────────────────────────────────┐
│                     pcap-ersap Timing                        │
└─────────────────────────────────────────────────────────────┘

Time ─────────────────────────────────────────────────────────▶

Stream 1: ─[Event A]───[Event D]───[Event G]──────────────────▶
                │           │           │
                ▼           ▼           ▼
Processor: ─────[Proc A]────[Proc D]────[Proc G]───────────────▶

Stream 2: ─────────[Event B]───────[Event E]───[Event H]──────▶
                       │               │           │
                       ▼               ▼           ▼
Processor: ────────────[Proc B]────────[Proc E]────[Proc H]────▶

Stream 3: ───────────────────[Event C]───────[Event F]─────────▶
                                 │               │
                                 ▼               ▼
Processor: ──────────────────────[Proc C]────────[Proc F]──────▶
```

### Data Flow

```
┌─────────────────────────────────────────────────────────────┐
│                 pcap-ersap Data Flow                         │
└─────────────────────────────────────────────────────────────┘

┌───────────┐     ┌───────────┐     ┌───────────┐
│  Stream 1 │     │  Stream 2 │     │  Stream 3 │
└─────┬─────┘     └─────┬─────┘     └─────┬─────┘
      │                 │                 │
      │                 │                 │
      ▼                 ▼                 ▼
┌─────────┐       ┌─────────┐       ┌─────────┐
│ Thread 1│       │ Thread 2│       │ Thread 3│
└─────┬───┘       └─────┬───┘       └─────┬───┘
      │                 │                 │
      │                 │                 │
      ▼                 ▼                 ▼
┌─────────┐       ┌─────────┐       ┌─────────┐
│ Event A │       │ Event B │       │ Event C │
└─────┬───┘       └─────┬───┘       └─────┬───┘
      │                 │                 │
      │                 │                 │
      ▼                 ▼                 ▼
┌───────────────────────────────────────────┐
│              ERSAP Framework              │
│         (asynchronous distribution)       │
└─────────────────┬─────────────────────────┘
                  │
                  │
                  ▼
┌───────────────────────────────────────────┐
│           Processor Engine                │
│    (processes individual events)          │
└───────────────────────────────────────────┘
```

### Benefits of Asynchronous Processing for PCAP Data

This asynchronous approach is particularly well-suited for PCAP data processing because:

1. **Maximized Throughput**: Processes data as soon as it arrives without waiting for slower connections
2. **Efficient Resource Utilization**: Doesn't waste time checking connections that have no data
3. **Scalability**: Can handle connections with different data rates without bottlenecks
4. **Fault Tolerance**: Issues with one connection don't affect processing of others
5. **Real-time Processing**: Enables immediate processing of time-sensitive network packets

This differs from synchronous aggregation approaches (like those used in PET-SRO) where events from all streams are collected into a single array before processing, which is better suited for applications requiring correlation between events from different sources.

### Comparison with Synchronous Aggregation (PET-SRO)

For comparison, here's how a synchronous aggregation approach (as used in PET-SRO) works:

```
┌─────────────────────────────────────────────────────────────┐
│                     Synchronous Aggregation                  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                        nextEvent()                           │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│  ┌───────────┐     ┌───────────┐     ┌───────────┐          │
│  │ Stream 1  │     │ Stream 2  │     │ Stream 3  │   ...    │
│  │ Receiver  │     │ Receiver  │     │ Receiver  │          │
│  └───────────┘     └───────────┘     └───────────┘          │
│        │                 │                 │                 │
│        ▼                 ▼                 ▼                 │
│  ┌───────────┐     ┌───────────┐     ┌───────────┐          │
│  │ nextEvent │     │ nextEvent │     │ nextEvent │   ...    │
│  └───────────┘     └───────────┘     └───────────┘          │
│        │                 │                 │                 │
│        ▼                 ▼                 ▼                 │
│  ┌───────────┐     ┌───────────┐     ┌───────────┐          │
│  │  Event 1  │     │  Event 2  │     │  Event 3  │   ...    │
│  └───────────┘     └───────────┘     └───────────┘          │
│                                                             │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      Combined Array                          │
│  ┌───────────┬───────────┬───────────┬─────┬───────────┐    │
│  │  Event 1  │  Event 2  │  Event 3  │ ... │  Event N  │    │
│  └───────────┴───────────┴───────────┴─────┴───────────┘    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Processor Engine                          │
└─────────────────────────────────────────────────────────────┘
```

**Timing Comparison:**

```
┌─────────────────────────────────────────────────────────────┐
│                      Synchronous Timing                      │
└─────────────────────────────────────────────────────────────┘

Time ─────────────────────────────────────────────────────────▶

Stream 1: ─[Event 1]───────────────────────────────────────────▶
Stream 2: ─────────────[Event 2]─────────────────────────────▶
Stream 3: ───────────────────────────────[Event 3]───────────▶
                                                │
                                                ▼
Processor: ────────────────────────────────────[Process All]─▶
```

#### Key Differences Between Approaches

| Feature | PCAP-ERSAP (Asynchronous) | PET-SRO (Synchronous) |
|---------|---------------------------|------------------------|
| Aggregation Type | Implicit through ERSAP framework | Explicit collection into arrays |
| Processing Model | Stream processing | Batch processing |
| Event Handling | Individual events | Combined arrays |
| Timing Dependency | Independent per stream | Dependent on all streams |
| Blocking Behavior | Non-blocking between streams | Blocks on slowest stream |
| Optimization For | High throughput, independent events | Correlated event processing |
| Use Case | Network packet processing | PET detector correlation |

The choice between these approaches depends on the specific requirements of the application:

- **PCAP-ERSAP's approach** is ideal for network packet processing where individual packets can be processed independently and maximizing throughput is critical.
  
- **Synchronous aggregation** is better suited for applications like PET imaging where events from different detectors need to be correlated and processed together.

## Engine Implementations

- **PcapSourceEngine**:  
  Implements the `Engine` interface, allowing ERSAP to present this engine as a source actor. It reads PCAP data from socket connections and passes it to the next actor in the pipeline.
  - Configurable via JSON with parameters for connections, buffer sizes, and timeouts
  - Manages multiple concurrent socket connections to different data sources
  - Creates a separate thread for each connection to enable parallel data ingestion
  - Uses socket timeouts to handle connection issues gracefully
  - Buffers incoming data using configurable buffer sizes
  - Accepts configuration with MIME type `application/json`
  - Outputs raw packet data with MIME type `binary/bytes`
  - Implements proper resource cleanup on shutdown

- **PcapProcessorEngine**:  
  Implements the `Engine` interface, allowing ERSAP to present this engine as a processor actor. It processes PCAP data and passes the processed data to the next actor in the pipeline.
  - Configurable via JSON with parameters for filtering criteria
  - Receives raw packet data with MIME type `binary/bytes`
  - Uses the `PcapProcessor` class to implement the actual processing logic
  - Can filter packets based on IP addresses, protocols, or other criteria
  - Processes packets in a streaming fashion without blocking
  - Maintains the binary format of the data for efficient processing
  - Outputs processed packet data with MIME type `binary/bytes`

- **PcapSinkEngine**:  
  Implements the `Engine` interface, allowing ERSAP to present this engine as a sink actor. It writes processed data to files.
  - Configurable via JSON with parameters for output directory
  - Receives processed packet data with MIME type `binary/bytes`
  - Creates and manages output files based on packet attributes (e.g., IP address)
  - Uses buffered writers for efficient file I/O
  - Handles file creation and resource management
  - Provides status information with MIME type `text/plain`
  - Ensures proper file closure on shutdown

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

The application is configured using a YAML file (`services.yaml`) that specifies the engines to use and their configuration parameters. The configuration is structured into sections for each actor type:

### Source Engine Configuration
- **connections**: List of socket connections to read PCAP data from
  - **ip**: IP address of the socket server (used for identification)
  - **host**: Host name used for connection establishment
  - **port**: Port number to connect to
- **ringBufferSize**: Size of the internal ring buffer for packet processing (number of slots)
- **socketBufferSize**: Size of the socket buffer in bytes for receiving packets
- **connectionTimeout**: Timeout in milliseconds for establishing a connection
- **readTimeout**: Timeout in milliseconds for reading data from the socket

### Processor Engine Configuration
- **filter**: Optional filter expression to select specific packets (e.g., "ip.src == 192.168.10.1")

### Sink Engine Configuration
- **outputDir**: Directory where processed packets will be written

### MIME Types
The configuration also specifies the supported MIME types for data exchange between services:
- **binary/bytes**: Raw binary data used for packet transfer
- **binary/data-jobj**: Java serialized objects for structured data
- **text/plain**: Plain text data for status messages and logging

### Example Configuration
```yaml
io-services:
  reader:
    class: org.jlab.ersap.actor.pcap.engine.PcapSourceEngine
    name: Source
  writer:
    class: org.jlab.ersap.actor.pcap.engine.PcapSinkEngine
    name: Sink
services:
  - class: org.jlab.ersap.actor.pcap.engine.PcapProcessorEngine
    name: Processor
configuration:
  io-services:
    reader:
      connections:
        - ip: "192.168.10.1"
          host: "localhost"
          port: 9000
      ringBufferSize: 1024
      socketBufferSize: 1024
      connectionTimeout: 5000
      readTimeout: 30
    writer:
      outputDir: "output"
  services:
    Processor:
      filter: "ip.src == 192.168.10.1"
```

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