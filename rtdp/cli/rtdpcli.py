import click
import yaml
import os
from jinja2 import Template, Environment, meta, nodes
import subprocess

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

    # Check if this is a multi-component workflow
    if workflow_type in ['multi_gpu_proxy', 'multi_cpu_emu', 'multi_mixed']:
        if workflow_type == 'multi_gpu_proxy' and 'gpu_proxies' not in config_data:
            raise click.ClickException("Multi-GPU proxy workflow requires a 'gpu_proxies' list in the config.")
        if workflow_type == 'multi_cpu_emu' and 'cpu_emulators' not in config_data:
            raise click.ClickException("Multi-CPU emulator workflow requires a 'cpu_emulators' list in the config.")
        if workflow_type == 'multi_mixed' and ('gpu_proxies' not in config_data or 'cpu_emulators' not in config_data):
            raise click.ClickException("Mixed workflow requires both 'gpu_proxies' and 'cpu_emulators' lists in the config.")

    # Load the template
    template_path = workflow_types[workflow_type]['template']
    with open(template_path, 'r') as f:
        template_content = f.read()

    # Render the template
    template = Template(template_content)
    rendered_content = template.render(**config_data)

    # Create the output directory if it doesn't exist
    os.makedirs(output, exist_ok=True)

    # Write the rendered content to the output file
    output_file = os.path.join(output, 'flow.cylc')
    with open(output_file, 'w') as f:
        f.write(rendered_content)

    # Copy the config file to the workflow directory
    import shutil
    config_output = os.path.join(output, 'config.yml')
    shutil.copy2(config, config_output)
    click.echo(f"Copied config file to {config_output}")

    click.echo(f"Workflow generated successfully in {output_file}")

@cli.command()
@click.option('--config', required=True, type=click.Path(exists=True, dir_okay=False), help='YAML config file')
@click.option('--template', required=True, type=click.Path(exists=True, dir_okay=False), help='Path to Jinja2 template (flow.cylc)')
def validate(config, template):
    """Validate a workflow config against a Jinja2 template."""
    # Read YAML config
    with open(config, 'r') as f:
        cfg = yaml.safe_load(f)
    # Flatten config for template context (top-level keys only)
    context = dict(cfg)
    for k, v in cfg.items():
        if isinstance(v, dict):
            context.update(v)
    # Read template
    with open(template, 'r') as f:
        template_str = f.read()
    env = Environment()
    ast = env.parse(template_str)
    required_vars = meta.find_undeclared_variables(ast)

    # Find variables with a default filter (optional)
    def find_defaulted_vars(node):
        defaulted = set()
        if isinstance(node, nodes.Filter) and node.name == 'default':
            # The variable is the first argument to the filter
            if isinstance(node.node, nodes.Name):
                defaulted.add(node.node.name)
            elif isinstance(node.node, nodes.Getattr):
                # e.g., containers.image_path | default('foo')
                # We want 'containers' or 'containers.image_path'
                parts = []
                n = node.node
                while isinstance(n, nodes.Getattr):
                    parts.append(n.attr)
                    n = n.node
                if isinstance(n, nodes.Name):
                    parts.append(n.name)
                    defaulted.add('.'.join(reversed(parts)))
        for child in node.iter_child_nodes():
            defaulted |= find_defaulted_vars(child)
        return defaulted
    defaulted_vars = find_defaulted_vars(ast)

    # Only require variables that do NOT have a default filter
    truly_required = set(var for var in required_vars if var not in defaulted_vars)
    missing = []
    for var in truly_required:
        if var not in context:
            missing.append(var)
    if missing:
        for var in missing:
            click.echo(f"Missing required variable: {var}", err=True)
        raise click.ClickException("Config validation failed: missing required variables.")
    click.echo("Config is valid")

@cli.command('example-config')
@click.option('--template', required=True, type=click.Path(exists=True, dir_okay=False), help='Path to Jinja2 template (flow.cylc)')
def example_config(template):
    """Show example YAML config for a workflow template (all vars, with placeholders)."""
    import yaml
    from jinja2 import Environment, nodes

    with open(template, 'r') as f:
        template_str = f.read()
    env = Environment()
    ast = env.parse(template_str)

    # Recursively collect all variable paths (including nested)
    def collect_vars(node):
        vars = set()
        if isinstance(node, nodes.Name):
            vars.add(node.name)
        elif isinstance(node, nodes.Getattr):
            path = []
            n = node
            while isinstance(n, nodes.Getattr):
                path.append(n.attr)
                n = n.node
            if isinstance(n, nodes.Name):
                path.append(n.name)
                full = ".".join(reversed(path))
                vars.add(full)
        for child in node.iter_child_nodes():
            vars |= collect_vars(child)
        return vars

    all_vars = collect_vars(ast)

    # Build nested dict robustly
    example = {}
    for var in all_vars:
        parts = var.split('.')
        d = example
        for i, p in enumerate(parts):
            if i == len(parts) - 1:
                d[p] = f'<{var}>'
            else:
                if p not in d or not isinstance(d[p], dict):
                    d[p] = {}
                d = d[p]

    def sort_dict(d):
        return {k: sort_dict(v) if isinstance(v, dict) else v for k, v in sorted(d.items())}

    example = sort_dict(example)
    yaml.dump(example, stream=click.get_text_stream('stdout'), default_flow_style=False, sort_keys=False)

