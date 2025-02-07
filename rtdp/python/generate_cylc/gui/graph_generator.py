import networkx as nx
import matplotlib.pyplot as plt
import os
from typing import Dict, List, Any
import graphviz


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
            'receiver': 'lightcoral',
            'load_balancer': '#ffadad',  # Light red
            'aggregator': '#e4c1f9'      # Light purple
        }.get(info['type'], 'gray')

        shape = {
            'sender': 'box',
            'emulator': 'box',
            'receiver': 'box',
            'load_balancer': 'diamond',
            'aggregator': 'hexagon'
        }.get(info['type'], 'box')

        G.add_node(name, color=color, shape=shape)

    # Add edges (data flows)
    for edge in edges:
        G.add_edge(edge['from'], edge['to'])

    # Create the static directory if it doesn't exist
    os.makedirs('static', exist_ok=True)

    # Create the visualization
    plt.figure(figsize=(12, 6))

    # Draw nodes with custom shapes and colors
    for shape in set(nx.get_node_attributes(G, 'shape').values()):
        node_list = [node for node in G.nodes(
        ) if G.nodes[node].get('shape') == shape]
        if node_list:
            nx.draw_networkx_nodes(
                G,
                pos,
                nodelist=node_list,
                node_color=[G.nodes[node]['color'] for node in node_list],
                node_shape=shape,
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


def create_workflow_graph(config: Dict[str, Any]) -> graphviz.Digraph:
    """Create a graphviz visualization of the workflow.

    Args:
        config: Workflow configuration dictionary

    Returns:
        Graphviz graph object
    """
    dot = graphviz.Digraph(comment='Workflow Graph')
    dot.attr(rankdir='LR')  # Left to right layout

    # Node styles for different component types
    styles = {
        'sender': {
            'shape': 'box',
            'style': 'filled',
            'fillcolor': '#a8e6cf',  # Light green
            'fontname': 'Arial'
        },
        'receiver': {
            'shape': 'box',
            'style': 'filled',
            'fillcolor': '#ffd3b6',  # Light orange
            'fontname': 'Arial'
        },
        'emulator': {
            'shape': 'box',
            'style': 'filled',
            'fillcolor': '#bde0fe',  # Light blue
            'fontname': 'Arial'
        },
        'load_balancer': {
            'shape': 'diamond',
            'style': 'filled',
            'fillcolor': '#ffadad',  # Light red
            'fontname': 'Arial'
        },
        'aggregator': {
            'shape': 'hexagon',
            'style': 'filled',
            'fillcolor': '#e4c1f9',  # Light purple
            'fontname': 'Arial'
        }
    }

    # Add component nodes
    components = config.get('components', {})
    for comp_id, comp in components.items():
        comp_type = comp.get('type', 'unknown')
        style = styles.get(comp_type, {})

        # Create label with component details
        label = f"{comp_id}\n({comp_type})"

        # Add configuration details based on type
        if comp_type == 'emulator':
            config_data = comp.get('configuration', {})
            label += f"\nThreads: {config_data.get('threads', 4)}"
            label += f"\nLatency: {config_data.get('latency', 50)}ms"
        elif comp_type == 'load_balancer':
            config_data = comp.get('load_balancer_config', {})
            label += f"\nStrategy: {config_data.get('strategy', 'round_robin')}"
        elif comp_type == 'aggregator':
            config_data = comp.get('aggregator_config', {})
            label += f"\nStrategy: {config_data.get('strategy', 'ordered')}"

        dot.node(comp_id, label, **style)

    # Add edges
    edges = config.get('edges', [])
    for edge in edges:
        from_id = edge.get('from')
        to_id = edge.get('to')
        if from_id and to_id:
            # Create edge label
            label = []
            if 'data_type' in edge:
                label.append(f"Type: {edge['data_type']}")
            if 'buffer_size' in edge:
                label.append(f"Buffer: {edge['buffer_size']}")

            dot.edge(
                from_id,
                to_id,
                label='\n'.join(label) if label else None,
                fontname='Arial',
                fontsize='10'
            )

    return dot


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
