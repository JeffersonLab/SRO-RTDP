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
    import yaml
    from click.testing import CliRunner
    from rtdpcli import cli
    import os

    # Minimal valid config for cpu_emu workflow
    config = {
        'workflow': {'name': 'cpu-emu', 'description': 'CPU Emulator test'},
        'platform': {'name': 'jlab_slurm'},
        'containers': {'image_path': 'cpu-emu.sif'},
        'BASE_PORT': 55555,
        'COMPONENTS': 5,
        'THREADS': 1,
        'LATENCY': 100,
        'MEM_FOOTPRINT': 0.01,
        'OUTPUT_SIZE': 0.001,
        'SLEEP': 0,
        'VERBOSE': 2
    }
    config_path = tmp_path / 'config.yml'
    with open(config_path, 'w') as f:
        yaml.dump(config, f)

    runner = CliRunner()
    result = runner.invoke(cli, [
        'validate',
        '--config', str(config_path),
        '--template', os.path.abspath('rtdp/cpp/cpu_emu/cylc/flow.cylc.j2')
    ])
    print(result.output)  # DEBUG: print CLI output for diagnosis
    assert result.exit_code == 0
    assert 'Config is valid' in result.output

def test_validate_config_failure(tmp_path):
    """Test validating an incorrect config file (should fail)."""
    import yaml
    from click.testing import CliRunner
    from rtdpcli import cli
    import os

    # Incomplete config (missing required variable 'BASE_PORT')
    config = {
        'workflow': {'name': 'cpu-emu', 'description': 'CPU Emulator test'},
        'platform': {'name': 'jlab_slurm'},
        # 'BASE_PORT' missing
        'COMPONENTS': 5,
        'THREADS': 1,
        'LATENCY': 100,
        'MEM_FOOTPRINT': 0.01,
        'OUTPUT_SIZE': 0.001,
        'SLEEP': 0,
        'VERBOSE': 2
    }
    config_path = tmp_path / 'config.yml'
    with open(config_path, 'w') as f:
        yaml.dump(config, f)

    runner = CliRunner()
    result = runner.invoke(cli, [
        'validate',
        '--config', str(config_path),
        '--template', os.path.abspath('rtdp/cpp/cpu_emu/cylc/flow.cylc.j2')
    ])
    assert result.exit_code != 0
    assert 'Missing required variable' in result.output

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

def test_generate_gpu_proxy_workflow(tmp_path):
    """Test generating a real GPU Proxy workflow from config and template."""
    import yaml
    from click.testing import CliRunner
    from rtdpcli import cli
    import os

    # Prepare a config with all required variables for the gpu_proxy template
    config = {
        'workflow': {'name': 'gpu-proxy', 'description': 'GPU Proxy test'},
        'platform': {'name': 'jlab_slurm'},
        'containers': {'image_path': 'gpu-proxy.sif'},
        'IN_PORT': 55555,
        'OUT_PORT': 55556,
        'NIC': 'eth0',
        'GPU_NIC': 'eth1',
        'MATRIX_WIDTH': 2048,
        'SEND_RATE': 150,
        'GROUP_SIZE': 30720000,
        'PROXY_RATE': 1.0,
        'SEND_ALL_ONES': 0,
        'SOCKET_HWM': 1,
        'proxy_partition': 'gpu',
        'proxy_gres': 'gpu:A100:1',
        'proxy_mem': '100G',
        'proxy_cpus': 4
    }
    config_path = tmp_path / 'config.yml'
    with open(config_path, 'w') as f:
        yaml.dump(config, f)

    output_dir = tmp_path / 'output'
    os.makedirs(output_dir)

    # Path to the Jinja2 template
    template_path = os.path.abspath('rtdp/cuda/gpu_proxy/cylc/flow.cylc.j2')

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
    assert 'IN_PORT = "55555"' in content
    assert '--partition = gpu' in content
    assert '--gres = gpu:A100:1' in content
    assert 'MATRIX_WIDTH = "2048"' in content 

