# CPU Emulator Apptainer Setup

This directory contains scripts to build a CPU emulator Apptainer container that provides a complete testing environment with source, CPU emulator, and receiver components.

## Prerequisites

- Apptainer (formerly Singularity) installed on your system
- Fakeroot support enabled in Apptainer configuration
- Required source files in parent directory:
  - cpu_emu.cc
  - cpu_emu.yaml
  - buildp

## Building the Container

To build the Apptainer container from the definition file, run:

```bash
./build.sh [-o SIF_NAME] [-d DEF_FILE]
```

Optional arguments:
- `-o SIF_NAME`: Output SIF file name (default: cpu-emu.sif)
- `-d DEF_FILE`: Definition file path (default: cpu-emu.def)

Example:
```bash
./build.sh -o cpu-emu.sif -d cpu-emu.def
```

Note: The build process uses Apptainer's fakeroot feature, which allows building containers without root privileges.

## Container Functionality

The container provides three main operation modes:

1. CPU Emulator Mode (Default):
   ```bash
   apptainer run cpu-emu.sif [options]
   ```
   Options:
   - `-t THREADS`: Number of threads (default: 10)
   - `-b LATENCY`: Seconds thread latency per GB input (default: 100)
   - `-m MEM`: Thread memory footprint in GB (default: 0.1)
   - `-o OUTPUT`: Output size in GB (default: 0.001)
   - `-r RECV_PORT`: Receive port (default: 50888)
   - `-p DEST_PORT`: Destination port (default: 50080)
   - `-i DEST_IP`: Destination IP (default: 127.0.0.1)
   - `-s`: Use sleep mode instead of CPU burn
   - `-v`: Enable verbose mode
   - `--output-dir DIR`: Output directory (default: /output)

2. Data Sender Mode:
   ```bash
   apptainer run cpu-emu.sif send <HOST> <PORT>
   ```
   Sends data from stdin to a specified host and port using ZMQ REQ socket.
   Waits for ACK response from the receiver.

3. Data Receiver Mode:
   ```bash
   apptainer run cpu-emu.sif receive <PORT> [BIND_IP]
   ```
   Receives data on specified port using ZMQ REP socket.
   Sends ACK responses back to the sender.
   Outputs received data to stdout.

## Example Scripts

The `example` directory contains scripts demonstrating the container's usage:

1. `start_receiver.sh`: Starts the container in receiver mode
2. `start_cpu_emu.sh`: Runs the CPU emulator
3. `send_data.sh`: Sends test data using the container
4. `test_connectivity.sh`: Tests network connectivity
5. `test_data_sizes.sh`: Tests various input data sizes

See the `example/README.md` for detailed usage instructions and test setups.

## Container Details

The container includes:
- Ubuntu 22.04 base image
- G++ compiler and build tools
- ZMQ libraries (libzmq3-dev)
- Python 3 with pyzmq
- Built-in Python sender and receiver scripts
- Pre-built CPU emulator executable

## Communication Protocol

The container uses ZMQ (ZeroMQ) for all network communication:
1. Sender -> CPU Emulator: ZMQ REQ-REP pattern
2. CPU Emulator -> Receiver: ZMQ REQ-REP pattern
3. All communications use TCP transport
4. ACK messages confirm successful data reception

## Data Flow

1. Sender sends data to CPU Emulator
2. CPU Emulator:
   - Receives data and reports size
   - Processes data using specified threads and parameters
   - Forwards processed data to receiver
   - Waits for ACK from receiver
3. Receiver:
   - Receives processed data
   - Sends ACK back to CPU Emulator
   - Outputs data to specified destination

## Output Handling

- Output files are stored in the specified output directory
- The container automatically handles directory mounting
- Output permissions match the user running the container
- Each run creates new output files with unique timestamps

## Notes

- No root privileges required to run the container
- Network ports are accessed directly from the host
- Compatible with HPC environments and job schedulers
- Works with standard TCP/IP networking
- Supports both single-machine and multi-machine setups
- All components use ZMQ for reliable communication
- ACK messages ensure reliable data transfer
- Verbose mode available for debugging

## Troubleshooting

1. Use `test_connectivity.sh` to verify network connectivity
2. Check firewall settings if connectivity fails
3. Verify port availability and permissions
4. Use verbose mode (-v) for detailed logging
5. Check ACK messages for communication status
6. Verify SIF file accessibility on all machines

## Security Notes

- The container runs unprivileged
- Network access uses standard TCP ports
- No special permissions required
- User permissions are preserved
- Temporary files are cleaned up automatically
- Bind addresses can be restricted for security