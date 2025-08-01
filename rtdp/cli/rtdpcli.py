import click
import yaml
import os
from jinja2 import Template, Environment, meta, nodes
import subprocess
import concurrent.futures
import hashlib
import json
from pathlib import Path
from .sif_cache import SIFCache

def docker_to_sif_filter(image_path):
    """Convert Docker image name to SIF filename."""
    if not image_path:
        return ""
    
    # If image_path is already a Docker image name (contains '/'), convert it
    if '/' in image_path:
        return image_path.replace('/', '_').replace(':', '_') + '.sif'
    else:
        # Legacy support: if it's a short name, assume it's a Docker image name
        return image_path + '.sif'

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

    'multi_gpu_proxy': {
        'template': 'rtdp/cli/cylc/multi_gpu_proxy/flow.cylc.j2',
        'template_separate': 'rtdp/cli/cylc/multi_gpu_proxy/flow.cylc.separate.j2',
        'description': 'Multi-GPU proxy workflow'
    },
    'multi_cpu_emu': {
        'template': 'rtdp/cli/cylc/multi_cpu_emu/flow.cylc.j2',
        'template_separate': 'rtdp/cli/cylc/multi_cpu_emu/flow.cylc.separate.j2',
        'description': 'Multi-CPU emulator workflow'
    },
    'multi_mixed': {
        'template': 'rtdp/cli/cylc/multi_mixed/flow.cylc.j2',
        'template_separate': 'rtdp/cli/cylc/multi_mixed/flow.cylc.separate.j2',
        'description': 'Mixed multi-component workflow'
    }
}

def build_sif_container(sif_path, docker_img, cache=None):
    """Build a single SIF container with caching support."""
    # Check cache first
    if cache and cache.is_sif_valid(sif_path, docker_img):
        return True, f"SIF {sif_path} is up-to-date (cached)"
    
    try:
        result = subprocess.run(
            ['apptainer', 'build', sif_path, f'docker://{docker_img}'],
            capture_output=True,
            text=True,
            check=True
        )
        
        # Update cache after successful build
        if cache:
            cache.update_cache(sif_path, docker_img)
        
        return True, f"Successfully built {sif_path}"
    except subprocess.CalledProcessError as e:
        return False, f"Failed to build SIF {sif_path}: {e.stderr}"

def extract_sif_requirements(config_data):
    """Extract SIF requirements from config using direct Docker image names."""
    unique_sifs = set()
    
    # Helper function to add SIF if needed
    def add_sif_if_needed(image_path, sif_dir):
        if not image_path:
            return
        
        # If image_path is already a Docker image name (contains '/'), use it directly
        if '/' in image_path:
            docker_img = image_path
            # Generate SIF filename from Docker image name
            sif_name = image_path.replace('/', '_').replace(':', '_') + '.sif'
        else:
            # Legacy support: if it's a short name, assume it's a Docker image name
            # This maintains backward compatibility
            docker_img = image_path
            sif_name = image_path + '.sif'
        
        sif_path = os.path.join(sif_dir, sif_name)
        if not os.path.exists(sif_path):
            unique_sifs.add((sif_path, docker_img))
    
    # Check all sections that might contain image_path
    sections_to_check = [
        config_data.get('containers', {}),
        config_data.get('sender', {}),
        config_data.get('receiver', {}),
        *config_data.get('components', []),
        *config_data.get('gpu_proxies', []),
        *config_data.get('cpu_emulators', [])
    ]
    
    for section in sections_to_check:
        if isinstance(section, dict) and 'image_path' in section:
            add_sif_if_needed(section['image_path'], 'sifs')
    
    # Handle default Docker image names for GPU proxies and CPU emulators
    for proxy in config_data.get('gpu_proxies', []):
        if isinstance(proxy, dict) and proxy.get('device') == 'gpu':
            # Use default GPU proxy Docker image if no image_path specified
            if 'image_path' not in proxy:
                add_sif_if_needed('jlabtsai/rtdp-gpu_proxy:latest', 'sifs')
    
    for emu in config_data.get('cpu_emulators', []):
        if isinstance(emu, dict) and emu.get('device') == 'cpu':
            # Use default CPU emulator Docker image if no image_path specified
            if 'image_path' not in emu:
                add_sif_if_needed('jlabtsai/rtdp-cpu_emu:latest', 'sifs')
    
    # Handle mixed workflow components
    for component in config_data.get('components', []):
        if isinstance(component, dict):
            # Check if component has explicit image_path
            if 'image_path' in component:
                add_sif_if_needed(component['image_path'], 'sifs')
            else:
                # Use default Docker images based on component type
                if component.get('type') == 'gpu_proxy':
                    add_sif_if_needed('jlabtsai/rtdp-gpu_proxy:latest', 'sifs')
                elif component.get('type') == 'cpu_emulator':
                    add_sif_if_needed('jlabtsai/rtdp-cpu_emu:latest', 'sifs')
    
    return unique_sifs

