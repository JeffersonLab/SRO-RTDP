"""
YAML Reader for Enhanced ERSAP Actor Pipeline Configuration

This module handles reading and parsing of enhanced actor pipeline YAML files
and converting them to the GUI editor's actor graph model.
"""

import yaml
from typing import Dict, List, Optional, Tuple
from pathlib import Path
from .model import ActorGraph, Actor, Connection, ActorType


class YAMLReader:
    """Handles reading enhanced ERSAP actor pipeline configurations."""
    
    def __init__(self):
        pass
    
    def read_yaml_file(self, yaml_path: str) -> ActorGraph:
        """
        Read a YAML file and return the actor graph (simplified interface for GUI).
        Raises exceptions on error for GUI to handle.
        """
        graph, metadata, errors = self.read_pipeline_yaml(yaml_path)
        if errors:
            raise Exception("\\n".join(errors))
        return graph
    
    def read_pipeline_yaml(self, yaml_path: str) -> Tuple[ActorGraph, Dict, List[str]]:
        """
        Read an enhanced pipeline YAML file and convert to actor graph.
        
        Args:
            yaml_path: Path to the YAML file to read
            
        Returns:
            Tuple of (ActorGraph, metadata, errors)
        """
        errors = []
        graph = ActorGraph()
        metadata = {}
        
        try:
            with open(yaml_path, 'r', encoding='utf-8') as f:
                yaml_data = yaml.safe_load(f)
            
            if not yaml_data:
                errors.append("Empty or invalid YAML file")
                return graph, metadata, errors
            
            # Determine YAML format (enhanced vs legacy)
            if 'pipeline' in yaml_data or 'nodes' in yaml_data:
                return self._read_enhanced_format(yaml_data)
            elif 'services' in yaml_data:
                return self._read_legacy_format(yaml_data)
            else:
                errors.append("Unknown YAML format - missing 'pipeline', 'nodes', or 'services' section")
                return graph, metadata, errors
                
        except Exception as e:
            errors.append(f"Error reading YAML file: {str(e)}")
            return graph, metadata, errors
    
    def _read_enhanced_format(self, yaml_data: Dict) -> Tuple[ActorGraph, Dict, List[str]]:
        """Read enhanced actor pipeline format."""
        errors = []
        graph = ActorGraph()
        metadata = {}
        
        # Extract pipeline metadata
        if 'pipeline' in yaml_data:
            pipeline_info = yaml_data['pipeline']
            metadata.update({
                'name': pipeline_info.get('name', 'Unnamed Pipeline'),
                'description': pipeline_info.get('description', ''),
                'version': pipeline_info.get('version', '1.0')
            })
        
        # Process nodes and actors
        if 'nodes' in yaml_data:
            nodes = yaml_data['nodes']
            for node in nodes:
                node_errors = self._process_node(node, graph)
                errors.extend(node_errors)
        else:
            errors.append("Enhanced format missing 'nodes' section")
        
        # Infer connections from topic matching
        connection_errors = self._infer_connections(graph)
        errors.extend(connection_errors)
        
        return graph, metadata, errors
    
    def _read_legacy_format(self, yaml_data: Dict) -> Tuple[ActorGraph, Dict, List[str]]:
        """Read legacy services format."""
        errors = []
        graph = ActorGraph()
        metadata = {'name': 'Legacy Pipeline', 'description': 'Converted from legacy format'}
        
        services = yaml_data.get('services', [])
        
        for service in services:
            try:
                actor = self._convert_legacy_service_to_actor(service)
                actor_errors = graph.add_actor(actor)
                errors.extend(actor_errors)
            except Exception as e:
                errors.append(f"Error converting service '{service.get('name', 'unknown')}': {str(e)}")
        
        # Infer connections from topic matching
        connection_errors = self._infer_connections(graph)
        errors.extend(connection_errors)
        
        return graph, metadata, errors
    
    def _process_node(self, node_data: Dict, graph: ActorGraph) -> List[str]:
        """Process a single node and add its actors to the graph."""
        errors = []
        
        node_host = node_data.get('host', 'localhost')
        actors_data = node_data.get('actors', [])
        
        for actor_data in actors_data:
            try:
                actor = self._create_actor_from_enhanced_data(actor_data, node_host)
                actor_errors = graph.add_actor(actor)
                errors.extend(actor_errors)
            except Exception as e:
                errors.append(f"Error creating actor '{actor_data.get('name', 'unknown')}': {str(e)}")
        
        return errors
    
    def _create_actor_from_enhanced_data(self, actor_data: Dict, default_host: str) -> Actor:
        """Create an Actor object from enhanced YAML actor data."""
        name = actor_data.get('name')
        if not name:
            raise ValueError("Actor missing 'name' field")
        
        type_str = actor_data.get('type', 'processor')
        try:
            actor_type = ActorType(type_str.lower())
        except ValueError:
            raise ValueError(f"Invalid actor type: {type_str}")
        
        engine_class = actor_data.get('engine', 'org.jlab.ersap.engine.Unknown')
        host = actor_data.get('host', default_host)
        
        # Extract input/output topics from port mappings
        # For collectors, use the first input topic as primary but store all for connection inference
        input_topic = self._extract_single_topic(actor_data.get('input_topics', {}))
        output_topic = self._extract_single_topic(actor_data.get('output_topics', {}))
        
        # Handle legacy input/output fields
        if not input_topic and 'input' in actor_data:
            if isinstance(actor_data['input'], list):
                input_topic = actor_data['input'][0] if actor_data['input'] else None
            else:
                input_topic = actor_data['input']
        
        if not output_topic and 'output' in actor_data:
            output_topic = actor_data['output']
        
        # Prepare parameters
        parameters = actor_data.get('parameters', {}).copy()
        parameters['class'] = engine_class  # Store engine class in parameters
        
        # Handle routing targets for router actors
        targets = []
        if actor_type == ActorType.ROUTING and 'routing_strategy' in actor_data:
            # Extract targets from output topics or routing rules
            output_topics = actor_data.get('output_topics', {})
            for port, topic in output_topics.items():
                # Extract target name from topic (assume format: data:target:type)
                topic_parts = topic.split(':')
                if len(topic_parts) >= 2:
                    targets.append(topic_parts[1])
        
        # Extract position data if available
        position = actor_data.get("position", {})
        x = position.get("x") if position else None
        y = position.get("y") if position else None
        
        actor = Actor(
            name=name,
            type=actor_type,
            input_topic=input_topic,
            output_topic=output_topic,
            host=host if host != 'localhost' else None,
            parameters=parameters,
            targets=targets,
            x=x,
            y=y
        )
        
        # Store original data for collectors to access multiple input topics
        actor._original_data = actor_data
        
        return actor
    
    def _convert_legacy_service_to_actor(self, service_data: Dict) -> Actor:
        """Convert legacy service format to Actor object."""
        name = service_data.get('name')
        if not name:
            raise ValueError("Service missing 'name' field")
        
        class_name = service_data.get('class', 'org.jlab.ersap.engine.Unknown')
        host = service_data.get('host')
        
        # Determine actor type from input/output configuration
        has_input = 'input' in service_data
        has_output = 'output' in service_data
        has_multiple_inputs = isinstance(service_data.get('input'), list)
        has_targets = 'targets' in service_data
        
        if not has_input and has_output:
            actor_type = ActorType.SOURCE
        elif has_input and not has_output:
            actor_type = ActorType.SINK
        elif has_multiple_inputs:
            actor_type = ActorType.COLLECTOR
        elif has_targets:
            actor_type = ActorType.ROUTING
        else:
            actor_type = ActorType.PROCESSOR
        
        # Extract topics
        input_topic = None
        output_topic = service_data.get('output')
        
        if has_input:
            input_data = service_data['input']
            if isinstance(input_data, list):
                input_topic = input_data[0] if input_data else None
            else:
                input_topic = input_data
        
        # Prepare parameters
        parameters = {'class': class_name}
        
        # Add other service parameters
        for key, value in service_data.items():
            if key not in ['name', 'class', 'input', 'output', 'host', 'targets', 'delivery_mode']:
                parameters[key] = str(value)
        
        # Handle routing targets
        targets = service_data.get('targets', [])
        
        # Extract position data if available
        position = service_data.get("position", {})
        x = position.get("x") if position else None
        y = position.get("y") if position else None
        
        actor = Actor(
            name=name,
            type=actor_type,
            input_topic=input_topic,
            output_topic=output_topic,
            host=host,
            parameters=parameters,
            targets=targets,
            x=x,
            y=y
        )
        
        return actor
    
    def _extract_single_topic(self, topic_map: Dict[str, str]) -> Optional[str]:
        """Extract a single topic from a port->topic mapping."""
        if not topic_map:
            return None
        
        # Return the first topic found
        return next(iter(topic_map.values()), None)
    
    def _infer_connections(self, graph: ActorGraph) -> List[str]:
        """Infer connections between actors based on matching topics and routing targets."""
        errors = []
        
        # Build topic to actor mappings
        output_topics = {}  # topic -> actor_name
        input_topics = {}   # topic -> actor_name
        collector_inputs = {}  # collector_name -> [input_topics]
        
        for actor in graph.actors.values():
            if actor.output_topic:
                if actor.output_topic in output_topics:
                    errors.append(f"Multiple actors output to same topic: {actor.output_topic}")
                else:
                    output_topics[actor.output_topic] = actor.name
            
            if actor.input_topic:
                input_topics[actor.input_topic] = actor.name
            
            # Handle collectors with multiple input topics
            if actor.type == ActorType.COLLECTOR:
                # Re-read the input topics from the original actor data
                actor_data = getattr(actor, '_original_data', {})
                input_topics_map = actor_data.get('input_topics', {})
                if input_topics_map:
                    collector_inputs[actor.name] = list(input_topics_map.values())
        
        # Handle routing actors first - create connections based on targets
        for actor in graph.actors.values():
            if actor.type == ActorType.ROUTING and hasattr(actor, 'targets') and actor.targets:
                for target_name in actor.targets:
                    if target_name in graph.actors:
                        connection = Connection(
                            source_actor=actor.name,
                            target_actor=target_name
                        )
                        conn_errors = graph.add_connection(connection)
                        errors.extend(conn_errors)
        
        # Handle collector connections - connect all actors that output to collector's input topics
        for collector_name, input_topic_list in collector_inputs.items():
            for input_topic in input_topic_list:
                if input_topic in output_topics:
                    source_actor = output_topics[input_topic]
                    if source_actor != collector_name:  # Avoid self-connections
                        existing_connection = any(
                            conn.source_actor == source_actor and conn.target_actor == collector_name 
                            for conn in graph.connections
                        )
                        if not existing_connection:
                            connection = Connection(
                                source_actor=source_actor,
                                target_actor=collector_name
                            )
                            conn_errors = graph.add_connection(connection)
                            errors.extend(conn_errors)
        
        # Create connections for matching topics (for non-routing, non-collector actors)
        for topic, target_actor in input_topics.items():
            if topic in output_topics:
                source_actor = output_topics[topic]
                if source_actor != target_actor:  # Avoid self-connections
                    # Check if this connection already exists
                    existing_connection = any(
                        conn.source_actor == source_actor and conn.target_actor == target_actor 
                        for conn in graph.connections
                    )
                    if not existing_connection:
                        connection = Connection(
                            source_actor=source_actor,
                            target_actor=target_actor
                        )
                        conn_errors = graph.add_connection(connection)
                        errors.extend(conn_errors)
        
        return errors
    
    def validate_enhanced_yaml(self, yaml_path: str) -> List[str]:
        """
        Validate an enhanced actor pipeline YAML file.
        
        Args:
            yaml_path: Path to the YAML file to validate
            
        Returns:
            List of validation errors
        """
        errors = []
        
        try:
            with open(yaml_path, 'r', encoding='utf-8') as f:
                yaml_data = yaml.safe_load(f)
            
            if not yaml_data:
                errors.append("Empty or invalid YAML file")
                return errors
            
            # Validate enhanced format structure
            if 'nodes' in yaml_data:
                errors.extend(self._validate_nodes_section(yaml_data['nodes']))
            elif 'services' in yaml_data:
                errors.extend(self._validate_services_section(yaml_data['services']))
            else:
                errors.append("Missing 'nodes' or 'services' section")
            
        except Exception as e:
            errors.append(f"Error reading YAML file: {str(e)}")
        
        return errors
    
    def _validate_nodes_section(self, nodes: List[Dict]) -> List[str]:
        """Validate the nodes section of enhanced format."""
        errors = []
        
        node_ids = set()
        actor_names = set()
        
        for i, node in enumerate(nodes):
            # Validate node structure
            if 'id' not in node:
                errors.append(f"Node {i}: Missing 'id' field")
                continue
            
            node_id = node['id']
            if node_id in node_ids:
                errors.append(f"Duplicate node ID: {node_id}")
            else:
                node_ids.add(node_id)
            
            # Validate actors
            actors = node.get('actors', [])
            for j, actor in enumerate(actors):
                actor_errors = self._validate_actor(actor, f"Node {node_id}, Actor {j}")
                errors.extend(actor_errors)
                
                # Check for duplicate actor names across all nodes
                actor_name = actor.get('name')
                if actor_name:
                    if actor_name in actor_names:
                        errors.append(f"Duplicate actor name: {actor_name}")
                    else:
                        actor_names.add(actor_name)
        
        return errors
    
    def _validate_services_section(self, services: List[Dict]) -> List[str]:
        """Validate the services section of legacy format."""
        errors = []
        
        service_names = set()
        
        for i, service in enumerate(services):
            if 'name' not in service:
                errors.append(f"Service {i}: Missing 'name' field")
                continue
            
            service_name = service['name']
            if service_name in service_names:
                errors.append(f"Duplicate service name: {service_name}")
            else:
                service_names.add(service_name)
            
            if 'class' not in service:
                errors.append(f"Service {service_name}: Missing 'class' field")
            
            # Validate topic format
            for topic_field in ['input', 'output']:
                if topic_field in service:
                    topic = service[topic_field]
                    if isinstance(topic, str):
                        if not self._is_valid_topic_format(topic):
                            errors.append(f"Service {service_name}: Invalid {topic_field} topic format: {topic}")
                    elif isinstance(topic, list):
                        for t in topic:
                            if not self._is_valid_topic_format(t):
                                errors.append(f"Service {service_name}: Invalid {topic_field} topic format: {t}")
        
        return errors
    
    def _validate_actor(self, actor: Dict, context: str) -> List[str]:
        """Validate a single actor definition."""
        errors = []
        
        # Required fields
        for required_field in ['name', 'type', 'engine']:
            if required_field not in actor:
                errors.append(f"{context}: Missing '{required_field}' field")
        
        # Validate actor type
        if 'type' in actor:
            valid_types = [t.value for t in ActorType]
            if actor['type'] not in valid_types:
                errors.append(f"{context}: Invalid type '{actor['type']}'. Valid types: {valid_types}")
        
        # Validate topic formats
        input_topics = actor.get('input_topics', {})
        output_topics = actor.get('output_topics', {})
        
        for port, topic in input_topics.items():
            if not self._is_valid_topic_format(topic):
                errors.append(f"{context}: Invalid input topic format for port '{port}': {topic}")
        
        for port, topic in output_topics.items():
            if not self._is_valid_topic_format(topic):
                errors.append(f"{context}: Invalid output topic format for port '{port}': {topic}")
        
        return errors
    
    def _is_valid_topic_format(self, topic: str) -> bool:
        """Validate xMsg topic format."""
        if not isinstance(topic, str) or not topic.strip():
            return False
        parts = topic.split(':')
        return len(parts) >= 2 and all(part.strip() for part in parts)