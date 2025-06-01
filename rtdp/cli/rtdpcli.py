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
def run():
    """Run a workflow."""
    click.echo("[run] Not implemented yet.")

@cli.command()
def status():
    """Show workflow status."""
    click.echo("[status] Not implemented yet.")

@cli.command()
def logs():
    """Stream logs for a workflow or task."""
    click.echo("[logs] Not implemented yet.")

@cli.command()
def list():
    """List all workflows."""
    click.echo("[list] Not implemented yet.")

@cli.command()
def stop():
    """Stop a workflow."""
    click.echo("[stop] Not implemented yet.")

@cli.command()
def restart():
    """Restart a workflow."""
    click.echo("[restart] Not implemented yet.")

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
def example_config():
    """Show example workflow config."""
    click.echo("[example-config] Not implemented yet.")

@cli.command('list-plugins')
def list_plugins():
    """List available plugins."""
    click.echo("[list-plugins] Not implemented yet.")

if __name__ == '__main__':
    cli() 