@click.group()
def cli():
    """RTDP Workflow CLI"""
    pass

@cli.command()
@click.option('--config', required=True, help='Path to the YAML configuration file')
@click.option('--output', required=True, help='Output directory for the generated workflow')
@click.option('--workflow-type', required=True, help='Type of workflow to generate (gpu_proxy, cpu_emu, multi_gpu_proxy, multi_cpu_emu, multi_mixed)')
@click.option('--consolidated-logging/--no-consolidated-logging', default=True, help='Enable/disable consolidated logging (default: enabled)')
def generate(config, output, workflow_type, consolidated_logging):
    """Generate a Cylc workflow from a YAML configuration file.

    The workflow type must be one of:
    - gpu_proxy: Single GPU proxy workflow
    - cpu_emu: Single CPU emulator workflow
    - multi_gpu_proxy: Multi-GPU proxy workflow (requires 'gpu_proxies' list in config)
    - multi_cpu_emu: Multi-CPU emulator workflow (requires 'cpu_emulators' list in config)
    - multi_mixed: Mixed multi-component workflow (requires 'components' list in config)

    Logging options (for multi-component workflows):
    - --consolidated-logging: Generate workflow with consolidated logging (default)
    - --no-consolidated-logging: Generate workflow with separate log files for each component
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
        if workflow_type == 'multi_mixed' and 'components' not in config_data:
            raise click.ClickException("Mixed workflow requires a 'components' list in the config.")

    # Load the template based on logging preference
    if workflow_type in ['multi_gpu_proxy', 'multi_cpu_emu', 'multi_mixed']:
        if consolidated_logging:
            template_path = workflow_types[workflow_type]['template']
        else:
            template_path = workflow_types[workflow_type]['template_separate']
    else:
        template_path = workflow_types[workflow_type]['template']
    
    with open(template_path, 'r') as f:
        template_content = f.read()

    # Add consolidated_logging flag to config data for template rendering
    config_data['consolidated_logging'] = consolidated_logging
    
    # Add absolute output directory to config data for template rendering
    config_data['output_dir'] = os.path.abspath(output)

    # Create Jinja2 environment with custom filter
    env = Environment()
    env.filters['docker_to_sif'] = docker_to_sif_filter
    
    # Render the template
    template = env.from_string(template_content)
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
@click.option('--parallel-builds', default=2, help='Number of parallel SIF builds (default: 2)')
@click.option('--skip-sif-build', is_flag=True, help='Skip SIF container building')
@click.option('--disable-cache', is_flag=True, help='Disable SIF caching')
def run(workflow, parallel_builds, skip_sif_build, disable_cache):
    """Run a workflow: build SIF if missing (only if config is in workflow dir), cd to dir, cylc install --workflow-name=NAME, then cylc play NAME."""
    import subprocess
    import os
    import yaml
    from concurrent.futures import ThreadPoolExecutor, as_completed
    
    orig_dir = os.getcwd()
    # Convert to absolute path to avoid issues with directory changes
    workflow_abs = os.path.abspath(workflow)
    flow_path = os.path.join(workflow_abs, 'flow.cylc')
    if not os.path.exists(flow_path):
        raise click.ClickException(f"No flow.cylc found in {workflow}")
    
    # Only use config if present in workflow directory
    config_path = os.path.join(workflow_abs, 'config.yml')
    workflow_name = os.path.basename(workflow_abs)
    
    if os.path.exists(config_path):
        click.echo(f"📋 Found config file: {os.path.basename(config_path)}")
        click.echo(f"   Processing workflow configuration...")
        with open(config_path, 'r') as f:
            try:
                cfg = yaml.safe_load(f)
                if 'workflow' in cfg and 'name' in cfg['workflow']:
                    workflow_name = cfg['workflow']['name']
                
                if not skip_sif_build:
                    # Initialize cache if not disabled
                    cache = None if disable_cache else SIFCache()
                    
                    # Find SIF(s) from config using optimized extraction
                    sif_dir = os.path.join(workflow_abs, 'sifs')
                    os.makedirs(sif_dir, exist_ok=True)
                    
                    # Change to workflow directory for SIF building
                    os.chdir(workflow_abs)
                    
                    # Extract SIF requirements efficiently
                    unique_sifs = extract_sif_requirements(cfg)
                    
                    if unique_sifs:
                        click.echo(f"🔧 Processing {len(unique_sifs)} SIF containers...")
                        click.echo(f"   Using {parallel_builds} parallel builds")
                        if cache:
                            click.echo(f"   Caching enabled (skips existing containers)")
                        else:
                            click.echo(f"   Caching disabled (force rebuild)")
                        click.echo(f"   ⏳ Building containers in parallel... (this may take several minutes)")
                        
                        # Build SIFs in parallel
                        with ThreadPoolExecutor(max_workers=parallel_builds) as executor:
                            # Submit all build tasks
                            future_to_sif = {
                                executor.submit(build_sif_container, sif_path, docker_img, cache): (sif_path, docker_img)
                                for sif_path, docker_img in unique_sifs
                            }
                            
                            click.echo(f"   📊 Building {len(unique_sifs)} containers...")
                            
                            # Collect results as they complete
                            for future in as_completed(future_to_sif):
                                sif_path, docker_img = future_to_sif[future]
                                try:
                                    success, message = future.result()
                                    if success:
                                        click.echo(f"   ✅ {message}")
                                    else:
                                        raise click.ClickException(message)
                                except Exception as e:
                                    raise click.ClickException(f"Failed to build SIF {sif_path}: {e}")
                        
                        click.echo(f"🎉 All SIF containers processed successfully!")
                    else:
                        click.echo(f"✅ No SIF containers need to be built (all up-to-date)")
                        
            except Exception as e:
                click.echo(f"⚠️  [Warning] Could not auto-build SIF: {e}")
                click.echo(f"   Proceeding with workflow installation...")
    else:
        click.echo(f"⚠️  Config file not found at {config_path}")
        click.echo(f"   Skipping SIF container building")
        click.echo(f"   Proceeding with workflow installation...")
    
    try:
        os.chdir(workflow_abs)
        
        # Install workflow
        click.echo(f"📦 Installing workflow '{workflow_name}'...")
        click.echo(f"   Working directory: {workflow_abs}")
        click.echo(f"   Flow file: {os.path.basename(flow_path)}")
        click.echo(f"   ⏳ Installation in progress... (this may take a moment)")
        
        result1 = subprocess.run(['cylc', 'install', f'--workflow-name={workflow_name}'])
        if result1.returncode != 0:
            raise click.ClickException("cylc install failed")
        click.echo(f"✅ Workflow '{workflow_name}' installed successfully!")
        
        # Start workflow
        click.echo(f"🚀 Starting workflow '{workflow_name}'...")
        click.echo(f"   This will launch the workflow in the background")
        click.echo(f"   Use 'cylc tui {workflow_name}' to monitor progress")
        click.echo(f"   ⏳ Startup in progress... (this may take a moment)")
        
        result2 = subprocess.run(['cylc', 'play', workflow_name])
        if result2.returncode != 0:
            raise click.ClickException("cylc play failed")
        
        click.echo(f"🎉 Workflow '{workflow_name}' started successfully!")
        click.echo(f"📊 Monitor with: cylc tui {workflow_name}")
        click.echo(f"📋 List workflows: cylc scan")
        click.echo(f"📋 Stop workflow: cylc stop {workflow_name}")
        
        # Summary
        click.echo(f"\n📈 Summary:")
        click.echo(f"   • Workflow: {workflow_name}")
        click.echo(f"   • Status: Running")
        click.echo(f"   • Directory: {workflow_abs}")
        click.echo(f"   • Monitor: cylc tui {workflow_name}")
        click.echo(f"   • Stop: cylc stop {workflow_name}")
        
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

@cli.command()
@click.option('--clear', is_flag=True, help='Clear all cached SIF containers')
@click.option('--stats', is_flag=True, help='Show cache statistics')
def cache(clear, stats):
    """Manage SIF container cache."""
    cache_manager = SIFCache()
    
    if clear:
        cache_manager.clear_cache()
        click.echo("SIF cache cleared successfully!")
    elif stats:
        stats_info = cache_manager.get_cache_stats()
        click.echo("SIF Cache Statistics:")
        click.echo(f"  Total cached files: {stats_info['total_files']}")
        click.echo(f"  Total cache size: {stats_info['total_size_mb']:.2f} MB")
        click.echo(f"  Cache directory: {stats_info['cache_dir']}")
    else:
        click.echo("Use --stats to view cache statistics or --clear to clear cache")

if __name__ == '__main__':
    cli() 