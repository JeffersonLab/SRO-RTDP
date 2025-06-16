import click
import yaml
import os
from jinja2 import Template, Environment, meta, nodes
import subprocess
from .resource_manager import ResourceManager

# Update workflow_types to include new multi-component templates
workflow_types = {
    'gpu_proxy': {
        'template': 'rtdp/cuda/gpu_proxy/cylc/flow.cylc.j2',
        'description': 'Single GPU proxy workflow'
    },
    'cpu_emu': {
        'template': 'rtdp/cpp/cpu_emu/cylc/flow.cylc.j2',
        'description': 'Single CPU emulator workflow'
    },
    'chain_workflow': {
        'template': 'rtdp/cylc/chain_workflow/flow.cylc.j2',
        'description': 'Simple chain workflow'
    },
    'multi_gpu_proxy': {
        'template': 'rtdp/cylc/multi_gpu_proxy/flow.cylc.j2',
        'description': 'Multi-GPU proxy workflow'
    },
    'multi_cpu_emu': {
        'template': 'rtdp/cylc/multi_cpu_emu/flow.cylc.j2',
        'description': 'Multi-CPU emulator workflow'
    },
    'multi_mixed': {
        'template': 'rtdp/cylc/multi_mixed/flow.cylc.j2',
        'description': 'Mixed multi-component workflow'
    }
}

@click.group()
def cli():
    """RTDP Workflow CLI"""
    pass

@cli.command()
@click.option('--config', required=True, help='Path to the YAML configuration file')
@click.option('--output', required=True, help='Output directory for the generated workflow')
@click.option('--workflow-type', required=True, help='Type of workflow to generate (gpu_proxy, cpu_emu, chain_workflow, multi_gpu_proxy, multi_cpu_emu, multi_mixed)')
def generate(config, output, workflow_type):
    """Generate a Cylc workflow from a YAML configuration file.

    The workflow type must be one of:
    - gpu_proxy: Single GPU proxy workflow
    - cpu_emu: Single CPU emulator workflow
    - chain_workflow: Simple chain workflow
    - multi_gpu_proxy: Multi-GPU proxy workflow (requires 'gpu_proxies' list in config)
    - multi_cpu_emu: Multi-CPU emulator workflow (requires 'cpu_emulators' list in config)
    - multi_mixed: Mixed multi-component workflow (requires both 'gpu_proxies' and 'cpu_emulators' lists in config)
    """
    if workflow_type not in workflow_types:
        raise click.ClickException(f"Unknown workflow type: {workflow_type}")

    # Load the configuration
    with open(config, 'r') as f:
        config_data = yaml.safe_load(f)

    # Initialize resource manager
    resource_manager = ResourceManager()

    # Validate and allocate resources based on workflow type
    if workflow_type == 'multi_gpu_proxy':
        if 'gpu_proxies' not in config_data:
            raise click.ClickException("Multi-GPU proxy workflow requires a 'gpu_proxies' list in the config.")
        
        # Validate and allocate resources for each GPU proxy
        for proxy in config_data['gpu_proxies']:
            if not resource_manager.allocate_resources('gpu_proxy', proxy):
                raise click.ClickException(f"Failed to allocate resources for GPU proxy: {proxy}")
    
    elif workflow_type == 'multi_cpu_emu':
        if 'cpu_emulators' not in config_data:
            raise click.ClickException("Multi-CPU emulator workflow requires a 'cpu_emulators' list in the config.")
        
        # Validate and allocate resources for each CPU emulator
        for emulator in config_data['cpu_emulators']:
            if not resource_manager.allocate_resources('cpu_emu', emulator):
                raise click.ClickException(f"Failed to allocate resources for CPU emulator: {emulator}")
    
    elif workflow_type == 'multi_mixed':
        if 'gpu_proxies' not in config_data or 'cpu_emulators' not in config_data:
            raise click.ClickException("Mixed workflow requires both 'gpu_proxies' and 'cpu_emulators' lists in the config.")
        
        # Validate and allocate resources for each GPU proxy
        for proxy in config_data['gpu_proxies']:
            if not resource_manager.allocate_resources('gpu_proxy', proxy):
                raise click.ClickException(f"Failed to allocate resources for GPU proxy: {proxy}")
        
        # Validate and allocate resources for each CPU emulator
        for emulator in config_data['cpu_emulators']:
            if not resource_manager.allocate_resources('cpu_emu', emulator):
                raise click.ClickException(f"Failed to allocate resources for CPU emulator: {emulator}")

    # Load the template
    template_path = workflow_types[workflow_type]['template']
    with open(template_path, 'r') as f:
        template_content = f.read()

    # Create output directory if it doesn't exist
    os.makedirs(output, exist_ok=True)

    # Render the template
    template = Template(template_content)
    rendered_content = template.render(**config_data)

    # Write the rendered content to flow.cylc
    with open(os.path.join(output, 'flow.cylc'), 'w') as f:
        f.write(rendered_content)

    # Write the configuration to config.yml
    with open(os.path.join(output, 'config.yml'), 'w') as f:
        yaml.dump(config_data, f)

    click.echo(f"Workflow generated successfully in {output}")

