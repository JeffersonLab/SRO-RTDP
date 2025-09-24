#!/usr/bin/env python3
"""
Test script to verify the routing export fix.
"""

import sys
import os
import tempfile

# Add the current directory to the path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from core.model import ActorGraph, Actor, ActorType, Connection
from core.yaml_writer import YAMLWriter
from core.yaml_reader import YAMLReader

def test_routing_export_fix():
    """Test that routing configurations export without duplicate topics."""
    print("Testing routing export fix...")
    
    # Create test graph: Source -> Router -> 2 Processors -> Collector -> Sink
    graph = ActorGraph()
    
    # Create actors
    source = Actor(
        name="source_1",
        type=ActorType.SOURCE,
        output_topic="data:events:source_1_output"
    )
    
    router = Actor(
        name="router_1", 
        type=ActorType.ROUTING,
        input_topic="data:events:source_1_output",
        output_topic="data:router_1:routed",
        targets=["processor_2", "processor_3"]
    )
    
    proc2 = Actor(
        name="processor_2",
        type=ActorType.PROCESSOR,
        input_topic="data:router_1:routed",
        output_topic="data:events:processor_2_output"  # This should be unique
    )
    
    proc3 = Actor(
        name="processor_3", 
        type=ActorType.PROCESSOR,
        input_topic="data:router_1:routed", 
        output_topic="data:events:processor_3_output"  # This should be unique
    )
    
    collector = Actor(
        name="collector_1",
        type=ActorType.COLLECTOR,
        input_topic="data:events:collector_input",
        output_topic="data:events:collector_output"
    )
    
    sink = Actor(
        name="sink_1",
        type=ActorType.SINK,
        input_topic="data:events:collector_output"
    )
    
    # Add actors to graph
    for actor in [source, router, proc2, proc3, collector, sink]:
        graph.add_actor(actor)
    
    # Add connections (simplified - just for export testing)
    graph.add_connection(Connection("source_1", "router_1"))
    graph.add_connection(Connection("processor_2", "collector_1"))
    graph.add_connection(Connection("processor_3", "collector_1"))
    graph.add_connection(Connection("collector_1", "sink_1"))
    
    print("Created test graph with:")
    print(f"  Source: {source.name} -> {source.output_topic}")
    print(f"  Router: {router.name} -> {router.output_topic}")
    print(f"  Proc2: {proc2.name} -> {proc2.output_topic}")
    print(f"  Proc3: {proc3.name} -> {proc3.output_topic}")
    print(f"  Collector: {collector.name} -> {collector.output_topic}")
    print(f"  Sink: {sink.name} -> {sink.output_topic}")
    
    # Export to YAML
    with tempfile.NamedTemporaryFile(mode='w', suffix='.yml', delete=False) as f:
        yaml_file = f.name
    
    try:
        writer = YAMLWriter()
        errors = writer.write_services_yaml(graph, yaml_file)
        assert not errors, f"Export errors: {errors}"
        print("✅ YAML exported successfully")
        
        # Read back and check for duplicate topics
        reader = YAMLReader()
        imported_graph = reader.read_yaml_file(yaml_file)
        print("✅ YAML imported successfully")
        
        # Check that all processors have unique output topics
        output_topics = []
        for actor in imported_graph.actors.values():
            if actor.output_topic:
                output_topics.append(actor.output_topic)
        
        unique_topics = set(output_topics)
        print(f"Output topics: {output_topics}")
        print(f"Unique topics: {len(unique_topics)}, Total topics: {len(output_topics)}")
        
        if len(unique_topics) == len(output_topics):
            print("✅ All output topics are unique!")
        else:
            print("❌ Found duplicate output topics!")
            duplicates = [topic for topic in output_topics if output_topics.count(topic) > 1]
            print(f"Duplicates: {set(duplicates)}")
        
        # Verify specific actors have correct topics
        imported_proc2 = imported_graph.get_actor_by_name("processor_2")
        imported_proc3 = imported_graph.get_actor_by_name("processor_3")
        
        print(f"Processor 2 output: {imported_proc2.output_topic}")
        print(f"Processor 3 output: {imported_proc3.output_topic}")
        
        assert imported_proc2.output_topic != imported_proc3.output_topic, "Processors have duplicate topics!"
        print("✅ Processors have unique output topics!")
        
    finally:
        # Cleanup
        try:
            os.unlink(yaml_file)
        except:
            pass
    
    print("\n🎉 Routing export fix test passed!")

if __name__ == "__main__":
    test_routing_export_fix()