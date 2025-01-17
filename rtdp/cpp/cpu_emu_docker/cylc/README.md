# Cylc-based CPU Emulator Testing Workflow

This workflow automates CPU emulator performance tests using Cylc workflow automation tool on JLab's ifarm cluster.

## Directory Structure

```
cylc/
├── flow.cylc                     # Main Cylc workflow definition
├── build.sh                      # SIF file build script
├── cleanup.sh                    # Cleanup script
├── install.sh                    # Installation script
├── README.md                     # This file
├── scripts/                      # Script directory
├── etc/                         # Configuration directory
│   └── config/                  # For any additional config files
└── sifs/                        # Container images directory
    └── cpu-emu.sif              # CPU emulator container
```

## Prerequisites

1. Access to JLab's ifarm cluster
2. Cylc workflow engine installed (version 8 or higher)
3. Apptainer installed for container execution
4. Docker image of CPU emulator available (e.g., jlabtsai/rtdp-cpu_emu:latest)

## Platform Configuration

The workflow uses JLab's SLURM cluster. Configure your global Cylc settings in `~/.cylc/flow/global.cylc`:

```
[platforms]
    [[jlab_slurm]]
        cylc path = /path/to/your/cylc-env/bin/
        hosts = tsai@ifarm2402
        job runner = slurm
        install target = localhost
```

## Configuration

The workflow uses Cylc environment variables for configuration. Key settings in `flow.cylc`:

```
[runtime]
    [[root]]
        [[[environment]]]
            # Network configuration
            RECEIVER_PORT = "50080"
            EMULATOR_PORT = "50888"
            
            # Emulator configuration
            EMU_THREADS = "4"              # Number of processing threads
            EMU_LATENCY = "50"            # Processing latency per GB
            EMU_MEM_FOOTPRINT = "0.05"    # Memory footprint in GB
            EMU_OUTPUT_SIZE = "0.001"     # Output size in GB
            
            # Test data configuration
            TEST_DATA_SIZE = "100M"       # Size of test data to send
```

## Installation

1. Create necessary directories:
```bash
mkdir -p sifs etc/config scripts
```

2. Build the SIF file:
```bash
./build.sh -i jlabtsai/rtdp-cpu_emu:latest
```

3. Run the installation script:
```bash
chmod +x install.sh
./install.sh
```

Note: The installation script will:
- Create required directories
- Install the workflow using `cylc install`
- Validate the workflow

The workflow will be installed to `~/cylc-run/cpu-emu/`.

## Running the Workflow

1. Validate the workflow:
```bash
cylc validate .
```

2. Start the workflow:
```bash
# First time after installation:
cylc install  # If not already installed
cylc play cpu-emu
```

## Workflow Tasks and Dependencies

The workflow follows this dependency chain:
```
receiver:ready => emulator => emulator:ready => sender
```

1. **receiver**: 
   - Starts netcat listener
   - Records hostname/IP
   - Monitors port availability
   - Signals readiness with `receiver:ready` message

2. **emulator**:
   - Waits for `receiver:ready` message
   - Records hostname/IP
   - Starts CPU emulator
   - Monitors memory usage
   - Signals readiness with `emulator:ready` message

3. **sender**:
   - Waits for `emulator:ready` message
   - Generates test data
   - Sends data through system
   - Cleans up temporary files

## Shared Resources

The workflow uses `$CYLC_WORKFLOW_SHARE_DIR` to share information between tasks:
- `receiver_hostname`: Written by receiver, read by emulator
- `receiver_ip`: Written by receiver, read by emulator
- `emulator_hostname`: Written by emulator, read by sender
- `emulator_ip`: Written by emulator, read by sender

## Task Resources

### Receiver Task
- CPUs: 4
- Memory: 8GB
- Output: `received_data.bin`

### Emulator Task
- CPUs: 4
- Memory: 16GB
- Output: Memory monitoring logs
- Binds: `output` directory

### Sender Task
- CPUs: 4
- Memory: 8GB
- Input: Generated random data
- Binds: `input` directory

## Output and Data

- Task logs: `log/job/`
- Performance data: `work/1/`
- Received data: `output/received_data.bin`
- Memory logs: `output/memory_monitor.log`

## Monitoring

1. Terminal UI:
```bash
cylc tui
```

2. Web UI:
```bash
cylc gui
```

3. View task logs:
```bash
cylc cat-log cpu-emu//1/receiver
cylc cat-log cpu-emu//1/emulator
cylc cat-log cpu-emu//1/sender
```

## Cleanup

To clean up jobs and data:
```bash
./cleanup.sh
```

This will:
- Cancel any running jobs
- Remove log files
- Clean up input/output data
- Remove temporary files

## Troubleshooting

1. Task Failures:
   - Check logs: `cylc cat-log cpu-emu//1/task_name`
   - Verify network connectivity
   - Check memory usage in `memory_monitor.log`

2. Common Issues:
   - Port conflicts: Change ports in flow.cylc
   - Memory issues: Adjust EMU_MEM_FOOTPRINT
   - Network issues: Check hostname resolution

3. Debug Commands:
```bash
# Check task status
cylc show cpu-emu

# View job logs
cylc cat-log cpu-emu//1/receiver
cylc cat-log cpu-emu//1/emulator
cylc cat-log cpu-emu//1/sender

# Check network connectivity
netstat -tuln | grep -E "50080|50888"
```

## Support

- Workflow issues: Check Cylc documentation
- CPU emulator issues: Check Docker image documentation
- JLab specific: Contact facility support

## References

- [Cylc Documentation](https://cylc.github.io/)
- [CPU Emulator Documentation](../README.md)
- [JLab Computing](https://scicomp.jlab.org/) 