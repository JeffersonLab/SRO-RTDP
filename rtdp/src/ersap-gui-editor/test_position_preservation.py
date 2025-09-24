#!/usr/bin/env python3
"""
Test script for actor position preservation functionality.
"""

import sys
import os
import tempfile

# Add the current directory to the path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from core.model import ActorGraph, Actor, ActorType, Connection
from core.yaml_writer import YAMLWriter
from core.yaml_reader import YAMLReader

def test_position_preservation():
    """Test that actor positions are preserved through save/load cycle."""
    print("Testing position preservation...")
    
    # Create test graph with positioned actors
    graph = ActorGraph()
    
    # Create actors with specific positions
    source = Actor(
        name="test_source",
        type=ActorType.SOURCE,
        output_topic="data:test:raw",
        host="localhost",
        x=100.0,
        y=150.0
    )
    
    processor = Actor(
        name="test_processor", 
        type=ActorType.PROCESSOR,
        input_topic="data:test:raw",
        output_topic="data:test:processed",
        host="localhost", 
        x=300.0,
        y=150.0
    )
    
    sink = Actor(
        name="test_sink",
        type=ActorType.SINK,
        input_topic="data:test:processed",
        host="localhost",
        x=500.0, 
        y=150.0
    )
    
    # Add actors to graph
    graph.add_actor(source)
    graph.add_actor(processor)
    graph.add_actor(sink)
    
    # Add connections
    graph.add_connection(Connection("test_source", "test_processor"))
    graph.add_connection(Connection("test_processor", "test_sink"))
    
    print(f"Original positions:")
    print(f"  Source: ({source.x}, {source.y})")
    print(f"  Processor: ({processor.x}, {processor.y})")
    print(f"  Sink: ({sink.x}, {sink.y})")
    
    # Test project file save/load
    with tempfile.NamedTemporaryFile(mode='w', suffix='.ersap', delete=False) as f:
        project_file = f.name
    
    try:
        # Save project
        writer = YAMLWriter()
        errors = writer.write_project_file(project_file, graph)
        assert not errors, f"Save errors: {errors}"
        print("✓ Project saved successfully")
        
        # Load project  
        reader = YAMLReader()
        loaded_graph, metadata, errors = writer.read_project_file(project_file)
        assert not errors, f"Load errors: {errors}"
        print("✓ Project loaded successfully")
        
        # Verify positions are preserved
        loaded_source = loaded_graph.get_actor_by_name("test_source")
        loaded_processor = loaded_graph.get_actor_by_name("test_processor") 
        loaded_sink = loaded_graph.get_actor_by_name("test_sink")
        
        print(f"Loaded positions:")
        print(f"  Source: ({loaded_source.x}, {loaded_source.y})")
        print(f"  Processor: ({loaded_processor.x}, {loaded_processor.y})")
        print(f"  Sink: ({loaded_sink.x}, {loaded_sink.y})")
        
        # Check positions match
        assert loaded_source.x == source.x and loaded_source.y == source.y
        assert loaded_processor.x == processor.x and loaded_processor.y == processor.y
        assert loaded_sink.x == sink.x and loaded_sink.y == sink.y
        print("✓ All positions preserved correctly!")
        
    finally:
        # Cleanup
        try:
            os.unlink(project_file)
        except:
            pass
    
    # Test YAML export/import
    with tempfile.NamedTemporaryFile(mode='w', suffix='.yml', delete=False) as f:
        yaml_file = f.name
    
    try:
        # Export to YAML
        errors = writer.write_services_yaml(graph, yaml_file)
        assert not errors, f"YAML export errors: {errors}"
        print("✓ YAML exported successfully")
        
        # Import from YAML
        imported_graph = reader.read_yaml_file(yaml_file)
        print("✓ YAML imported successfully")
        
        # Note: YAML export/import currently only supports positions in project files,
        # not in the enhanced pipeline format. This is expected behavior.
        
    finally:
        # Cleanup
        try:
            os.unlink(yaml_file)
        except:
            pass
    
    print("\n🎉 All position preservation tests passed!")

if __name__ == "__main__":
    test_position_preservation()