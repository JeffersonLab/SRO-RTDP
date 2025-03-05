# PCAP Stream Source for ERSAP

This module provides an ERSAP source actor that reads network packet data from a socket stream and publishes it to the ERSAP framework for further processing.

## Overview

The PCAP Stream Source connects to a socket server that streams network packet data, reads the packets, and makes them available to downstream ERSAP actors for processing. It uses the LMAX Disruptor for high-performance event processing between the socket connection and the ERSAP framework.

## Core Components

### ERSAP Integration Components

- **PcapStreamSourceEngine**: ERSAP engine for a single socket connection, extending `AbstractEventReaderService`.
- **MultiSocketSourceEngine**: ERSAP engine for multiple socket connections, extending `AbstractEventReaderService`.

### Supporting Components

- **SocketSource**: Manages a single socket connection to a data source, handling the connection lifecycle and data retrieval.
- **MultiSocketSource**: Manages multiple socket connections simultaneously, implementing a round-robin strategy for event retrieval.
- **SocketConnectionHandler**: Handles the low-level socket operations, reading data from the socket and publishing it to the ring buffer.
- **StreamParameters**: Stores configuration parameters for socket connections (host, port, timeouts, etc.).
- **RingBufferMonitor**: Monitors the performance and health of the ring buffer.

## Data Flow

The data flow through the system follows these steps:

1. **Data Source** → External socket server streams packet data
2. **Socket Connection** → `SocketConnectionHandler` establishes and maintains the connection
3. **Data Reading** → `SocketConnectionHandler` reads packet data from the socket
4. **Event Publishing** → Data is published to the Disruptor ring buffer
5. **Event Buffering** → The ring buffer holds events until they are consumed
6. **Data Retrieval** → `SocketSource`/`MultiSocketSource` retrieves events from the ring buffer
7. **ERSAP Integration** → ERSAP engines provide the data to the ERSAP framework
8. **Downstream Processing** → Data is passed to downstream ERSAP services

## Prerequisites

- Java 11 or higher
- ERSAP framework
- LMAX Disruptor library

## Building

To build the project, run:

```bash
cd pcap-stream-source
./gradlew build
```

This will compile the code and create a JAR file in the `build/libs` directory.

## ERSAP Configuration

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

For multiple IP streams (e.g., 24 streams), you can configure multiple connections:

```json
{
  "connections": [
    {"host": "localhost", "port": 9000, "connection_timeout": 5000, "read_timeout": 30000, "buffer_size": 1024},
    {"host": "localhost", "port": 9001, "connection_timeout": 5000, "read_timeout": 30000, "buffer_size": 1024},
    // ... additional connections ...
    {"host": "localhost", "port": 9023, "connection_timeout": 5000, "read_timeout": 30000, "buffer_size": 1024}
  ]
}
```

## ERSAP Integration

### Using in an ERSAP Application

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

### ERSAP Test Script

The `run_ersap_test.sh` script demonstrates integration with the ERSAP framework:

```bash
# Usage
./scripts/run_ersap_test.sh
```

This script:
- Creates an ERSAP container and service
- Configures the PCAP Stream Source engine with multiple connections (up to 24)
- Shows how the source integrates with ERSAP

### Step-by-Step Guide to Run the ERSAP Test

Follow these steps to run the ERSAP integration test:

1. **Prerequisites**:
   
   You only need the following to run the ERSAP test:
   
   - Java 11 or higher
   - The `pcap-stream-source` directory with its scripts and source code
   - A PCAP file for testing (the script will look for this at a specific location)

2. **Build the project**:
   ```bash
   cd pcap-stream-source
   ./gradlew build
   ```

