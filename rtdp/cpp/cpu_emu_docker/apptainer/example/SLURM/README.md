# CPU Emulator SLURM Test Scripts

This directory contains SLURM scripts for testing the CPU emulator in a distributed environment. The test involves three components running on separate nodes:

1. Receiver: Listens for processed data
2. Emulator: Performs CPU-intensive processing
3. Sender: Sends test data through the system

## Prerequisites

- SLURM cluster access
- Apptainer installed on compute nodes
- CPU emulator SIF file (../../cpu-emu.sif)

## Script Overview

- `ifarm_receiver.slurm`: Starts the data receiver
- `ifarm_emulator.slurm`: Runs the CPU emulator
- `ifarm_sender.slurm`: Sends test data
- `submit_jobs.bash`: Orchestrates job submission
- `cleanup.bash`: Cleans up test files and cancels jobs

## File Structure

```
.
├── README.md
├── cleanup.bash
├── ifarm_emulator.slurm
├── ifarm_receiver.slurm
├── ifarm_sender.slurm
└── submit_jobs.bash
```

## Usage

1. Clean up any previous test:
```bash
./cleanup.bash
```

2. Submit the jobs:
```bash
./submit_jobs.bash
```

The submit script will:
- Start the receiver
- Start the emulator (connecting to receiver)
- Start the sender (connecting to emulator)

## Configuration

### Port Numbers
- Receiver: 50080
- Emulator: 50888

These can be modified in `submit_jobs.bash`.

### Resource Allocation

Receiver:
```bash
#SBATCH --cpus-per-task=4
#SBATCH --mem=8G
```

Emulator:
```bash
#SBATCH --cpus-per-task=4
#SBATCH --mem=16G
```

Sender:
```bash
#SBATCH --cpus-per-task=4
#SBATCH --mem=8G
```

Modify these in the respective SLURM scripts as needed.

### Emulator Parameters

In `ifarm_emulator.slurm`:
```bash
THREADS=4                  # Number of processing threads
LATENCY=50                # Processing latency per GB
MEM_FOOTPRINT=0.05        # Memory footprint in GB
OUTPUT_SIZE=0.001         # Output size in GB
```

### Test Data Size

In `ifarm_sender.slurm`:
```bash
DATA_SIZE="100M"          # Size of test data to send
```

## Output Files

- `node_info.txt`: Contains node and IP information for all components
- `slurm_*_*.log`: SLURM job output logs
- `memory_monitor.log`: Memory usage log from emulator
- `received_data.bin`: Data received by receiver
- `input/`: Directory containing generated test data
- `output/`: Directory containing emulator output

## Monitoring

1. Check job status:
```bash
squeue --name=cpu-emu-recv
squeue --name=cpu-emu
squeue --name=cpu-emu-send
```

2. View node assignments:
```bash
cat node_info.txt
```

3. Monitor memory usage:
```bash
tail -f memory_monitor.log
```

4. View job logs:
```bash
tail -f slurm_*_*.log
```

## Troubleshooting

1. If jobs fail to start:
   - Check SLURM partition availability
   - Verify SIF file path
   - Ensure ports are available

2. If connections fail:
   - Check node_info.txt for correct IPs
   - Verify port numbers
   - Check network connectivity between nodes

3. If emulator crashes:
   - Check memory_monitor.log for OOM events
   - Reduce memory footprint or increase allocation
   - Adjust number of threads

4. Clean up and retry:
```bash
./cleanup.bash
./submit_jobs.bash
```

## Notes

- Jobs communicate using IP addresses rather than hostnames
- Each job records its information in node_info.txt
- The emulator includes memory monitoring
- All processes run under Apptainer containers
- Files are cleaned up between runs

## Example Output

Successful run:
```
Receiver node: node1 (IP: 192.168.1.101)
Emulator node: node2 (IP: 192.168.1.102)
Sender node: node3 (IP: 192.168.1.103)
```

Node info file:
```
Receiver Node: node1
Receiver IP: 192.168.1.101
Receiver Job: 12345

Emulator Node: node2
Emulator IP: 192.168.1.102
Emulator Job: 12346

Sender Node: node3
Sender IP: 192.168.1.103
Sender Job: 12347
``` 