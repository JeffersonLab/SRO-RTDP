import pytest
import yaml
from click.testing import CliRunner
from rtdpcli import cli
import os

# --- Workflow Generation Tests ---

def test_generate_workflow_from_valid_yaml(tmp_path):
    """Test generating a workflow from a valid YAML config file."""
    # Create a minimal valid config
    config = {
        'workflow': {'name': 'testflow', 'description': 'A test workflow'},
        'platform': {'name': 'testplatform', 'job_runner': 'slurm'},
        'components': {},
        'edges': [],
        'containers': {'image_path': 'dummy.sif'}
    }
    config_path = tmp_path / 'config.yml'
    with open(config_path, 'w') as f:
        yaml.dump(config, f)

    output_dir = tmp_path / 'output'
    os.makedirs(output_dir)

    runner = CliRunner()
    result = runner.invoke(cli, [
        'generate',
        '--config', str(config_path),
        '--output', str(output_dir)
    ])

    # Check CLI exit code
    assert result.exit_code == 0
    # Check that flow.cylc was created
    assert (output_dir / 'flow.cylc').exists()

def test_validate_config_success(tmp_path):
    """Test validating a correct config file (should succeed)."""
    # TODO: Implement: call CLI validate, expect success
    pass

def test_validate_config_failure(tmp_path):
    """Test validating an incorrect config file (should fail)."""
    # TODO: Implement: call CLI validate, expect error
    pass

def test_export_template_vars(tmp_path):
    """Test exporting required variables for a workflow template."""
    # TODO: Implement: call CLI template-vars, check output
    pass

def test_generate_workflow_missing_required_vars(tmp_path):
    """Test error when required variables are missing in config."""
    # TODO: Implement: call CLI generate with incomplete config, expect error
    pass

def test_generate_cpu_emu_workflow(tmp_path):
    """Test generating a real CPU Emulator workflow from config and template."""
    import yaml
    from click.testing import CliRunner
    from rtdpcli import cli
    import os

    # Prepare a config with all required variables for the cpu_emu template
    config = {
        'workflow': {'name': 'cpu-emu', 'description': 'CPU Emulator test'},
        'platform': {'name': 'jlab_slurm', 'job_runner': 'slurm'},
        'BASE_PORT': 55555,
        'COMPONENTS': 5,
        'THREADS': 1,
        'LATENCY': 100,
        'MEM_FOOTPRINT': 0.01,
        'OUTPUT_SIZE': 0.001,
        'SLEEP': 0,
        'VERBOSE': 2,
        # Add more as needed for the template
    }
    config_path = tmp_path / 'config.yml'
    with open(config_path, 'w') as f:
        yaml.dump(config, f)

    output_dir = tmp_path / 'output'
    os.makedirs(output_dir)

    # Assume the template is in a known location, e.g. rtdp/cpp/cpu_emu/cylc/flow.cylc
    template_path = os.path.abspath('rtdp/cpp/cpu_emu/cylc/flow.cylc')

    runner = CliRunner()
    result = runner.invoke(cli, [
        'generate',
        '--config', str(config_path),
        '--output', str(output_dir),
        '--template', template_path
    ])

    assert result.exit_code == 0
    flow_path = output_dir / 'flow.cylc'
    assert flow_path.exists()
    content = flow_path.read_text()
    assert 'BASE_PORT=55555' in content
    assert 'THREADS = "1"' in content 