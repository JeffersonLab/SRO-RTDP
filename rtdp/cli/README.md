# RTDP Workflow CLI

A command-line tool for generating and managing RTDP (Real-Time Data Processing) workflows using Cylc. This CLI supports various workflow types including single-component and multi-component configurations with built-in resource management and validation.

## Prerequisites

Before using the RTDP CLI, ensure you have the following installed:

- **Python 3.7 or higher**
- **pip** (Python package installer)
- **Apptainer/Singularity** (for container management)
- **SLURM** (for cluster job scheduling)
- **Network access** to pull Docker images

## Installation

### 1. Clone and Setup

```bash
# Navigate to the CLI directory
cd rtdp/cli

# Create and activate virtual environment (recommended)
python -m venv venv
source venv/bin/activate  # On Linux/macOS
# or
.\venv\Scripts\activate  # On Windows

# Install CLI package in development mode
pip install -e .
```

### 2. Setup RTDP Environment

```bash
# Setup RTDP environment including Cylc installation
./rtdp setup

# Check environment status
./rtdp status
```

The setup command will:
- Install Cylc in the current environment
- Create necessary Cylc directories (`~/.cylc/flow/`)
- Configure Apptainer environment variables
- Verify the installation

## Quick Start

### 1. Generate a Workflow

```bash
# Generate a multi-GPU proxy workflow
./rtdp generate --config config.yml --output workflow_dir --workflow-type multi_gpu_proxy
```

### 2. Run the Workflow

```bash
# Run the generated workflow
./rtdp run workflow_dir

# Monitor the workflow
./rtdp monitor workflow_dir
```

## Workflow Types

The CLI supports the following workflow types:

| Type | Description | Template Location |
|------|-------------|-------------------|
| `multi_gpu_proxy` | Multi-GPU proxy workflow | `cylc/multi_gpu_proxy/` |
| `multi_cpu_emu` | Multi-CPU emulator workflow | `cylc/multi_cpu_emu/` |
| `multi_mixed` | Mixed multi-component workflow | `cylc/multi_mixed/` |

**Note**: Refer to `cylc/` directory for workflow templates and example configurations.

## Configuration Examples

### Multi-GPU Proxy Workflow

Create `gpu_config.yml`:

```yaml
platform:
  name: "jlab_slurm"

containers:
  image_path: "jlabtsai/rtdp-gpu_proxy:latest"

receiver:
  listen_port: 8000

sender:
  target_port: 8003
  host: "destination.example.com"

gpu_proxies:
  - device: "gpu"
    partition: "gpu"
    gpus: 1
    mem: "100G"
    cpus: 4
    in_port: 8000
    out_port: 8001
    device_id: 0
    nic: "eno1"
  - device: "gpu"
    partition: "gpu"
    gpus: 1
    mem: "100G"
    cpus: 4
    in_port: 8001
    out_port: 8002
    device_id: 1
    nic: "eno2"
```

Generate and run:

```bash
./rtdp generate --config gpu_config.yml --output gpu_workflow --workflow-type multi_gpu_proxy
./rtdp run gpu_workflow
```

### Multi-CPU Emulator Workflow

Create `cpu_config.yml`:

```yaml
platform:
  name: "cpu_cluster"

containers:
  image_path: "jlabtsai/rtdp-cpu_emu:latest"

receiver:
  listen_port: 8000

sender:
  target_port: 8002
  host: "destination.example.com"

cpu_emulators:
  - id: "emu1"
    cpus: 4
    mem: "8G"
    in_port: 8000
    out_port: 8001
    threads: 4
    latency: 100
  - id: "emu2"
    cpus: 4
    mem: "8G"
    in_port: 8001
    out_port: 8002
    threads: 4
    latency: 100
```

Generate and run:

```bash
./rtdp generate --config cpu_config.yml --output cpu_workflow --workflow-type multi_cpu_emu
./rtdp run cpu_workflow
```

## CLI Commands

### Core Commands

| Command | Description |
|---------|-------------|
| `rtdp setup` | Setup RTDP environment and Cylc installation |
| `rtdp status` | Check RTDP environment status |
| `rtdp generate` | Generate workflow from configuration |
| `rtdp run` | Run a generated workflow |
| `rtdp monitor` | Monitor a running workflow |
| `rtdp cache` | Manage SIF container cache |

### Generate Command Options

```bash
./rtdp generate --config <config_file> --output <output_dir> --workflow-type <type> [OPTIONS]

Options:
  --consolidated-logging/--no-consolidated-logging  Enable/disable consolidated logging (default: enabled)
```

