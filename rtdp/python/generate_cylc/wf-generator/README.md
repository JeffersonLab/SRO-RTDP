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
  cylc_path: "/path/to/cylc"
  hosts: "host-specification"
  job_runner: "slurm"
```

### Components Section
Settings for each component (receiver, emulator, sender):
```yaml
components:
  receiver:
    resources:
      ntasks: 1
      cpus_per_task: 4
      mem: "8G"
      partition: "partition-name"
      timeout: "2h"
    network:
      port: 50080
      bind_address: "0.0.0.0"
    environment:
      output_dir: "path/to/output"
      log_dir: "path/to/logs"
```

### Containers Section
Container image settings:
```yaml
containers:
  image_path: "image.sif"
  docker_source: "docker/image:tag"
```

## Customization

To customize the workflow:

1. Modify the configuration file to match your requirements
2. Update the Jinja2 template in `templates/flow.cylc.j2` if needed
3. Run the generator with your modified configuration

## Output

The generator creates a Cylc workflow in the specified output directory with the following structure:

```
generated/
└── flow.cylc    # Generated Cylc workflow file
```

## Error Handling

The generator validates your configuration against the schema before generating the workflow. If there are any validation errors, it will display them and exit without generating the workflow. 