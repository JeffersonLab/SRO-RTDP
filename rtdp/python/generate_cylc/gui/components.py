from typing import Dict, List, Optional
from dataclasses import dataclass, field
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


@dataclass
class Resources:
    partition: str
    cpus_per_task: int = 4
    mem: str = "4G"


@dataclass
class Network:
    listen_port: int
    bind_address: Optional[str] = None


@dataclass
class EmulatorConfig:
    threads: int = 4
    latency: int = 50
    mem_footprint: float = 0.05
    output_size: float = 0.001


@dataclass
class TestData:
    size: str = "100M"


@dataclass
class Component:
    id: str
    type: str  # "receiver", "emulator", or "sender"
    resources: Resources
    network: Optional[Network] = None
    configuration: Optional[EmulatorConfig] = None  # For emulator type
    test_data: Optional[TestData] = None  # For sender type


@dataclass
class Edge:
    from_id: str  # Source component ID (data producer)
    to_id: str    # Target component ID (data consumer)
    description: Optional[str] = None  # Optional description of the data flow


@dataclass
class Platform:
    name: str
    job_runner: str


@dataclass
class WorkflowManager:
    name: str = ""
    description: str = ""
    platform: Platform = field(
        default_factory=lambda: Platform("jlab_slurm", "slurm"))
    components: Dict[str, Component] = field(default_factory=dict)
    edges: List[Edge] = field(default_factory=list)
    container_image_path: str = ""

    def add_component(self, component_id: str, component_type: str, resources: Dict) -> None:
        """Add a new component to the workflow."""
        if component_id in self.components:
            raise ValueError(f"Component {component_id} already exists")

        # Set default memory to 16G for emulator
        if component_type == 'emulator':
            resources['mem'] = '16G'

        resources_obj = Resources(**resources)
        component = Component(
            id=component_id,
            type=component_type,
            resources=resources_obj,
            test_data=TestData() if component_type == 'sender' else None
        )
        self.components[component_id] = component

    def remove_component(self, component_id: str) -> None:
        """Remove a component and its associated edges from the workflow."""
        if component_id in self.components:
            # Remove the component
            del self.components[component_id]

            # Remove any edges connected to this component
            self.edges = [edge for edge in self.edges
                          if edge.from_id != component_id and edge.to_id != component_id]

            # Ensure the component is completely removed from any internal references
            if hasattr(self, '_component_types'):
                if hasattr(self._component_types, component_id):
                    delattr(self._component_types, component_id)

    def add_edge(
        self,
        from_id: str,
        to_id: str,
        description: Optional[str] = None
    ) -> None:
        """Add a new data flow edge between components."""
        if from_id not in self.components or to_id not in self.components:
            raise ValueError("Both components must exist")

        # Validate that the edge represents a valid data flow
        from_comp = self.components[from_id]
        to_comp = self.components[to_id]

        # Validate data flow direction
        if from_comp.type == 'receiver':
            raise ValueError("Receiver cannot be a data producer")
        if to_comp.type == 'sender':
            raise ValueError("Sender cannot be a data consumer")

        edge = Edge(
            from_id=from_id,
            to_id=to_id,
            description=description
        )
        self.edges.append(edge)

    def remove_edge(self, from_id: str, to_id: str) -> None:
        """Remove an edge between components."""
        self.edges = [edge for edge in self.edges
                      if edge.from_id != from_id or edge.to_id != to_id]

    def update_component_config(self, component_id: str, config: Dict) -> None:
        """Update a component's configuration."""
        if component_id not in self.components:
            raise ValueError(f"Component {component_id} does not exist")

        component = self.components[component_id]

        # Update resources
        if "resources" in config:
            component.resources = Resources(**config["resources"])

        # Update network configuration
        if "network" in config and config["network"]:
            network_config = config["network"]
            if "listen_port" in network_config:
                component.network = Network(
                    listen_port=int(network_config["listen_port"]),
                    bind_address=network_config.get("bind_address")
                )

        # Update type-specific configuration
        if component.type == "emulator" and "configuration" in config:
            component.configuration = EmulatorConfig(**config["configuration"])

        if component.type == "sender" and "test_data" in config:
            component.test_data = TestData(**config["test_data"])

    def to_dict(self) -> Dict:
        """Convert the workflow to a dictionary format matching the schema."""
        return {
            "workflow": {
                "name": self.name,
                "description": self.description
            },
            "platform": {
                "name": self.platform.name,
                "job_runner": self.platform.job_runner
            },
            "components": {
                comp.id: {
                    "type": comp.type,
                    "resources": {
                        "partition": comp.resources.partition,
                        "cpus_per_task": comp.resources.cpus_per_task,
                        "mem": comp.resources.mem
                    },
                    **({"network": {
                        "listen_port": comp.network.listen_port,
                        **({
                            "bind_address": comp.network.bind_address
                        } if (comp.type == "receiver" and
                              comp.network.bind_address) else {})
                    }} if comp.network else {}),
                    **({"configuration": {
                        "threads": comp.configuration.threads,
                        "latency": comp.configuration.latency,
                        "mem_footprint": comp.configuration.mem_footprint,
                        "output_size": comp.configuration.output_size
                    }} if comp.configuration else {}),
                    **({"test_data": {
                        "size": comp.test_data.size
                    }} if comp.test_data else {})
                }
                for comp in self.components.values()
            },
            "edges": [
                {
                    "from": edge.from_id,
                    "to": edge.to_id,
                    **({"description": edge.description} if edge.description else {})
                }
                for edge in self.edges
            ],
            "containers": {
                "image_path": self.container_image_path
            }
        }

    def generate_config(self) -> Dict:
        config = self.to_dict()
        return config

    def save_config(self, filename: str = 'config.yml') -> None:
        config = self.generate_config()
        with open(filename, 'w') as f:
            yaml.dump(config, f, default_flow_style=False)

    def get_component_types(self) -> Dict[str, str]:
        return {name: comp.type for name, comp in self.components.items()}
