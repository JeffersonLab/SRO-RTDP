# Author: xmei@jlab.org

"""
Layouts and callbacks of the Dash application using dash_cytoscape library.
"""

import logging
# Dash modules. Ref: https://dash.plotly.com
from dash import html, Dash, Input, Output, callback
# Dash cytoscape. Ref: https://dash.plotly.com/cytoscape
import dash_cytoscape as cyto

logger = logging.getLogger(__name__)

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

styles = {
    'pre': {
        'border': 'thin lightgrey solid',
        'overflowX': 'scroll'
    }
}


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
    logger.info("Cytoscape elements created")
    return r


def get_dash_app(nodes):
    """Define the Dash application layouts and callbacks.

    Args:
    - nodes: The node list parsed from the YAML configuration file.

    Returns:
    - app: The created Dash application.
    """
    app = Dash(__name__)

    app.layout = html.Div([
        html.H1(
            children='SRO-RTDP Dash Demo',
            style={'textAlign':'Left'}
            ),
        html.Div([
            html.H3(
                children='Visualization of the ERSAP configuration file',
                style={'textAlign':'Left'}
                ),
            cyto.Cytoscape(
                id='cyto-display-config-flowchart',
                layout={'name': 'grid'},
                style={'width': '800px', 'height': '200px'},
                elements=get_cytoscape_elements(nodes),
                stylesheet=cyto_display_stylesheet_config_flowchart
            )
        ]),

        # Display the info about the selected ERSAP flowchart node using the callback function.
        html.Div([
            html.Pre(id='cyto-tapNode-resp', style=styles['pre'])
        ])
    ])

    # Ref: https://dash.plotly.com/cytoscape/events
    @callback(Output('cyto-tapNode-resp', 'children'),
              Input('cyto-display-config-flowchart', 'tapNodeData'))
    def display_tap_config_node(data):
        if not data:
            return html.H4("No service selected.")

        logger.debug("On config flowchart, node-%s selected", data['id'])
        node_id = int(data['id'])
        node_info = nodes[node_id]

        return html.Div([
            html.H4(f"Selected service: {data['label']}"),
            html.P(f"  class: {node_info.cls}\n  language: {node_info.lan}\n")
        ])

    return app