### Run Command Options

```bash
./rtdp run <workflow_directory> [OPTIONS]

Options:
  --parallel-builds <number>  Number of parallel SIF builds (default: 2)
  --skip-sif-build           Skip SIF container building
  --disable-cache            Disable SIF caching
```

### Cache Management

```bash
# View cache statistics
./rtdp cache --stats

# Clear all cached containers
./rtdp cache --clear
```

## Configuration File Format

### Common Structure

```yaml
platform:
  name: <platform_name>

containers:
  image_path: <docker_image_path>

receiver:
  listen_port: <port_number>

sender:
  target_port: <port_number>
  host: <destination_host>
  # Optional: send_rate, group_size, send_all_ones, socket_hwm
```

### GPU Proxy Configuration

```yaml
gpu_proxies:
  - device: "gpu"
    partition: <slurm_partition>
    gpus: <number_of_gpus>
    mem: <memory_allocation>
    cpus: <cpu_cores>
    in_port: <input_port>
    out_port: <output_port>
    device_id: <gpu_device_id>
    nic: <network_interface>  # Optional
    nodelist: <node_name>     # Optional
    # Optional: matrix_width, proxy_rate, socket_hwm
```

### CPU Emulator Configuration

```yaml
cpu_emulators:
  - id: <unique_emulator_id>
    cpus: <cpu_cores>
    mem: <memory_allocation>
    in_port: <input_port>
    out_port: <output_port>
    threads: <number_of_threads>
    latency: <latency_in_ms>
    nic: <network_interface>  # Optional
```

## Data Flow Architecture

### Multi-Component Flow
```
Sender → Component_0 → Component_1 → ... → Component_N → Receiver
```

**Port Configuration:**
- Sender connects to Component_0 on `target_port`
- Component_i connects to Component_{i+1} on `out_port`
- Component_N connects to Receiver on `out_port`
- Receiver listens on `listen_port`

## Performance Features

- **Parallel SIF Building**: Builds multiple containers simultaneously
- **Intelligent Caching**: Skips rebuilds when containers are up-to-date
- **Optimized Parsing**: Faster configuration processing
- **Resource Management**: Automatic GPU/CPU allocation and validation

## Troubleshooting

### Common Issues

1. **Cylc Installation Fails**:
   ```bash
   pip install --upgrade pip
   rtdp setup --skip-cylc-install  # If Cylc already installed
   ```

2. **Permission Errors**:
   ```bash
   # Ensure write permissions to ~/.cylc/
   chmod 755 ~/.cylc/
   ```

3. **CLI Command Not Found**:
   ```bash
   # Reinstall CLI package
   pip uninstall rtdp-cli -y && pip install -e .
   
   # Alternative: Run directly
   python rtdpcli.py --help
   ```

4. **Port Conflicts**: Ensure unique ports for each component
5. **GPU Device IDs**: Use sequential device IDs (0, 1, 2, etc.)

### Debug Mode

Enable debug output for detailed information:

```bash
./rtdp generate --config config.yml --output workflow --workflow-type multi_gpu_proxy --debug
```

## Example Workflows

Refer to the following directories for complete workflow templates and examples:

- **Multi-GPU Proxy**: `cylc/multi_gpu_proxy/`
  - `example_config.yml` - Complete configuration example
  - `flow.cylc.j2` - Workflow template
- **Multi-CPU Emulator**: `cylc/multi_cpu_emu/`
- **Mixed Workflows**: `cylc/multi_mixed/`

Each template directory contains:
- Jinja2 workflow templates
- Example configuration files
- Test configurations

## Testing

A comprehensive test script is available to verify the CLI functionality:

```bash
# Run the thorough test script
./test.sh
```

The test script (`test.sh`) performs comprehensive testing of all workflow types with different logging configurations:
- Tests all multi-component workflow types (GPU, CPU, Mixed)
- Tests both consolidated and separate logging options
- Generates multiple workflow variants for comparison
- Provides clear instructions for running and monitoring generated workflows

This script is useful for:
- Verifying CLI installation and functionality
- Testing different logging configurations
- Generating example workflows for learning
- Validating workflow generation across all supported types

## Support

For issues and questions:
1. Check the troubleshooting section above
2. Review example configurations in `cylc/` directory
3. Enable debug mode for detailed error information
4. Verify prerequisites and environment setup
5. Run the test script to verify functionality