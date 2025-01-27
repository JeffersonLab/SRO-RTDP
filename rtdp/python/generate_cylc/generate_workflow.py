import yaml
import os
import sys
from pathlib import Path

def load_config(config_file):
    with open(config_file, 'r') as f:
        return yaml.safe_load(f)

def generate_iperf_workflow(config):
    """Generate iperf3 testing workflow files"""
    # Create directory structure
    create_directories(['scripts', 'etc/config', 'sifs'])
    
    # Generate flow.cylc
    generate_iperf_flow_cylc(config)
    
    # Generate global.cylc
    generate_global_cylc(config)
    
    # Generate other configuration files
    generate_prometheus_config(config)
    generate_process_exporter_config(config)

def generate_cpu_emu_workflow(config):
    """Generate CPU emulator workflow files"""
    # Create directory structure
    create_directories(['scripts', 'etc/config', 'sifs'])
    
    # Generate flow.cylc
    generate_cpu_emu_flow_cylc(config)
    
    # Generate global.cylc
    generate_global_cylc(config)
    
    # Generate build script
    generate_build_script(config)

def create_directories(dirs):
    """Create required directories"""
    for d in dirs:
        Path(d).mkdir(parents=True, exist_ok=True)

def main():
    if len(sys.argv) != 2:
        print("Usage: python generate_workflow.py <config.yml>")
        sys.exit(1)
        
    config_file = sys.argv[1]
    config = load_config(config_file)
    
    # Determine workflow type and generate appropriate files
    if config['workflow']['name'] == 'iperf-test':
        generate_iperf_workflow(config)
    elif config['workflow']['name'] == 'cpu-emu':
        generate_cpu_emu_workflow(config)
    else:
        print(f"Unknown workflow type: {config['workflow']['name']}")
        sys.exit(1)

if __name__ == "__main__":
    main() 