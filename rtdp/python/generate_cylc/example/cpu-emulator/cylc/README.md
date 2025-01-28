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

After installation, Cylc creates the following directory structure:
```
~/cylc-run/cpu-emu/             # CYLC_WORKFLOW_RUN_DIR
├── runN/                       # Run directory for each workflow run
│   ├── work/                   # CYLC_WORKFLOW_WORK_DIR (task working directories)
│   │   └── 1/                 # Cycle point directory
│   │       ├── receiver/      # Task working directories
│   │       ├── emulator/
│   │       └── sender/
│   ├── share/                 # CYLC_WORKFLOW_SHARE_DIR (shared between tasks)
│   │   ├── output/           # Shared output directory
│   │   ├── input/            # Shared input directory
│   │   └── logs/             # Shared logs directory
│   │       ├── receiver/     # Task-specific logs
│   │       ├── emulator/
│   │       └── sender/
│   └── log/                   # Cylc log directory
│       └── job/              # Job logs
└── share/                     # Workflow share directory (persists between runs)
```

## Task Communication and Dependencies

The workflow uses Cylc's message system for task coordination:

1. **Receiver Task**:
   - Starts and initializes the receiver process
   - Sends "ready" message when process is running
   - Monitors received data by tracking file size changes
   - Sends "completed" message when data is received

2. **Emulator Task**:
   - Waits for receiver's "ready" message
   - Starts the CPU emulator process
   - Sends "ready" message when process is running
   - Monitors memory usage throughout execution

3. **Sender Task**:
   - Waits for emulator's "ready" message
   - Generates and sends test data
   - Sends "succeeded" message on successful transfer

## Task Outputs and Messages

Each task uses specific messages to coordinate with others:

```
[scheduling]
    [[graph]]
        R1 = """
            # Start chain
            receiver:ready => emulator
            emulator:ready => sender
            
            # Completion chain
            sender:succeeded => !receiver
            receiver:completed
        """
```

Message definitions:
- `ready`: Indicates task is initialized and running
- `completed`: Indicates data transfer completion
- `succeeded`: Indicates successful operation

## Monitoring and Logs

Each task maintains detailed logs in `$CYLC_WORKFLOW_SHARE_DIR/logs/<task_name>/`:
- `stdout.log`: Standard output with timestamped messages
- `stderr.log`: Error messages and debugging information
- `apptainer.log`: Container execution logs
- `process.log`: Process status information
- `memory.log`: Memory usage tracking (emulator only)

## Data Transfer Monitoring

The receiver task monitors data transfer by:
1. Tracking changes in received data file size
2. Detecting actual data reception
3. Logging transfer progress and completion
4. Maintaining process health checks

## Installation and Usage

1. Create necessary directories:
```bash
mkdir -p sifs etc/config scripts
```

2. Build the SIF file:
```bash
./build.sh -i jlabtsai/rtdp-cpu_emu:latest
```

3. Install the workflow:
```bash
./install.sh
```

4. Run the workflow:
```bash
cylc play cpu-emu
```

## Monitoring Workflow Progress

1. View workflow status:
```bash
cylc tui cpu-emu
```

2. View task logs:
```bash
# View specific task logs
cylc cat-log cpu-emu//1/receiver
cylc cat-log cpu-emu//1/emulator
cylc cat-log cpu-emu//1/sender

# View detailed logs in share directory
ls -l ~/cylc-run/cpu-emu/runN/share/logs/
```

## Troubleshooting

1. **Task Communication Issues**:
   - Check task logs for message sending/receiving
   - Verify message formats match output definitions
   - Check Cylc workflow log for message reception

2. **Data Transfer Issues**:
   - Check receiver's file size monitoring logs
   - Verify network connectivity between nodes
   - Check apptainer.log for container errors

3. **Process Health**:
   - Monitor process status in process.log
   - Check memory usage in memory.log
   - Verify port availability and connections

## Cleanup

To clean up the workflow and data:
```bash
./cleanup.sh
```

This will:
- Stop running workflows
- Cancel SLURM jobs
- Remove log files and data
- Clean up Cylc installation

## Support and References

- [Cylc Documentation](https://cylc.github.io/)
- [CPU Emulator Documentation](../README.md)
- [JLab Computing](https://scicomp.jlab.org/) 