# Author: xmei@jlab.org

"""
Entry point of rdtp.
"""

import argparse
# Logging cookboook: https://docs.python.org/3/howto/logging-cookbook.html
# import logging

# Dash modules
from dash import html, Dash, dcc
import dash_cytoscape as cyto

from config_parser import ERSAPReader

RTDP_CLI_APP_DESCRIP_STR = \
    "rtdp: JLab's streaming readout RealTime Development and testing Platform."
RTDP_CLI_APP_URL_STR = "https://github.com/JeffersonLab/SRO-RTDP"
RTDP_CLI_APP_VERSION_STR = "0.0"

def get_parser():
    """Define the application arguments. Create the ArgumentParser object and return it.

    Returns:
        parser (argparse.ArgumentParser): The created argument parser.
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


def run_rtdp(parser):
    """Proocess the cli inputs.

    Args:
        parser (argparse.ArgumentParser): The created argument parser.
    """
    args = parser.parse_args()
    if args.config_file:
        print(f"Starting the platform using the specified YAML configuration file:\
            {args.config_file}")
        # TODO: using ERSAP reader here. Should be generalized.
        # TODO: not a service launching yet.
        configurations = ERSAPReader(args.config_file)
        # configurations.print_cytoscape_elements()

        app = Dash(__name__)

        app.layout = html.Div([
            html.H1(children='Visulization of the ERSAP configuration file', style={'textAlign':'Left'}),
            cyto.Cytoscape(
                id='cyto-display',
                layout={'name': 'grid'},
                style={'width': '1200px', 'height': '400px'},
                elements=configurations.get_cytoscape_elements()
            )
        ])

        app.run_server(debug=True)
    else:
        parser.print_help()


if __name__ == '__main__':
    run_rtdp(get_parser())