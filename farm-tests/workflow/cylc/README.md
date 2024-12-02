# Cylc-based iperf3 Testing Workflow

This workflow automates iperf3 performance tests with Prometheus monitoring using Cylc workflow automation tool on JLab's ifarm cluster.

## Directory Structure

```
cylc-run/iperf-test/
├── flow.cylc                     # Main Cylc workflow definition
├── global.cylc                   # Global Cylc platform configuration
├── install.sh                    # Installation script
├── README.md                     # This file
├── scripts/                      # Script directory
│   ├── ifarm_iperf3Server.sh    # iperf3 server script
│   ├── ifarm_iperf3Client.sh    # iperf3 client script
│   ├── prom_server.sh           # Prometheus server script
│   └── run-local-prom.sh        # Script for local Prometheus setup
├── etc/
│   └── config/                  # Configuration directory
│       ├── process-exporter-config.yml    # Process exporter configuration
│       └── prometheus-config.yml          # Prometheus configuration 
└── sifs/                        # Container images directory
    ├── process-exporter.sif     # Process exporter container
    └── prom.sif                 # Prometheus container
```

## Prerequisites

1. Access to JLab's ifarm cluster
2. Cylc workflow engine installed (version 8 or higher)
3. Singularity/Apptainer installed for container execution
4. Required container images in your workflow's `sifs` directory:
   - `process-exporter.sif`
   - `prom.sif`
5. iperf3 binary and libraries installed and accessible

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
            # Ports configuration
            PROCESS_EXPORTER_PORT = "32801"
            APP_PORT = "32901"
            
            # Directory paths
            CONFIG_DIR = "$CYLC_WORKFLOW_RUN_DIR/etc/config"
            PROCESS_EXPORTER_SIF = "$CYLC_WORKFLOW_RUN_DIR/sifs/process-exporter.sif"
            PROMETHEUS_SIF = "$CYLC_WORKFLOW_RUN_DIR/sifs/prom.sif"
            
            # iperf3 configuration
            IPERF3_PATH = "/path/to/iperf3"
            IPERF3_LIB_PATH = "/path/to/iperf3/lib"
```

Required modifications:
- `IPERF3_PATH`: Path to your iperf3 binary
- `IPERF3_LIB_PATH`: Path to iperf3 libraries
- Adjust ports if needed: `PROCESS_EXPORTER_PORT`, `APP_PORT`

## Installation

1. Create a new Cylc workflow directory:
```bash
mkdir -p ~/cylc-run/iperf-test
cd ~/cylc-run/iperf-test
```

2. Copy all workflow files to this directory

3. Run the installation script:
```bash
chmod +x install.sh
./install.sh
```

4. Copy container images to the sifs directory:
```bash
cp /path/to/process-exporter.sif sifs/
cp /path/to/prom.sif sifs/
```

## Running the Workflow

1. Validate the workflow:
```bash
cylc validate .
```

2. Start the workflow:
```bash
cylc play iperf-test
```

## Workflow Tasks

1. **iperf_server**: 
   - Starts iperf3 server
   - Launches process exporter
   - Stores server hostname for other tasks
   - Monitors server performance

2. **iperf_client**:
   - Connects to iperf3 server
   - Runs performance tests (3600 seconds default)
   - Launches process exporter
   - Monitors client performance

3. **prometheus_server**:
   - Generates Prometheus configuration
   - Starts Prometheus server
   - Collects metrics from both exporters
   - Stores metrics in time-series database

## Local Prometheus Setup

Use `run-local-prom.sh` to:
1. Copy Prometheus data from remote cluster
2. Set up local Prometheus instance
3. View metrics locally

## Monitoring

1. Terminal UI:
```bash
cylc tui
```

2. Web UI:
```bash
cylc gui
```

3. Prometheus metrics:
   - Remote: `http://localhost:32900`
   - Local: Set up using `run-local-prom.sh`

## Output and Data

- Task logs: `~/cylc-run/iperf-test/runN/log/job/`
- Performance data: `~/cylc-run/iperf-test/runN/work/1/`
- Prometheus data: `prom-data-<timestamp>/`
- Configuration: `etc/config/`

## Troubleshooting

1. Task Failures:
   - Check logs: `cylc cat-log iperf-test//1/task_name`
   - Verify network connectivity
   - Check process exporter and Prometheus logs

2. Common Issues:
   - Port conflicts: Change ports in flow.cylc
   - Library path issues: Verify IPERF3_LIB_PATH
   - Container errors: Check Singularity/Apptainer installation

3. Debug Commands:
```bash
# Check task status
cylc show iperf-test

# View job logs
cylc cat-log iperf-test//1/iperf_server
cylc cat-log iperf-test//1/iperf_client
cylc cat-log iperf-test//1/prometheus_server

# Check network connectivity
netstat -tuln | grep -E "32801|32901|32900"
```

## Support

- Workflow issues: Check Cylc documentation
- iperf3 issues: Refer to iperf3 documentation
- Monitoring: Check Prometheus documentation
- JLab specific: Contact facility support

## References

- [Cylc Documentation](https://cylc.github.io/)
- [iperf3 Documentation](https://iperf.fr/)
- [Prometheus Documentation](https://prometheus.io/docs/)
- [JLab Computing](https://scicomp.jlab.org/)