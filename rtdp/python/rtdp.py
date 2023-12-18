# Author: xmei@jlab.org

"""
Entry point of rdtp.
"""

import argparse
# Logging cookboook: https://docs.python.org/3/howto/logging-cookbook.html
# import logging

# Dash modules. Ref: https://dash.plotly.com
from dash import html, Dash
# Dash cytoscape. Ref: https://dash.plotly.com/cytoscape
import dash_cytoscape as cyto

from config_parser import ERSAPReader

RTDP_CLI_APP_DESCRIP_STR = \
    "rtdp: JLab's streaming readout RealTime Development and testing Platform."
RTDP_CLI_APP_URL_STR = "https://github.com/JeffersonLab/SRO-RTDP"
RTDP_CLI_APP_VERSION_STR = "0.0"

def get_parser():
    """Define the application arguments. Create the ArgumentParser object and return it.

    Returns:
    - parser (argparse.ArgumentParser): The created argument parser.
    """
    parser = argparse.ArgumentParser(
        prog="rtdp",
        description=RTDP_CLI_APP_DESCRIP_STR,
        epilog=f'Github page: {RTDP_CLI_APP_URL_STR}'
    )
    parser.add_argument('-v', '--version', action='version',
        version=f'%(prog)s {RTDP_CLI_APP_VERSION_STR}')
    parser.add_argument('config_file', nargs='?',
        help='path to your YAML configuration file')
    return parser

# dash_cytoscape stylesheets
cyto_display_stylesheet=[
        {
            'selector': 'node',
            'style': {
                'label': 'data(id)'
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


def get_dash_app(config):
    """Define the Dash application layout and callbacks.

    Args:
    - config: The parsed configuration object based on the input yaml file.

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
            id='cyto-display-1',
            layout={'name': 'grid'},
            style={'width': '800px', 'height': '300px'},
            elements=config.get_cytoscape_elements(),
            stylesheet=cyto_display_stylesheet
        )
    ])
    return app


def run_rtdp(parser):
    """Proocess the cli inputs.

    Args:
    - parser (argparse.ArgumentParser): The created argument parser.
    """
    args = parser.parse_args()
    if args.config_file:
        # TODO: using ERSAP reader here. Should be generalized.
        # TODO: not a service launching yet.
        configurations = ERSAPReader(args.config_file)
        # configurations.print_cytoscape_elements()

        app = get_dash_app(configurations)
        app.run_server(debug=True)
    else:
        parser.print_help()


if __name__ == '__main__':
    run_rtdp(get_parser())
