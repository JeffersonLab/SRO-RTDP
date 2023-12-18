# Author: xmei@jlab.org

"""
Entry point of rdtp.
"""

# Logging cookboook: https://docs.python.org/3/howto/logging-cookbook.html
import logging
import argparse

from rtdp_config_parser import ERSAPReader
from rtdp_dash_cyto import get_dash_app

RTDP_CLI_APP_DESCRIP_STR = \
    "rtdp: JLab's streaming readout RealTime Development and testing Platform."
RTDP_CLI_APP_URL_STR = "https://github.com/JeffersonLab/SRO-RTDP"
RTDP_CLI_APP_VERSION_STR = "0.0"
RTDP_CLI_DEFAULT_LOGFILE = "rtdp.log"

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
    parser.add_argument('--log_file', type=str,
                        default=RTDP_CLI_DEFAULT_LOGFILE, help='log file name')
    parser.add_argument('--log_level', type=str, default='debug',
                        choices=['debug', 'info', 'warning', 'error', 'critical'],
                        help='log level: debug, info, warning, error, critical')
    parser.add_argument('config_file', nargs='?',
        help='path to your YAML configuration file')
    return parser


def setup_logging(log_file, log_level):
    """Setup the logging instance."""
    numeric_level = getattr(logging, log_level.upper(), None)
    if not isinstance(numeric_level, int):
        raise ValueError(f'Invalid log level: {log_level}')

    logging.basicConfig(filename=log_file, level=numeric_level,
                        format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
    logging.info("Start application rtdp")



def run_rtdp(parser):
    """Proocess the cli inputs.

    Args:
    - parser (argparse.ArgumentParser): The created argument parser.
    """
    args = parser.parse_args()

    setup_logging(args.log_file, args.log_level)

    if args.config_file:
        # TODO: using ERSAP reader here. Should be generalized.
        configurations = ERSAPReader(args.config_file)
        ersap_nodes = configurations.get_flowchart_nodes()

        app = get_dash_app(ersap_nodes)
        app.run_server(debug=True)
    else:
        parser.print_help()


if __name__ == '__main__':
    run_rtdp(get_parser())
