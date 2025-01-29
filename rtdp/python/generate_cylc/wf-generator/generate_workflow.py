#!/usr/bin/env python3

import argparse
import os
import sys
from pathlib import Path
import yaml
from jinja2 import Environment, FileSystemLoader
import jsonschema


def load_yaml(file_path):
    """Load and parse a YAML file."""
    with open(file_path, 'r') as f:
        return yaml.safe_load(f)


def validate_config(config, schema):
    """Validate configuration against schema."""
    try:
        jsonschema.validate(instance=config, schema=schema)
    except jsonschema.exceptions.ValidationError as e:
        print(f"Configuration validation error: {e.message}")
        sys.exit(1)


def generate_workflow(config, template_dir, output_dir):
    """Generate Cylc workflow files from configuration."""
    # Set up Jinja2 environment
    env = Environment(
        loader=FileSystemLoader(template_dir),
        trim_blocks=True,
        lstrip_blocks=True
    )

    # Generate flow.cylc
    flow_template = env.get_template('flow.cylc.j2')
    flow_content = flow_template.render(config=config)

    # Create output directory if it doesn't exist
    os.makedirs(output_dir, exist_ok=True)

    # Write flow.cylc
    with open(os.path.join(output_dir, 'flow.cylc'), 'w') as f:
        f.write(flow_content)


def main():
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
