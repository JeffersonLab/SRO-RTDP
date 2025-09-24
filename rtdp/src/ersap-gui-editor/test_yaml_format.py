#!/usr/bin/env python3
"""
Test script to verify YAML writer generates enhanced orchestrator format.
"""

from core.model import Actor, ActorType, ActorGraph, Connection
from core.yaml_writer import YAMLWriter
import yaml

def test_enhanced_yaml_format():
    """Test that YAML writer generates enhanced orchestrator format."""
    
    # Create a test graph similar to the branching example
    graph = ActorGraph()
    
    # Create feeder (source with routing)
    feeder = Actor(
        name="feeder",
        type=ActorType.ROUTING,
        output_topic="data:events:raw"
    )
    feeder.parameters['class'] = 'org.jlab.epsci.ersap.std.services.EnhancedFeederService'
    feeder.targets = ['processor_a', 'processor_b', 'processor_c']
    
    # Create processors
    processor_a = Actor(
        name="processor_a",
        type=ActorType.PROCESSOR,
        input_topic="data:processor_a:event",
        output_topic="data:events:a_out"
    )
    processor_a.parameters['class'] = 'org.jlab.clas12.ana.ProcessorService'
    
    processor_b = Actor(
        name="processor_b",
        type=ActorType.PROCESSOR,
        input_topic="data:processor_b:event",
        output_topic="data:events:b_out"
    )
    processor_b.parameters['class'] = 'org.jlab.clas12.ana.ProcessorService'
    
    processor_c = Actor(
        name="processor_c",
        type=ActorType.PROCESSOR,
        input_topic="data:processor_c:event",
        output_topic="data:events:c_out"
    )
    processor_c.parameters['class'] = 'org.jlab.clas12.ana.ProcessorService'
    
    # Create merger (collector)
    merger = Actor(
        name="merger",
        type=ActorType.COLLECTOR,
        input_topic="data:events:a_out",
        output_topic="data:events:merged"
    )
    merger.parameters['class'] = 'org.jlab.clas12.ana.MergerService'
    
    # Create writer (sink)
    writer = Actor(
        name="writer",
        type=ActorType.SINK,
        input_topic="data:events:merged"
    )
    writer.parameters['class'] = 'org.jlab.clas12.ana.WriterService'
    
    # Add actors
    graph.add_actor(feeder)
    print(f"[DEBUG] Added actor: {feeder.name}, type: {feeder.type}")
    graph.add_actor(processor_a)
    print(f"[DEBUG] Added actor: {processor_a.name}, type: {processor_a.type}")
    graph.add_actor(processor_b)
    print(f"[DEBUG] Added actor: {processor_b.name}, type: {processor_b.type}")
    graph.add_actor(processor_c)
    print(f"[DEBUG] Added actor: {processor_c.name}, type: {processor_c.type}")
    graph.add_actor(merger)
    print(f"[DEBUG] Added actor: {merger.name}, type: {merger.type}")
    graph.add_actor(writer)
    print(f"[DEBUG] Added actor: {writer.name}, type: {writer.type}")
    
    # Add connections
    graph.add_connection(Connection("feeder", "processor_a"))
    graph.add_connection(Connection("feeder", "processor_b"))
    graph.add_connection(Connection("feeder", "processor_c"))
    graph.add_connection(Connection("processor_a", "merger"))
    graph.add_connection(Connection("processor_b", "merger"))
    graph.add_connection(Connection("processor_c", "merger"))
    graph.add_connection(Connection("merger", "writer"))
    
    # Print all actors in the graph before YAML
    print("[DEBUG] Actors in graph before YAML:")
    for name, actor in graph.actors.items():
        print(f"  {name}: {actor.type}")
    # Print all connections
    print("[DEBUG] Connections in graph:")
    for conn in graph.connections:
        print(f"  {conn.source_actor} -> {conn.target_actor}")
    
    # Generate YAML
    writer = YAMLWriter()
    
    # Debug: print all actors in the graph
    print("Actors in graph:")
    for name, actor in graph.actors.items():
        print(f"  {name}: {actor.type.value}")
    
    yaml_data = writer._graph_to_yaml(graph)
    
    # Debug: print services in YAML
    print("\nServices in YAML:")
    for service in yaml_data['services']:
        print(f"  {service['name']}: {service.get('class', 'no-class')}")
    
    # Print the generated YAML
    print("\nGenerated YAML:")
    print(yaml.dump(yaml_data, default_flow_style=False, indent=2))
    
    # Verify key elements
    assert 'container' in yaml_data
    assert yaml_data['container'] == 'default'
    assert 'lang' in yaml_data
    assert yaml_data['lang'] == 'java'
    assert 'services' in yaml_data
    
    services = yaml_data['services']
    
    # Find feeder service
    feeder_service = next((s for s in services if s['name'] == 'feeder'), None)
    assert feeder_service is not None
    assert 'targets' in feeder_service
    assert feeder_service['targets'] == ['processor_a', 'processor_b', 'processor_c']
    assert 'delivery_mode' in feeder_service
    assert feeder_service['delivery_mode'] == 'round_robin'
    
    # Find merger service
    merger_service = next((s for s in services if s['name'] == 'merger'), None)
    assert merger_service is not None
    assert 'input' in merger_service
    # Should have multiple inputs for collector
    
    print("\n✓ YAML format verification passed!")
    print("✓ Enhanced orchestrator format is correctly generated")
    
    return yaml_data

if __name__ == "__main__":
    test_enhanced_yaml_format() 