def test_generate_chain_workflow(tmp_path):
    """Test generating a real chain_workflow from config and template."""
    import yaml
    from click.testing import CliRunner
    from rtdpcli import cli
    import os

    # Prepare a config with all required variables for the chain_workflow template
    config = {
        'workflow': {'name': 'chain-workflow', 'description': 'Chain workflow test'},
        'platform': {'name': 'jlab_slurm'},
        'BASE_PORT': 55555,
        'VERBOSE': 2,
        'containers': {
            'CPU_EMU_SIF': 'cpu-emu.sif',
            'GPU_PROXY_SIF': 'gpu-proxy.sif'
        }
    }
    config_path = tmp_path / 'config.yml'
    with open(config_path, 'w') as f:
        yaml.dump(config, f)

    output_dir = tmp_path / 'output'
    os.makedirs(output_dir)

    # Path to the Jinja2 template (to be created)
    template_path = os.path.abspath('rtdp/cylc/chain_workflow/flow.cylc.j2')

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
    assert 'CPU_EMU_SIF' in content
    assert 'GPU_PROXY_SIF' in content
    assert '--job-name = receiver' in content 

def test_template_vars_lists_required_vars(tmp_path):
    """Test that template-vars outputs required variables for a template (excluding those with defaults)."""
    from click.testing import CliRunner
    from rtdpcli import cli
    import os

    template_path = os.path.abspath('rtdp/cpp/cpu_emu/cylc/flow.cylc.j2')
    runner = CliRunner()
    result = runner.invoke(cli, [
        'template-vars',
        '--template', template_path
    ])
    output = result.output
    # Should list required variables (not those with defaults)
    assert 'BASE_PORT' in output
    assert 'COMPONENTS' in output
    assert 'THREADS' in output
    assert 'LATENCY' in output
    assert 'MEM_FOOTPRINT' in output
    assert 'OUTPUT_SIZE' in output
    assert 'SLEEP' in output
    assert 'VERBOSE' in output
    # Should NOT list variables with defaults (e.g., AVG_RATE, RMS, DUTY, NIC)
    assert 'AVG_RATE' not in output
    assert 'RMS' not in output
    assert 'DUTY' not in output
    assert 'NIC' not in output
    # Should include containers (since it's required)
    assert 'containers' in output 

def test_example_config_outputs_valid_yaml(tmp_path):
    """Test that example-config outputs valid YAML with all required variables for a template."""
    from click.testing import CliRunner
    from rtdpcli import cli
    import os
    import yaml

    template_path = os.path.abspath('rtdp/cpp/cpu_emu/cylc/flow.cylc.j2')
    runner = CliRunner()
    result = runner.invoke(cli, [
        'example-config',
        '--template', template_path
    ])
    output = result.output
    # Should be valid YAML
    config = yaml.safe_load(output)
    # Should contain all required variables (not those with defaults)
    for var in [
        'BASE_PORT', 'COMPONENTS', 'THREADS', 'LATENCY', 'MEM_FOOTPRINT',
        'OUTPUT_SIZE', 'SLEEP', 'VERBOSE', 'containers']:
        assert var in config or (var == 'containers' and 'containers' in config)
    # Should not require variables with defaults
    for var in ['AVG_RATE', 'RMS', 'DUTY', 'NIC']:
        assert var not in config 

def test_run_workflow_invokes_cylc(tmp_path, monkeypatch):
    """Test that 'run' invokes cylc install and cylc play on the workflow directory."""
    from click.testing import CliRunner
    from rtdpcli import cli
    import os
    import builtins

    # Create a dummy workflow directory with a flow.cylc
    wf_dir = tmp_path / 'myflow'
    os.makedirs(wf_dir)
    (wf_dir / 'flow.cylc').write_text('[scheduler]\n')

    # Track subprocess calls
    calls = []
    def fake_run(cmd, *args, **kwargs):
        calls.append(cmd)
        class Result:
            returncode = 0
        return Result()
    monkeypatch.setattr('subprocess.run', fake_run)

    runner = CliRunner()
    result = runner.invoke(cli, [
        'run',
        '--workflow', str(wf_dir)
    ])
    output = result.output
    # Should call cylc install and cylc play
    assert any('cylc' in c[0] and 'install' in c for c in calls)
    assert any('cylc' in c[0] and 'play' in c for c in calls)
    # Should report success
    assert 'Workflow started' in output 