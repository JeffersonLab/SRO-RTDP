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


def generate_graph(edges: List[Dict[str, str]]) -> str:
    """Generate Cylc graph from edge definitions."""
    graph_lines = []

    for edge in edges:
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


def generate_workflow(config: Dict[str, Any], template_dir: str, output_dir: str) -> None:
    """Generate Cylc workflow files from configuration."""
    # Set up Jinja2 environment
    env = Environment(
        loader=FileSystemLoader(template_dir),
        trim_blocks=True,
        lstrip_blocks=True
    )

    # Add custom filters
    env.filters['generate_graph'] = generate_graph

    # Generate flow.cylc
    flow_template = env.get_template('flow.cylc.j2')
    flow_content = flow_template.render(config=config)

    # Create output directory if it doesn't exist
    os.makedirs(output_dir, exist_ok=True)

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
