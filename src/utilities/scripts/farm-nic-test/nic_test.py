#!/usr/bin/env python3

import subprocess
import sys
import argparse
import re

def run_iperf_test(receiver_ip, port=52011):
    """
    Run iperf2 test directly.
    Returns the transmission rate in Gbits/sec.
    """
    try:
        # Run iperf client
        client_cmd = [
            "iperf", "-c", receiver_ip, "-t", "10", "-p", str(port)
        ]
        result = subprocess.run(client_cmd, capture_output=True, text=True)

        # Print raw output for debugging
        print("\nRaw iperf output:")
        print("=" * 50)
        print(result.stdout)
        print("=" * 50)
        print("Stderr:", result.stderr)
        print("=" * 50)

        # Parse the output to get the transmission rate
        # Try different patterns that might appear in iperf output
        patterns = [
            r'(\d+\.\d+)\s*Gbits/sec',  # Gbits/sec
            r'(\d+\.\d+)\s*Mbits/sec',  # Mbits/sec
            r'(\d+\.\d+)\s*Kbits/sec'   # Kbits/sec
        ]

        for pattern in patterns:
            match = re.search(pattern, result.stdout)
            if match:
                rate = float(match.group(1))
                # Convert to Gbits/sec if needed
                if 'Mbits/sec' in result.stdout:
                    rate = rate / 1000
                elif 'Kbits/sec' in result.stdout:
                    rate = rate / 1000000
                return rate

        print("Error: Could not find transmission rate in iperf output")
        return None

    except Exception as e:
        print(f"Error running iperf test: {str(e)}")
        return None

def main():
    parser = argparse.ArgumentParser(description='Test NIC speed using iperf2')
    parser.add_argument('receiver_ip', help='IP address of the receiver NIC')
    parser.add_argument('port', nargs='?', default=52011, type=int, help='Port number to use (default: 52011)')
    args = parser.parse_args()

    print(f"Testing NIC speed with receiver IP: {args.receiver_ip} on port {args.port}")
    rate = run_iperf_test(args.receiver_ip, args.port)
    
    if rate is not None:
        print(f"Transmission rate: {rate} Gbits/sec")
    else:
        print("Failed to measure transmission rate")
        sys.exit(1)

if __name__ == "__main__":
    main() 