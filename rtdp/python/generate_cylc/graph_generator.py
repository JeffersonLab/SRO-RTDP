import networkx as nx
import matplotlib.pyplot as plt
import os
from typing import Dict, List


def generate_workflow_graph(components: Dict[str, Dict], connections: List[tuple]) -> None:
    """Generate a visualization of the CPU emulator workflow.

    Args:
        components: Dictionary of components with their types and positions
        connections: List of tuples representing connections between components
    """
    # Create a directed graph
    G = nx.DiGraph()

    # Calculate positions for components
    num_components = len(components)
    x_spacing = 1.0 / (num_components + 1) if num_components > 1 else 0.5

    pos = {}
    for i, (name, info) in enumerate(components.items()):
        x_pos = x_spacing * (i + 1)
        pos[name] = (x_pos, 0.5 if info['type'] == 'emulator' else 0)

    # Add nodes with custom colors based on type
    for name, info in components.items():
        color = {
            'sender': 'lightgreen',
            'emulator': 'lightblue',
            'receiver': 'lightcoral'
        }.get(info['type'], 'gray')

        G.add_node(name, color=color)

    # Add edges
    for source, target in connections:
        G.add_edge(source, target)

    # Create the static directory if it doesn't exist
    os.makedirs('static', exist_ok=True)

    # Create the visualization
    plt.figure(figsize=(12, 6))

    # Draw nodes with custom colors
    node_colors = [G.nodes[node].get('color', 'gray') for node in G.nodes()]

    nx.draw(G, pos,
            with_labels=True,
            node_color=node_colors,
            node_size=2000,
            arrowsize=20,
            font_size=10,
            font_weight='bold',
            edge_color='gray',
            arrows=True)

    # Add a title
    plt.title('CPU Emulator Workflow')

    # Save the graph
    plt.savefig('static/workflow_graph.png', bbox_inches='tight', dpi=300)
    plt.close()


def validate_workflow(components: Dict[str, Dict], connections: List[tuple]) -> bool:
    """Validate the workflow configuration.

    Args:
        components: Dictionary of components with their types
        connections: List of tuples representing connections between components

    Returns:
        bool: True if workflow is valid, False otherwise
    """
    # Check if there's at least one sender and one receiver
    has_sender = any(c['type'] == 'sender' for c in components.values())
    has_receiver = any(c['type'] == 'receiver' for c in components.values())

    if not (has_sender and has_receiver):
        return False

    # Create a directed graph for validation
    G = nx.DiGraph()
    G.add_nodes_from(components.keys())
    G.add_edges_from(connections)

    # Check if the graph is acyclic
    if not nx.is_directed_acyclic_graph(G):
        return False

    # Check if all components are connected
    if not nx.is_weakly_connected(G):
        return False

    # Validate that senders have no incoming edges
    senders = [name for name, info in components.items() if info['type']
               == 'sender']
    for sender in senders:
        if list(G.predecessors(sender)):
            return False

    # Validate that receivers have no outgoing edges
    receivers = [name for name, info in components.items()
                 if info['type'] == 'receiver']
    for receiver in receivers:
        if list(G.successors(receiver)):
            return False

    return True


if __name__ == '__main__':
    # Example usage
    test_components = {
        'sender1': {'type': 'sender'},
        'emulator1': {'type': 'emulator'},
        'emulator2': {'type': 'emulator'},
        'receiver1': {'type': 'receiver'}
    }

    test_connections = [
        ('sender1', 'emulator1'),
        ('emulator1', 'emulator2'),
        ('emulator2', 'receiver1')
    ]

    if validate_workflow(test_components, test_connections):
        generate_workflow_graph(test_components, test_connections)
