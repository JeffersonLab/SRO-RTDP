import os
import yaml
import subprocess
import logging

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')

# Sample configs for multi-component workflows
multi_gpu_config = {
    'platform': {
        'name': 'jlab_slurm'
    },
    'partition': 'gpu',
    'containers': {
        'image_path': 'gpu-proxy.sif'
    },
    'receiver': {
        'port': 5000
    },
    'gpu_proxies': [
        {
            'device': 'A100',
            'partition': 'gpu',
            'gres': 'gpu:A100:1',
            'mem': '100G',
            'cpus': 4,
            'in_port': 5000,
            'out_port': 5001,
            'device_id': 0
        },
        {
            'device': 'V100',
            'partition': 'gpu',
            'gres': 'gpu:V100:1',
            'mem': '80G',
            'cpus': 4,
            'in_port': 5001,
            'out_port': 5002,
            'device_id': 1
        }
    ],
    'sender': {
        'port': 5002,
        'host': 'localhost'
    }
}

multi_cpu_config = {
    'platform': {
        'name': 'jlab_slurm'
    },
    'partition': 'ifarm',
    'containers': {
        'image_path': 'cpu-emu.sif'
    },
    'receiver': {
        'port': 5000
    },
    'cpu_emulators': [
        {
            'id': 'cpu1',
            'cpus': 8,
            'mem': '16G',
            'in_port': 5000,
            'out_port': 5001,
            'threads': 4,
            'latency': 100
        },
        {
            'id': 'cpu2',
            'cpus': 8,
            'mem': '16G',
            'in_port': 5001,
            'out_port': 5002,
            'threads': 4,
            'latency': 100
        }
    ],
    'sender': {
        'port': 5002,
        'host': 'localhost'
    }
}

multi_mixed_config = {
    'platform': {
        'name': 'jlab_slurm'
    },
    'partition': 'gpu',
    'containers': {
        'image_path': 'mixed-workflow.sif'
    },
    'receiver': {
        'port': 5000
    },
    'gpu_proxies': [
        {
            'device': 'A100',
            'partition': 'gpu',
            'gres': 'gpu:A100:1',
            'mem': '100G',
            'cpus': 4,
            'in_port': 5000,
            'out_port': 5001,
            'device_id': 0
        }
    ],
    'cpu_emulators': [
        {
            'id': 'cpu1',
            'cpus': 8,
            'mem': '16G',
            'in_port': 5001,
            'out_port': 5002,
            'threads': 4,
            'latency': 100
        }
    ],
    'sender': {
        'port': 5002,
        'host': 'localhost'
    }
}

# Write sample configs to temporary YAML files
def write_config(config, filename):
    with open(filename, 'w') as f:
        yaml.dump(config, f)

# Test the CLI generate command
def test_generate(config_file, workflow_type, output_dir):
    cmd = ['python3', 'rtdp/cli/rtdpcli.py', 'generate', '--config', config_file, '--output', output_dir, '--workflow-type', workflow_type]
    try:
        subprocess.run(cmd, check=True)
        logging.info(f"Successfully generated {workflow_type} workflow in {output_dir}")
    except subprocess.CalledProcessError as e:
        logging.error(f"Error generating {workflow_type} workflow: {e}")

# Main test function
def main():
    # Create temporary config files
    write_config(multi_gpu_config, 'multi_gpu_config.yml')
    write_config(multi_cpu_config, 'multi_cpu_config.yml')
    write_config(multi_mixed_config, 'multi_mixed_config.yml')

    # Test multi_gpu_proxy
    test_generate('multi_gpu_config.yml', 'multi_gpu_proxy', 'output_multi_gpu')

    # Test multi_cpu_emu
    test_generate('multi_cpu_config.yml', 'multi_cpu_emu', 'output_multi_cpu')

    # Test multi_mixed
    test_generate('multi_mixed_config.yml', 'multi_mixed', 'output_multi_mixed')

    # Clean up temporary config files
    os.remove('multi_gpu_config.yml')
    os.remove('multi_cpu_config.yml')
    os.remove('multi_mixed_config.yml')

if __name__ == '__main__':
    main() 