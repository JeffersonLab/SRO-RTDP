# CPU Emulator Cylc Workflow

This directory contains the Cylc workflow for running the CPU emulator test with sender and receiver components.

## Directory Structure

- `flow.cylc`: Main workflow definition file
- `global.cylc`: Global Cylc configuration
- `build.sh`: Script to build the Apptainer container
- `sifs/`: Directory containing the Apptainer container (created by build.sh)

## Workflow Components

The workflow consists of three main components:

1. **Receiver**: Receives data from the emulator
   - Runs on port 55555
   - Uses 4 CPU cores and 8GB memory
   - Outputs received data to `$CYLC_WORKFLOW_SHARE_DIR/output/received_data.bin`
   - Monitors data transfer progress and logs to `$CYLC_WORKFLOW_SHARE_DIR/logs/receiver/`

2. **Emulator**: Processes data and forwards it to the receiver
   - Connects to receiver on port 55555
   - Uses 8 CPU cores and 16GB memory
   - Configurable parameters:
     - Threads: 1
     - Latency: 100 nsec/byte
     - Memory footprint: 0.01 GB
     - Output size: 0.001 GB
     - Sleep mode: 0 (burn CPU)
     - Verbosity: 2
   - Network settings:
     - Base port: 55555
     - Components: 5
     - Average rate: 50
     - RMS: 0.3
     - Duty cycle: 0.7
     - NIC: 100

3. **Sender**: Sends test data to the emulator
   - Connects to emulator on port 55555
   - Uses 4 CPU cores and 8GB memory
   - Sends data with configured network parameters

## Building the Container

To build the Apptainer container:

```bash
./build.sh
```

This will create `sifs/cpu-emu.sif` from the Dockerfile in the parent directory.

## Running the Workflow

1. Make sure the Apptainer container is built
2. Copy `global.cylc` to `~/.cylc/flows/global.cylc`
3. Run the workflow:

```bash
cylc install .
cylc play cpu-emu
```

## Monitoring

Logs are stored in `$CYLC_WORKFLOW_SHARE_DIR/logs/` for each component:
- `receiver/`: Receiver logs
  - `stdout.log`: Standard output
  - `stderr.log`: Standard error
  - `apptainer.log`: Apptainer container logs
  - `process.log`: Process details and status
- `emulator/`: Emulator logs
  - `stdout.log`: Standard output
  - `stderr.log`: Standard error
  - `apptainer.log`: Apptainer container logs
  - `memory.log`: Memory usage and process details
- `sender/`: Sender logs
  - `stdout.log`: Standard output
  - `stderr.log`: Standard error
  - `apptainer.log`: Apptainer container logs

## Output

Received data is stored in:
`$CYLC_WORKFLOW_SHARE_DIR/output/received_data.bin`

## Platform Configuration

The workflow runs on the JLab SLURM platform with the following settings:
- Platform: jlab_slurm
- Host: ifarm2401
- Partition: ifarm
- Job timeout: 2 hours
- Tasks per job: 1 