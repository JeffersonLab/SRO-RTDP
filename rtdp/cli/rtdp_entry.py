#!/usr/bin/env python3
"""
RTDP CLI Entry Point
This script serves as the entry point for the rtdp command.
"""

import sys
import os

# Add the current directory to Python path
current_dir = os.path.dirname(os.path.abspath(__file__))
if current_dir not in sys.path:
    sys.path.insert(0, current_dir)

def main():
    try:
        from rtdpcli import cli
        return cli()
    except ImportError as e:
        print(f"Error importing RTDP CLI: {e}")
        print(f"Current directory: {os.getcwd()}")
        print(f"Python path: {sys.path}")
        return 1
    except Exception as e:
        print(f"Error running RTDP CLI: {e}")
        return 1

if __name__ == '__main__':
    sys.exit(main()) 