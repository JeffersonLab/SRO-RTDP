from typing import Dict, List
import yaml


class WorkflowComponent:
    def __init__(self, name: str, component_type: str) -> None:
        self.name = name
        self.type = component_type
        self.config = self._get_default_config()

    def _get_default_config(self) -> Dict:
        if self.type == 'sender':
            return {
                'ntasks': 1,
                'cpus_per_task': 4,
                'mem': '8G',
                'partition': 'ifarm',
                'timeout': '2h'
            }
        elif self.type == 'receiver':
            return {
                'ntasks': 1,
                'cpus_per_task': 4,
                'mem': '8G',
                'partition': 'ifarm',
                'timeout': '2h'
            }
        else:  # emulator
            return {
                'ntasks': 1,
                'cpus_per_task': 4,
                'mem': '16G',
                'partition': 'ifarm',
                'timeout': '2h',
                'threads': 4,
                'latency': 50,
                'mem_footprint': 0.05,
                'output_size': 0.001
            }

    def update_config(self, config: Dict) -> None:
        self.config.update(config)


class WorkflowManager:
    def __init__(self) -> None:
        self.components: Dict[str, WorkflowComponent] = {}
        self.connections: List[tuple] = []
        self.platform_config = {
            'name': 'jlab_slurm',
            'cylc_path': '/path/to/cylc-env/bin/',
            'hosts': 'tsai@ifarm2402',
            'job_runner': 'slurm'
        }
        self.container_config = {
            'image': 'cpu-emu.sif',
            'docker_source': 'jlabtsai/rtdp-cpu_emu:latest'
        }
        self.test_data = {'size': '100M'}

    def add_component(self, name: str, component_type: str) -> None:
        if name not in self.components:
            self.components[name] = WorkflowComponent(name, component_type)

    def remove_component(self, name: str) -> None:
        if name in self.components:
            # Remove any connections involving this component
            self.connections = [
                (src, dst) for src, dst in self.connections
                if src != name and dst != name
            ]
            del self.components[name]

    def add_connection(self, source: str, target: str) -> bool:
        if (source in self.components and
            target in self.components and
                (source, target) not in self.connections):
            self.connections.append((source, target))
            return True
        return False

    def remove_connection(self, source: str, target: str) -> None:
        if (source, target) in self.connections:
            self.connections.remove((source, target))

    def update_component_config(self, name: str, config: Dict) -> None:
        if name in self.components:
            self.components[name].update_config(config)

    def update_platform_config(self, config: Dict) -> None:
        self.platform_config.update(config)

    def update_container_config(self, config: Dict) -> None:
        self.container_config.update(config)

    def update_test_data(self, config: Dict) -> None:
        self.test_data.update(config)

    def generate_config(self) -> Dict:
        config = {
            'workflow': {
                'name': 'cpu-emu',
                'description': 'Cylc-based CPU Emulator Testing Workflow'
            },
            'platform': self.platform_config,
            'resources': {},
            'network': {
                'ports': {}
            },
            'emulators': {},
            'test_data': self.test_data,
            'containers': {
                'cpu_emulator': self.container_config
            }
        }

        # Add component configurations
        for name, component in self.components.items():
            if component.type == 'emulator':
                config['emulators'][name] = component.config
            else:
                config['resources'][name] = component.config

        return config

    def save_config(self, filename: str = 'config.yml') -> None:
        config = self.generate_config()
        with open(filename, 'w') as f:
            yaml.dump(config, f, default_flow_style=False)

    def get_component_types(self) -> Dict[str, str]:
        return {name: comp.type for name, comp in self.components.items()}

