#!/usr/bin/env python3

import argparse
import os
import sys
import yaml
from jinja2 import Environment, FileSystemLoader
import jsonschema
from typing import Dict, Any, List


def load_yaml(file_path: str) -> Dict[str, Any]:
    """Load and parse a YAML file."""
    with open(file_path, 'r') as f:
        return yaml.safe_load(f)


def validate_config(config: Dict[str, Any], schema: Dict[str, Any]) -> None:
    """Validate configuration against schema."""
    try:
        jsonschema.validate(instance=config, schema=schema)
    except jsonschema.exceptions.ValidationError as e:
        print(f"Configuration validation error: {e.message}")
        sys.exit(1)


def generate_control_edges(data_edges: List[Dict[str, str]]) -> List[Dict[str, str]]:
    """Generate control flow edges from data flow edges.
    Control flow goes in reverse direction of data flow to ensure consumers
    are ready before producers start."""
    control_edges = []

    # Create a mapping of data flow paths
    data_flow_paths = {}
    for edge in data_edges:
        from_comp = edge['from']
        to_comp = edge['to']

        # Initialize the path for this sender if not exists
        if from_comp.startswith('sender'):
            data_flow_paths[from_comp] = []

        # For each edge, add the destination to all paths that include the source
        for sender, path in data_flow_paths.items():
            if not path or path[-1] == from_comp:
                path.append(to_comp)

    # Create reverse dependencies for each edge
    for edge in data_edges:
        # Consumer must be ready before producer starts
        control_edges.append({
            'from': edge['to'],
            'to': edge['from'],
            'type': 'ready'
        })

    # Add completion edges based on actual data flow paths
    for sender, path in data_flow_paths.items():
        if path:  # If there is a path
            receiver = path[-1]  # Get the last component in the path
            control_edges.append({
                'from': sender,
                'to': receiver,
                'type': 'succeeded',
                'condition': '!'
            })

    return control_edges


def generate_graph(config: Dict[str, Any]) -> str:
    """Generate Cylc graph from data flow edges."""
    # Generate control edges from data flow
    control_edges = generate_control_edges(config['edges'])

    # Generate graph lines
    graph_lines = []

    for edge in control_edges:
        from_task = edge['from']
        to_task = edge['to']
        edge_type = edge['type']
        condition = edge.get('condition', '')

        # Construct the edge string
        if edge_type == 'ready':
            edge_str = f"{from_task}:ready => {to_task}"
        elif edge_type == 'succeeded':
            edge_str = f"{from_task}:succeeded => {condition}{to_task}"
        elif edge_type == 'completed':
            edge_str = f"{from_task}:completed"

        graph_lines.append(edge_str)

    return '\n            '.join(graph_lines)


def generate_component_config(component_id: str, component: Dict[str, Any],
                              edges: List[Dict[str, str]]) -> Dict[str, Any]:
    """Generate component-specific configuration."""
    if component['type'] == 'emulator':
        # Find target from edges
        target_id = None
        for edge in edges:
            if edge['from'] == component_id:
                target_id = edge['to']
                break

        # Generate CPU emulator config format
        return {
            'output_dir': '/output',
            'threads': component['configuration']['threads'],
            'latency': component['configuration']['latency'],
            'mem_footprint': component['configuration']['mem_footprint'],
            'output_size': component['configuration']['output_size'],
            'processing_type': component['configuration']['processing_type'],
            'network': {
                'listen_port': component['network']['listen_port'],
                'target_port': '${TARGET_PORT}',  # Will be replaced at runtime
                'target_ip': '${TARGET_IP}',      # Will be replaced at runtime
                'verbosity': 1
            }
        }
    else:
        # Standard config for Python components
        config = {
            'id': component_id,
            'type': component['type'],
            'resources': component['resources'],
            'network': component['network']
        }

        # Add component-specific configuration
        if component['type'] == 'sender':
            config['sender_config'] = component['sender_config']
            config['test_data'] = component['test_data']
        elif component['type'] == 'receiver':
            config['receiver_config'] = component['receiver_config']
        elif component['type'] == 'load_balancer':
            config['load_balancer_config'] = component['load_balancer_config']
        elif component['type'] == 'aggregator':
            config['aggregator_config'] = component['aggregator_config']

        # Add connection information from edges
        config['connections'] = []
        for edge in edges:
            if edge['from'] == component_id:
                config['connections'].append({
                    'target': edge['to'],
                    'data_type': edge.get('data_type', 'raw'),
                    'buffer_size': edge.get('buffer_size', '1M')
                })
            elif edge['to'] == component_id:
                config['connections'].append({
                    'source': edge['from'],
                    'data_type': edge.get('data_type', 'raw'),
                    'buffer_size': edge.get('buffer_size', '1M')
                })

        return config


def generate_workflow(config: Dict[str, Any], template_dir: str,
                      output_dir: str) -> None:
    """Generate Cylc workflow files from configuration."""
    # Set up Jinja2 environment
    env = Environment(
        loader=FileSystemLoader(template_dir),
        trim_blocks=True,
        lstrip_blocks=True
    )

    # Add custom filters
    env.filters['generate_graph'] = lambda _: generate_graph(config)

    # Create output directory structure
    os.makedirs(output_dir, exist_ok=True)
    os.makedirs(os.path.join(output_dir, 'share'), exist_ok=True)
    os.makedirs(os.path.join(output_dir, 'sifs'), exist_ok=True)

    # Generate component-specific configurations
    for component_id, component in config['components'].items():
        component_config = generate_component_config(
            component_id, component, config['edges']
        )
        config_path = os.path.join(
            output_dir, 'share', f'{component_id}_config.yml'
        )
        with open(config_path, 'w') as f:
            yaml.dump(component_config, f, default_flow_style=False)

    # Generate flow.cylc
    flow_template = env.get_template('flow.cylc.j2')
    flow_content = flow_template.render(config=config)

    # Write flow.cylc
    with open(os.path.join(output_dir, 'flow.cylc'), 'w') as f:
        f.write(flow_content)


def main() -> None:
    parser = argparse.ArgumentParser(
        description='Generate Cylc workflow from configuration')
    parser.add_argument('config', help='Path to configuration YAML file')
    parser.add_argument('--schema', default='config_schema.yml',
                        help='Path to configuration schema file')
    parser.add_argument('--template-dir', default='templates',
                        help='Directory containing workflow templates')
    parser.add_argument('--output-dir', default='generated',
                        help='Output directory for generated workflow')

    args = parser.parse_args()

    # Load configuration and schema
    config = load_yaml(args.config)
    schema = load_yaml(args.schema)

    # Validate configuration
    validate_config(config, schema)

    # Generate workflow
    generate_workflow(
        config,
        args.template_dir,
        args.output_dir
    )

    print(f"Workflow generated successfully in {args.output_dir}")


if __name__ == '__main__':
    main()
