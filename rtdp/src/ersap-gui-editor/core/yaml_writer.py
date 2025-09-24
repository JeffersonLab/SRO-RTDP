"""
YAML Writer for ERSAP Services Configuration

This module handles the generation of services.yaml files from
the actor graph model.
"""

import yaml
from typing import Dict, List, Optional
from pathlib import Path
from .model import ActorGraph, Actor, Connection, ActorType


class YAMLWriter:
    """Handles writing ERSAP actor configurations to YAML files."""
    
    def __init__(self):
        self.yaml_config = {
            'default_flow_style': False,
            'indent': 2,
            'width': 80,
            'allow_unicode': True
        }
    
    def write_services_yaml(self, graph: ActorGraph, output_path: str) -> List[str]:
        """
        Write the actor graph to a services.yaml file.
        
        Args:
            graph: The ActorGraph to export
            output_path: Path to the output YAML file
            
        Returns:
            List of validation errors (empty if successful)
        """
        # Check if graph has any services
        if not graph.actors:
            return ["No services found in YAML file"]
        
        # Fix duplicate output topics before export
        self._fix_duplicate_output_topics(graph)
        
        # Validate the graph first
        errors = graph.get_validation_errors()
        if errors:
            return errors
        
        try:
            # Convert graph to YAML structure
            yaml_data = self._graph_to_yaml(graph)
            
            # Write to file
            output_file = Path(output_path)
            output_file.parent.mkdir(parents=True, exist_ok=True)
            
            with open(output_file, 'w', encoding='utf-8') as f:
                yaml.dump(yaml_data, f, **self.yaml_config)
            
            return []
            
        except Exception as e:
            return [f"Error writing YAML file: {str(e)}"]
    
    def _graph_to_yaml(self, graph: ActorGraph) -> Dict:
        """Convert actor graph to YAML-compatible dictionary (enhanced actor pipeline format)."""
        # Enhanced actor pipeline format
        result = {
            'pipeline': {
                'name': 'gui-generated-pipeline',
                'description': 'Pipeline generated from GUI editor',
                'version': '1.0'
            },
            'config': {
                'container': 'default',
                'lang': 'java',
                'mime-types': [
                    'binary/data-hipo',
                    'binary/data-evio'
                ],
                'global': {
                    'ccdb': 'sqlite:///ccdb.sqlite',
                    'magnet': '0.5'
                }
            }
        }
        
        # Group actors by host for node organization
        actors_by_host = {}
        for actor in graph.actors.values():
            host = actor.host if actor.host else 'localhost'
            if host not in actors_by_host:
                actors_by_host[host] = []
            actors_by_host[host].append(actor)
        
        # Create nodes section
        nodes = []
        node_id = 1
        
        for host, host_actors in actors_by_host.items():
            node = {
                'id': f'node{node_id}',
                'host': host,
                'dpe': 'java',
                'port': 7770 + node_id,
                'actors': []
            }
            
            # Process actors for this node
            for actor in host_actors:
                # Use parameters['class'] if present, else a placeholder
                engine_class = actor.parameters.get('class', 'org.jlab.ersap.engine.Unknown')
                
                actor_entry = {
                    'name': actor.name,
                    'type': actor.type.value,
                    'engine': engine_class
                }
                
                # Configure ports and topics based on actor type
                if actor.type == ActorType.SOURCE:
                    actor_entry['output_ports'] = ['out']
                    if actor.output_topic:
                        actor_entry['output_topics'] = {'out': actor.output_topic}
                        
                elif actor.type == ActorType.SINK:
                    actor_entry['input_ports'] = ['in']
                    if actor.input_topic:
                        actor_entry['input_topics'] = {'in': actor.input_topic}
                        
                elif actor.type == ActorType.COLLECTOR:
                    # Collectors have multiple inputs from connections
                    input_topics = {}
                    input_ports = []
                    port_num = 1
                    
                    for conn in graph.connections:
                        if conn.target_actor == actor.name:
                            source_actor = graph.get_actor_by_name(conn.source_actor)
                            if source_actor and source_actor.output_topic:
                                port_name = f'in{port_num}'
                                input_ports.append(port_name)
                                input_topics[port_name] = source_actor.output_topic
                                port_num += 1
                    
                    if not input_ports:
                        input_ports = ['in1', 'in2']  # Default ports
                    
                    actor_entry['input_ports'] = input_ports
                    if input_topics:
                        actor_entry['input_topics'] = input_topics
                    
                    actor_entry['output_ports'] = ['out']
                    if actor.output_topic:
                        actor_entry['output_topics'] = {'out': actor.output_topic}
                        
                elif actor.type == ActorType.ROUTING:
                    actor_entry['input_ports'] = ['in']
                    if actor.input_topic:
                        actor_entry['input_topics'] = {'in': actor.input_topic}
                    
                    # Router outputs to multiple targets
                    if hasattr(actor, 'targets') and actor.targets:
                        output_ports = [f'out{i+1}' for i in range(len(actor.targets))]
                        actor_entry['output_ports'] = output_ports
                        
                        output_topics = {}
                        for i, target in enumerate(actor.targets):
                            port_name = f'out{i+1}'
                            # Generate topic based on target name
                            output_topics[port_name] = f'data:{target}:routed'
                        actor_entry['output_topics'] = output_topics
                        
                        actor_entry['routing_strategy'] = 'round_robin'  # Default strategy
                    else:
                        actor_entry['output_ports'] = ['out']
                        if actor.output_topic:
                            actor_entry['output_topics'] = {'out': actor.output_topic}
                            
                else:  # PROCESSOR
                    actor_entry['input_ports'] = ['in']
                    actor_entry['output_ports'] = ['out']
                    
                    if actor.input_topic:
                        actor_entry['input_topics'] = {'in': actor.input_topic}
                    if actor.output_topic:
                        actor_entry['output_topics'] = {'out': actor.output_topic}
                
                # Add parameters (excluding 'class' which is now 'engine')
                parameters = {}
                for key, value in actor.parameters.items():
                    if key != 'class':
                        parameters[key] = value
                
                if parameters:
                    actor_entry['parameters'] = parameters
                
                # Add position data if available
                if actor.x is not None and actor.y is not None:
                    actor_entry['position'] = {
                        'x': actor.x,
                        'y': actor.y
                    }
                
                node['actors'].append(actor_entry)
            
            nodes.append(node)
            node_id += 1
        
        result['nodes'] = nodes
        
        # Add legacy services section for backward compatibility
        legacy_services = []
        for actor in graph.actors.values():
            engine_class = actor.parameters.get('class', 'org.jlab.ersap.engine.Unknown')
            service_entry = {
                'name': actor.name,
                'class': engine_class
            }
            
            # Add legacy input/output format
            if actor.input_topic:
                service_entry['input'] = actor.input_topic
            if actor.output_topic:
                service_entry['output'] = actor.output_topic
            if actor.host:
                service_entry['host'] = actor.host
            
            legacy_services.append(service_entry)
        
        result['services'] = legacy_services
        
        return result
    
    def write_project_file(self, output_path: str, graph: ActorGraph, 
                          metadata: Optional[Dict] = None) -> List[str]:
        """
        Write the project state to a .ersapproj file.
        
        Args:
            graph: The ActorGraph to save
            output_path: Path to the output project file
            metadata: Optional metadata about the project
            
        Returns:
            List of errors (empty if successful)
        """
        try:
            project_data = {
                "version": "1.0",
                "metadata": metadata or {},
                "actors": [actor.to_dict() for actor in graph.actors.values()],
                "connections": [
                    {
                        "source_actor": conn.source_actor,
                        "target_actor": conn.target_actor,
                        "source_port": conn.source_port,
                        "target_port": conn.target_port
                    }
                    for conn in graph.connections
                ]
            }
            
            output_file = Path(output_path)
            output_file.parent.mkdir(parents=True, exist_ok=True)
            
            with open(output_file, 'w', encoding='utf-8') as f:
                yaml.dump(project_data, f, **self.yaml_config)
            
            return []
            
        except Exception as e:
            return [f"Error writing project file: {str(e)}"]
    
    def read_project_file(self, project_path: str) -> tuple[ActorGraph, Dict, List[str]]:
        """
        Read a project file and reconstruct the actor graph.
        
        Args:
            project_path: Path to the .ersapproj file
            
        Returns:
            Tuple of (ActorGraph, metadata, errors)
        """
        errors = []
        graph = ActorGraph()
        metadata = {}
        
        try:
            with open(project_path, 'r', encoding='utf-8') as f:
                project_data = yaml.safe_load(f)
            
            if not project_data:
                errors.append("Empty or invalid project file")
                return graph, metadata, errors
            
            # Extract metadata
            metadata = project_data.get("metadata", {})
            
            # Reconstruct actors
            actors_data = project_data.get("actors", [])
            for actor_data in actors_data:
                try:
                    actor = self._dict_to_actor(actor_data)
                    actor_errors = graph.add_actor(actor)
                    errors.extend(actor_errors)
                except Exception as e:
                    errors.append(f"Error loading actor {actor_data.get('name', 'unknown')}: {str(e)}")
            
            # Reconstruct connections
            connections_data = project_data.get("connections", [])
            for conn_data in connections_data:
                try:
                    connection = Connection(
                        source_actor=conn_data["source_actor"],
                        target_actor=conn_data["target_actor"],
                        source_port=conn_data.get("source_port", "output"),
                        target_port=conn_data.get("target_port", "input")
                    )
                    conn_errors = graph.add_connection(connection)
                    errors.extend(conn_errors)
                except Exception as e:
                    errors.append(f"Error loading connection: {str(e)}")
            
        except Exception as e:
            errors.append(f"Error reading project file: {str(e)}")
        
        return graph, metadata, errors
    
    def _dict_to_actor(self, actor_data: Dict) -> Actor:
        """Convert dictionary back to Actor object."""
        from .model import ActorType
        
        # Extract position data if available
        position = actor_data.get("position", {})
        x = position.get("x") if position else None
        y = position.get("y") if position else None
        
        return Actor(
            name=actor_data["name"],
            type=ActorType(actor_data["type"]),
            input_topic=actor_data.get("input"),
            output_topic=actor_data.get("output"),
            host=actor_data.get("host"),
            parameters=actor_data.get("parameters", {}),
            targets=actor_data.get("targets", []),
            x=x,
            y=y
        )
    
    def generate_example_yaml(self) -> str:
        """Generate an example services.yaml content."""
        example_data = {
            "services": [
                {
                    "name": "data_source",
                    "type": "source",
                    "output": "clas12:raw:data",
                    "host": "localhost"
                },
                {
                    "name": "data_processor",
                    "type": "processor",
                    "input": "clas12:raw:data",
                    "output": "clas12:processed:data",
                    "parameters": {
                        "threads": "4",
                        "buffer_size": "1024"
                    }
                },
                {
                    "name": "data_sink",
                    "type": "sink",
                    "input": "clas12:processed:data",
                    "host": "remote-node"
                }
            ]
        }
        
        return yaml.dump(example_data, **self.yaml_config)
    
    def _fix_duplicate_output_topics(self, graph: ActorGraph) -> None:
        """Fix duplicate output topics by ensuring each actor has a unique output topic."""
        # Track used output topics
        used_topics = set()
        
        # First pass: collect all current output topics
        for actor in graph.actors.values():
            if actor.output_topic:
                used_topics.add(actor.output_topic)
        
        # Second pass: fix duplicates
        topic_counts = {}
        for actor in graph.actors.values():
            if actor.output_topic:
                # Count how many times this topic is used
                topic_counts[actor.output_topic] = topic_counts.get(actor.output_topic, 0) + 1
        
        # Third pass: assign unique topics to duplicates
        for actor in graph.actors.values():
            if actor.output_topic and topic_counts.get(actor.output_topic, 0) > 1:
                # This topic is duplicated, assign a unique one
                old_topic = actor.output_topic
                new_topic = f"data:events:{actor.name}_output"
                
                # Ensure the new topic is truly unique
                counter = 1
                while new_topic in used_topics:
                    new_topic = f"data:events:{actor.name}_output_{counter}"
                    counter += 1
                
                actor.output_topic = new_topic
                used_topics.add(new_topic)
                
                # Update the count to prevent fixing the same topic multiple times
                topic_counts[old_topic] -= 1
    
    def validate_yaml_file(self, yaml_path: str) -> List[str]:
        """
        Validate a services.yaml file.
        
        Args:
            yaml_path: Path to the YAML file to validate
            
        Returns:
            List of validation errors
        """
        errors = []
        
        try:
            with open(yaml_path, 'r', encoding='utf-8') as f:
                data = yaml.safe_load(f)
            
            if not data:
                errors.append("Empty or invalid YAML file")
                return errors
            
            services = data.get("services", [])
            if not services:
                errors.append("No services found in YAML file")
                return errors
            
            # Validate each service
            for i, service in enumerate(services):
                service_errors = self._validate_service(service, i)
                errors.extend(service_errors)
            
            # Check for duplicate names
            names = [service.get("name", "") for service in services]
            if len(names) != len(set(names)):
                errors.append("Duplicate service names found")
            
        except Exception as e:
            errors.append(f"Error reading YAML file: {str(e)}")
        
        return errors
    
    def _validate_service(self, service: Dict, index: int) -> List[str]:
        """Validate a single service entry."""
        errors = []
        
        # Check required fields
        if "name" not in service:
            errors.append(f"Service {index}: Missing 'name' field")
        if "type" not in service:
            errors.append(f"Service {index}: Missing 'type' field")
        
        # Validate type
        if "type" in service:
            valid_types = ["source", "processor", "sink"]
            if service["type"] not in valid_types:
                errors.append(f"Service {index}: Invalid type '{service['type']}'. Must be one of {valid_types}")
        
        # Validate topic format
        for topic_field in ["input", "output"]:
            if topic_field in service:
                topic = service[topic_field]
                if not self._is_valid_topic_format(topic):
                    errors.append(f"Service {index}: Invalid {topic_field} topic format '{topic}'. Expected format: domain:subject:type")
        
        return errors
    
    def _is_valid_topic_format(self, topic: str) -> bool:
        """Validate xMsg topic format."""
        if not isinstance(topic, str) or not topic.strip():
            return False
        parts = topic.split(':')
        return len(parts) == 3 and all(part.strip() for part in parts) 