# CPU Emulator Apptainer Example Scripts

This directory contains example scripts to demonstrate the usage of the CPU emulator using Apptainer. The scripts provide a complete testing setup with source, CPU emulator, and receiver components.

## Prerequisites

- Apptainer installed on your system
- The CPU emulator SIF file (built using `../build.sh`)

## Scripts Overview

1. `start_receiver.sh`: Starts the container in receiver mode to receive processed data
2. `start_cpu_emu.sh`: Runs the CPU emulator using the container
3. `send_data.sh`: Sends test data using the container's built-in sender
4. `test_connectivity.sh`: Tests network connectivity using the container

## Example Usage

### 1. Test Network Connectivity (Optional but Recommended)

Before running the actual components, you can test network connectivity:

```bash
./test_connectivity.sh -h remote_host -p 50888
```

Options:
- `-h HOST`: Target host to test (default: localhost)
- `-p PORT`: Target port to test (default: 50888)
- `-i SIF_PATH`: Path to the SIF file (default: ../cpu-emu.sif)
- `-t TEST_IMAGE`: Test container image (default: docker://ubuntu:22.04)

### 2. Start the Receiver

```bash
./start_receiver.sh -p 50080 -o output.bin -b 0.0.0.0
```

Options:
- `-p PORT`: Port to listen on (default: 50080)
- `-o FILE`: Output file (default: received_data.bin)
- `-b BIND_IP`: IP address to bind to (default: 0.0.0.0)
- `-f SIF_PATH`: Path to the SIF file (default: ../cpu-emu.sif)

### 3. Start the CPU Emulator

```bash
./start_cpu_emu.sh -t 10 -b 100 -m 0.1 -o 0.001 -i "127.0.0.1"
```

Options:
- `-t THREADS`: Number of threads (default: 10)
- `-b LATENCY`: Seconds thread latency per GB input (default: 100)
- `-m MEM`: Thread memory footprint in GB (default: 0.1)
- `-o OUTPUT`: Output size in GB (default: 0.001)
- `-r RECV_PORT`: Receive port (default: 50888)
- `-p DEST_PORT`: Destination port (default: 50080)
- `-i DEST_IP`: Destination IP (default: 127.0.0.1)
- `-f SIF_PATH`: Path to the SIF file (default: ../cpu-emu.sif)
- `-s`: Use sleep mode instead of CPU burn
- `-v`: Enable verbose mode

### 4. Send Test Data

```bash
./send_data.sh -s 10M
```

or

```bash
./send_data.sh -f input.bin
```

Options:
- `-h HOST`: Target host (default: localhost)
- `-p PORT`: Target port (default: 50888)
- `-f FILE`: Input file to send
- `-s SIZE`: Size of random data to generate if no input file (default: 10M)
- `-i SIF_PATH`: Path to the SIF file (default: ../cpu-emu.sif)

## Example Test Setups

### Single Machine Test

1. In terminal 1 (receiver):
```bash
./start_receiver.sh -p 50080
```

2. In terminal 2 (CPU emulator):
```bash
./start_cpu_emu.sh -t 4 -b 50 -m 0.2 -o 0.001 -v
```

3. In terminal 3 (source):
```bash
./send_data.sh -s 100M
```

### Multi-Machine Test

Assume we have three machines:
- Machine A (IP: 192.168.1.10) - Will run the sender
- Machine B (IP: 192.168.1.20) - Will run the CPU emulator
- Machine C (IP: 192.168.1.30) - Will run the receiver

1. On Machine C (receiver):
```bash
./start_receiver.sh -p 50080 -b 192.168.1.30 -f ~/cpu-emu.sif
```

2. On Machine B (CPU emulator):
```bash
./start_cpu_emu.sh -t 4 -b 50 -m 0.2 -o 0.001 -i "192.168.1.30" -p 50080 -r 50888 -v -f ~/cpu-emu.sif
```

3. On Machine A (sender):
```bash
./send_data.sh -h 192.168.1.20 -p 50888 -s 100M -f ~/cpu-emu.sif
```

## Container Details

The container includes:
- Built-in Python sender and receiver scripts using ZMQ
- Pre-built CPU emulator executable
- Automatic output directory handling
- Three operation modes:
  1. CPU Emulator mode (default)
  2. Sender mode (`send` command)
  3. Receiver mode (`receive` command)

## Network Requirements
- Ensure the firewall on each machine allows the required ports
- All machines must have network connectivity to each other
- All machines must have Apptainer installed
- The SIF file must be accessible on all machines

## Troubleshooting

1. Use `test_connectivity.sh` to verify network connectivity
2. Check firewall settings if connectivity tests fail
3. Ensure all required ports are open and not in use
4. Verify that the SIF file is accessible and valid on all machines

## Notes
- The container uses ZMQ for all network communication
- No root privileges or special network configuration needed
- User permissions are preserved for all file operations
- Compatible with HPC environments and job schedulers
- Works with standard TCP/IP networking