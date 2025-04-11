# CPU Emulator Apptainer Setup

This directory contains scripts to convert a CPU emulator Docker image from Docker Hub to Apptainer SIF format and run it using Apptainer.

## Prerequisites

- Apptainer (formerly Singularity) installed on your system
- Internet access to pull from Docker Hub
- Docker Hub image location (e.g., username/image:tag)

## Converting Docker Image to SIF

To convert the Docker image to Apptainer SIF format, run:

```bash
./build.sh -i <docker-hub-image>
```

Required:
- `-i DOCKER_IMAGE`: Docker Hub image location (e.g., 'username/image:tag')

Optional:
- `-o SIF_NAME`: Output SIF file name (default: cpu-emu.sif)

Example:
```bash
./build.sh -i jlabtsai/rtdp-cpu_emu:latest
```

This will:
1. Pull the specified image from Docker Hub
2. Convert it to a SIF file (default name: cpu-emu.sif)

## Example Scripts

The `example` directory contains scripts demonstrating how to use the CPU emulator with Apptainer:

1. `start_receiver.sh`: Starts a netcat listener to receive processed data
2. `start_cpu_emu.sh`: Runs the CPU emulator using Apptainer
3. `send_data.sh`: Sends test data to the CPU emulator

### Basic Usage

1. Start the receiver:
```bash
cd example
./start_receiver.sh -p 50080
```

2. Start the CPU emulator:
```bash
./start_cpu_emu.sh -t 4 -b 50 -m 0.2 -o 0.001 -v
```

3. Send test data:
```bash
./send_data.sh -s 100M
```

See the README in the example directory for more detailed usage instructions.

## Notes

- The SIF file contains all necessary dependencies
- Network ports are accessed directly from the host (no network isolation)
- No root privileges required to run the container
- The SIF file is portable and can be moved to other systems with Apptainer installed
- Make sure you have access to the Docker Hub image you're trying to pull

## HPC Environment Notes
- Designed to work in unprivileged HPC environments
- No special network configuration required
- Works with standard user permissions
- Compatible with job scheduler environments (Slurm, PBS, etc.)
- Uses standard TCP/IP networking available on compute nodes