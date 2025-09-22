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

## Workflow Tasks and Dependencies

The workflow follows this dependency chain:
```
iperf_server:ready => iperf_client => iperf_client:ready => prometheus_server
```

1. **iperf_server**: 
   - Starts iperf3 server
   - Launches process exporter
   - Stores server hostname in `$CYLC_WORKFLOW_SHARE_DIR/server_hostname`
   - Monitors server performance
   - Signals readiness with `iperf_server:ready` message

2. **iperf_client**:
   - Start conditions:
     * Waits for `iperf_server:ready` message
     * Server must be accessible on APP_PORT (tries 30 times, 1s apart)
     * Server hostname must be available in shared directory
   - Startup sequence:
     1. Reads server hostname from shared directory
     2. Tests connection to server
     3. Stores client hostname in `$CYLC_WORKFLOW_SHARE_DIR/client_hostname`
     4. Launches process exporter
     5. Starts iperf3 client test (3600s duration)
   - Signals readiness with `iperf_client:ready` message

3. **prometheus_server**:
   - Start conditions:
     * Waits for `iperf_client:ready` message
     * Both server and client hostnames must be in shared directory
     * Both process exporters must be accessible
   - Startup sequence:
     1. Reads server and client hostnames from shared directory
     2. Verifies connection to both process exporters
     3. Creates timestamp-based data directory
     4. Generates dynamic Prometheus configuration
     5. Starts Prometheus server
   - Collects metrics from:
     * Server process exporter (port 32801)
     * Client process exporter (port 32801)
     * Prometheus itself (port 32900)

## Shared Resources

The workflow uses `$CYLC_WORKFLOW_SHARE_DIR` to share information between tasks:
- `server_hostname`: Written by iperf_server, read by iperf_client and prometheus_server
- `client_hostname`: Written by iperf_client, read by prometheus_server
- `server_info.txt`: Contains detailed server configuration (optional)

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