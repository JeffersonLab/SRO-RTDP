# RTDP Workflow CLI Documentation

## Overview

The RTDP Workflow CLI is a command-line tool for generating and managing RTDP (Real-Time Data Processing) workflows. It supports various workflow types, including single-component and multi-component configurations, with built-in resource management and validation.

## Installation

### Prerequisites

- Python 3.7 or higher
- pip (Python package installer)

### Setup Development Environment

   ```bash
# Navigate to the project root directory
cd /path/to/rtdp

# Create and activate virtual environment
   python -m venv venv
   source venv/bin/activate  # On Linux/macOS
   # or
   .\venv\Scripts\activate  # On Windows

# Install development dependencies
pip install -e .
```

## Basic Usage

The CLI provides two main commands:

1. `generate`: Generate a workflow from a configuration file
2. `validate`: Validate a workflow configuration file

### Generate Command

   ```bash
python3 -m rtdp.cli.rtdpcli generate --config <config_file> --output <output_dir> --workflow-type <type>
```

#### Workflow Types

The CLI supports the following workflow types:

1. **Single Component Workflows**:
   - `gpu_proxy`: Single GPU proxy workflow
   - `cpu_emu`: Single CPU emulator workflow
   - `chain_workflow`: Simple chain workflow

2. **Multi-Component Workflows**:
   - `multi_gpu_proxy`: Multi-GPU proxy workflow with exclusive node allocation
   - `multi_cpu_emu`: Multi-CPU emulator workflow
   - `multi_mixed`: Mixed multi-component workflow

### Validate Command

   ```bash
python3 -m rtdp.cli.rtdpcli validate --config <config_file> --workflow-type <type>
```

## Example Cases

### 1. Multi-GPU Proxy Workflow (Exclusive Nodes)

Create a configuration file `gpu_config.yml`:
```yaml
platform:
  name: "gpu_cluster"

containers:
  image_path: "/path/to/gpu_proxy.sif"

receiver:
  listen_port: 8000
  nic: "eno1"

sender:
  target_port: 8003
  host: "destination.example.com"

gpu_proxies:
  - device: "gpu"
    partition: "gpu_partition"
    gpus: 1
    mem: "100G"
    cpus: 4
    in_port: 8000
    out_port: 8001
    device_id: 0
    nic: "eno1"
  - device: "gpu"
    partition: "gpu_partition"
    gpus: 1
    mem: "100G"
    cpus: 4
    in_port: 8001
    out_port: 8002
    device_id: 1
    nic: "eno2"
```

Generate the workflow:
   ```bash
python3 -m rtdp.cli.rtdpcli generate --config gpu_config.yml --output gpu_workflow --workflow-type multi_gpu_proxy
```

**Key Features:**
- **Exclusive Node Allocation**: Each proxy runs on its own dedicated node (`--exclusive`)
- **GPU Resource Management**: Automatic GPU allocation and device binding
- **Network Interface Configuration**: Each component can specify its own NIC
- **Port Chaining**: Automatic port configuration for data flow: sender → proxy_0 → proxy_1 → ... → receiver

### 2. Multi-CPU Emulator Workflow

Create a configuration file `cpu_config.yml`:
```yaml
platform:
  name: "cpu_cluster"

containers:
  image_path: "/path/to/cpu_emu.sif"

receiver:
  listen_port: 8000
  nic: "eno1"

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
    nic: "eno1"
  - id: "emu2"
    cpus: 4
    mem: "8G"
    in_port: 8001
    out_port: 8002
    threads: 4
    latency: 100
    nic: "eno2"
```

Generate the workflow:
   ```bash
python3 -m rtdp.cli.rtdpcli generate --config cpu_config.yml --output cpu_workflow --workflow-type multi_cpu_emu
   ```

### 3. Mixed Workflow (GPU + CPU)

Create a configuration file `mixed_config.yml`:
   ```yaml
platform:
  name: "mixed_cluster"

   containers:
  image_path: "/path/to/mixed.sif"

receiver:
  listen_port: 8000
  nic: "eno1"

sender:
  target_port: 8004
  host: "destination.example.com"

gpu_proxies:
  - device: "gpu"
    partition: "gpu_partition"
    gpus: 1
    mem: "100G"
    cpus: 4
    in_port: 8000
    out_port: 8001
    device_id: 0
    nic: "eno1"

cpu_emulators:
  - id: "emu1"
    cpus: 4
    mem: "8G"
    in_port: 8001
    out_port: 8002
    threads: 4
    latency: 100
    nic: "eno2"
  - id: "emu2"
    cpus: 4
    mem: "8G"
    in_port: 8002
    out_port: 8003
    threads: 4
    latency: 100
    nic: "eno3"
```

Generate the workflow:
```bash
python3 -m rtdp.cli.rtdpcli generate --config mixed_config.yml --output mixed_workflow --workflow-type multi_mixed
   ```

## Running and Monitoring Workflows

### Running a Workflow

To run a generated workflow, use the `run` command:

   ```bash
python3 -m rtdp.cli.rtdpcli run --workflow <workflow_directory>
   ```

**Example:**
   ```bash
python3 -m rtdp.cli.rtdpcli run --workflow gpu_workflow
```

This command will:
- Build the SIF container if needed (if the config is in the workflow directory),
- Change to the workflow directory,
- Run `cylc install --workflow-name=NAME`,
- Then run `cylc play NAME`.

### Monitoring a Workflow

To monitor a running workflow, use the `monitor` command:

   ```bash
python3 -m rtdp.cli.rtdpcli monitor --workflow <workflow_directory>
   ```

**Example:**
   ```bash
python3 -m rtdp.cli.rtdpcli monitor --workflow gpu_workflow
```

