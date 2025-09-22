# CPU Emulator Docker System

This document explains the mechanism and usage of the containerized CPU emulator system, which consists of three main components: source, cpu_emu (sender), and receiver.

## System Architecture

```
[Source] ---> [CPU Emulator] ---> [Receiver]
(Data)     Port 18888    (Processed)  Port 18080
```

### Components

1. **Source**
   - Role: Sends input data to the CPU emulator
   - Script: `run_source.sh`
   - Default port: 18888
   - Uses netcat (nc) to stream data
   - Mounts local directory to access input files

2. **CPU Emulator (Sender)**
   - Role: Processes incoming data with configurable CPU and memory load
   - Script: `run_sender.sh`
   - Receives on port 18888 (configurable)
   - Sends to port 18080 (configurable)
   - Configurable parameters:
     * Thread count
     * Memory footprint
     * Processing latency
     * Output size
     * CPU burn vs sleep mode

3. **Receiver**
   - Role: Collects processed data from CPU emulator
   - Script: `run_receiver.sh`
   - Default port: 18080
   - Saves received data to specified output file

## Data Flow

1. Source reads local input file and streams it to CPU emulator
2. CPU emulator:
   - Receives data on its input port (18888)
   - Processes data using specified number of threads
   - Each thread:
     * Consumes specified memory
     * Introduces specified latency
     * Either burns CPU or sleeps
   - Forwards processed data to receiver
3. Receiver saves processed data to output file

## Usage

### 1. Build the Docker Image

```bash
./build_docker.sh
```

### 2. Start the Receiver

```bash
./run_receiver.sh [-p port] [-o output_file]

Options:
  -p: Port to listen on (default: 18080)
  -o: Output file name (default: received_data)
```

### 3. Start the CPU Emulator

```bash
./run_sender.sh [-r receive_port] [-i dest_ip] [-p dest_port] [-t num_threads] \
                [-v verbosity] [-b thread_latency] [-m memory_footprint] \
                [-o output_size] [-s sleep_mode]

Options:
  -r: Receive port (default: 18888)
  -i: Destination IP address (default: localhost)
  -p: Destination port (default: 18080)
  -t: Number of threads (default: 10)
  -v: Verbosity (0/1, default: 0)
  -b: Thread latency in seconds per GB input (default: 1)
  -m: Thread memory footprint in GB (default: 1)
  -o: Output size in GB (default: 1)
  -s: Sleep mode (0=CPU burn, 1=sleep, default: 0)
```

### 4. Send Data from Source

```bash
./run_source.sh -f input_file [-h cpu_emu_host] [-p port]

Options:
  -f: Input file to send (required)
  -h: CPU emulator host (default: localhost)
  -p: Port to send to (default: 18888)
```

## Example Workflow

1. Start receiver:
```bash
./run_receiver.sh -p 18080 -o my_output
```

2. Start CPU emulator:
```bash
./run_sender.sh -i "localhost" -p 18080 -t 4 -v 1 -b 2 -m 1 -o 1
```

3. Send test data:
```bash
echo "Test data" > input.txt
./run_source.sh -f input.txt
```

## Network Configuration

- The system uses Docker networking
- Default ports:
  * Source → CPU Emulator: 18888
  * CPU Emulator → Receiver: 18080
- When using localhost:
  * Linux: translates to 172.17.0.1
  * macOS/Windows: translates to host.docker.internal

## Monitoring and Debugging

- Use `-v 1` with CPU emulator for verbose output
- Check Docker logs for each component
- Monitor system resources with `docker stats`
- Use `netstat -tuln` to check port availability