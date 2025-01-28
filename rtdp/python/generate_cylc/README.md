# Cylc Workflow Generator

This tool generates Cylc workflow configurations from YAML templates. It currently supports two types of workflows:
1. iperf3 Testing Workflow
2. CPU Emulator Testing Workflow

## Installation

1. Clone the repository
2. Install required dependencies:
```bash
pip install pyyaml
```

## Usage

1. Copy the appropriate template configuration:
```bash
# For iperf3 testing workflow
cp templates/iperf-test-config.yml my-iperf-config.yml

# For CPU emulator workflow
cp templates/cpu-emu-config.yml my-cpu-config.yml
```

2. Edit the configuration file to match your environment:
```yaml
# Example: my-iperf-config.yml
workflow:
  name: "iperf-test"
  description: "My iperf3 test workflow"

platform:
  name: "jlab_slurm"
  cylc_path: "/actual/path/to/cylc-env/bin/"
  hosts: "your-username@your-host"
  # ... rest of configuration
```

3. Generate the workflow:
```bash
python generate_workflow.py my-iperf-config.yml
```

## Configuration Templates

### iperf3 Testing Workflow
```yaml
workflow:
  name: "iperf-test"
  description: "Cylc-based iperf3 Testing Workflow"

platform:
  name: "jlab_slurm"
  cylc_path: "/path/to/your/cylc-env/bin/"
  hosts: "username@host"
  job_runner: "slurm"

resources:
  default:
    ntasks: 1
    cpus_per_task: 4
    mem: "8G"
    partition: "ifarm"
    timeout: "2h"

network:
  process_exporter_port: 32801
  app_port: 32901
  prometheus_port: 32900

paths:
  iperf3:
    binary: "/path/to/iperf3"
    lib: "/path/to/iperf3/lib"
  
containers:
  process_exporter:
    image: "process-exporter.sif"
    config: "process-exporter-config.yml"
  prometheus:
    image: "prom.sif"
    config: "prometheus-config.yml"

monitoring:
  scrape_interval: "15s"
  external_labels:
    monitor: "ifarm-prom-monitor"
```

### CPU Emulator Workflow
```yaml
workflow:
  name: "cpu-emu"
  description: "Cylc-based CPU Emulator Testing Workflow"

platform:
  name: "jlab_slurm"
  cylc_path: "/path/to/cylc-env/bin/"
  hosts: "username@host"
  job_runner: "slurm"

resources:
  receiver:
    ntasks: 1
    cpus_per_task: 4
    mem: "8G"
    partition: "ifarm"
    timeout: "2h"
  emulator:
    ntasks: 1
    cpus_per_task: 4
    mem: "16G"
    partition: "ifarm"
    timeout: "2h"
  sender:
    ntasks: 1
    cpus_per_task: 4
    mem: "8G"
    partition: "ifarm"
    timeout: "2h"

network:
  receiver_port: 50080
  emulator_port: 50888

emulator:
  threads: 4
  latency: 50          # Processing latency per GB
  mem_footprint: 0.05  # Memory footprint in GB
  output_size: 0.001   # Output size in GB

test_data:
  size: "100M"        # Size of test data to send

containers:
  cpu_emulator:
    image: "cpu-emu.sif"
    docker_source: "jlabtsai/rtdp-cpu_emu:latest"
```

## Generated Files

The generator will create the following directory structure:

```
workflow-name/
├── flow.cylc                     # Main workflow definition
├── global.cylc                   # Global platform configuration
├── scripts/                      # Task scripts
├── etc/
│   └── config/                  # Configuration files
└── sifs/                        # Container images
```

## Validation

The generator performs basic validation:
- Checks for required configuration parameters
- Validates port numbers and resource specifications
- Ensures paths and hostnames are properly formatted

## Error Handling

If there are issues with the configuration:
1. The generator will print specific error messages
2. No files will be generated until all validation passes
3. Existing files will not be overwritten without confirmation

## Support

For issues or feature requests:
1. Check the existing configuration templates
2. Verify your configuration matches the template format
3. Submit an issue with your configuration file and error message

## References

- [Cylc Documentation](https://cylc.github.io/)
- [YAML Specification](https://yaml.org/spec/)
- [JLab Computing](https://scicomp.jlab.org/)

## Development Setup

### Using VS Code Dev Containers

1. Install Prerequisites:
   - [Docker](https://www.docker.com/get-started)
   - [VS Code](https://code.visualstudio.com/)
   - [Remote - Containers extension](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers)

2. Open in Dev Container:
   ```bash
   code /path/to/rtdp/python/generate_cylc
   ```
   - When prompted, click "Reopen in Container"
   - VS Code will build the dev container and install all dependencies

3. Development Features:
   - Python 3.10 environment
   - PyQt6 with X11 forwarding for GUI
   - Code formatting (black)
   - Linting (flake8)
   - Type checking (mypy)
   - Git integration
   - Auto-documentation

### Manual Setup

1. Create virtual environment:
   ```bash
   python -m venv venv
   source venv/bin/activate  # Linux/Mac
   # or
   .\venv\Scripts\activate  # Windows
   ```

2. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```

3. Install system dependencies for PyQt6:
   ```bash
   # Ubuntu/Debian
   sudo apt-get install qt6-base-dev libgl1-mesa-dev

   # Fedora
   sudo dnf install qt6-qtbase-devel mesa-libGL-devel

   # macOS
   brew install qt6
   ```

4. Run the application:
   ```bash
   python generate_gui.py
   ```

### Running Tests

```bash
pytest
```

### Code Style

This project uses:
- black for code formatting
- flake8 for code linting
- mypy for type checking

To format code:
```bash
black .
```

To run linting:
```bash
flake8 .
mypy .
``` 