This command will display the current status of the workflow, including task states and progress.

## Configuration File Format

The configuration file should be in YAML format. Here's the structure for different workflow types:

### Common Configuration

```yaml
platform:
  name: <platform_name>

containers:
  image_path: <path_to_container_image>

receiver:
  listen_port: <port_number>  # Port where receiver listens for incoming data

sender:
  target_port: <port_number>  # Port where sender sends data to
  host: <destination_host>    # Destination host for sender
  # Component-specific sender parameters (optional)
  send_rate: <rate_mbps>      # Send rate in MB/s (default: 150)
  group_size: <size>          # Group size for data transmission (default: 30720000)
  send_all_ones: <0_or_1>     # Send all ones (0=random, 1=all ones, default: 0)
  socket_hwm: <buffer_size>   # Socket high water mark (default: 1)
```

**Component-Specific Parameters:**
- **Sender Parameters**: Each sender can have its own `send_rate`, `group_size`, `send_all_ones`, and `socket_hwm`
- **Proxy Parameters**: Each proxy can have its own `matrix_width`, `proxy_rate`, and `socket_hwm`
- **No Global Defaults**: Matrix configuration parameters are component-specific, not global

### Multi-GPU Proxy Configuration

```yaml
gpu_proxies:
  - device: "gpu"
    partition: <slurm_partition>
    gpus: <number_of_gpus>           # Number of GPUs per proxy
    mem: <memory_allocation>         # Memory allocation (e.g., "100G")
    cpus: <cpu_cores>               # CPU cores per proxy
    in_port: <input_port>           # Port to receive data from previous component
    out_port: <output_port>         # Port to send data to next component
    device_id: <gpu_device_id>      # GPU device ID (0, 1, 2, etc.)
    nic: <network_interface>        # Network interface for this proxy (optional)
    nodelist: <node_name>           # Explicit SLURM node assignment (optional)
    # Component-specific processing parameters (optional - local defaults)
    matrix_width: <matrix_size>     # GPU matrix width for processing (default: 2048)
    proxy_rate: <processing_rate>   # Proxy processing rate multiplier (default: 1.0)
    socket_hwm: <buffer_size>       # Socket high water mark buffer size (default: 1)
```

**Component-Specific Parameters:**
- **`matrix_width`**: GPU matrix width for processing (local default: 2048)
- **`proxy_rate`**: Processing rate multiplier (local default: 1.0)
- **`socket_hwm`**: Socket buffer size (local default: 1)

**Network Configuration:**
- **Per-Component NIC**: Each component can specify its own `nic` parameter
- **Auto-Detection**: If `nic` is not specified, the system auto-detects the default network interface
- **Validation**: NIC names are validated to ensure they exist on the target system

**SLURM Configuration:**
- **Node Assignment**: Use `nodelist` parameter to specify exact nodes for each proxy
- **GPU Allocation**: Uses `--gres=gpu:N` for GPU allocation
- **Resource Binding**: Automatic GPU device binding and memory allocation
- **Node Isolation**: Each proxy can be assigned to specific nodes to prevent conflicts

### Multi-CPU Emulator Configuration

```yaml
cpu_emulators:
  - id: <unique_emulator_id>
    cpus: <cpu_cores>
    mem: <memory_allocation>
    in_port: <input_port>
    out_port: <output_port>
    threads: <number_of_threads>
    latency: <latency_in_ms>
    nic: <network_interface>
```

## Data Flow Architecture

### Multi-GPU Proxy Flow
```
Sender → Proxy_0 → Proxy_1 → ... → Proxy_N → Receiver
```

**Port Configuration:**
- Sender connects to Proxy_0 on `target_port`
- Proxy_i connects to Proxy_{i+1} on `out_port`
- Proxy_N connects to Receiver on `out_port`
- Receiver listens on `listen_port`

**Network Configuration:**
- Each component can specify its own network interface (`nic`)
- Automatic IP address extraction (excludes loopback addresses)
- Hostname and IP information shared between components

### Resource Management

The CLI includes built-in resource management features:

1. **Port Validation**: Ensures ports are in valid range (1024-65535)
2. **Port Chaining**: Validates port connectivity between components
3. **GPU Resource Allocation**: Manages GPU device IDs and memory
4. **CPU Resource Allocation**: Manages CPU cores and memory
5. **Network Interface Validation**: Ensures specified NICs exist

## Validation Features

The CLI validates:

- **Required Fields**: All mandatory configuration parameters
- **Data Types**: Correct data types for all parameters
- **Port Ranges**: Valid port numbers (1024-65535)
- **Resource Conflicts**: No overlapping GPU device IDs or ports
- **Network Interfaces**: Valid network interface names
- **Container Paths**: Valid container image paths

## Troubleshooting

### Common Issues

1. **Port Conflicts**: Ensure unique ports for each component
2. **GPU Device IDs**: Use sequential device IDs (0, 1, 2, etc.)
3. **Network Interfaces**: Verify NIC names exist on target systems
4. **Memory Allocation**: Ensure sufficient memory for GPU workloads
5. **SLURM Partitions**: Verify partition names exist on cluster

### Example Configuration Files

Example configuration files are available for each workflow type:

- **Multi-GPU Proxy**: `rtdp/cylc/multi_gpu_proxy/example_config.yml`
  - Demonstrates component-specific parameters
  - Shows explicit node assignment with `nodelist`
  - Includes different processing parameters for each proxy
  - Features simplified network interface configuration

### Debug Mode

Enable debug output for detailed validation and generation information:

```bash
python3 -m rtdp.cli.rtdpcli generate --config config.yml --output workflow --workflow-type multi_gpu_proxy --debug
```