@cli.command()
@click.option('--workflow', required=True, type=click.Path(exists=True, file_okay=False), help='Path to workflow directory')
def run(workflow):
    """Run a workflow: build SIF if missing (only if config is in workflow dir), cd to dir, cylc install --workflow-name=NAME, then cylc play NAME."""
    import subprocess
    import os
    import yaml
    orig_dir = os.getcwd()
    flow_path = os.path.join(workflow, 'flow.cylc')
    if not os.path.exists(flow_path):
        raise click.ClickException(f"No flow.cylc found in {workflow}")
    # Only use config if present in workflow directory
    config_path = os.path.join(workflow, 'config.yml')
    click.echo(f"Looking for config file at: {config_path}")
    workflow_name = os.path.basename(os.path.abspath(workflow))
    if os.path.exists(config_path):
        click.echo("Found config file, reading...")
        with open(config_path, 'r') as f:
            try:
                cfg = yaml.safe_load(f)
                click.echo(f"Config contents: {cfg}")
                if 'workflow' in cfg and 'name' in cfg['workflow']:
                    workflow_name = cfg['workflow']['name']
                # Find SIF(s) from config
                sif_dir = os.path.join(workflow, 'sifs')
                os.makedirs(sif_dir, exist_ok=True)
                
                # Use a set to track unique SIFs
                unique_sifs = set()
                
                # Check containers section
                containers = cfg.get('containers', {})
                click.echo(f"Containers section: {containers}")
                if 'image_path' in containers:
                    sif_name = containers['image_path']
                    sif_path = os.path.join(sif_dir, sif_name)
                    click.echo(f"Found image_path: {sif_name}")
                    if 'gpu-proxy' in sif_name:
                        docker_img = 'jlabtsai/rtdp-gpu_proxy:latest'
                    elif 'cpu-emu' in sif_name:
                        docker_img = 'jlabtsai/rtdp-cpu_emu:latest'
                    else:
                        docker_img = None
                    if docker_img and not os.path.exists(sif_path):
                        unique_sifs.add((sif_path, docker_img))

                # Check gpu_proxies section
                gpu_proxies = cfg.get('gpu_proxies', [])
                click.echo(f"GPU proxies section: {gpu_proxies}")
                for proxy in gpu_proxies:
                    if isinstance(proxy, dict) and 'device' in proxy and proxy['device'] == 'gpu':
                        sif_name = 'gpu-proxy.sif'  # Default name for GPU proxies
                        sif_path = os.path.join(sif_dir, sif_name)
                        docker_img = 'jlabtsai/rtdp-gpu_proxy:latest'
                        if not os.path.exists(sif_path):
                            unique_sifs.add((sif_path, docker_img))

                # Check cpu_emulators section
                cpu_emulators = cfg.get('cpu_emulators', [])
                click.echo(f"CPU emulators section: {cpu_emulators}")
                for emu in cpu_emulators:
                    if isinstance(emu, dict) and 'device' in emu and emu['device'] == 'cpu':
                        sif_name = 'cpu-emu.sif'  # Default name for CPU emulators
                        sif_path = os.path.join(sif_dir, sif_name)
                        docker_img = 'jlabtsai/rtdp-cpu_emu:latest'
                        if not os.path.exists(sif_path):
                            unique_sifs.add((sif_path, docker_img))

                click.echo(f"Total unique SIFs to build: {len(unique_sifs)}")
                # Build all required SIFs
                for sif_path, docker_img in unique_sifs:
                    click.echo(f"SIF not found: {sif_path}. Building from {docker_img}...")
                    result = subprocess.run(['apptainer', 'build', sif_path, f'docker://{docker_img}'])
                    if result.returncode != 0:
                        raise click.ClickException(f"Failed to build SIF: {sif_path}")
                    click.echo(f"Successfully built {sif_path}")
            except Exception as e:
                click.echo(f"[Warning] Could not auto-build SIF: {e}")
    else:
        click.echo(f"Config file not found at {config_path}")
    try:
        os.chdir(workflow)
        result1 = subprocess.run(['cylc', 'install', f'--workflow-name={workflow_name}'])
        if result1.returncode != 0:
            raise click.ClickException("cylc install failed")
        result2 = subprocess.run(['cylc', 'play', workflow_name])
        if result2.returncode != 0:
            raise click.ClickException("cylc play failed")
        click.echo(f"Workflow '{workflow_name}' started")
    finally:
        os.chdir(orig_dir)

@cli.command()
@click.option('--workflow', required=True, type=click.Path(exists=True, file_okay=False), help='Path to workflow directory')
def monitor(workflow):
    """Monitor a workflow using Cylc's TUI interface."""
    # Get workflow name from config.yml
    config_path = os.path.join(workflow, 'config.yml')
    if not os.path.exists(config_path):
        click.echo(f"Error: config.yml not found in {workflow}", err=True)
        return
        
    try:
        with open(config_path, 'r') as f:
            config = yaml.safe_load(f)
            workflow_name = config.get('workflow', {}).get('name')
            if not workflow_name:
                click.echo("Error: workflow.name not found in config.yml", err=True)
                return
    except Exception as e:
        click.echo(f"Error reading config.yml: {e}", err=True)
        return
    
    # Start Cylc TUI
    try:
        click.echo(f"Starting Cylc TUI for workflow {workflow_name}...")
        click.echo("Press 'q' to quit the TUI")
        subprocess.run(['cylc', 'tui', workflow_name])
    except subprocess.CalledProcessError as e:
        click.echo(f"Error starting Cylc TUI: {e}", err=True)
    except KeyboardInterrupt:
        click.echo("\nTUI closed by user")

if __name__ == '__main__':
    cli() 