# CPU Emulator Apptainer Setup

This directory contains scripts to build a CPU emulator Apptainer container from a definition file.

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

Note: The build process uses Apptainer's fakeroot feature, which allows building containers without root privileges. Make sure your Apptainer installation has fakeroot properly configured.

## Container Functionality

The container provides three main modes of operation:

1. CPU Emulator Mode (Default):
   ```bash
   ./cpu-emu.sif [options]
   ```
   Options include:
   - Standard CPU emulator parameters
   - `--output-dir`: Specify output directory (default: /output)

2. Data Sender Mode:
   ```bash
   ./cpu-emu.sif send <HOST> <PORT>
   ```
   Sends data to a specified host and port using ZMQ REQ socket.

3. Data Receiver Mode:
   ```bash
   ./cpu-emu.sif receive <PORT> [BIND_IP]
   ```
   Receives data on specified port using ZMQ REP socket.

## Example Usage

1. Start a receiver:
```bash
./cpu-emu.sif receive 50080
```

2. Run the CPU emulator:
```bash
./cpu-emu.sif --output-dir /path/to/output [other options]
```

3. Send data:
```bash
./cpu-emu.sif send localhost 50080 < input_data
```

## Container Details

The container includes:
- Ubuntu 22.04 base image
- G++ compiler and build tools
- ZMQ libraries (libzmq3-dev)
- Python 3 with pyzmq
- Built-in sender and receiver scripts
- Pre-built CPU emulator executable

## Notes

- The SIF file contains all necessary dependencies
- Network ports are accessed directly from the host (no network isolation)
- No root privileges required to run the container (only for building)
- The SIF file is portable and can be moved to other systems with Apptainer installed
- Output directory is mounted automatically for data persistence

## HPC Environment Notes
- Designed to work in unprivileged HPC environments
- No special network configuration required
- Works with standard user permissions
- Compatible with job scheduler environments (Slurm, PBS, etc.)
- Uses standard TCP/IP networking available on compute nodes