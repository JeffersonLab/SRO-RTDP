"""
Unit tests for the core model classes.
"""

import unittest
from core.model import Actor, ActorType, Connection, ActorGraph


class TestActor(unittest.TestCase):
    """Test cases for the Actor class."""
    
    def test_valid_source_actor(self):
        """Test creating a valid source actor."""
        actor = Actor(
            name="test_source",
            type=ActorType.SOURCE,
            output_topic="domain:subject:type"
        )
        errors = actor.validate()
        self.assertEqual(errors, [])
    
    def test_valid_processor_actor(self):
        """Test creating a valid processor actor."""
        actor = Actor(
            name="test_processor",
            type=ActorType.PROCESSOR,
            input_topic="domain:input:type",
            output_topic="domain:output:type"
        )
        errors = actor.validate()
        self.assertEqual(errors, [])
    
    def test_valid_sink_actor(self):
        """Test creating a valid sink actor."""
        actor = Actor(
            name="test_sink",
            type=ActorType.SINK,
            input_topic="domain:subject:type"
        )
        errors = actor.validate()
        self.assertEqual(errors, [])
    
    def test_invalid_name(self):
        """Test actor with invalid name."""
        actor = Actor(
            name="123invalid",
            type=ActorType.SOURCE,
            output_topic="domain:subject:type"
        )
        errors = actor.validate()
        self.assertIn("Actor name must start with letter or underscore", errors[0])
    
    def test_source_with_input_topic(self):
        """Test source actor with input topic (should be invalid)."""
        actor = Actor(
            name="test_source",
            type=ActorType.SOURCE,
            input_topic="domain:subject:type",
            output_topic="domain:subject:type"
        )
        errors = actor.validate()
        self.assertIn("Source actors should not have input topics", errors)
    
    def test_source_without_output_topic(self):
        """Test source actor without output topic (should be invalid)."""
        actor = Actor(
            name="test_source",
            type=ActorType.SOURCE
        )
        errors = actor.validate()
        self.assertIn("Source actors must have an output topic", errors)
    
    def test_processor_without_input_topic(self):
        """Test processor actor without input topic (should be invalid)."""
        actor = Actor(
            name="test_processor",
            type=ActorType.PROCESSOR,
            output_topic="domain:subject:type"
        )
        errors = actor.validate()
        self.assertIn("Processor actors must have an input topic", errors)
    
    def test_sink_without_input_topic(self):
        """Test sink actor without input topic (should be invalid)."""
        actor = Actor(
            name="test_sink",
            type=ActorType.SINK
        )
        errors = actor.validate()
        self.assertIn("Sink actors must have an input topic", errors)
    
    def test_sink_with_output_topic(self):
        """Test sink actor with output topic (should be invalid)."""
        actor = Actor(
            name="test_sink",
            type=ActorType.SINK,
            input_topic="domain:subject:type",
            output_topic="domain:subject:output"
        )
        errors = actor.validate()
        self.assertIn("Sink actors should not have output topics", errors)
    
    def test_invalid_topic_format(self):
        """Test actor with invalid topic format."""
        actor = Actor(
            name="test_actor",
            type=ActorType.PROCESSOR,
            input_topic="invalid_topic",
            output_topic="domain:subject:type"
        )
        errors = actor.validate()
        self.assertIn("Invalid input topic format", errors[0])
    
    def test_to_dict(self):
        """Test converting actor to dictionary."""
        actor = Actor(
            name="test_actor",
            type=ActorType.PROCESSOR,
            input_topic="domain:input:type",
            output_topic="domain:output:type",
            host="localhost",
            parameters={"threads": "4"}
        )
        
        result = actor.to_dict()
        expected = {
            "name": "test_actor",
            "type": "processor",
            "input": "domain:input:type",
            "output": "domain:output:type",
            "host": "localhost",
            "parameters": {"threads": "4"}
        }
        self.assertEqual(result, expected)


class TestConnection(unittest.TestCase):
    """Test cases for the Connection class."""
    
    def test_valid_connection(self):
        """Test creating a valid connection."""
        connection = Connection("source", "target")
        self.assertEqual(connection.source_actor, "source")
        self.assertEqual(connection.target_actor, "target")
    
    def test_connection_to_self(self):
        """Test connection to self (should raise ValueError)."""
        with self.assertRaises(ValueError):
            Connection("actor", "actor")
    
    def test_empty_actors(self):
        """Test connection with empty actor names (should raise ValueError)."""
        with self.assertRaises(ValueError):
            Connection("", "target")
        
        with self.assertRaises(ValueError):
            Connection("source", "")


