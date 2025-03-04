# Ring Buffer Monitoring for PCAP Stream Source

This document describes the ring buffer monitoring capabilities implemented for the PCAP Stream Source in the ERSAP application.

## Overview

The PCAP Stream Source uses a ring buffer to efficiently process network packets received from a socket connection. Monitoring the status of this ring buffer is crucial for ensuring optimal performance and detecting potential bottlenecks or issues.

## Monitoring Approaches

We've implemented several approaches to monitor the ring buffer:

### 1. Standalone Test Client with Built-in Monitoring

The `TestClientWithMonitoring` class provides a standalone client that connects to a PCAP stream server and processes packets using a ring buffer. It includes built-in monitoring capabilities that display:

- Buffer size and capacity
- Buffer fill level (percentage)
- Number of packets received
- Throughput (packets/second and Mbps)
- Running time

This approach is useful for testing and development purposes, as it doesn't require the full ERSAP framework.

### 2. Buffer Status Checker Script

The `check_buffer_status.sh` script can be used to check the status of the ring buffer by examining the output of the test client. It displays:

- Buffer size and capacity
- Buffer fill level (percentage)
- Throughput information

### 3. JMX-based Ring Buffer Monitor

The `RingBufferMonitor` class provides a more comprehensive monitoring solution using JMX (Java Management Extensions). It can monitor any ring buffer that exposes JMX metrics, including:

- Buffer size and capacity
- Buffer used and available space
- Fill percentage
- Events published and consumed
- Throughput (Mbps)

## Integration with ERSAP

To integrate ring buffer monitoring with ERSAP, the following components have been enhanced:

1. **AbstractConnectionHandler**: Added automatic logging of buffer status at regular intervals.

2. **SocketSource**: Added methods to expose ring buffer status and register JMX MBeans.

3. **PcapStreamSourceEngine**: Added methods to retrieve and report ring buffer metrics.

## Usage

### Running the Standalone Test Client

```bash
./run_simple_test.sh [pcap_file] [port] [buffer_size] [monitor_interval_ms]
```

This script starts a mock PCAP server and a test client with built-in monitoring.

### Checking Buffer Status

```bash
./check_buffer_status.sh
```

This script checks the status of the ring buffer by examining the output of the test client.

### Using the JMX-based Ring Buffer Monitor

```bash
./run_ring_buffer_monitor.sh [object_name_pattern] [interval_seconds]
```

This script runs the JMX-based ring buffer monitor to monitor any ring buffer that exposes JMX metrics.

## Performance Considerations

- The monitoring capabilities are designed to have minimal impact on performance.
- The monitoring interval can be adjusted to balance between monitoring frequency and performance overhead.
- For production environments, it's recommended to use the JMX-based monitoring approach, as it provides more comprehensive metrics with minimal overhead.

## Troubleshooting

If you encounter issues with the ring buffer, such as buffer overflow or underflow, consider:

1. Increasing the buffer size to handle bursts of packets.
2. Optimizing the consumer processing to keep up with the producer.
3. Implementing back-pressure mechanisms to slow down the producer if the consumer can't keep up.

## Future Enhancements

Potential future enhancements to the monitoring capabilities include:

1. Integration with external monitoring systems (e.g., Prometheus, Grafana).
2. Alert mechanisms for critical buffer conditions.
3. Historical data collection for trend analysis.
4. Web-based dashboard for real-time monitoring. 