#!/usr/bin/env python3
"""
RTDP CLI entry point for console_scripts.
"""

import sys
import os

# Add the directory containing this script to Python path
script_dir = os.path.dirname(os.path.abspath(__file__))
if script_dir not in sys.path:
    sys.path.insert(0, script_dir)

def main():
    try:
        from rtdpcli import cli
        cli()
    except ImportError as e:
        print(f"Error importing RTDP CLI: {e}")
        print(f"Script directory: {script_dir}")
        print(f"Python path: {sys.path}")
        sys.exit(1)
    except Exception as e:
        print(f"Error running RTDP CLI: {e}")
        sys.exit(1)

if __name__ == '__main__':
    main() 