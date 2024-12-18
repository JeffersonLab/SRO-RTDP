# CPU Emulator Apptainer Example Scripts

This directory contains example scripts to demonstrate the usage of the CPU emulator using Apptainer. The scripts provide a complete testing setup with source, CPU emulator, and receiver components.

## Prerequisites

- Apptainer installed on your system
- The CPU emulator SIF file (built using `../build.sh`)

## Scripts Overview

1. `start_receiver.sh`: Starts a netcat listener using the container to receive processed data
2. `start_cpu_emu.sh`: Runs the CPU emulator using Apptainer
3. `send_data.sh`: Sends test data using the container

## Example Usage

### 1. Start the Receiver

```bash
./start_receiver.sh -p 50080 -o output.bin -b 0.0.0.0
```

Options:
- `-p PORT`: Port to listen on (default: 50080)
- `-o FILE`: Output file (default: received_data.bin)
- `-b BIND_IP`: IP address to bind to (default: 0.0.0.0)
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
- `-i SIF_PATH`: Path to the SIF file (default: ../cpu-emu.sif)

The script will create an `input` directory in the current working directory for temporary files when generating random data.

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

This will:
1. Start a receiver listening on port 50080
2. Start the CPU emulator with 4 threads, 50 seconds latency per GB, and 0.2GB memory footprint
3. Send 100MB of random test data through the system

### Multi-Machine Test

Assume we have three machines:
- Machine A (IP: 192.168.1.10) - Will run the sender
- Machine B (IP: 192.168.1.20) - Will run the CPU emulator
- Machine C (IP: 192.168.1.30) - Will run the receiver

First, ensure the SIF file is copied to all machines:
```bash
# Copy SIF file to each machine (run from the build machine)
scp ../cpu-emu.sif user@192.168.1.10:~/cpu-emu.sif
scp ../cpu-emu.sif user@192.168.1.20:~/cpu-emu.sif
scp ../cpu-emu.sif user@192.168.1.30:~/cpu-emu.sif
```

Then run the components:

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

This setup:
1. Starts a receiver on Machine C:
   - Binds to its IP (192.168.1.30) on port 50080
   - Only accepts connections to its specific IP
2. Starts the CPU emulator on Machine B:
   - Listens on port 50888 for incoming data from the sender
   - Forwards processed data to Machine C (192.168.1.30) on port 50080
3. Sends data from Machine A to Machine B's CPU emulator
4. The CPU emulator processes the data and forwards it to the receiver

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
- The same container is used for CPU emulator, receiver, and sender functionality
- The container's `/output` directory is bound to `./output` in your current working directory
- Input files are bound to `/data` in the container when sending data
- All files will be created with your user permissions in the input and output directories

## Network Requirements
- Ensure the firewall on each machine allows the required ports (50080 and 50888 by default)
- All machines must have network connectivity to each other
- Each machine must have Apptainer installed
- The SIF file must be accessible on each machine