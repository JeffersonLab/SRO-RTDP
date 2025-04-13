# GPU Proxy Cylc Workflow

This workflow is designed to test the GPU proxy functionality using Cylc workflow manager. The workflow consists of three components:
- Sender: Sends test data to the GPU proxy
- GPU Proxy: Processes data using GPU
- Receiver: Receives processed data from GPU proxy

## Prerequisites

- Access to JLab's SLURM cluster
- Apptainer (formerly Singularity) installed
- Cylc workflow manager installed
- Docker image `jlabtsai/rtdp-gpu_proxy:latest` available

## Directory Structure

```
cylc/
├── config.yml          # Workflow configuration
├── global.cylc         # Global workflow settings
├── flow.cylc          # Workflow graph definition
├── build.sh           # Script to build Apptainer container
├── cleanup.sh         # Script to clean up workflow artifacts
├── install.sh         # Script to install dependencies
└── containers/        # Directory for Apptainer containers
```

## Setup and Usage

1. **Install Dependencies**
   ```bash
   ./install.sh
   ```

2. **Build Apptainer Container**
   ```bash
   ./build.sh
   ```

3. **Start the Workflow**
   ```bash
   cylc play gpu-proxy
   ```

4. **Monitor the Workflow**
   ```bash
   cylc tui gpu-proxy
   ```

5. **Cleanup (when done)**
   ```bash
   ./cleanup.sh
   ```

## Resource Requirements

- **Sender Node**:
  - 4 CPUs
  - 8GB memory
  - ifarm partition

- **GPU Proxy Node**:
  - 1 GPU
  - 4 CPUs
  - 16GB memory
  - gpu partition

- **Receiver Node**:
  - 4 CPUs
  - 8GB memory
  - ifarm partition

## Network Configuration

- Receiver Port: 50080
- GPU Proxy Port: 50888

## Troubleshooting

1. If the workflow fails to start:
   - Check if all required modules are loaded
   - Verify that the Docker image exists
   - Ensure you have sufficient permissions on the cluster

2. If the GPU proxy fails:
   - Check GPU availability
   - Verify CUDA drivers are properly loaded
   - Check container logs for errors

3. If network communication fails:
   - Verify ports are not in use
   - Check firewall settings
   - Ensure all nodes can communicate with each other

## Notes

- The workflow uses Apptainer containers for isolation and reproducibility
- GPU access is enabled through the `--nv` flag in Apptainer
- All components communicate via TCP/IP sockets 