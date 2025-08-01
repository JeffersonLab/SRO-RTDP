"""
RTDP Workflow CLI package.
"""

# Import the CLI function from the rtdpcli module
try:
    from .rtdpcli import cli
    __all__ = ['cli']
except ImportError:
    # Fallback for direct execution
    import sys
    import os
    sys.path.insert(0, os.path.dirname(__file__))
    from rtdpcli import cli
    __all__ = ['cli'] 