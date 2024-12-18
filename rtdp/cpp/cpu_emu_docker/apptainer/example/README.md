# CPU Emulator Apptainer Example Scripts

This directory contains example scripts to demonstrate the usage of the CPU emulator using Apptainer. The scripts provide a complete testing setup with source, CPU emulator, and receiver components.

## Prerequisites

- Apptainer installed on your system
- The CPU emulator SIF file (built using `../build.sh`)

## Scripts Overview

1. `start_receiver.sh`: Starts a netcat listener using the container to receive processed data
2. `start_cpu_emu.sh`: Runs the CPU emulator using Apptainer
3. `send_data.sh`: Sends test data to the CPU emulator

## Example Usage

### 1. Start the Receiver

```bash
./start_receiver.sh -p 50080 -o output.bin
```

Options:
- `-p PORT`: Port to listen on (default: 50080)
- `-o FILE`: Output file (default: received_data.bin)
- `-f SIF_PATH`: Path to the SIF file (default: ../cpu-emu.sif)

### 2. Start the CPU Emulator

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

The CPU emulator will create an `output` directory in the current working directory to store its output files. This directory is automatically bound to the container's `/output` directory.

### 3. Send Test Data

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

## Complete Test Example

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

This will:
1. Start a receiver listening on port 50080
2. Start the CPU emulator with 4 threads, 50 seconds latency per GB, and 0.2GB memory footprint
3. Send 100MB of random test data through the system

## Note on Port Numbers
The scripts use high port numbers by default:
- Receiver port: 50080
- CPU emulator receive port: 50888

These high port numbers (above 49152) are in the dynamic/private port range and are less likely to conflict with other services.

## Apptainer-specific Notes
- The scripts assume the SIF file is located at `../cpu-emu.sif`
- Use the `-f` option with any script if your SIF file is in a different location
- Apptainer automatically handles network and filesystem access
- No special privileges are required to run these scripts
- The same container is used for both CPU emulator and receiver functionality
- The container's `/output` directory is bound to `./output` in your current working directory
- All output files will be created with your user permissions in the output directory