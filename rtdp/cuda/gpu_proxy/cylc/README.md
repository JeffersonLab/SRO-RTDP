# GPU Proxy Cylc Workflow Setup

This document provides instructions for building the Apptainer image and installing the Cylc workflow for the GPU proxy system.

## Prerequisites

- Access to a system with Apptainer/Singularity installed
- Access to a system with Cylc installed
- Access to a GPU node (preferably with A100 100G GPU)

## Building the Apptainer Image

1. Navigate to the GPU proxy directory:
```bash
cd /home/jeng-yuantsai/RTDP/SRO-RTDP/rtdp/cuda/gpu_proxy/cylc
```

2. Run the build script:
```bash
./build.sh
```

This script will:
- Create the `sifs` directory if it doesn't exist
- Build the Apptainer container from the Docker image
- Place the container in `sifs/gpu-proxy.sif`

## Installing the Cylc Workflow

1. Run the install script:
```bash
./install.sh
```

This script will:
- Create necessary directories (sifs, etc/config, scripts)
- Install the workflow using Cylc
- Validate the workflow configuration
- Provide instructions for running the workflow

## Running the Workflow

1. Start the workflow:
```bash
cylc play gpu-proxy
```

2. Monitor the workflow:
```bash
# View workflow status
cylc gui gpu-proxy

# View task logs
cylc cat-log gpu-proxy//1/<task_name>
```

3. Stop the workflow:
```bash
cylc stop gpu-proxy
```

## Troubleshooting

### Common Issues

1. **Apptainer Build Fails**
   - Ensure you have sufficient disk space
   - Check network connectivity
   - Verify Docker Hub credentials if using private repository

2. **Workflow Fails to Start**
   - Check Cylc configuration
   - Verify all required directories exist
   - Check permissions on workflow directories

3. **GPU Access Issues**
   - Verify GPU node allocation
   - Check GPU driver compatibility
   - Ensure proper CUDA version

### Log Files

Workflow logs are stored in:
- `~/cylc-run/gpu-proxy/log/job/1/`
- `~/cylc-run/gpu-proxy/share/logs/`

Task-specific logs:
- Receiver: `~/cylc-run/gpu-proxy/share/logs/receiver/`
- GPU Proxy: `~/cylc-run/gpu-proxy/share/logs/proxy/`
- Sender: `~/cylc-run/gpu-proxy/share/logs/sender/`

## Cleanup

To remove the workflow:
```bash
cylc clean gpu-proxy
```

## Notes

- The workflow is configured to use A100 100G GPU with 100GB memory
- Default ports are 55555 (input) and 55556 (output)
- Matrix width is set to 2048 by default
- Send rate is set to 25 MB/s by default
- Group size is set to 2048 by default

For more information, refer to:
- [Cylc Documentation](https://cylc.github.io/cylc-doc/stable/html/)
- [Apptainer Documentation](https://apptainer.org/docs/)
- [GPU Proxy Documentation](../README.container.md) 