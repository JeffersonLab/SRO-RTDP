# RTDP Workflow CLI Documentation

## Overview

The RTDP Workflow CLI is a command-line tool for generating and managing RTDP (Real-Time Data Processing) workflows. It supports various workflow types, including single-component and multi-component configurations, with built-in resource management and validation.

## Installation

### Prerequisites

- Python 3.7 or higher
- pip (Python package installer)
- Git (for development installation)

### Install the CLI

1. **Install from PyPI (Recommended)**:
```bash
pip install rtdp-workflow-cli
```

2. **Install from Source**:
```bash
# Navigate to the project root directory
cd /path/to/rtdp

# Create and activate virtual environment
python -m venv venv
source venv/bin/activate  # On Linux/macOS
# or
.\venv\Scripts\activate  # On Windows

# Install the package
pip install -e .
```

## Basic Usage

The CLI provides two main commands:

1. `generate`: Generate a workflow from a configuration file
2. `validate`: Validate a workflow configuration file

### Generate Command

```bash
rtdp-workflow generate --config <config_file> --output <output_dir> --workflow-type <type>
```

#### Workflow Types

The CLI supports the following workflow types:

1. **Single Component Workflows**:
   - `gpu_proxy`: Single GPU proxy workflow
   - `cpu_emu`: Single CPU emulator workflow
   - `chain_workflow`: Simple chain workflow

2. **Multi-Component Workflows**:
   - `multi_gpu_proxy`: Multi-GPU proxy workflow
   - `multi_cpu_emu`: Multi-CPU emulator workflow
   - `multi_mixed`: Mixed multi-component workflow

### Validate Command

```bash
rtdp-workflow validate --config <config_file> --workflow-type <type>
```

## Example Cases

### 1. Multi-GPU Proxy Workflow

Create a configuration file `gpu_config.yml`:
```yaml
platform:
  name: "gpu_cluster"

containers:
  image_path: "/path/to/gpu_proxy.sif"

receiver:
  port: 8000

sender:
  port: 8003
  host: "destination.example.com"

gpu_proxies:
  - device: "gpu"
    partition: "gpu_partition"
    gres: "gpu:1"
    mem: "16G"
    cpus: 4
    in_port: 8000
    out_port: 8001
    device_id: 0
  - device: "gpu"
    partition: "gpu_partition"
    gres: "gpu:1"
    mem: "16G"
    cpus: 4
    in_port: 8001
    out_port: 8002
    device_id: 1
```

Generate the workflow:
```bash
rtdp-workflow generate --config gpu_config.yml --output gpu_workflow --workflow-type multi_gpu_proxy
```

### 2. Multi-CPU Emulator Workflow

Create a configuration file `cpu_config.yml`:
```yaml
platform:
  name: "cpu_cluster"

containers:
  image_path: "/path/to/cpu_emu.sif"

receiver:
  port: 8000

sender:
  port: 8002
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

Generate the workflow:
```bash
rtdp-workflow generate --config cpu_config.yml --output cpu_workflow --workflow-type multi_cpu_emu
```

### 3. Mixed Workflow (GPU + CPU)

Create a configuration file `mixed_config.yml`:
```yaml
platform:
  name: "mixed_cluster"

containers:
  image_path: "/path/to/mixed.sif"

receiver:
  port: 8000

sender:
  port: 8004
  host: "destination.example.com"

gpu_proxies:
  - device: "gpu"
    partition: "gpu_partition"
    gres: "gpu:1"
    mem: "16G"
    cpus: 4
    in_port: 8000
    out_port: 8001
    device_id: 0

cpu_emulators:
  - id: "emu1"
    cpus: 4
    mem: "8G"
    in_port: 8001
    out_port: 8002
    threads: 4
    latency: 100
  - id: "emu2"
    cpus: 4
    mem: "8G"
    in_port: 8002
    out_port: 8003
    threads: 4
    latency: 100
```

Generate the workflow:
```bash
rtdp-workflow generate --config mixed_config.yml --output mixed_workflow --workflow-type multi_mixed
```

## Configuration File Format

The configuration file should be in YAML format. Here's the structure for different workflow types:

### Common Configuration

```yaml
platform:
  name: <platform_name>

containers:
  image_path: <path_to_container_image>

receiver:
  port: <port_number>

sender:
  port: <port_number>
  host: <destination_host>
```

### Multi-GPU Proxy Configuration

```yaml
# Common configuration as above
gpu_proxies:
  - device: <gpu_device>
    partition: <partition_name>
    gres: <gpu_resource>
    mem: <memory_allocation>
    cpus: <cpu_count>
    in_port: <input_port>
    out_port: <output_port>
    device_id: <gpu_device_id>
  # Add more GPU proxies as needed
```

### Multi-CPU Emulator Configuration

```yaml
# Common configuration as above
cpu_emulators:
  - id: <emulator_id>
    cpus: <cpu_count>
    mem: <memory_allocation>
    in_port: <input_port>
    out_port: <output_port>
    threads: <thread_count>
    latency: <latency_ms>
  # Add more CPU emulators as needed
```

### Mixed Workflow Configuration

```yaml
# Common configuration as above
gpu_proxies:
  # GPU proxy configurations as above
cpu_emulators:
  # CPU emulator configurations as above
```

## Resource Management

The CLI includes a built-in resource manager that handles:

1. **GPU Resources**:
   - Device allocation
   - Memory management
   - CPU allocation per GPU
   - Device ID validation

2. **CPU Resources**:
   - CPU count validation
   - Memory allocation
   - Thread count management
   - Latency configuration

3. **Network Resources**:
   - Port allocation and validation
   - Port chain validation
   - Host configuration

### Resource Validation Rules

1. **GPU Proxy Validation**:
   - Device must be a valid GPU device
   - Memory allocation must be within system limits
   - CPU count must be positive
   - Ports must be between 1024 and 65535
   - Device ID must be non-negative

2. **CPU Emulator Validation**:
   - CPU count must be positive
   - Memory allocation must be within system limits
   - Thread count must be positive
   - Latency must be non-negative
   - Ports must be between 1024 and 65535

3. **Network Validation**:
   - Ports must be unique across components
   - Port chain must be continuous
   - Host must be a valid hostname or IP address

## Error Handling

The CLI provides detailed error messages for:

1. **Configuration Errors**:
   - Missing required fields
   - Invalid field types
   - Out-of-range values

2. **Resource Errors**:
   - Resource conflicts
   - Insufficient resources
   - Invalid resource specifications

3. **Network Errors**:
   - Port conflicts
   - Invalid port chains
   - Invalid host configurations

## Best Practices

1. **Resource Allocation**:
   - Allocate resources based on actual requirements
   - Consider system limits when configuring resources
   - Use appropriate memory and CPU allocations

2. **Network Configuration**:
   - Use ports in the range 1024-65535
   - Ensure continuous port chains
   - Configure appropriate host settings

3. **Workflow Design**:
   - Start with simple configurations
   - Test resource allocations
   - Validate configurations before deployment

## Troubleshooting

Common issues and solutions:

1. **Resource Allocation Failures**:
   - Check system resource limits
   - Verify resource specifications
   - Ensure no resource conflicts

2. **Port Conflicts**:
   - Verify port availability
   - Check port chain continuity
   - Ensure unique port assignments

3. **Configuration Errors**:
   - Validate configuration format
   - Check required fields
   - Verify field types and ranges 