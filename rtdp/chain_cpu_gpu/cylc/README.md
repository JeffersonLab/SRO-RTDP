# CPU-GPU Chain Workflow

This workflow allows you to create arbitrary chains of CPU and GPU components for data processing.

## Configuration

The workflow is configured through a YAML file that specifies:
- The sequence of components (CPU/GPU)
- Port assignments
- Node assignments
- Component-specific parameters

### Example Configuration

```yaml
chain:
  - type: cpu
    node: node1
    in_port: 55555
    out_port: 55556
    params:
      threads: 1
      latency: 100
      mem_footprint: 0.01
      output_size: 0.001
  - type: gpu
    node: node2
    in_port: 55556
    out_port: 55557
    params:
      matrix_width: 2048
      send_rate: 25
      group_size: 2048
  - type: cpu
    node: node3
    in_port: 55557
    out_port: 55558
    params:
      threads: 1
      latency: 100
      mem_footprint: 0.01
      output_size: 0.001
```

## Usage

1. Create a configuration file (e.g., `chain_config.yaml`)
2. Run the workflow:
   ```bash
   cylc install chain_cpu_gpu
   cylc play chain_cpu_gpu
   ```

## Components

- **Sender**: Sends data to the first component in the chain
- **Components**: Process data and forward to the next component
- **Receiver**: Receives data from the last component in the chain

## Port Configuration

Each component in the chain:
- Receives data on its `in_port`
- Sends data to the next component's `in_port`
- The last component sends to the receiver's port

## Node Assignment

Each component runs on a different node in the `ifarm` partition. 