3. **Create a JSON configuration file**:
   ```bash
   mkdir -p custom-config
   cat > custom-config/multi-socket-config.json << 'EOF'
   {
     "connections": [
       {"host": "localhost", "port": 9000, "connection_timeout": 5000, "read_timeout": 30000, "buffer_size": 1024},
       {"host": "localhost", "port": 9001, "connection_timeout": 5000, "read_timeout": 30000, "buffer_size": 1024}
     ]
   }
   EOF
   ```
   
   For testing with more connections (e.g., 24), create a more extensive configuration:
   ```bash
   mkdir -p custom-config
   cat > custom-config/multi-socket-config.json << 'EOF'
   {
     "connections": [
       {"host": "localhost", "port": 9000, "connection_timeout": 5000, "read_timeout": 30000, "buffer_size": 1024},
       {"host": "localhost", "port": 9001, "connection_timeout": 5000, "read_timeout": 30000, "buffer_size": 1024},
       {"host": "localhost", "port": 9002, "connection_timeout": 5000, "read_timeout": 30000, "buffer_size": 1024},
       {"host": "localhost", "port": 9003, "connection_timeout": 5000, "read_timeout": 30000, "buffer_size": 1024},
       {"host": "localhost", "port": 9004, "connection_timeout": 5000, "read_timeout": 30000, "buffer_size": 1024},
       {"host": "localhost", "port": 9005, "connection_timeout": 5000, "read_timeout": 30000, "buffer_size": 1024},
       {"host": "localhost", "port": 9006, "connection_timeout": 5000, "read_timeout": 30000, "buffer_size": 1024},
       {"host": "localhost", "port": 9007, "connection_timeout": 5000, "read_timeout": 30000, "buffer_size": 1024},
       {"host": "localhost", "port": 9008, "connection_timeout": 5000, "read_timeout": 30000, "buffer_size": 1024},
       {"host": "localhost", "port": 9009, "connection_timeout": 5000, "read_timeout": 30000, "buffer_size": 1024},
       {"host": "localhost", "port": 9010, "connection_timeout": 5000, "read_timeout": 30000, "buffer_size": 1024},
       {"host": "localhost", "port": 9011, "connection_timeout": 5000, "read_timeout": 30000, "buffer_size": 1024},
       {"host": "localhost", "port": 9012, "connection_timeout": 5000, "read_timeout": 30000, "buffer_size": 1024},
       {"host": "localhost", "port": 9013, "connection_timeout": 5000, "read_timeout": 30000, "buffer_size": 1024},
       {"host": "localhost", "port": 9014, "connection_timeout": 5000, "read_timeout": 30000, "buffer_size": 1024},
       {"host": "localhost", "port": 9015, "connection_timeout": 5000, "read_timeout": 30000, "buffer_size": 1024},
       {"host": "localhost", "port": 9016, "connection_timeout": 5000, "read_timeout": 30000, "buffer_size": 1024},
       {"host": "localhost", "port": 9017, "connection_timeout": 5000, "read_timeout": 30000, "buffer_size": 1024},
       {"host": "localhost", "port": 9018, "connection_timeout": 5000, "read_timeout": 30000, "buffer_size": 1024},
       {"host": "localhost", "port": 9019, "connection_timeout": 5000, "read_timeout": 30000, "buffer_size": 1024},
       {"host": "localhost", "port": 9020, "connection_timeout": 5000, "read_timeout": 30000, "buffer_size": 1024},
       {"host": "localhost", "port": 9021, "connection_timeout": 5000, "read_timeout": 30000, "buffer_size": 1024},
       {"host": "localhost", "port": 9022, "connection_timeout": 5000, "read_timeout": 30000, "buffer_size": 1024},
       {"host": "localhost", "port": 9023, "connection_timeout": 5000, "read_timeout": 30000, "buffer_size": 1024}
     ]
   }
   EOF
   ```

4. **Compile the test components**:
   ```bash
   javac -d build/classes/java/scripts scripts/MockPcapServer.java
   javac -d build/classes/java/scripts -cp "build/classes/java/main:lib/json-20231013.jar:lib/disruptor-3.4.4.jar:lib/snakeyaml-2.0.jar" scripts/SimpleMultiSocketTest.java
   ```

5. **Run the ERSAP test script**:
   ```bash
   ./scripts/run_ersap_test.sh
   ```
   
   This script will:
   - Start mock PCAP servers on the configured ports
   - Create an ERSAP container and service
   - Configure the service with the JSON configuration
   - Run the test for 60 seconds
   - Display performance statistics
   - Clean up resources when done

   Note: The script assumes you have the necessary PCAP file at the location specified in the script. You may need to modify the script to point to your PCAP file.

6. **Analyze the results**:
   - Check the connection status for each socket
   - Review the throughput metrics
   - Verify that all connections were established successfully
   - Examine any error messages or warnings

7. **Clean up after testing**:
   ```bash
   # Kill any remaining MockPcapServer processes
   pkill -f MockPcapServer
   
   # Remove the configuration file if no longer needed
   rm -f custom-config/multi-socket-config.json
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
- **Throughput**: Rate of events and bytes being processed

### Accessing Monitoring Data in ERSAP

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

## Advanced ERSAP Usage

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

## Troubleshooting

### JSON Configuration Issues

If you encounter errors related to the JSON configuration:

1. Ensure the JSON file is properly formatted with no syntax errors
2. Verify that the top-level object has a "connections" key for multi-socket configurations
3. Check that each connection object has the required fields (host, port)

### Connection Issues

If you have trouble connecting to the servers:

1. Verify that the servers are running and listening on the expected ports
2. Check for firewall or network issues that might block connections
3. Increase the connection timeout if needed
4. Verify that the host and port values in the configuration are correct

### Performance Issues

If you experience performance problems:

1. Adjust the buffer size to better match your data volume
2. Monitor the ring buffer status to identify bottlenecks
3. Consider increasing the read timeout for high-latency connections
4. Reduce the number of connections if the system is overwhelmed