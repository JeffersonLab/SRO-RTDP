# PCAP Stream Source for ERSAP

This module provides an ERSAP source actor that reads network packet data from a socket stream and publishes it to the ERSAP framework for further processing.

## Overview

The PCAP Stream Source connects to a socket server that streams network packet data, reads the packets, and makes them available to downstream ERSAP actors for processing. It uses the LMAX Disruptor for high-performance event processing between the socket connection and the ERSAP framework.

## Components

The PCAP Stream Source consists of several key components that work together:

### Core Components

- **SocketSource**: Manages a single socket connection to a data source, handling the connection lifecycle and data retrieval.
- **MultiSocketSource**: Manages multiple socket connections simultaneously, implementing a round-robin strategy for event retrieval.
- **SocketConnectionHandler**: Handles the low-level socket operations, reading data from the socket and publishing it to the ring buffer.
- **StreamParameters**: Stores configuration parameters for socket connections (host, port, timeouts, etc.).

### ERSAP Integration

- **PcapStreamSourceEngine**: ERSAP engine for a single socket connection, extending `AbstractEventReaderService`.
- **MultiSocketSourceEngine**: ERSAP engine for multiple socket connections, extending `AbstractEventReaderService`.

### Performance Components

- **Event**: Data container for the Disruptor ring buffer.
- **RingBufferMonitor**: Monitors the performance and health of the ring buffer.

## Data Flow

The data flow through the system follows these steps:

1. **Data Source** → External socket server (e.g., MockPcapServer) streams packet data
2. **Socket Connection** → `SocketConnectionHandler` establishes and maintains the connection
3. **Data Reading** → `SocketConnectionHandler` reads packet data from the socket
4. **Event Publishing** → Data is published to the Disruptor ring buffer
5. **Event Buffering** → The ring buffer holds events until they are consumed
6. **Data Retrieval** → `SocketSource`/`MultiSocketSource` retrieves events from the ring buffer
7. **ERSAP Integration** → ERSAP engines provide the data to the ERSAP framework
8. **Downstream Processing** → Data is passed to downstream ERSAP services

## Prerequisites

- Java 11 or higher
- Gradle 7.0 or higher
- ERSAP framework
- LMAX Disruptor library (automatically downloaded by the build scripts)

## Building

To build the project, run:

```bash
cd pcap-stream-source
./gradlew build
```

This will compile the code and create a JAR file in the `build/libs` directory.

## Configuration

### Single Socket Configuration

The `PcapStreamSourceEngine` can be configured using a JSON configuration with the following parameters:

```json
{
  "host": "localhost",
  "port": 9000,
  "connection_timeout": 5000,
  "read_timeout": 30000,
  "buffer_size": 1024
}
```

### Multi-Socket Configuration

The `MultiSocketSourceEngine` can be configured using a JSON configuration with an array of connections:

```json
{
  "connections": [
    {
      "host": "localhost",
      "port": 9000,
      "connection_timeout": 5000,
      "read_timeout": 30000,
      "buffer_size": 1024
    },
    {
      "host": "localhost",
      "port": 9001,
      "connection_timeout": 5000,
      "read_timeout": 30000,
      "buffer_size": 1024
    }
  ]
}
```

## Testing Tools

The project includes several testing tools to help you verify functionality:

### MockPcapServer

A simple server that reads a PCAP file and streams the packet data to connected clients.

```bash
# Usage
java -cp <classpath> scripts.MockPcapServer <port> <pcap_file>

# Example
java -cp build/classes/java/main:build/classes/java/scripts scripts.MockPcapServer 9000 /path/to/capture.pcap
```

The server:
1. Reads the PCAP file
2. Skips the global header
3. For each packet:
   - Reads the packet header
   - Extracts the packet length
   - Reads the packet data
   - Sends the length followed by the data to the client

### Test Clients

#### BasicMultiSocketTest

A simple test client that connects to multiple socket servers without using JSON configuration.

```bash
# Usage
java -cp <classpath> scripts.BasicMultiSocketTest <timeout_seconds>

# Example
java -cp build/classes/java/main:build/classes/java/scripts scripts.BasicMultiSocketTest 30
```

This client:
1. Creates a `MultiSocketSource` with hardcoded connection parameters
2. Opens connections to multiple servers
3. Checks connection status with retries
4. Processes data for the specified timeout
5. Reports performance statistics

#### SimpleMultiSocketTest

A test client that connects to multiple socket servers using a JSON configuration file.

```bash
# Usage
java -cp <classpath> scripts.SimpleMultiSocketTest <config_file> [timeout_seconds]

# Example
java -cp build/classes/java/main:build/classes/java/scripts scripts.SimpleMultiSocketTest config.json 30
```

This client:
1. Reads connection parameters from a JSON configuration file
2. Creates a `MultiSocketSource` with the specified parameters
3. Opens connections to multiple servers
4. Checks connection status with retries
5. Processes data for the specified timeout
6. Reports performance statistics

## Testing Scripts

The project includes four essential scripts to demonstrate its functionality:

### 1. Basic Multi-Socket Test

The `run_basic_test.sh` script demonstrates the core functionality with a simple configuration:

```bash
# Usage
./scripts/run_basic_test.sh <pcap_file> [timeout_seconds]

# Example
./scripts/run_basic_test.sh /path/to/capture.pcap 30
```

This script:
- Starts two mock PCAP servers on ports 9000 and 9001
- Runs the `BasicMultiSocketTest` client that connects to both servers
- Processes data for the specified timeout
- Displays performance statistics

### 2. JSON Configuration Test

The `run_multi_socket_test.sh` script demonstrates using JSON configuration:

```bash
# Usage
./scripts/run_multi_socket_test.sh <pcap_file> [timeout_seconds]

# Example
./scripts/run_multi_socket_test.sh /path/to/capture.pcap 30
```

