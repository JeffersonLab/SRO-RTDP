# CPU Emulator 3-Node Test Setup

This directory contains scripts for testing the CPU emulator in a 3-node configuration using ZeroMQ (ZMQ) for communication. The setup consists of:

1. A sender node that generates and sends test data
2. A CPU emulator node that processes the data
3. A receiver node that collects the processed data

## Prerequisites

- Docker installed and running
- The `cpu-emu` Docker image built (run `./build.sh` in the parent directory)
- Network connectivity between the nodes if running on different machines

## Network Architecture

The setup uses ZMQ REQ/REP sockets for communication:

```
[Sender] --REQ--> [CPU Emulator] --REQ--> [Receiver]
          <-REP--              <--REP--
```

- Sender sends data using a REQ socket and waits for ACK
- CPU Emulator receives on a REP socket and forwards using a REQ socket
- Receiver listens on a REP socket and sends ACK for received data

## Running the Test

### 1. Start the Receiver

```bash
./start_receiver.sh -p 8889 -o received_data.bin
```

Options:
- `-p PORT`: Port to listen on (default: 8888)
- `-o FILE`: Output file for received data
- `-b IP`: IP address to bind to (default: *)

### 2. Start the CPU Emulator

```bash
./start_cpu_emu.sh -r 8888 -p 8889 -i receiver_host
```

Options:
- `-t THREADS`: Number of threads (default: 5)
- `-b LATENCY`: Processing latency in seconds per GB (default: 100)
- `-m MEM`: Memory footprint in GB per thread (default: 10)
- `-o OUTPUT`: Output size in GB (default: 0.01)
- `-r PORT`: Port to receive on (default: 8888)
- `-p PORT`: Port to forward to (default: 8888)
- `-i HOST`: Destination host (default: 127.0.0.1)
- `-s`: Use sleep mode instead of CPU burn
- `-v`: Enable verbose mode
- `-z`: Act as terminal node (don't forward data)

### 3. Send Test Data

```bash
./send_data.sh -p 8888 -h emulator_host -s 100M
```

Options:
- `-h HOST`: Target host (default: localhost)
- `-p PORT`: Target port (default: 8888)
- `-f FILE`: Input file to send
- `-s SIZE`: Size of random data to generate (default: 10M)

## Example: Local Testing

1. Start the receiver:
```bash
./start_receiver.sh -p 8889
```

2. Start the CPU emulator:
```bash
./start_cpu_emu.sh -r 8888 -p 8889 -i localhost -v
```

3. Send test data:
```bash
./send_data.sh -p 8888 -s 50M
```

## Example: Distributed Testing

1. On the receiver machine:
```bash
./start_receiver.sh -p 8889 -b receiver_ip
```

2. On the CPU emulator machine:
```bash
./start_cpu_emu.sh -r 8888 -p 8889 -i receiver_ip -v
```

3. On the sender machine:
```bash
./send_data.sh -h emulator_ip -p 8888 -s 100M
```

## Notes

- All nodes use host networking (`--network host`) for better performance
- The CPU emulator can be configured via YAML file (automatically generated)
- Use verbose mode (-v) for debugging and monitoring
- Memory footprint and thread count should be adjusted based on available resources
- For testing large data transfers, ensure sufficient disk space for input/output files