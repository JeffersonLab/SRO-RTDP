# CPU Emulator Apptainer Scripts

This directory contains scripts for running the CPU emulator system using Apptainer containers. The system consists of three main components that work together to process and transfer data.

## Important Note About Network Connectivity

**Due to container networking constraints, all components must run on the same machine using localhost (127.0.0.1) for communication.** Remote connections between components are not supported when running in Apptainer containers.

## System Architecture

```
[Source] ---> [CPU Emulator] ---> [Receiver]
(Data)     Port 18888    (Processed)  Port 18080

All components must run on the same machine using localhost (127.0.0.1)
```

## Components

### 1. Receiver (`run_receiver.sh`)
- Listens for processed data from the CPU emulator
- Default port: 18080
- Saves received data to specified output file
- Must run on the same machine as other components
- Usage:
  ```bash
  ./run_receiver.sh [-p port] [-o output_file] [-c container_path]
  ```
  Options:
  - `-p`: Port to listen on (default: 18080)
  - `-o`: Output file name (default: received_data)
  - `-c`: Path to Apptainer container (default: cpu_emu.sif)

### 2. CPU Emulator (`run_sender.sh`)
- Receives data from source
- Processes data with configurable CPU and memory load
- Forwards processed data to receiver
- Must use 127.0.0.1 as destination IP
- Usage:
  ```bash
  ./run_sender.sh [-r receive_port] [-p dest_port] [-t num_threads] \
                  [-v verbosity] [-b thread_latency] [-m memory_footprint] \
                  [-o output_size] [-s sleep_mode] [-c container_path]
  ```
  Options:
  - `-r`: Receive port (default: 18888)
  - `-p`: Destination port (default: 18080)
  - `-t`: Number of threads (default: 10)
  - `-v`: Verbosity (0/1, default: 0)
  - `-b`: Thread latency in seconds per GB input (default: 1)
  - `-m`: Thread memory footprint in GB (default: 1)
  - `-o`: Output size in GB (default: 1)
  - `-s`: Sleep mode (0=CPU burn, 1=sleep, default: 0)
  - `-c`: Path to Apptainer container (default: cpu_emu.sif)

### 3. Source (`run_source.sh`)
- Sends input data to CPU emulator
- Performs pre-flight connectivity checks
- Must use 127.0.0.1 as CPU emulator host
- Usage:
  ```bash
  ./run_source.sh -f input_file [-p port] [-c container_path]
  ```
  Options:
  - `-f`: Input file to send (required)
  - `-p`: Port to send to (default: 18888)
  - `-c`: Path to Apptainer container (default: cpu_emu.sif)

## Setup

1. Build/Pull the container:
```bash
./build_container.sh
```
This will pull the pre-built Docker image and convert it to Apptainer format.

## Usage Example (All on Same Machine)

1. Start the receiver:
```bash
./run_receiver.sh -p 18080 -o received_data
```

2. Start the CPU emulator:
```bash
./run_sender.sh -p 18080 -r 18888 -t 4 -v 1 -b 2 -m 1 -o 1
```

3. Send data:
```bash
echo "test data" > input.txt
./run_source.sh -f input.txt -p 18888
```

## Network Testing

Use the provided test script to verify localhost connectivity:
```bash
./test_network.sh
```

The test script checks:
- Local port accessibility
- Container network configuration
- Localhost communication

## Troubleshooting

1. Connection Issues:
   - Verify all components are running on the same machine
   - Check if ports are already in use
   - Ensure all components use localhost (127.0.0.1)

2. Container Issues:
   - Ensure the container image exists (`cpu_emu.sif`)
   - Check container bind mounts and permissions

3. Data Transfer Issues:
   - Check file permissions
   - Verify port numbers match between components
   - Ensure all components are using localhost

## Network Requirements

- Open local ports:
  * 18888: For source → CPU emulator communication
  * 18080: For CPU emulator → receiver communication
- Permission to bind to ports on localhost
- All components must run on the same machine

## Notes

- The system uses high ports (>1024) to avoid permission issues
- All components must use localhost (127.0.0.1) for communication
- Remote connections between components are not supported
- All components must run on the same machine