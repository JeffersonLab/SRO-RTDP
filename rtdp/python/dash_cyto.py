# Author: xmei@jlab.org

"""
Layouts and callbacks of the Dash application using dash_cytoscape library.
"""

# Dash modules. Ref: https://dash.plotly.com
from dash import html, Dash
# Dash cytoscape. Ref: https://dash.plotly.com/cytoscape
import dash_cytoscape as cyto


# dash_cytoscape stylesheets
cyto_display_stylesheet_config_flowchart=[
        {
            'selector': 'node',
            'style': {
                'label': 'data(label)'
            }
        },
            {
                'selector': 'edge',
                'style': {
                    'curve-style': 'bezier',
                    'target-arrow-shape': 'triangle'
                }
            }
        ]


def get_cytoscape_elements(node_list):
    """
    Transfer the node list into a cytoscape-format dictionary array.

    Args:
    - node_list: A list of configuration parsed from YAML.

    Returns:
    - r: A list of dictionaries where the keywords subject to Cytoscape.
    """
    r = []
    n = len(node_list)

    # The Dash Cytoscape elements are represented in the Python dictionary format with
    # specified keywords. Ref: https://dash.plotly.com/cytoscape/elements

    # The node elements.
    # "position" is not required because the "layout" of "cyto.Cytoscape" is "grid".
    for i in range(n):
        r.append({
            'data': {'id': str(i), 'label': node_list[i].name},
            'position': {'x': 20 + 50 * i, 'y': 50}
            })

    # The edge elements
    for i in range(n - 1):
        r.append({
            'data': {'source': str(i), 'target': str(i + 1)},
            'selectable': False
            })

    return r


def get_dash_app(nodes):
    """Define the Dash application layout and callbacks.

    Args:
    - nodes: The node list parsed from the YAML configuration file.

    Returns:
    - app: The created Dash application.
    """
    app = Dash(__name__)

    app.layout = html.Div([
        html.H1(
            children='Visulization of the ERSAP configuration file',
            style={'textAlign':'Left'}
            ),
        cyto.Cytoscape(
            id='cyto-display-config-flowchart',
            layout={'name': 'grid'},
            style={'width': '800px', 'height': '300px'},
            elements=get_cytoscape_elements(nodes),
            stylesheet=cyto_display_stylesheet_config_flowchart
        ),
        html.Pre(id='cyto-tapNode')
    ])
    return app
