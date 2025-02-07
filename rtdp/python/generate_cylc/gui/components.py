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
    connect_to: Optional[List[Dict[str, str]]] = None


@dataclass
class EmulatorConfig:
    threads: int = 4
    latency: int = 50
    mem_footprint: float = 0.05
    output_size: float = 0.001
    processing_type: str = "cpu_intensive"


@dataclass
class SenderConfig:
    data_source: Optional[str] = None
    data_format: str = "raw"
    chunk_size: str = "1M"
    test_data: Dict[str, str] = field(
        default_factory=lambda: {"size": "100M", "pattern": "random"}
    )


@dataclass
class ReceiverConfig:
    output_dir: str = "received_data"
    data_validation: bool = True
    buffer_size: str = "64M"
    compression: bool = False


@dataclass
class LoadBalancerConfig:
    strategy: str = "round_robin"
    max_queue_size: str = "100M"
    health_check_interval: int = 5
    backpressure_threshold: float = 0.8
    rebalance_threshold: float = 0.2


@dataclass
class AggregatorConfig:
    strategy: str = "ordered"
    buffer_size: str = "256M"
    max_delay: int = 1000
    batch_size: int = 100
    window_size: str = "1s"


@dataclass
class TestData:
    size: str = "100M"


@dataclass
class Component:
    id: str
    type: str  # "receiver", "emulator", "sender", "load_balancer", "aggregator"
    resources: Resources
    network: Optional[Network] = None
    configuration: Optional[EmulatorConfig] = None  # For emulator type
    sender_config: Optional[SenderConfig] = None  # For sender type
    receiver_config: Optional[ReceiverConfig] = None  # For receiver type
    # For load balancer
    load_balancer_config: Optional[LoadBalancerConfig] = None
    aggregator_config: Optional[AggregatorConfig] = None  # For aggregator
    test_data: Optional[TestData] = None  # For sender type


