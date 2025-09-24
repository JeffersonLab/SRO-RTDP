"""
ERSAP Actor Model and Validation Logic

This module defines the core data structures and validation logic
for ERSAP actors in the GUI editor.
"""

from dataclasses import dataclass, field
from typing import Dict, List, Optional, Set
from enum import Enum
import re


class ActorType(Enum):
    """Enumeration of ERSAP actor types."""
    SOURCE = "source"
    PROCESSOR = "processor"
    SINK = "sink"
    ROUTING = "routing"  # New type for EnhancedRoutingService
    COLLECTOR = "collector"  # New type for Collector


@dataclass
class Actor:
    """Represents an ERSAP actor with all its properties.
    - COLLECTOR: Accepts multiple incoming connections from processors, one output to a sink.
    """
    
    name: str
    type: ActorType
    input_topic: Optional[str] = None
    output_topic: Optional[str] = None
    host: Optional[str] = None
    parameters: Dict[str, str] = field(default_factory=dict)
    targets: List[str] = field(default_factory=list)  # Only used for routing actors
    x: Optional[float] = None  # Canvas X position
    y: Optional[float] = None  # Canvas Y position
    
    def __post_init__(self):
        """Validate actor data after initialization."""
        self.validate()
    
    def validate(self) -> List[str]:
        """Validate actor properties and return list of errors."""
        errors = []
        
        # Validate name
        if not self.name or not self.name.strip():
            errors.append("Actor name is required")
        elif not re.match(r'^[a-zA-Z_][a-zA-Z0-9_]*$', self.name):
            errors.append("Actor name must start with letter or underscore and contain only alphanumeric characters and underscores")
        
        # Validate type
        if not isinstance(self.type, ActorType):
            errors.append("Invalid actor type")
        
        # Validate topics based on type
        if self.type == ActorType.SOURCE:
            if not self.output_topic:
                errors.append("Source actors must have an output topic")
            if self.input_topic:
                errors.append("Source actors should not have input topics")
        elif self.type == ActorType.PROCESSOR:
            if not self.input_topic:
                errors.append("Processor actors must have an input topic")
            if not self.output_topic:
                errors.append("Processor actors must have an output topic")
        elif self.type == ActorType.SINK:
            if not self.input_topic:
                errors.append("Sink actors must have an input topic")
            if self.output_topic:
                errors.append("Sink actors should not have output topics")
        elif self.type == ActorType.ROUTING:
            if not self.targets or not isinstance(self.targets, list):
                errors.append("Routing actor must have a list of targets.")
        elif self.type == ActorType.COLLECTOR:
            # Collector must have an input topic and output topic
            if not self.input_topic:
                errors.append("Collector actors must have an input topic")
            if not self.output_topic:
                errors.append("Collector actors must have an output topic")
        
        # Validate topic format
        if self.input_topic and not self._is_valid_topic(self.input_topic):
            errors.append(f"Invalid input topic format: {self.input_topic}")
        if self.output_topic and not self._is_valid_topic(self.output_topic):
            errors.append(f"Invalid output topic format: {self.output_topic}")
        
        # Validate host (if provided)
        if self.host and not self._is_valid_host(self.host):
            errors.append(f"Invalid host format: {self.host}")
        
        return errors
    
    def _is_valid_topic(self, topic: str) -> bool:
        """Validate xMsg topic format: domain:subject:type"""
        if not topic or not topic.strip():
            return False
        parts = topic.split(':')
        return len(parts) == 3 and all(part.strip() for part in parts)
    
    def _is_valid_host(self, host: str) -> bool:
        """Validate host format (basic validation)."""
        if not host or not host.strip():
            return False
        # Basic hostname validation
        return re.match(r'^[a-zA-Z0-9.-]+$', host) is not None
    
    def to_dict(self) -> Dict:
        """Convert actor to dictionary for YAML export."""
        result = {
            "name": self.name,
            "type": self.type.value
        }
        
        if self.input_topic:
            result["input"] = self.input_topic
        if self.output_topic:
            result["output"] = self.output_topic
        if self.host:
            result["host"] = self.host
        if self.parameters:
            result["parameters"] = self.parameters
        if self.type == ActorType.ROUTING and self.targets:
            result["targets"] = self.targets
        
        # Include position data if available
        if self.x is not None and self.y is not None:
            result["position"] = {"x": self.x, "y": self.y}
        
        return result
    
    def __eq__(self, other):
        """Equality comparison based on name."""
        if not isinstance(other, Actor):
            return False
        return self.name == other.name
    
    def __hash__(self):
        """Hash based on name."""
        return hash(self.name)


