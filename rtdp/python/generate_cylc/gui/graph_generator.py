import networkx as nx
import matplotlib.pyplot as plt
import os
from typing import Dict, List, Any


def get_edge_style(edge_type: str) -> Dict[str, Any]:
    """Get the visual style for different edge types."""
    styles = {
        'trigger': {
            'color': 'blue',
            'style': 'solid',
            'width': 1.5
        },
        'data': {
            'color': 'green',
            'style': 'dashed',
            'width': 2.0
        },
        'state': {
            'color': 'red',
            'style': 'dotted',
            'width': 1.5
        },
        'resource': {
            'color': 'purple',
            'style': 'dashdot',
            'width': 1.5
        }
    }
    return styles.get(edge_type, styles['trigger'])


def generate_workflow_graph(
    components: Dict[str, Dict],
    edges: List[Dict]
) -> None:
    """Generate a visualization of the workflow.

    Args:
        components: Dictionary of components with their types and positions
        edges: List of edge dictionaries with from/to and optional description
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

    # Add edges (data flows)
    for edge in edges:
        G.add_edge(edge['from'], edge['to'])

    # Create the static directory if it doesn't exist
    os.makedirs('static', exist_ok=True)

    # Create the visualization
    plt.figure(figsize=(12, 6))

    # Draw nodes
    node_colors = [G.nodes[node].get('color', 'gray') for node in G.nodes()]
    nx.draw_networkx_nodes(
        G,
        pos,
        node_color=node_colors,
        node_size=2000
    )

    # Draw edges with data flow style
    nx.draw_networkx_edges(
        G,
        pos,
        edge_color='green',
        style='dashed',
        width=2.0,
        arrowsize=20,
        arrowstyle='->'
    )

    # Draw labels
    nx.draw_networkx_labels(
        G,
        pos,
        font_size=10,
        font_weight='bold'
    )

    # Add edge labels for descriptions
    edge_labels = {
        (edge['from'], edge['to']): edge.get('description', '')
        for edge in edges if 'description' in edge
    }
    if edge_labels:
        nx.draw_networkx_edge_labels(
            G,
            pos,
            edge_labels=edge_labels,
            font_size=8
        )

    # Add a title
    plt.title('Data Flow Graph')

    # Save the graph
    plt.savefig(
        'static/workflow_graph.png',
        bbox_inches='tight',
        dpi=300
    )
    plt.close()


def validate_workflow(components: Dict[str, Dict],
                     edges: List[Dict]) -> bool:
    """Validate the workflow configuration.

    Args:
        components: Dictionary of components with their types
        edges: List of edge dictionaries with from and to fields

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
    G.add_edges_from([(e['from'], e['to']) for e in edges])

    # Check if the graph is acyclic
    if not nx.is_directed_acyclic_graph(G):
        return False

    # Check if all components are connected
    if not nx.is_weakly_connected(G):
        return False

    # Validate data flow rules
    for edge in edges:
        from_comp = components[edge['from']]
        to_comp = components[edge['to']]

        # Receivers can't produce data
        if from_comp['type'] == 'receiver':
            return False

        # Senders can't consume data
        if to_comp['type'] == 'sender':
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

    test_edges = [
        {'from': 'sender1', 'to': 'emulator1',
         'description': 'Raw data'},
        {'from': 'emulator1', 'to': 'emulator2',
         'description': 'Processed data'},
        {'from': 'emulator2', 'to': 'receiver1',
         'description': 'Final output'}
    ]

    if validate_workflow(test_components, test_edges):
        generate_workflow_graph(test_components, test_edges)
