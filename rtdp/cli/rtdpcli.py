import click
import yaml
import os
from jinja2 import Template, Environment, meta

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
    # Check for missing variables
    missing = []
    for var in required_vars:
        if var not in context:
            missing.append(var)
    if missing:
        for var in missing:
            click.echo(f"Missing required variable: {var}", err=True)
        raise click.ClickException("Config validation failed: missing required variables.")
    click.echo("Config is valid")

@cli.command('template-vars')
def template_vars():
    """Show required variables for a workflow template."""
    click.echo("[template-vars] Not implemented yet.")

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