# PCAP Stream Source for ERSAP

This module provides an ERSAP source actor that reads network packet data from a socket stream and publishes it to the ERSAP framework for further processing.

## Overview

The PCAP Stream Source connects to a socket server that streams network packet data, reads the packets, and makes them available to downstream ERSAP actors for processing. It uses the LMAX Disruptor for high-performance event processing between the socket connection and the ERSAP framework.

## Architecture

The architecture consists of the following components:

1. **PcapStreamSourceEngine**: An ERSAP source engine that extends `AbstractEventReaderService` to provide packet data to the ERSAP framework.

2. **SocketSource**: Implements the `IESource` interface and manages the connection to the socket server, using LMAX Disruptor for buffering events.

3. **SocketConnectionHandler**: Handles the socket connection, reads packet data, and publishes it to the Disruptor ring buffer.

4. **Event**: A data container for packet data in the Disruptor ring buffer.

5. **StreamParameters**: Holds configuration parameters for the socket connection.

## Prerequisites

- Java 11 or higher
- Gradle 7.0 or higher
- ERSAP framework
- LMAX Disruptor library

## Building

To build the project, run:

```bash
cd pcap-stream-source
gradle build
```

This will compile the code and create a JAR file in the `build/libs` directory.

## Configuration

The PCAP Stream Source can be configured using a JSON configuration with the following parameters:

- `host`: The hostname or IP address of the socket server (default: "localhost")
- `port`: The port number of the socket server (default: 9000)
- `connection_timeout`: The connection timeout in milliseconds (default: 5000)
- `read_timeout`: The read timeout in milliseconds (default: 30000)
- `buffer_size`: The size of the Disruptor ring buffer (default: 1024)

Example configuration:

```json
{
  "host": "192.168.1.100",
  "port": 9000,
  "connection_timeout": 5000,
  "read_timeout": 30000,
  "buffer_size": 2048
}
```

## Usage in ERSAP

To use the PCAP Stream Source in an ERSAP service composition:

1. Register the service:

```java
ServiceRegistrationData registration = new ServiceRegistrationData(
    "PcapStreamSource",
    "org.jlab.ersap.actor.pcap.engine.PcapStreamSourceEngine",
    "PCAP stream source service"
);
```

2. Configure the service:

```java
JSONObject config = new JSONObject();
config.put("host", "192.168.1.100");
config.put("port", 9000);

EngineData input = new EngineData();
input.setData(EngineDataType.JSON, config.toString());

service.configure(input);
```

3. Connect to downstream services in your ERSAP application.

## Integration with pcap2stream

This source actor is designed to work with the `pcap2stream` server, which reads PCAP files and streams the packet data over a socket connection. The `pcap2stream` server should be configured to send packet data in the following format:

1. 4-byte integer representing the packet length
2. Packet data of the specified length

## Data Flow

The data flow through the PCAP Stream Source implementation follows these steps:

1. **Data Source (External)**: 
   - Network packets are captured and streamed from a socket server (like the MockPcapServer)
   - The MockPcapServer reads a PCAP file and streams packet data to connected clients

2. **Socket Connection Layer**:
   - `SocketConnectionHandler` establishes and maintains a socket connection to the data source
   - It reads packet data from the socket in its `run()` method, which executes in a dedicated thread

3. **Event Publishing**:
   - When data is received, `SocketConnectionHandler` publishes it to the Disruptor ring buffer:
   - It calls `publishEvent()` which:
     - Claims a sequence in the ring buffer
     - Gets the Event object at that sequence
     - Sets the data and length in the Event
     - Publishes the sequence to make it available to consumers

4. **Event Buffering**:
   - The LMAX Disruptor ring buffer (`RingBuffer<Event>`) serves as a high-performance buffer between the socket connection and the ERSAP framework
   - Events are stored in the ring buffer until they are consumed by the `SocketSource`

5. **Data Retrieval**:
   - `SocketSource` implements the `IESource` interface
   - When `getNextEvent()` is called, it retrieves the next event from the ring buffer through the `SocketConnectionHandler`
   - The `AbstractConnectionHandler.getNextEvent()` method:
     - Checks if there are events available
     - Gets the next event from the ring buffer
     - Returns the data as a byte array
     - Advances the sequence to mark the event as processed

6. **ERSAP Integration**:
   - `PcapStreamSourceEngine` extends `AbstractEventReaderService<IESource>`
   - It creates a `SocketSource` instance in its `createReader()` method
   - When ERSAP requests data via `readEvent()`, it:
     - Calls `getNextEvent()` on the `SocketSource`
     - Wraps the byte array in a `ByteBuffer`
     - Returns the buffer to ERSAP

7. **Data Processing**:
   - ERSAP framework receives the data as `EngineData` with type `EngineDataType.BYTES`
   - The data is then passed to downstream ERSAP services for further processing

## Performance Considerations

- The LMAX Disruptor is used for high-performance event processing between the socket connection and the ERSAP framework.
- The buffer size can be adjusted to optimize performance based on the packet rate and available memory.
- The socket connection is managed in a separate thread to avoid blocking the ERSAP event processing.

## Ring Buffer Monitoring

The PCAP Stream Source includes built-in monitoring capabilities for the Disruptor ring buffer. This allows you to track the performance and health of the data pipeline in real-time.

### Monitoring Metrics

The following metrics are available:

- **Buffer Size**: The total capacity of the ring buffer.
- **Used Slots**: The number of slots currently in use.
- **Fill Level**: The percentage of the buffer that is currently filled.
- **Available Slots**: The number of slots available for new events.
- **Consumer Lag**: The number of events that have been published but not yet consumed.
- **Total Events Published**: The total number of events published to the buffer since startup.
- **Total Events Consumed**: The total number of events consumed from the buffer since startup.
- **Total Bytes Published**: The total number of bytes published to the buffer since startup.
- **Throughput**: The rate at which events and bytes are being processed.

### Checking Ring Buffer Status

You can check the ring buffer status using the provided script:

```bash
./scripts/check_ring_buffer.sh
```

By default, this will check the status of the `pcap-source` service in the `pcap-container` container every 5 seconds. You can customize these parameters:

```bash
./scripts/check_ring_buffer.sh --container my-container --service my-service --interval 10
```

### Programmatic Access

You can also access the ring buffer status programmatically:

```java
// Get the ring buffer status as a JSON string
String jsonStatus = pcapStreamSourceEngine.getRingBufferStatus();

// Get the ring buffer status as a formatted string
String formattedStatus = pcapStreamSourceEngine.getRingBufferStatusString();
```

## Error Handling

The source actor includes robust error handling for socket connections:

- Automatic reconnection attempts if the connection is lost
- Timeouts for connection and read operations
- Graceful shutdown of resources when the service is stopped

## License

This project is licensed under the same license as the ERSAP framework. 