This script:
- Creates a JSON configuration file with connection parameters
- Starts two mock PCAP servers
- Runs the `SimpleMultiSocketTest` client that reads the JSON configuration
- Shows how to configure multiple connections via JSON

### 3. ERSAP Integration Test

The `run_ersap_test.sh` script demonstrates integration with the ERSAP framework:

```bash
# Usage
./scripts/run_ersap_test.sh

# Example
./scripts/run_ersap_test.sh
```

This script:
- Creates an ERSAP container and service
- Configures the PCAP Stream Source engine
- Shows how the source integrates with ERSAP

### 4. Performance Monitoring

The `check_ring_buffer.sh` script demonstrates the monitoring capabilities:

```bash
# Usage
./scripts/check_ring_buffer.sh [options]

# Example
./scripts/check_ring_buffer.sh --container my-container --service my-service --interval 10
```

Options:
- `-c, --container CONTAINER_NAME`: Container name (default: pcap-container)
- `-s, --service SERVICE_NAME`: Service name (default: pcap-source)
- `-i, --interval SECONDS`: Update interval in seconds (default: 5)

This script:
- Connects to a running ERSAP service
- Displays the ring buffer status at regular intervals
- Shows how to monitor performance in real-time

## Step-by-Step Usage Guide

### 1. Build the Project

```bash
cd pcap-stream-source
./gradlew build
```

### 2. Run a Basic Test

```bash
./scripts/run_basic_test.sh /path/to/capture.pcap 30
```

This will:
- Start two mock PCAP servers on ports 9000 and 9001
- Connect to both servers using the `BasicMultiSocketTest` client
- Process data for 30 seconds
- Display performance statistics

### 3. Run a Test with JSON Configuration

```bash
./scripts/run_multi_socket_test.sh /path/to/capture.pcap 30
```

This will:
- Create a JSON configuration file with connection parameters
- Start two mock PCAP servers on ports 9000 and 9001
- Connect to both servers using the `SimpleMultiSocketTest` client
- Process data for 30 seconds
- Display performance statistics

### 4. Use in an ERSAP Application

To use the PCAP Stream Source in an ERSAP application:

```java
// Register the service
ServiceRegistrationData registration = new ServiceRegistrationData(
    "PcapStreamSource",
    "org.jlab.ersap.actor.pcap.engine.PcapStreamSourceEngine",
    "PCAP stream source service"
);

// Configure the service
JSONObject config = new JSONObject();
config.put("host", "localhost");
config.put("port", 9000);

EngineData input = new EngineData();
input.setData(EngineDataType.JSON, config.toString());

service.configure(input);
```

For multiple connections:

```java
// Register the service
ServiceRegistrationData registration = new ServiceRegistrationData(
    "MultiSocketSource",
    "org.jlab.ersap.actor.pcap.engine.MultiSocketSourceEngine",
    "PCAP multi-socket stream source service"
);

// Configure the service
JSONObject config = new JSONObject();
JSONArray connections = new JSONArray();

// First connection
JSONObject conn1 = new JSONObject();
conn1.put("host", "localhost");
conn1.put("port", 9000);
connections.put(conn1);

// Second connection
JSONObject conn2 = new JSONObject();
conn2.put("host", "localhost");
conn2.put("port", 9001);
connections.put(conn2);

config.put("connections", connections);

EngineData input = new EngineData();
input.setData(EngineDataType.JSON, config.toString());

service.configure(input);
```

## Performance Monitoring

The PCAP Stream Source includes built-in monitoring capabilities for the Disruptor ring buffer.

### Monitoring Metrics

- **Buffer Size**: Total capacity of the ring buffer
- **Used Slots**: Number of slots currently in use
- **Fill Level**: Percentage of the buffer that is filled
- **Available Slots**: Number of slots available for new events
- **Consumer Lag**: Number of events published but not yet consumed
- **Total Events Published**: Total number of events published since startup
- **Total Events Consumed**: Total number of events consumed since startup
- **Total Bytes Published**: Total number of bytes published since startup
- **Throughput**: Rate of events and bytes being processed

### Accessing Monitoring Data

```java
// Get the ring buffer status as a JSON string
String jsonStatus = pcapStreamSourceEngine.getRingBufferStatus();

// Get the ring buffer status as a formatted string
String formattedStatus = pcapStreamSourceEngine.getRingBufferStatusString();
```

## Error Handling and Reconnection

The PCAP Stream Source includes robust error handling for socket connections:

- Automatic reconnection attempts if the connection is lost
- Timeouts for connection and read operations
- Graceful shutdown of resources when the service is stopped

The `SocketConnectionHandler` will attempt to reconnect to the server if the connection is lost, with a configurable number of retry attempts and delay between attempts.

## Advanced Usage

### Custom Data Processing

To implement custom data processing, create a downstream ERSAP service that receives data from the PCAP Stream Source:

```java
public class MyProcessingService extends AbstractEngineService {
    @Override
    public EngineData execute(EngineData input) {
        ByteBuffer data = (ByteBuffer) input.getData();
        // Process the data
        return output;
    }
}
```

### Multiple Data Sources

The `MultiSocketSourceEngine` can connect to multiple data sources simultaneously, which is useful for:

- Handling different IP streams from a PCAP file
- Combining data from multiple capture points
- Implementing redundant data sources for high availability

### Integration with pcap2stream

This source actor is designed to work with the `pcap2stream` server, which reads PCAP files and streams the packet data over a socket connection. The `pcap2stream` server should be configured to send packet data in the following format:

1. 4-byte integer representing the packet length
2. Packet data of the specified length

The MockPcapServer included in this project follows the same protocol and can be used as a drop-in replacement for testing purposes.