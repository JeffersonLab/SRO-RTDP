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
   - Runs on port 8888
   - Uses 4 CPU cores and 8GB memory
   - Outputs received data to `$CYLC_WORKFLOW_SHARE_DIR/output/received_data.bin`

2. **Emulator**: Processes data and forwards it to the receiver
   - Connects to receiver on port 8888
   - Uses 8 CPU cores and 16GB memory
   - Configurable parameters:
     - Threads: 5
     - Latency: 500 nsec/byte
     - Memory footprint: 0.05 GB
     - Output size: 0.001 GB
     - Sleep mode: 0 (burn CPU)

3. **Sender**: Sends test data to the emulator
   - Connects to emulator on port 5555
   - Uses 4 CPU cores and 8GB memory
   - Sends 10 events of 10MB each

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
- `emulator/`: Emulator logs
- `sender/`: Sender logs

Each component's logs include:
- `stdout.log`: Standard output
- `stderr.log`: Standard error
- `apptainer.log`: Apptainer container logs
- `process.log`: Process details (for receiver and emulator)
- `memory.log`: Memory usage (for emulator)

## Output

Received data is stored in:
`$CYLC_WORKFLOW_SHARE_DIR/output/received_data.bin` 