# RTDP Components

This repository contains the Python components for the Real-Time Data Processing (RTDP) system. The components work together with the CPU emulator to form a complete data processing workflow.

## Components Overview

The system consists of the following components:

1. **Sender**: Generates test data and sends it to the load balancer
2. **Load Balancer**: Distributes incoming data across multiple emulators using configurable strategies
3. **CPU Emulators**: Process data with configurable CPU, memory, and I/O characteristics
4. **Aggregator**: Collects and combines processed data from multiple emulators
5. **Receiver**: Stores the final processed data

## Example Workflow

This example demonstrates a workflow with:
- 1 Sender generating test data
- 1 Load Balancer distributing data
- 2 CPU Emulators processing data in parallel
- 1 Aggregator combining processed data
- 1 Receiver storing final results

### Data Flow
```
                     ┌─────────────┐
                     │  Emulator1  │
                     └──────┬──────┘
┌────────┐    ┌──────┐     │     ┌──────────┐    ┌──────────┐
│ Sender │───>│  LB  │─────┤────>│Aggregator│───>│ Receiver │
└────────┘    └──────┘     │     └──────────┘    └──────────┘
                     ┌──────┴──────┐
                     │  Emulator2  │
                     └─────────────┘
```

### Component Roles

1. **Sender (sender1)**:
   - Generates test data of configurable size
   - Sends data to the load balancer
   - Configuration includes data size, format, and chunk size

2. **Load Balancer (load_balancer1)**:
   - Receives data from sender
   - Distributes data across emulators using round-robin strategy
   - Monitors emulator health and performance
   - Supports backpressure handling

3. **CPU Emulators (emulator1, emulator2)**:
   - Process data with configurable characteristics:
     - CPU utilization (threads, latency)
     - Memory footprint
     - I/O patterns
   - Send processed data to aggregator

4. **Aggregator (aggregator1)**:
   - Collects processed data from both emulators
   - Orders/combines data based on configured strategy
   - Supports different aggregation modes:
     - Ordered: Maintains sequence
     - Unordered: Forwards as received
     - Time Window: Groups by time windows

5. **Receiver (receiver1)**:
   - Stores aggregated data
   - Validates data integrity
   - Supports compression and buffering

## Building the Components

The components are packaged into two container images:
1. CPU Emulator (from jlabtsai/rtdp-cpu_emu:v0.1)
2. Python Components (built locally and available as jlabtsai/rtdp-components:latest)

To build and convert the images to Singularity/Apptainer format:
```bash
# Make build script executable
chmod +x build.sh

# Run build script
./build.sh
```

This will:
1. Pull the CPU emulator image
2. Build the Python components image
3. Convert both to SIF format in the `sifs` directory

## Configuration

The workflow is configured through a YAML file specifying:
- Component settings (resources, network, etc.)
- Data flow connections
- Processing parameters
- Container images

Example configuration is available in `workflow_config.yml`.

## Running the Workflow

The workflow runs under Cylc workflow manager:
1. Components are started in dependency order
2. Data flows from sender through the processing chain
3. Results are collected at the receiver

Monitor the workflow through:
- Component logs in the workflow's log directory
- Cylc workflow status commands
- Output data in the receiver's output directory

## Performance Monitoring

The workflow provides several monitoring points:
- Load balancer statistics (distribution, queue sizes)
- Emulator metrics (CPU, memory usage)
- Aggregator throughput
- End-to-end data flow metrics

## Requirements

- Python 3.10 or later
- Docker for building images
- Singularity/Apptainer for running containers
- Cylc workflow manager
- ZeroMQ for component communication

## Dependencies

See `requirements.txt` for Python package dependencies. 