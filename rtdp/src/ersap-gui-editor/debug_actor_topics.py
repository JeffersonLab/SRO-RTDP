#!/usr/bin/env python3
"""
Debug script to check actor topics before export.
"""

import sys
import os

# Add the current directory to the path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from core.yaml_reader import YAMLReader

def debug_actor_topics(yaml_file):
    """Debug what topics actors have when loaded."""
    print(f"Debugging actor topics in: {yaml_file}")
    
    try:
        reader = YAMLReader()
        graph = reader.read_yaml_file(yaml_file)
        
        print(f"\nLoaded {len(graph.actors)} actors:")
        print("=" * 60)
        
        output_topics = {}  # topic -> list of actors
        
        for name, actor in graph.actors.items():
            print(f"Actor: {name}")
            print(f"  Type: {actor.type.value}")
            print(f"  Input: {actor.input_topic}")
            print(f"  Output: {actor.output_topic}")
            if hasattr(actor, 'targets') and actor.targets:
                print(f"  Targets: {actor.targets}")
            print()
            
            # Track output topics
            if actor.output_topic:
                if actor.output_topic not in output_topics:
                    output_topics[actor.output_topic] = []
                output_topics[actor.output_topic].append(name)
        
        print("OUTPUT TOPIC ANALYSIS:")
        print("=" * 60)
        for topic, actors in output_topics.items():
            status = "✅ UNIQUE" if len(actors) == 1 else "❌ DUPLICATE"
            print(f"{status}: {topic}")
            for actor in actors:
                print(f"    -> {actor}")
            print()
        
        # Check for duplicates
        duplicates = {topic: actors for topic, actors in output_topics.items() if len(actors) > 1}
        if duplicates:
            print("🚨 FOUND DUPLICATE TOPICS:")
            for topic, actors in duplicates.items():
                print(f"  Topic: {topic}")
                print(f"  Actors: {', '.join(actors)}")
        else:
            print("✅ All output topics are unique!")
            
    except Exception as e:
        print(f"❌ Error loading file: {e}")

if __name__ == "__main__":
    yaml_file = "/Users/gurjyan/Documents/aman.yml"
    debug_actor_topics(yaml_file)