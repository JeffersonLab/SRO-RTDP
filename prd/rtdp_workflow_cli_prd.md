# RTDP Workflow CLI Product Requirements Document

## Overview
The RTDP Workflow CLI is a command-line interface tool designed to generate and manage Cylc workflows for RTDP (Real-Time Data Processing) applications. It supports various workflow types including GPU proxies, CPU emulators, and mixed CPU/GPU configurations.

## Core Features

### 1. Workflow Generation
- Generate Cylc workflows from Jinja2 templates
- Support multiple workflow types:
  - GPU proxy workflows (1 to N GPUs)
  - CPU emulator workflows (1 to N CPUs)
  - Mixed CPU/GPU workflows (M CPUs + N GPUs)
- Template-based configuration
- YAML-based workflow configuration

### 2. Configuration Management
- Generate example configurations for templates
- Validate configurations against templates
- Support nested configuration structures
- Environment variable substitution
- Default value handling

### 3. Workflow Types

#### 3.1 GPU Proxy Workflows
- Support for 1 to N GPU proxies
- Each GPU proxy can be configured independently
- Shared configuration options:
  - GPU device selection
  - Memory allocation
  - Compute capability
  - Power management
- Individual proxy configurations:
  - Input/output ports
  - Processing parameters
  - Resource limits

#### 3.2 CPU Emulator Workflows
- Support for 1 to N CPU emulators
- Each CPU emulator can be configured independently
- Shared configuration options:
  - CPU core allocation
  - Memory allocation
  - Thread management
- Individual emulator configurations:
  - Processing parameters
  - Resource limits
  - Performance settings

#### 3.3 Mixed CPU/GPU Workflows
- Support for M CPUs and N GPUs
- Flexible resource allocation
- Cross-device communication
- Load balancing options
- Resource sharing policies

### 4. CLI Commands

#### 4.1 Basic Commands
```bash
# Generate workflow from template
rtdp generate --template <template> --config <config>

# Show example configuration
rtdp example-config --template <template>

# Validate configuration
rtdp validate --template <template> --config <config>
```

#### 4.2 Advanced Commands
```bash
# Generate multi-GPU workflow
rtdp generate --template gpu_proxy --config <config> --gpus N

# Generate multi-CPU workflow
rtdp generate --template cpu_emu --config <config> --cpus M

# Generate mixed workflow
rtdp generate --template mixed --config <config> --cpus M --gpus N
```

### 5. Configuration Structure

#### 5.1 GPU Proxy Configuration
```yaml
gpu_proxies:
  - id: 0
    device: 0
    memory: 8G
    compute_capability: 7.5
    ports:
      input: 8000
      output: 8001
  - id: 1
    device: 1
    memory: 8G
    compute_capability: 7.5
    ports:
      input: 8002
      output: 8003
```

#### 5.2 CPU Emulator Configuration
```yaml
cpu_emulators:
  - id: 0
    cores: 4
    memory: 16G
    threads: 8
    ports:
      input: 9000
      output: 9001
  - id: 1
    cores: 4
    memory: 16G
    threads: 8
    ports:
      input: 9002
      output: 9003
```

#### 5.3 Mixed Configuration
```yaml
gpu_proxies:
  - id: 0
    device: 0
    memory: 8G
    compute_capability: 7.5
    ports:
      input: 8000
      output: 8001

cpu_emulators:
  - id: 0
    cores: 4
    memory: 16G
    threads: 8
    ports:
      input: 9000
      output: 9001

# Shared configuration
shared:
  communication:
    protocol: tcp
    buffer_size: 1M
  monitoring:
    interval: 1s
    metrics: [cpu, memory, gpu]
```

### 6. Template Requirements

#### 6.1 GPU Proxy Template (@/cylc)
The GPU proxy template (`rtdp/cuda/gpu_proxy/cylc/flow.cylc.j2`) serves as a base template for single GPU proxy workflows. A new template needs to be developed to support N-number of GPU proxies:

**Base Template Features:**
- **Network Configuration**:
  - Configurable input/output ports
  - Network interface selection
  - Socket high-water mark settings
- **GPU Configuration**:
  - Matrix width settings
  - Processing rate control
  - Group size management
- **Resource Management**:
  - GPU memory allocation
  - CPU core allocation
  - Memory limits
- **Monitoring**:
  - Detailed logging
  - Process status tracking
  - Memory usage monitoring
- **Error Handling**:
  - Port conflict detection
  - Process health monitoring
  - Graceful shutdown

#### 6.2 CPU Emulator Template (@/cylc)
The CPU emulator template (`rtdp/cpp/cpu_emu/cylc/flow.cylc.j2`) serves as a base template for single CPU emulator workflows. A new template needs to be developed to support N-number of CPU emulators:

**Base Template Features:**
- **Network Configuration**:
  - Base port configuration
  - Component-specific port allocation
  - Network interface settings
- **CPU Configuration**:
  - Thread count management
  - Latency control
  - Memory footprint settings
- **Resource Management**:
  - CPU core allocation
  - Memory limits
  - Thread management
- **Monitoring**:
  - Detailed logging
  - Process status tracking
  - Memory usage monitoring
- **Error Handling**:
  - Port conflict detection
  - Process health monitoring
  - Graceful shutdown

#### 6.3 Mixed Template (@/chain_workflow)
The mixed template (`rtdp/cylc/chain_workflow/flow.cylc.j2`) serves as a base template for a simple chain workflow. A new template needs to be developed to support M-number of CPU emulators and N-number of GPU proxies:

**Base Template Features:**
- **Component Chain**:
  - Sequential processing pipeline
  - Inter-component communication
  - Data flow control
- **Resource Management**:
  - GPU resource allocation
  - CPU resource allocation
  - Memory management
- **Network Configuration**:
  - Base port configuration
  - Component-specific port allocation
  - Network interface settings
- **Monitoring**:
  - Component-specific logging
  - Process status tracking
  - Resource usage monitoring
- **Error Handling**:
  - Chain-wide error detection
  - Component health monitoring
  - Graceful shutdown

### 7. Template Development Requirements

#### 7.1 Template Generation
- **Base Template Analysis**:
  - Analyze existing templates for common patterns
  - Identify reusable components
  - Extract configuration patterns
- **Template Parameterization**:
  - Define variable component counts
  - Parameterize resource allocation
  - Parameterize network configuration
- **Dynamic Configuration**:
  - Support for runtime component scaling
  - Dynamic resource allocation
  - Flexible network setup

#### 7.2 Configuration Management
- **Component Configuration**:
  - Per-component settings
  - Resource allocation rules
  - Network configuration rules
- **Global Configuration**:
  - Overall resource limits
  - Network topology rules
  - Monitoring settings
- **Validation Rules**:
  - Component count validation
  - Resource allocation validation
  - Network configuration validation

#### 7.3 Implementation Guidelines
- **Template Structure**:
  - Modular component definitions
  - Reusable configuration blocks
  - Clear separation of concerns
- **Configuration Generation**:
  - Dynamic port allocation
  - Resource allocation rules
  - Network setup rules
- **Error Handling**:
  - Component-specific error handling
  - Chain-wide error propagation
  - Recovery mechanisms

### 8. Error Handling
- Configuration validation
- Resource availability checks
- Port conflict detection
- Device compatibility verification
- Graceful error reporting

 