@dataclass
class Edge:
    from_id: str  # Source component ID (data producer)
    to_id: str    # Target component ID (data consumer)
    description: Optional[str] = None  # Optional description of the data flow
    data_type: Optional[str] = None  # Type of data being transferred
    buffer_size: str = "1M"  # Buffer size for data transfer


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

    def add_component(
        self,
        component_id: str,
        component_type: str,
        resources: Dict
    ) -> None:
        """Add a new component to the workflow."""
        if component_id in self.components:
            raise ValueError(f"Component {component_id} already exists")

        # Set default memory based on component type
        if component_type == 'emulator':
            resources['mem'] = '16G'
        elif component_type in ['load_balancer', 'aggregator']:
            resources['mem'] = '8G'

        resources_obj = Resources(**resources)
        component = Component(
            id=component_id,
            type=component_type,
            resources=resources_obj
        )

        # Initialize type-specific configurations
        if component_type == 'sender':
            component.sender_config = SenderConfig()
        elif component_type == 'receiver':
            component.receiver_config = ReceiverConfig()
        elif component_type == 'emulator':
            component.configuration = EmulatorConfig()
        elif component_type == 'load_balancer':
            component.load_balancer_config = LoadBalancerConfig()
        elif component_type == 'aggregator':
            component.aggregator_config = AggregatorConfig()

        self.components[component_id] = component

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
                component.network = Network(
                    listen_port=int(network_config["listen_port"]),
                bind_address=network_config.get("bind_address"),
                connect_to=network_config.get("connect_to", [])
                )

        # Update type-specific configuration
        if component.type == "emulator" and "configuration" in config:
            component.configuration = EmulatorConfig(**config["configuration"])
        elif component.type == "sender" and "sender_config" in config:
            component.sender_config = SenderConfig(**config["sender_config"])
        elif component.type == "receiver" and "receiver_config" in config:
            component.receiver_config = ReceiverConfig(
                **config["receiver_config"])
        elif (component.type == "load_balancer" and
              "load_balancer_config" in config):
            component.load_balancer_config = LoadBalancerConfig(
                **config["load_balancer_config"]
            )
        elif component.type == "aggregator" and "aggregator_config" in config:
            component.aggregator_config = AggregatorConfig(
                **config["aggregator_config"]
            )

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
                        "bind_address": comp.network.bind_address,
                        **({"connect_to": comp.network.connect_to}
                           if comp.network.connect_to else {})
                    }} if comp.network else {}),
                    **({"configuration": {
                        "threads": comp.configuration.threads,
                        "latency": comp.configuration.latency,
                        "mem_footprint": comp.configuration.mem_footprint,
                        "output_size": comp.configuration.output_size,
                        "processing_type": comp.configuration.processing_type
                    }} if comp.configuration else {}),
                    **({"sender_config": {
                        "data_source": comp.sender_config.data_source,
                        "data_format": comp.sender_config.data_format,
                        "chunk_size": comp.sender_config.chunk_size,
                        "test_data": comp.sender_config.test_data
                    }} if comp.sender_config else {}),
                    **({"receiver_config": {
                        "output_dir": comp.receiver_config.output_dir,
                        "data_validation": comp.receiver_config.data_validation,
                        "buffer_size": comp.receiver_config.buffer_size,
                        "compression": comp.receiver_config.compression
                    }} if comp.receiver_config else {}),
                    **({"load_balancer_config": {
                        "strategy": comp.load_balancer_config.strategy,
                        "max_queue_size": comp.load_balancer_config.max_queue_size,
                        "health_check_interval":
                            comp.load_balancer_config.health_check_interval,
                        "backpressure_threshold":
                            comp.load_balancer_config.backpressure_threshold,
                        "rebalance_threshold":
                            comp.load_balancer_config.rebalance_threshold
                    }} if comp.load_balancer_config else {}),
                    **({"aggregator_config": {
                        "strategy": comp.aggregator_config.strategy,
                        "buffer_size": comp.aggregator_config.buffer_size,
                        "max_delay": comp.aggregator_config.max_delay,
                        "batch_size": comp.aggregator_config.batch_size,
                        "window_size": comp.aggregator_config.window_size
                    }} if comp.aggregator_config else {})
                }
                for comp in self.components.values()
            },
            "edges": [
                {
                    "from": edge.from_id,
                    "to": edge.to_id,
                    "data_type": edge.data_type,
                    "buffer_size": edge.buffer_size,
                    **({"description": edge.description}
                       if edge.description else {})
                }
                for edge in self.edges
            ],
            "containers": {
                "image_path": self.container_image_path
            }
        }

    def validate_edge(self, from_id: str, to_id: str) -> None:
        """Validate that an edge represents a valid data flow."""
        if from_id not in self.components or to_id not in self.components:
            raise ValueError("Both components must exist")

        from_comp = self.components[from_id]
        to_comp = self.components[to_id]

        # Define valid connections
        valid_flows = {
            'sender': ['emulator', 'load_balancer'],
            'emulator': ['emulator', 'receiver', 'aggregator'],
            'load_balancer': ['emulator'],
            'aggregator': ['receiver']
        }

        if (from_comp.type not in valid_flows or
                to_comp.type not in valid_flows.get(from_comp.type, [])):
            raise ValueError(
                f"Invalid connection: {from_comp.type} -> {to_comp.type}"
            )

    def add_edge(
        self,
        from_id: str,
        to_id: str,
        description: Optional[str] = None,
        data_type: Optional[str] = None,
        buffer_size: str = "1M"
    ) -> None:
        """Add a new data flow edge between components."""
        self.validate_edge(from_id, to_id)
        edge = Edge(
            from_id=from_id,
            to_id=to_id,
            description=description,
            data_type=data_type,
            buffer_size=buffer_size
        )
        self.edges.append(edge)

    def remove_component(self, component_id: str) -> None:
        """Remove a component and its associated edges from the workflow."""
        if component_id in self.components:
            del self.components[component_id]
            self.edges = [
                edge for edge in self.edges
                if edge.from_id != component_id and edge.to_id != component_id
            ]

    def save_config(self, filename: str = 'config.yml') -> None:
        """Save the workflow configuration to a YAML file."""
        config = self.to_dict()
        with open(filename, 'w') as f:
            yaml.dump(config, f, default_flow_style=False)

    def get_component_types(self) -> Dict[str, str]:
        return {name: comp.type for name, comp in self.components.items()}
