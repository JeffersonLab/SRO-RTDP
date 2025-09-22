# Cylc Workflow Generator

This tool generates Cylc workflows from configuration files, allowing for easy customization and management of workflow parameters.

## Directory Structure

```
wf-generator/
├── config_schema.yml     # Schema defining the configuration structure
├── example_config.yml    # Example configuration file
├── generate_workflow.py  # Main workflow generator script
├── templates/           
│   └── flow.cylc.j2     # Jinja2 template for the Cylc workflow
└── README.md            # This file
```

## Requirements

- Python 3.6+
- PyYAML
- Jinja2
- jsonschema

Install dependencies:

```bash
pip install pyyaml jinja2 jsonschema
```

## Usage

1. Create a configuration file following the schema defined in `config_schema.yml`. You can use `example_config.yml` as a reference.

2. Run the workflow generator:

```bash
python generate_workflow.py your_config.yml
```

Optional arguments:
- `--schema`: Path to schema file (default: config_schema.yml)
- `--template-dir`: Directory containing templates (default: templates)
- `--output-dir`: Output directory for generated workflow (default: generated)

## Configuration Structure

The configuration file is organized into several sections:

### Workflow Section
Basic workflow information:
```yaml
workflow:
  name: "workflow-name"
  description: "Workflow description"
```

### Platform Section
Platform-specific settings:
```yaml
platform:
  name: "platform-name"
  job_runner: "slurm"
```

### Components Section
Define workflow components (receiver, emulator, sender):
```yaml
components:
  my_receiver:
    type: "receiver"
    resources:
      cpus_per_task: 4
      mem: "8G"
      partition: "partition-name"
    network:
      port: 50080
      bind_address: "0.0.0.0"

  my_emulator:
    type: "emulator"
    resources:
      cpus_per_task: 4
      mem: "16G"
      partition: "partition-name"
    configuration:
      threads: 4
      latency: 50
      mem_footprint: 0.05
      output_size: 0.001
    network:
      port: 50888

  my_sender:
    type: "sender"
    resources:
      cpus_per_task: 4
      mem: "8G"
      partition: "partition-name"
    test_data:
      size: "100M"
```

### Edges Section
Define relationships between components:
```yaml
edges:
  - from: "my_receiver"
    to: "my_emulator"
    type: "ready"
  
  - from: "my_emulator"
    to: "my_sender"
    type: "ready"
  
  - from: "my_sender"
    to: "my_receiver"
    type: "succeeded"
    condition: "!"  # Indicates task should complete
```

### Containers Section
Container image settings:
```yaml
containers:
  image_path: "jlabtsai/rtdp-cpu_emu:latest"
```

## Component Types

### Receiver
- Receives data from senders
- Requires network port configuration
- Monitors data reception and signals completion

### Emulator
- Processes data between receiver and sender
- Configurable processing parameters (threads, latency, etc.)
- Requires network port for communication

### Sender
- Generates and sends test data
- Configurable data size
- Signals success upon completion

## Edge Types
- `ready`: Component is ready to receive data
- `succeeded`: Component has completed successfully
- `completed`: Component has finished its task

## Output

The generator creates a Cylc workflow in the specified output directory with the following structure:

```
generated/
└── flow.cylc    # Generated Cylc workflow file
```

## Error Handling

The generator validates your configuration against the schema before generating the workflow. If there are any validation errors, it will display them and exit without generating the workflow. 