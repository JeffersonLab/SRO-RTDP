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

## Testing Tools

The project includes several testing tools to help you verify the functionality of the PCAP Stream Source:

### MockPcapServer

The `MockPcapServer` is a simple server that reads a PCAP file and streams the packet data to connected clients. It's useful for testing the PCAP Stream Source without needing a real network capture.

To run the MockPcapServer:

```bash
cd scripts
javac MockPcapServer.java
java MockPcapServer <pcap_file> [port]
```

Where:
- `<pcap_file>` is the path to a PCAP file
- `[port]` is the optional port number (default: 9000)

The server will read the PCAP file, parse the packets, and stream them to any connected clients. It supports both standard PCAP format and modified CLAS12 PCAP format.

### TestClientWithMonitoring

The `TestClientWithMonitoring` is a simple client that connects to the MockPcapServer, receives packet data, and monitors the performance of the connection. It's useful for testing the server without the full ERSAP framework.

To run the TestClientWithMonitoring:

```bash
cd scripts
javac TestClientWithMonitoring.java
java TestClientWithMonitoring [host] [port] [buffer_size] [monitor_interval_ms]
```

Where:
- `[host]` is the optional hostname or IP address (default: "localhost")
- `[port]` is the optional port number (default: 9000)
- `[buffer_size]` is the optional buffer size (default: 1024)
- `[monitor_interval_ms]` is the optional monitoring interval in milliseconds (default: 1000)

The client will connect to the server, receive packet data, and display performance statistics at regular intervals.

### Testing Scripts

The project includes several scripts to help you test the PCAP Stream Source:

#### run_simple_test.sh

This script runs a simple test using the MockPcapServer and TestClientWithMonitoring:

```bash
./scripts/run_simple_test.sh [pcap_file] [port] [buffer_size] [monitor_interval_ms]
```

The script will:
1. Start the MockPcapServer with the specified PCAP file
2. Start the TestClientWithMonitoring to connect to the server
3. Display performance statistics
4. Clean up resources when you press Ctrl+C

#### run_ersap_test.sh

This script runs a test using the ERSAP framework:

```bash
./scripts/run_ersap_test.sh
```

The script will:
1. Start the MockPcapServer with a default PCAP file
2. Create and configure an ERSAP container and service
3. Connect the PCAP Stream Source to the server
4. Keep the application running until you press Ctrl+C

#### check_ring_buffer.sh

This script checks the ring buffer status of a running PCAP Stream Source:

```bash
./scripts/check_ring_buffer.sh [options]
```

Options:
- `-c, --container CONTAINER_NAME`: Container name (default: pcap-container)
- `-s, --service SERVICE_NAME`: Service name (default: pcap-source)
- `-i, --interval SECONDS`: Update interval in seconds (default: 5)

The script will connect to the specified ERSAP service and display the ring buffer status at regular intervals.

## Integration with pcap2stream

This source actor is designed to work with the `pcap2stream` server, which reads PCAP files and streams the packet data over a socket connection. The `pcap2stream` server should be configured to send packet data in the following format:

1. 4-byte integer representing the packet length
2. Packet data of the specified length

The MockPcapServer included in this project follows the same protocol and can be used as a drop-in replacement for testing purposes.

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
- The MockPcapServer includes performance monitoring to help you understand the throughput of your PCAP data.
- The TestClientWithMonitoring includes buffer monitoring to help you optimize the buffer size.

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

## Multi-Socket Support

The PCAP Stream Source also supports connecting to multiple socket servers simultaneously, which is useful for handling different IP streams from a PCAP file. This is implemented through the `MultiSocketSource` and `MultiSocketSourceEngine` classes.

### MultiSocketSource

The `MultiSocketSource` class manages multiple `SocketSource` instances, each connected to a different socket server. It provides the following features:

- Creates and manages multiple socket connections
- Implements a round-robin strategy for retrieving events from the connections
- Provides monitoring capabilities for all connections
- Handles connection failures gracefully

### MultiSocketSourceEngine

The `MultiSocketSourceEngine` class extends `AbstractEventReaderService` to provide an ERSAP source engine that reads data from multiple socket streams. It can be configured using a JSON configuration with an array of connection parameters.

### Configuration

The MultiSocketSourceEngine can be configured using a JSON configuration with the following structure:

```json
{
  "connections": [
    {
      "host": "localhost",
      "port": 9001,
      "connection_timeout": 5000,
      "read_timeout": 30000,
      "buffer_size": 1024
    },
    {
      "host": "localhost",
      "port": 9002,
      "connection_timeout": 5000,
      "read_timeout": 30000,
      "buffer_size": 1024
    },
    {
      "host": "localhost",
      "port": 9003,
      "connection_timeout": 5000,
      "read_timeout": 30000,
      "buffer_size": 1024
    }
  ]
}
```

Each connection in the array can have its own configuration parameters, allowing for flexibility in connecting to different socket servers.

### Usage in ERSAP

To use the MultiSocketSourceEngine in an ERSAP service composition:

1. Register the service:

```java
ServiceRegistrationData registration = new ServiceRegistrationData(
    "MultiSocketSource",
    "org.jlab.ersap.actor.pcap.engine.MultiSocketSourceEngine",
    "PCAP multi-socket stream source service"
);
```

2. Configure the service:

```java
JSONObject config = new JSONObject();
JSONArray connections = new JSONArray();

// First connection
JSONObject conn1 = new JSONObject();
conn1.put("host", "localhost");
conn1.put("port", 9001);
connections.put(conn1);

// Second connection
JSONObject conn2 = new JSONObject();
conn2.put("host", "localhost");
conn2.put("port", 9002);
connections.put(conn2);

config.put("connections", connections);

EngineData input = new EngineData();
input.setData(EngineDataType.JSON, config.toString());

service.configure(input);
```

3. Connect to downstream services in your ERSAP application.

### Monitoring Multiple Connections

The MultiSocketSourceEngine provides methods to monitor the status of all connections:

- `getRingBufferStatus()`: Returns a JSON string with the status of all ring buffers
- `getRingBufferStatusString()`: Returns a formatted string with the status of all ring buffers

You can use these methods to monitor the performance and health of all connections in real-time.