@dataclass
class Connection:
    """Represents a connection between two actors."""
    
    source_actor: str
    target_actor: str
    source_port: str = "output"
    target_port: str = "input"
    
    def __post_init__(self):
        """Validate connection data."""
        if not self.source_actor or not self.target_actor:
            raise ValueError("Source and target actors are required")
        if self.source_actor == self.target_actor:
            raise ValueError("Cannot connect actor to itself")
    
    def __eq__(self, other):
        """Equality comparison."""
        if not isinstance(other, Connection):
            return False
        return (self.source_actor == other.source_actor and 
                self.target_actor == other.target_actor and
                self.source_port == other.source_port and
                self.target_port == other.target_port)
    
    def __hash__(self):
        """Hash based on connection properties."""
        return hash((self.source_actor, self.target_actor, self.source_port, self.target_port))


class ActorGraph:
    """Represents a graph of connected ERSAP actors."""
    
    def __init__(self):
        self.actors: Dict[str, Actor] = {}
        self.connections: Set[Connection] = set()
    
    def add_actor(self, actor: Actor) -> List[str]:
        """Add an actor to the graph and return validation errors."""
        errors = actor.validate()
        if not errors:
            self.actors[actor.name] = actor
        return errors
    
    def remove_actor(self, actor_name: str) -> bool:
        """Remove an actor and all its connections."""
        if actor_name not in self.actors:
            return False
        
        # Remove all connections involving this actor
        self.connections = {
            conn for conn in self.connections
            if conn.source_actor != actor_name and conn.target_actor != actor_name
        }
        
        del self.actors[actor_name]
        return True
    
    def add_connection(self, connection: Connection) -> List[str]:
        """Add a connection and return validation errors."""
        errors = []
        
        # Check if actors exist
        if connection.source_actor not in self.actors:
            errors.append(f"Source actor '{connection.source_actor}' not found")
        if connection.target_actor not in self.actors:
            errors.append(f"Target actor '{connection.target_actor}' not found")
        
        # Check for duplicate connections
        if connection in self.connections:
            errors.append("Connection already exists")
        
        # Check for cycles (basic check)
        if self._would_create_cycle(connection):
            errors.append("Connection would create a cycle")
        
        if not errors:
            self.connections.add(connection)
        
        return errors
    
    def remove_connection(self, connection: Connection) -> bool:
        """Remove a connection from the graph."""
        try:
            self.connections.remove(connection)
            return True
        except KeyError:
            return False
    
    def _would_create_cycle(self, new_connection: Connection) -> bool:
        """Check if adding a connection would create a cycle."""
        # Simple cycle detection using DFS
        visited = set()
        rec_stack = set()
        
        def has_cycle_dfs(node: str) -> bool:
            if node in rec_stack:
                return True
            if node in visited:
                return False
            
            visited.add(node)
            rec_stack.add(node)
            
            for conn in self.connections:
                if conn.source_actor == node:
                    if has_cycle_dfs(conn.target_actor):
                        return True
            
            rec_stack.remove(node)
            return False
        
        # Add the new connection temporarily
        self.connections.add(new_connection)
        
        # Check for cycles
        has_cycle = False
        for actor_name in self.actors:
            if has_cycle_dfs(actor_name):
                has_cycle = True
                break
        
        # Remove the temporary connection
        self.connections.remove(new_connection)
        
        return has_cycle
    
    def get_validation_errors(self) -> List[str]:
        """Get all validation errors for the graph."""
        errors = []
        
        # Validate all actors
        for actor in self.actors.values():
            errors.extend(actor.validate())
        
        # Check for disconnected actors
        connected_actors = set()
        for conn in self.connections:
            connected_actors.add(conn.source_actor)
            connected_actors.add(conn.target_actor)
        
        for actor_name in self.actors:
            if actor_name not in connected_actors:
                errors.append(f"Actor '{actor_name}' is not connected to any other actor")
        
        # Check for duplicate names
        names = [actor.name for actor in self.actors.values()]
        if len(names) != len(set(names)):
            errors.append("Duplicate actor names found")
        
        return errors
    
    def to_services_list(self) -> List[Dict]:
        """Convert graph to list of services for YAML export."""
        return [actor.to_dict() for actor in self.actors.values()]
    
    def get_actor_by_name(self, name: str) -> Optional[Actor]:
        """Get actor by name."""
        return self.actors.get(name)
    
    def get_connections_for_actor(self, actor_name: str) -> List[Connection]:
        """Get all connections involving a specific actor."""
        return [
            conn for conn in self.connections
            if conn.source_actor == actor_name or conn.target_actor == actor_name
        ] 