@cli.command()
@click.option('--config', required=True, help='Path to the YAML configuration file')
@click.option('--workflow-type', required=True, help='Type of workflow to validate')
def validate(config, workflow_type):
    """Validate a workflow configuration file."""
    if workflow_type not in workflow_types:
        raise click.ClickException(f"Unknown workflow type: {workflow_type}")

    # Load the configuration
    with open(config, 'r') as f:
        config_data = yaml.safe_load(f)

    # Initialize resource manager
    resource_manager = ResourceManager()

    # Validate resources based on workflow type
    if workflow_type == 'multi_gpu_proxy':
        if 'gpu_proxies' not in config_data:
            raise click.ClickException("Multi-GPU proxy workflow requires a 'gpu_proxies' list in the config.")
        
        # Validate resources for each GPU proxy
        for proxy in config_data['gpu_proxies']:
            if not resource_manager.validate_gpu_resources(proxy):
                raise click.ClickException(f"Invalid GPU proxy configuration: {proxy}")
            if not resource_manager.validate_network_resources(proxy):
                raise click.ClickException(f"Invalid network configuration for GPU proxy: {proxy}")
    
    elif workflow_type == 'multi_cpu_emu':
        if 'cpu_emulators' not in config_data:
            raise click.ClickException("Multi-CPU emulator workflow requires a 'cpu_emulators' list in the config.")
        
        # Validate resources for each CPU emulator
        for emulator in config_data['cpu_emulators']:
            if not resource_manager.validate_cpu_resources(emulator):
                raise click.ClickException(f"Invalid CPU emulator configuration: {emulator}")
            if not resource_manager.validate_network_resources(emulator):
                raise click.ClickException(f"Invalid network configuration for CPU emulator: {emulator}")
    
    elif workflow_type == 'multi_mixed':
        if 'gpu_proxies' not in config_data or 'cpu_emulators' not in config_data:
            raise click.ClickException("Mixed workflow requires both 'gpu_proxies' and 'cpu_emulators' lists in the config.")
        
        # Validate resources for each GPU proxy
        for proxy in config_data['gpu_proxies']:
            if not resource_manager.validate_gpu_resources(proxy):
                raise click.ClickException(f"Invalid GPU proxy configuration: {proxy}")
            if not resource_manager.validate_network_resources(proxy):
                raise click.ClickException(f"Invalid network configuration for GPU proxy: {proxy}")
        
        # Validate resources for each CPU emulator
        for emulator in config_data['cpu_emulators']:
            if not resource_manager.validate_cpu_resources(emulator):
                raise click.ClickException(f"Invalid CPU emulator configuration: {emulator}")
            if not resource_manager.validate_network_resources(emulator):
                raise click.ClickException(f"Invalid network configuration for CPU emulator: {emulator}")

    click.echo("Configuration is valid")

if __name__ == '__main__':
    cli() 