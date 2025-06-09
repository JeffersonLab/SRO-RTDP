import click
import yaml
import os
from jinja2 import Template, Environment, meta, nodes
import subprocess

@click.group()
def cli():
    """RTDP Workflow CLI"""
    pass

@cli.command()
@click.option('--config', required=True, type=click.Path(exists=True, dir_okay=False), help='YAML config file')
@click.option('--output', required=True, type=click.Path(file_okay=False), help='Output directory for workflow files')
@click.option('--template', required=True, type=click.Path(exists=True, dir_okay=False), help='Path to Jinja2 template (flow.cylc)')
def generate(config, output, template):
    """Generate a workflow from config and template."""
    # Read YAML config
    with open(config, 'r') as f:
        cfg = yaml.safe_load(f)
    # Flatten config for template context (top-level keys only)
    context = dict(cfg)
    # Also add nested keys at top-level for convenience
    for k, v in cfg.items():
        if isinstance(v, dict):
            context.update(v)
    # Read template
    with open(template, 'r') as f:
        template_str = f.read()
    j2_template = Template(template_str)
    # Render template
    try:
        rendered = j2_template.render(**context)
    except Exception as e:
        click.echo(f"Error rendering template: {e}", err=True)
        raise click.ClickException("Template rendering failed")
    # Ensure output directory exists
    os.makedirs(output, exist_ok=True)
    # Write rendered flow.cylc
    flow_path = os.path.join(output, 'flow.cylc')
    with open(flow_path, 'w') as f:
        f.write(rendered)
    # Copy the config file into the output directory as config.yml
    import shutil
    shutil.copyfile(config, os.path.join(output, 'config.yml'))
    click.echo(f"Generated flow.cylc at {flow_path}")

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
    from jinja2 import Environment, meta, nodes
    # Read template
    with open(template, 'r') as f:
        template_str = f.read()
    env = Environment()
    ast = env.parse(template_str)
    template_vars = meta.find_undeclared_variables(ast)

    # Build example config dict with proper tree structure
    example = {}
    for var in template_vars:
        # Split into parts and build nested structure
        parts = var.split('.')
        d = example
        for p in parts[:-1]:
            if p not in d:
                d[p] = {}
            d = d[p]
        d[parts[-1]] = f'<{var}>'
    
    # Sort keys to maintain consistent order
    def sort_dict(d):
        return {k: sort_dict(v) if isinstance(v, dict) else v 
                for k, v in sorted(d.items())}
    
    example = sort_dict(example)
    
    # Dump with proper formatting
    yaml.dump(example, 
             stream=click.get_text_stream('stdout'),
             default_flow_style=False,
             sort_keys=False)  # Don't sort keys in YAML output to maintain our order

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
    workflow_name = os.path.basename(os.path.abspath(workflow))
    if os.path.exists(config_path):
        with open(config_path, 'r') as f:
            try:
                cfg = yaml.safe_load(f)
                if 'workflow' in cfg and 'name' in cfg['workflow']:
                    workflow_name = cfg['workflow']['name']
                # Find SIF(s) from config
                sif_dir = os.path.join(workflow, 'sifs')
                os.makedirs(sif_dir, exist_ok=True)
                sif_tasks = []
                containers = cfg.get('containers', {})
                if 'image_path' in containers:
                    sif_name = containers['image_path']
                    sif_path = os.path.join(sif_dir, sif_name)
                    if 'gpu-proxy' in sif_name:
                        docker_img = 'jlabtsai/rtdp-gpu_proxy:latest'
                    elif 'cpu-emu' in sif_name:
                        docker_img = 'jlabtsai/rtdp-cpu_emu:latest'
                    else:
                        docker_img = None
                    if docker_img and not os.path.exists(sif_path):
                        sif_tasks.append((sif_path, docker_img))
                for k, v in containers.items():
                    if k.endswith('_SIF') and isinstance(v, str):
                        sif_name = v
                        sif_path = os.path.join(sif_dir, sif_name)
                        if 'gpu-proxy' in sif_name:
                            docker_img = 'jlabtsai/rtdp-gpu_proxy:latest'
                        elif 'cpu-emu' in sif_name:
                            docker_img = 'jlabtsai/rtdp-cpu_emu:latest'
                        else:
                            docker_img = None
                        if docker_img and not os.path.exists(sif_path):
                            sif_tasks.append((sif_path, docker_img))
                for sif_path, docker_img in sif_tasks:
                    click.echo(f"SIF not found: {sif_path}. Building from {docker_img}...")
                    result = subprocess.run(['apptainer', 'build', sif_path, f'docker://{docker_img}'])
                    if result.returncode != 0:
                        raise click.ClickException(f"Failed to build SIF: {sif_path}")
                    click.echo(f"Successfully built {sif_path}")
            except Exception as e:
                click.echo(f"[Warning] Could not auto-build SIF: {e}")
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