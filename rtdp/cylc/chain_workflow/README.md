# Chain Workflow for CPU Emulator and GPU Proxy

This workflow implements a chain process that connects the CPU emulator and GPU proxy components in the following order:

```
sender -> cpu_emu -> gpu_proxy -> receiver
```

## Prerequisites

- Access to JLab's HPC environment (ifarm)
- Apptainer/Singularity installed
- Access to the Docker images:
  - jlabtsai/rtdp-cpu_emu:latest
  - jlabtsai/rtdp-gpu_proxy:latest

## Setup

1. Build the SIF files:
   ```bash
   ./build.sh
   ```
   This will create the necessary SIF files in the `sifs/` directory.

2. Install the workflow:
   ```bash
   cylc install .
   ```

## Running the Workflow

1. Start the workflow:
   ```bash
   cylc play chain_workflow
   ```

2. Monitor the workflow:
   ```bash
   cylc gui chain_workflow
   ```

## Workflow Components

The workflow consists of four main components:

1. **Sender**: Sends data to the CPU emulator
2. **CPU Emulator**: Processes data and forwards to GPU proxy
3. **GPU Proxy**: Processes data on GPU and forwards to receiver
4. **Receiver**: Receives and stores the final processed data

## Data Flow

1. The sender component sends data to the CPU emulator
2. The CPU emulator processes the data and forwards it to the GPU proxy
3. The GPU proxy processes the data using GPU acceleration and forwards it to the receiver
4. The receiver stores the final processed data

## Logs and Output

- Logs are stored in `$CYLC_WORKFLOW_SHARE_DIR/logs/`
- Output data is stored in `$CYLC_WORKFLOW_SHARE_DIR/output/`

## Resource Requirements

- CPU Emulator: 4 CPUs, 8GB RAM
- GPU Proxy: 4 CPUs, 16GB RAM, 1 GPU
- Sender/Receiver: 4 CPUs, 8GB RAM each

## Troubleshooting

1. Check the logs in `$CYLC_WORKFLOW_SHARE_DIR/logs/` for each component
2. Verify that the SIF files are built correctly
3. Ensure all components can communicate over the network
4. Check GPU availability on the HPC system 