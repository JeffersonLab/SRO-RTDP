"""
Unit tests for the YAML writer.
"""

import unittest
import tempfile
import os
from pathlib import Path
from core.yaml_writer import YAMLWriter
from core.model import Actor, ActorType, Connection, ActorGraph


class TestYAMLWriter(unittest.TestCase):
    """Test cases for the YAMLWriter class."""
    
    def setUp(self):
        """Set up test fixtures."""
        self.writer = YAMLWriter()
        self.temp_dir = tempfile.mkdtemp()
        
        # Create test graph
        self.graph = ActorGraph()
        
        # Add test actors
        source_actor = Actor(
            name="test_source",
            type=ActorType.SOURCE,
            output_topic="domain:subject:type"
        )
        
        processor_actor = Actor(
            name="test_processor",
            type=ActorType.PROCESSOR,
            input_topic="domain:subject:type",
            output_topic="domain:processed:type"
        )
        
        sink_actor = Actor(
            name="test_sink",
            type=ActorType.SINK,
            input_topic="domain:processed:type"
        )
        
        self.graph.add_actor(source_actor)
        self.graph.add_actor(processor_actor)
        self.graph.add_actor(sink_actor)
        
        # Add connections
        self.graph.add_connection(Connection("test_source", "test_processor"))
        self.graph.add_connection(Connection("test_processor", "test_sink"))
    
    def tearDown(self):
        """Clean up test fixtures."""
        # Remove temporary files
        for file_path in Path(self.temp_dir).glob("*"):
            if file_path.is_file():
                file_path.unlink()
        os.rmdir(self.temp_dir)
    
    def test_write_services_yaml(self):
        """Test writing services.yaml file."""
        output_path = os.path.join(self.temp_dir, "services.yaml")
        
        errors = self.writer.write_services_yaml(self.graph, output_path)
        self.assertEqual(errors, [])
        
        # Check that file was created
        self.assertTrue(os.path.exists(output_path))
        
        # Read and verify content
        with open(output_path, 'r') as f:
            content = f.read()
        
        # Check that content contains expected services
        self.assertIn("test_source", content)
        self.assertIn("test_processor", content)
        self.assertIn("test_sink", content)
        self.assertIn("source", content)
        self.assertIn("processor", content)
        self.assertIn("sink", content)
    
    def test_write_services_yaml_with_validation_errors(self):
        """Test writing services.yaml with validation errors."""
        # Create invalid graph
        invalid_graph = ActorGraph()
        invalid_actor = Actor(
            name="123invalid",
            type=ActorType.SOURCE,
            output_topic="domain:subject:type"
        )
        # Try to add invalid actor - should return errors and not add to graph
        errors = invalid_graph.add_actor(invalid_actor)
        self.assertGreater(len(errors), 0)
        self.assertIn("Actor name must start with letter or underscore", errors[0])
        self.assertEqual(len(invalid_graph.actors), 0)  # Actor should not be added

        output_path = os.path.join(self.temp_dir, "invalid_services.yaml")
        yaml_errors = self.writer.write_services_yaml(invalid_graph, output_path)
        
        # Should return error about no services
        self.assertEqual(yaml_errors, ["No services found in YAML file"])
        
        # File should not be created
        self.assertFalse(os.path.exists(output_path))
    
    def test_write_project_file(self):
        """Test writing project file."""
        output_path = os.path.join(self.temp_dir, "test.ersapproj")
        metadata = {"name": "test_project", "version": "1.0"}
        
        errors = self.writer.write_project_file(self.graph, output_path, metadata)
        self.assertEqual(errors, [])
        
        # Check that file was created
        self.assertTrue(os.path.exists(output_path))
        
        # Read and verify content
        with open(output_path, 'r') as f:
            content = f.read()
        
        # Check that content contains expected data
        self.assertIn("test_source", content)
        self.assertIn("test_processor", content)
        self.assertIn("test_sink", content)
        self.assertIn("test_project", content)
    
    def test_read_project_file(self):
        """Test reading project file."""
        # Write a project file first
        output_path = os.path.join(self.temp_dir, "test.ersapproj")
        metadata = {"name": "test_project"}
        self.writer.write_project_file(self.graph, output_path, metadata)
        
        # Read it back
        graph, read_metadata, errors = self.writer.read_project_file(output_path)
        
        self.assertEqual(errors, [])
        self.assertEqual(read_metadata["name"], "test_project")
        
        # Check that actors were restored
        self.assertEqual(len(graph.actors), 3)
        self.assertIn("test_source", graph.actors)
        self.assertIn("test_processor", graph.actors)
        self.assertIn("test_sink", graph.actors)
        
        # Check that connections were restored
        self.assertEqual(len(graph.connections), 2)
    
    def test_read_nonexistent_project_file(self):
        """Test reading non-existent project file."""
        nonexistent_path = os.path.join(self.temp_dir, "nonexistent.ersapproj")
        graph, metadata, errors = self.writer.read_project_file(nonexistent_path)
        
        self.assertGreater(len(errors), 0)
        self.assertIn("Error reading project file", errors[0])
    
    def test_read_empty_project_file(self):
        """Test reading empty project file."""
        empty_path = os.path.join(self.temp_dir, "empty.ersapproj")
        with open(empty_path, 'w') as f:
            f.write("")
        
        graph, metadata, errors = self.writer.read_project_file(empty_path)
        
        self.assertGreater(len(errors), 0)
        self.assertIn("Empty or invalid project file", errors[0])
    
    def test_generate_example_yaml(self):
        """Test generating example YAML."""
        example_yaml = self.writer.generate_example_yaml()
        
        # Check that it contains expected content
        self.assertIn("services:", example_yaml)
        self.assertIn("data_source", example_yaml)
        self.assertIn("data_processor", example_yaml)
        self.assertIn("data_sink", example_yaml)
        self.assertIn("source", example_yaml)
        self.assertIn("processor", example_yaml)
        self.assertIn("sink", example_yaml)
    
    def test_validate_yaml_file(self):
        """Test validating a YAML file."""
        # Create a valid YAML file
        valid_yaml_path = os.path.join(self.temp_dir, "valid.yaml")
        self.writer.write_services_yaml(self.graph, valid_yaml_path)
        
        errors = self.writer.validate_yaml_file(valid_yaml_path)
        self.assertEqual(errors, [])
    
    def test_validate_invalid_yaml_file(self):
        """Test validating an invalid YAML file."""
        # Create an invalid YAML file
        invalid_yaml_path = os.path.join(self.temp_dir, "invalid.yaml")
        with open(invalid_yaml_path, 'w') as f:
            f.write("""
services:
  - name: invalid_actor
    type: invalid_type
    input: invalid_topic
""")
        
        errors = self.writer.validate_yaml_file(invalid_yaml_path)
        self.assertGreater(len(errors), 0)
        self.assertIn("Invalid type 'invalid_type'", errors[0])
        self.assertIn("Invalid input topic format", errors[1])
    
    def test_validate_empty_yaml_file(self):
        """Test validating an empty YAML file."""
        empty_yaml_path = os.path.join(self.temp_dir, "empty.yaml")
        with open(empty_yaml_path, 'w') as f:
            f.write("")
        
        errors = self.writer.validate_yaml_file(empty_yaml_path)
        self.assertGreater(len(errors), 0)
        self.assertIn("Empty or invalid YAML file", errors[0])
    
    def test_validate_yaml_with_duplicate_names(self):
        """Test validating YAML with duplicate service names."""
        duplicate_yaml_path = os.path.join(self.temp_dir, "duplicate.yaml")
        with open(duplicate_yaml_path, 'w') as f:
            f.write("""
services:
  - name: actor1
    type: source
    output: domain:subject:type
  - name: actor1
    type: processor
    input: domain:subject:type
    output: domain:processed:type
""")
        
        errors = self.writer.validate_yaml_file(duplicate_yaml_path)
        self.assertGreater(len(errors), 0)
        self.assertIn("Duplicate service names found", errors[0])
    
    def test_validate_yaml_with_missing_fields(self):
        """Test validating YAML with missing required fields."""
        missing_fields_yaml_path = os.path.join(self.temp_dir, "missing_fields.yaml")
        with open(missing_fields_yaml_path, 'w') as f:
            f.write("""
services:
  - type: source
    output: domain:subject:type
  - name: processor1
    input: domain:subject:type
    output: domain:processed:type
""")
        
        errors = self.writer.validate_yaml_file(missing_fields_yaml_path)
        self.assertGreater(len(errors), 0)
        self.assertIn("Missing 'name' field", errors[0])
        self.assertIn("Missing 'type' field", errors[1])
    
    def test_is_valid_topic_format(self):
        """Test topic format validation."""
        # Valid topics
        self.assertTrue(self.writer._is_valid_topic_format("domain:subject:type"))
        self.assertTrue(self.writer._is_valid_topic_format("clas12:raw:data"))
        self.assertTrue(self.writer._is_valid_topic_format("test:input:events"))
        
        # Invalid topics
        self.assertFalse(self.writer._is_valid_topic_format("invalid_topic"))
        self.assertFalse(self.writer._is_valid_topic_format("domain:subject"))
        self.assertFalse(self.writer._is_valid_topic_format("domain:subject:type:extra"))
        self.assertFalse(self.writer._is_valid_topic_format(""))
        self.assertFalse(self.writer._is_valid_topic_format("domain::type"))
        self.assertFalse(self.writer._is_valid_topic_format(":subject:type"))


if __name__ == "__main__":
    unittest.main() 