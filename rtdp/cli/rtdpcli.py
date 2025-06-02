import click
import yaml
import os
from jinja2 import Template, Environment, meta, nodes

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

@cli.command('template-vars')
@click.option('--template', required=True, type=click.Path(exists=True, dir_okay=False), help='Path to Jinja2 template (flow.cylc)')
def template_vars(template):
    """Show required variables for a workflow template (excluding those with defaults)."""
    from jinja2 import Environment, meta, nodes
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
            if isinstance(node.node, nodes.Name):
                defaulted.add(node.node.name)
            elif isinstance(node.node, nodes.Getattr):
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

    truly_required = set(var for var in required_vars if var not in defaulted_vars)
    for var in sorted(truly_required):
        click.echo(var)

@cli.command()
@click.option('--workflow', required=True, type=click.Path(exists=True, file_okay=False), help='Path to workflow directory')
def run(workflow):
    """Run a workflow using cylc install and cylc play."""
    import subprocess
    import os
    flow_path = os.path.join(workflow, 'flow.cylc')
    if not os.path.exists(flow_path):
        raise click.ClickException(f"No flow.cylc found in {workflow}")
    # Install the workflow
    result1 = subprocess.run(['cylc', 'install', workflow])
    if result1.returncode != 0:
        raise click.ClickException("cylc install failed")
    # Play the workflow
    result2 = subprocess.run(['cylc', 'play', workflow])
    if result2.returncode != 0:
        raise click.ClickException("cylc play failed")
    click.echo("Workflow started")

@cli.command()
@click.option('--workflow', required=True, help='Workflow name or directory')
def status(workflow):
    """Show workflow status using cylc status."""
    import subprocess
    try:
        result = subprocess.run(['cylc', 'status', workflow], capture_output=True, text=True)
        if result.returncode != 0:
            raise click.ClickException(f"cylc status failed: {result.stderr.strip()}")
        click.echo(result.stdout.strip())
    except Exception as e:
        raise click.ClickException(f"Failed to get workflow status: {e}")

@cli.command()
@click.option('--workflow', required=True, help='Workflow name')
@click.option('--task', required=True, help='Task name')
def logs(workflow, task):
    """Stream logs for a workflow or task using cylc cat-log."""
    import subprocess
    try:
        # For simplicity, show logs for cycle 1
        log_target = f"{workflow}//1/{task}"
        result = subprocess.run(['cylc', 'cat-log', log_target], capture_output=True, text=True)
        if result.returncode != 0:
            raise click.ClickException(f"cylc cat-log failed: {result.stderr.strip()}")
        click.echo(result.stdout.strip())
    except Exception as e:
        raise click.ClickException(f"Failed to get logs: {e}")

@cli.command()
def list():
    """List all workflows using cylc list."""
    import subprocess
    try:
        result = subprocess.run(['cylc', 'list'], capture_output=True, text=True)
        if result.returncode != 0:
            raise click.ClickException(f"cylc list failed: {result.stderr.strip()}")
        click.echo(result.stdout.strip())
    except Exception as e:
        raise click.ClickException(f"Failed to list workflows: {e}")

@cli.command()
@click.option('--workflow', required=True, help='Workflow name')
def stop(workflow):
    """Stop a workflow using cylc stop."""
    import subprocess
    try:
        result = subprocess.run(['cylc', 'stop', workflow], capture_output=True, text=True)
        if result.returncode != 0:
            raise click.ClickException(f"cylc stop failed: {result.stderr.strip()}")
        click.echo(result.stdout.strip())
    except Exception as e:
        raise click.ClickException(f"Failed to stop workflow: {e}")

@cli.command()
@click.option('--workflow', required=True, help='Workflow name')
def restart(workflow):
    """Restart a workflow using cylc restart."""
    import subprocess
    try:
        result = subprocess.run(['cylc', 'restart', workflow], capture_output=True, text=True)
        if result.returncode != 0:
            raise click.ClickException(f"cylc restart failed: {result.stderr.strip()}")
        click.echo(result.stdout.strip())
    except Exception as e:
        raise click.ClickException(f"Failed to restart workflow: {e}")

@cli.command()
def remove():
    """Remove a workflow."""
    click.echo("[remove] Not implemented yet.")

@cli.command()
def export():
    """Export workflow configuration or graph."""
    click.echo("[export] Not implemented yet.")

@cli.command()
def cleanup():
    """Clean up workflow outputs and logs."""
    click.echo("[cleanup] Not implemented yet.")

@cli.command('example-config')
@click.option('--template', required=True, type=click.Path(exists=True, dir_okay=False), help='Path to Jinja2 template (flow.cylc)')
def example_config(template):
    """Show example YAML config for a workflow template (required vars, with placeholders)."""
    import yaml
    from jinja2 import Environment, meta, nodes
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
            if isinstance(node.node, nodes.Name):
                defaulted.add(node.node.name)
            elif isinstance(node.node, nodes.Getattr):
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

    truly_required = set(var for var in required_vars if var not in defaulted_vars)
    # Build example config dict, handling nested keys (e.g., containers.image_path)
    example = {}
    for var in truly_required:
        if '.' in var:
            # Nested key, e.g., containers.image_path
            parts = var.split('.')
            d = example
            for p in parts[:-1]:
                if p not in d:
                    d[p] = {}
                d = d[p]
            d[parts[-1]] = f'<{var}>'
        else:
            example[var] = f'<{var}>'
    yaml.dump(example, stream=click.get_text_stream('stdout'), default_flow_style=False)

@cli.command('list-plugins')
def list_plugins():
    """List available plugins."""
    click.echo("[list-plugins] Not implemented yet.")

if __name__ == '__main__':
    cli() 