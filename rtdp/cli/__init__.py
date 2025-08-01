"""
RTDP Workflow CLI package.
"""

# Import the CLI function from the rtdpcli module
import sys
import os

# Add current directory to path for direct imports
if os.path.dirname(__file__) not in sys.path:
    sys.path.insert(0, os.path.dirname(__file__))

try:
    from rtdpcli import cli
    __all__ = ['cli']
except ImportError as e:
    print(f"Error importing RTDP CLI: {e}")
    sys.exit(1) 