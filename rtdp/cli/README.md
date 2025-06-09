# RTDP Workflow CLI

A command-line interface for generating, running, and monitoring RTDP workflows.

## Installation

1. Create and activate a Python virtual environment:
   ```bash
   python -m venv venv
   source venv/bin/activate  # On Linux/macOS
   # or
   .\venv\Scripts\activate  # On Windows
   ```

2. Install required packages:
   ```bash
   pip install -r requirements.txt
   ```

## Available Commands

### Workflow Generation

1. **Generate a workflow** from a YAML config and template:
   ```bash
   python -m rtdp.cli.rtdpcli generate \
     --config <config.yml> \
     --output <output_dir> \
     --template <flow.cylc.j2>
   ```
   Example:
   ```bash
   python -m rtdp.cli.rtdpcli generate \
     --config rtdp/cuda/gpu_proxy/cylc/cli-config-example.yml \
     --output rtdp/cuda/gpu_proxy/cylc/generated \
     --template rtdp/cuda/gpu_proxy/cylc/flow.cylc.j2
   ```

2. **Validate a config** against a template:
   ```bash
   python -m rtdp.cli.rtdpcli validate \
     --config <config.yml> \
     --template <flow.cylc.j2>
   ```

3. **Show example config** for a template:
   ```bash
   python -m rtdp.cli.rtdpcli example-config \
     --template <flow.cylc.j2>
   ```
   This command shows a YAML config template with all variables used in the template, including nested fields. For example:
   ```yaml
   # For a template using these variables:
   # {{ workflow.name }}
   # {{ workflow.description }}
   # {{ containers.image_path }}
   # {{ containers.settings.timeout }}
   workflow:
     name: <workflow.name>
     description: <workflow.description>
   containers:
     image_path: <containers.image_path>
     settings:
       timeout: <containers.settings.timeout>
   ```

### Workflow Execution

1. **Run a workflow**:
   ```bash
   python -m rtdp.cli.rtdpcli run \
     --workflow <workflow_dir>
   ```
   Example:
   ```bash
   python -m rtdp.cli.rtdpcli run \
     --workflow rtdp/cuda/gpu_proxy/cylc/generated
   ```

### Workflow Monitoring

1. **Monitor a workflow** using Cylc's TUI:
   ```bash
   python -m rtdp.cli.rtdpcli monitor \
     --workflow <workflow_dir>
   ```
   Example:
   ```bash
   python -m rtdp.cli.rtdpcli monitor \
     --workflow rtdp/cuda/gpu_proxy/cylc/generated
   ```

## Example Workflow Types

The CLI supports several workflow types:

1. **GPU Proxy Workflow**
   - Location: `rtdp/cuda/gpu_proxy/cylc/`
   - Config: `cli-config-example.yml`
   - Template: `flow.cylc.j2`

2. **CPU Emulator Workflow**
   - Location: `rtdp/cpp/cpu_emu/cylc/`
   - Config: `cli-config-example.yml`
   - Template: `flow.cylc.j2`

3. **Chain Workflow**
   - Location: `rtdp/cylc/chain_workflow/`
   - Config: `cli-config-example.yml`
   - Template: `flow.cylc.j2`

## Directory Structure

Each workflow directory should contain:
- `flow.cylc.j2`: Jinja2 template for the workflow
- `cli-config-example.yml`: Example configuration
- `generated/`: Directory for generated workflow files
  - `flow.cylc`: Generated Cylc workflow file
  - `config.yml`: Copied configuration file
  - `input/`: Input files directory
  - `output/`: Output files directory
  - `logs/`: Log files directory

## Notes

- All commands require a Python virtual environment
- The CLI uses Cylc for workflow execution and monitoring
- Workflow names are taken from `workflow.name` in the config file
- The TUI interface (monitor command) provides real-time workflow status 