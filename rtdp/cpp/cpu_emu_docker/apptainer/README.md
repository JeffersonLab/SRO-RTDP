# CPU Emulator Apptainer Setup

This directory contains scripts to convert a CPU emulator Docker image from a container registry to Apptainer SIF format and run it using Apptainer.

## Prerequisites

- Apptainer (formerly Singularity) installed on your system
- Access to the Docker image in a container registry (e.g., Docker Hub, Azure Container Registry, etc.)

## Converting Docker Image to SIF

To convert a Docker image to Apptainer SIF format, run:

```bash
./build.sh -i <docker-image>
```

Required:
- `-i DOCKER_IMAGE`: Full path to Docker image (e.g., 'username/cpu-emu:latest')

Optional:
- `-o SIF_NAME`: Output SIF file name (default: cpu-emu.sif)

Examples:
```bash
# Pull from Docker Hub
./build.sh -i docker.io/username/cpu-emu:latest

```

This will create a `cpu-emu.sif` file that can be used with Apptainer.

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
- Network ports and filesystem access are automatically handled by Apptainer
- The SIF file is portable and can be moved to other systems with Apptainer installed
- No root privileges are required to run the container
- Make sure you have proper authentication if pulling from a private registry