#!/usr/bin/env python3
"""
Simple wrapper to run RTDP CLI when console_scripts entry point has issues.
"""

import sys
import os

# Add current directory to path
sys.path.insert(0, os.path.dirname(__file__))

try:
    from rtdpcli import cli
    cli()
except ImportError as e:
    print(f"Error importing RTDP CLI: {e}")
    print("Make sure you're in the correct directory.")
    sys.exit(1)
except Exception as e:
    print(f"Error running RTDP CLI: {e}")
    sys.exit(1) 