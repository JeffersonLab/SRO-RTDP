# Cylc-based iperf3 Testing Workflow

This workflow automates the process of running iperf3 performance tests with Prometheus monitoring using Cylc workflow automation tool.

## Directory Structure

```
cylc-run/iperf-test/
├── flow.cylc                     # Main Cylc workflow definition
├── install.sh                    # Installation script
├── README.md                     # This file
├── scripts/                      # Script directory
│   ├── ifarm_iperf3Server.sh    # iperf3 server script
│   ├── ifarm_iperf3Client.sh    # iperf3 client script
│   └── prom_server.sh           # Prometheus server script
├── etc/
│   └── config/                  # Configuration directory
│       ├── process-exporter-config.yml    # Process exporter configuration
│       └── prometheus-config.yml          # Prometheus configuration 
└── sifs/                        # Container images directory
    ├── process-exporter.sif     # Process exporter container
    └── prom.sif                 # Prometheus container
```

## Prerequisites

1. Cylc workflow engine installed (version 8 or higher)
2. Singularity/Apptainer installed for container execution
3. Required container images in your workflow's `sifs` directory:
   - `process-exporter.sif`
   - `prom.sif`
4. iperf3 binary installed and accessible

## Configuration

The workflow uses Cylc environment variables for configuration. The main settings are defined in `flow.cylc`:

```yaml
[runtime]
    [[root]]
        [[[environment]]]
            # Ports configuration
            PROCESS_EXPORTER_PORT = "32801"
            APP_PORT = "32901"
            
            # Directory structure (relative to workflow run directory)
            CONFIG_DIR = "etc/config"
            PROCESS_EXPORTER_SIF = "sifs/process-exporter.sif"
            PROMETHEUS_SIF = "sifs/prom.sif"
            
            # Path to iperf3 binary
            IPERF3_PATH = "/path/to/iperf3"
```

You only need to modify:
- `IPERF3_PATH`: Set to your iperf3 binary location
- Ports if needed: `PROCESS_EXPORTER_PORT`, `APP_PORT`

## Installation

1. Create a new Cylc workflow directory:
```bash
mkdir -p ~/cylc-run/iperf-test
cd ~/cylc-run/iperf-test
```

2. Copy the workflow files:
```bash
cp -r /path/to/cylc-iperf/* .
```

3. Run the installation script:
```bash
chmod +x install.sh
./install.sh
```

The install script will:
- Create necessary directories (`etc/config`, `sifs`, `scripts`)
- Install and validate the workflow
- Provide instructions for next steps

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

## Monitoring

You can monitor the workflow using either:

1. Terminal UI:
```bash
cylc tui
```

2. Web-based UI:
```bash
cylc gui
```

## Workflow Steps

1. **iperf_server**: 
   - Starts the iperf3 server
   - Launches process exporter for monitoring
   - Stores server hostname for other tasks

2. **iperf_client**:
   - Connects to the iperf3 server
   - Runs performance tests for 1 hour (3600 seconds)
   - Launches process exporter for monitoring
   - Stores client hostname for Prometheus

3. **prometheus_server**:
   - Generates Prometheus configuration
   - Starts Prometheus server
   - Collects metrics from both server and client

## Output and Data

All data is stored in the workflow run directory:
- Performance test results: Created by iperf3 tasks
- Prometheus data: `prom-data-<timestamp>/`
- Configuration files: `etc/config/`

## Troubleshooting

1. If the workflow fails to start:
   - Check if container images exist in `sifs/` directory
   - Verify file permissions
   - Ensure ports are available

2. If tasks fail:
   - Check Cylc logs: `cylc cat-log iperf-test//1/task_name`
   - Verify network connectivity between nodes
   - Check process exporter and Prometheus logs

3. Common issues:
   - Port conflicts: Change ports in flow.cylc
   - Permission denied: Check file and directory permissions
   - Missing containers: Verify container paths in `sifs/` directory

## Cleaning Up

To stop the workflow:
```bash
cylc stop iperf-test
```

To remove the workflow:
```bash
cylc clean iperf-test
```

## Additional Notes

- The workflow is designed to run once (non-cycling)
- All tasks use Cylc's shared directory (`$CYLC_WORKFLOW_SHARE_DIR`) for communication
- Prometheus configuration is generated dynamically
- Task dependencies ensure proper execution order
- Monitor Prometheus metrics at `http://localhost:32900`

## Support

For issues related to:
- Workflow configuration: Check Cylc documentation
- iperf3 testing: Refer to iperf3 documentation
- Monitoring: Check Prometheus documentation

## References

- [Cylc Documentation](https://cylc.github.io/)
- [iperf3 Documentation](https://iperf.fr/)
- [Prometheus Documentation](https://prometheus.io/docs/)

## Cylc Installation Guide

### Prerequisites
- Python 3.7 or later
- pip (Python package installer)
- Git

### Method 1: Using pip (Recommended for users)

1. Create and activate a virtual environment:
```bash
python3 -m venv ~/cylc-env
source ~/cylc-env/bin/activate
```

2. Install Cylc Flow and its UI:
```bash
pip install cylc-flow cylc-uiserver
```

3. Configure your shell environment (add to ~/.bashrc):
```bash
export PATH=$HOME/cylc-env/bin:$PATH
```

### Method 2: From Source (For developers)

1. Create and enter a directory for Cylc:
```bash
mkdir -p ~/cylc
cd ~/cylc
```

2. Clone the repositories:
```bash
git clone https://github.com/cylc/cylc-flow.git
git clone https://github.com/cylc/cylc-uiserver.git
```

3. Create and activate a virtual environment:
```bash
python3 -m venv venv
source venv/bin/activate
```

4. Install in development mode:
```bash
cd cylc-flow
pip install -e .
cd ../cylc-uiserver
pip install -e .
```

### Verify Installation

Test your installation:
```bash
cylc version  # Should show Cylc version
cylc help     # Should show help message
```

### Additional Components (Optional)

For enhanced functionality:
```bash
pip install cylc-rose    # For Rose suite support
pip install cylc-doc     # For offline documentation
```

For more detailed installation instructions and troubleshooting, visit the [Cylc Installation Guide](https://cylc.github.io/cylc-doc/stable/html/installation.html).