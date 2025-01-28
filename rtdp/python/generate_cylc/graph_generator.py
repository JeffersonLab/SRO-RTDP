import networkx as nx
import matplotlib.pyplot as plt
import os


def generate_workflow_graph():
    """Generate a visualization of the CPU emulator workflow."""
    # Create a directed graph
    G = nx.DiGraph()

    # Add nodes with positions
    pos = {
        'sender': (0, 0),
        'emulator': (1, 0),
        'receiver': (2, 0)
    }

    # Add nodes
    G.add_nodes_from(pos.keys())

    # Add edges
    G.add_edge('sender', 'emulator')
    G.add_edge('emulator', 'receiver')

    # Create the static directory if it doesn't exist
    os.makedirs('static', exist_ok=True)

    # Create the visualization
    plt.figure(figsize=(10, 4))
    nx.draw(G, pos,
            with_labels=True,
            node_color='lightblue',
            node_size=2000,
            arrowsize=20,
            font_size=12,
            font_weight='bold')

    # Add a title
    plt.title('CPU Emulator Workflow')

    # Save the graph
    plt.savefig('static/workflow_graph.png', bbox_inches='tight', dpi=300)
    plt.close()


if __name__ == '__main__':
    generate_workflow_graph()