class TestActorGraph(unittest.TestCase):
    """Test cases for the ActorGraph class."""
    
    def setUp(self):
        """Set up test fixtures."""
        self.graph = ActorGraph()
        
        # Create test actors
        self.source_actor = Actor(
            name="source",
            type=ActorType.SOURCE,
            output_topic="domain:subject:type"
        )
        
        self.processor_actor = Actor(
            name="processor",
            type=ActorType.PROCESSOR,
            input_topic="domain:subject:type",
            output_topic="domain:processed:type"
        )
        
        self.sink_actor = Actor(
            name="sink",
            type=ActorType.SINK,
            input_topic="domain:processed:type"
        )
    
    def test_add_valid_actor(self):
        """Test adding a valid actor."""
        errors = self.graph.add_actor(self.source_actor)
        self.assertEqual(errors, [])
        self.assertIn("source", self.graph.actors)
    
    def test_add_invalid_actor(self):
        """Test adding an invalid actor."""
        invalid_actor = Actor(
            name="123invalid",
            type=ActorType.SOURCE,
            output_topic="domain:subject:type"
        )
        errors = self.graph.add_actor(invalid_actor)
        self.assertGreater(len(errors), 0)
        self.assertNotIn("123invalid", self.graph.actors)
    
    def test_add_duplicate_actor(self):
        """Test adding duplicate actors."""
        self.graph.add_actor(self.source_actor)
        
        duplicate_actor = Actor(
            name="source",
            type=ActorType.SOURCE,
            output_topic="domain:other:type"
        )
        self.graph.add_actor(duplicate_actor)
        
        # Should replace the original
        self.assertEqual(len(self.graph.actors), 1)
        self.assertEqual(self.graph.actors["source"].output_topic, "domain:other:type")
    
    def test_remove_actor(self):
        """Test removing an actor."""
        self.graph.add_actor(self.source_actor)
        self.assertTrue(self.graph.remove_actor("source"))
        self.assertNotIn("source", self.graph.actors)
    
    def test_remove_nonexistent_actor(self):
        """Test removing a non-existent actor."""
        self.assertFalse(self.graph.remove_actor("nonexistent"))
    
    def test_add_valid_connection(self):
        """Test adding a valid connection."""
        self.graph.add_actor(self.source_actor)
        self.graph.add_actor(self.processor_actor)
        
        connection = Connection("source", "processor")
        errors = self.graph.add_connection(connection)
        self.assertEqual(errors, [])
        self.assertIn(connection, self.graph.connections)
    
    def test_add_connection_with_nonexistent_actors(self):
        """Test adding connection with non-existent actors."""
        connection = Connection("nonexistent1", "nonexistent2")
        errors = self.graph.add_connection(connection)
        self.assertIn("Source actor 'nonexistent1' not found", errors)
        self.assertIn("Target actor 'nonexistent2' not found", errors)
    
    def test_add_duplicate_connection(self):
        """Test adding duplicate connections."""
        self.graph.add_actor(self.source_actor)
        self.graph.add_actor(self.processor_actor)
        
        connection = Connection("source", "processor")
        self.graph.add_connection(connection)
        
        # Try to add the same connection again
        errors = self.graph.add_connection(connection)
        self.assertIn("Connection already exists", errors)
    
    def test_remove_connection(self):
        """Test removing a connection."""
        self.graph.add_actor(self.source_actor)
        self.graph.add_actor(self.processor_actor)
        
        connection = Connection("source", "processor")
        self.graph.add_connection(connection)
        
        self.assertTrue(self.graph.remove_connection(connection))
        self.assertNotIn(connection, self.graph.connections)
    
    def test_remove_nonexistent_connection(self):
        """Test removing a non-existent connection."""
        connection = Connection("source", "target")
        self.assertFalse(self.graph.remove_connection(connection))
    
    def test_cycle_detection(self):
        """Test cycle detection in the graph."""
        self.graph.add_actor(self.source_actor)
        self.graph.add_actor(self.processor_actor)
        self.graph.add_actor(self.sink_actor)
        
        # Create a cycle: source -> processor -> sink -> source
        self.graph.add_connection(Connection("source", "processor"))
        self.graph.add_connection(Connection("processor", "sink"))
        
        # Try to add connection that would create a cycle
        cycle_connection = Connection("sink", "source")
        errors = self.graph.add_connection(cycle_connection)
        self.assertIn("Connection would create a cycle", errors)
    
    def test_get_validation_errors(self):
        """Test getting validation errors for the entire graph."""
        # Add actors without connections
        self.graph.add_actor(self.source_actor)
        self.graph.add_actor(self.processor_actor)
        
        errors = self.graph.get_validation_errors()
        self.assertIn("Actor 'source' is not connected to any other actor", errors)
        self.assertIn("Actor 'processor' is not connected to any other actor", errors)
    
    def test_to_services_list(self):
        """Test converting graph to services list."""
        self.graph.add_actor(self.source_actor)
        self.graph.add_actor(self.processor_actor)
        
        services = self.graph.to_services_list()
        self.assertEqual(len(services), 2)
        
        # Check that services contain the expected data
        service_names = [service["name"] for service in services]
        self.assertIn("source", service_names)
        self.assertIn("processor", service_names)
    
    def test_get_actor_by_name(self):
        """Test getting actor by name."""
        self.graph.add_actor(self.source_actor)
        
        actor = self.graph.get_actor_by_name("source")
        self.assertEqual(actor, self.source_actor)
        
        actor = self.graph.get_actor_by_name("nonexistent")
        self.assertIsNone(actor)
    
    def test_get_connections_for_actor(self):
        """Test getting connections for a specific actor."""
        self.graph.add_actor(self.source_actor)
        self.graph.add_actor(self.processor_actor)
        self.graph.add_actor(self.sink_actor)
        
        self.graph.add_connection(Connection("source", "processor"))
        self.graph.add_connection(Connection("processor", "sink"))
        
        source_connections = self.graph.get_connections_for_actor("source")
        self.assertEqual(len(source_connections), 1)
        self.assertEqual(source_connections[0].source_actor, "source")
        self.assertEqual(source_connections[0].target_actor, "processor")
        
        processor_connections = self.graph.get_connections_for_actor("processor")
        self.assertEqual(len(processor_connections), 2)


if __name__ == "__main__":
    unittest.main() 