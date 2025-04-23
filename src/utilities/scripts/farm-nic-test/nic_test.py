#!/usr/bin/env python3

import subprocess
import sys
import argparse
import re

def run_iperf_test(receiver_ip):
    """
    Run iperf2 test using Docker containers for both sender and receiver.
    Returns the transmission rate in Gbits/sec.
    """
    try:
        # Start iperf server (receiver) in Docker
        server_cmd = [
            "docker", "run", "-d", "--rm",
            "--network=host",
            "networkstatic/iperf2",
            "iperf", "-s"
        ]
        server_container = subprocess.run(server_cmd, capture_output=True, text=True)
        server_id = server_container.stdout.strip()

        # Run iperf client (sender) in Docker
        client_cmd = [
            "docker", "run", "--rm",
            "--network=host",
            "networkstatic/iperf2",
            "iperf", "-c", receiver_ip, "-t", "10"
        ]
        result = subprocess.run(client_cmd, capture_output=True, text=True)

        # Clean up server container
        subprocess.run(["docker", "stop", server_id])

        # Parse the output to get the transmission rate
        match = re.search(r'(\d+\.\d+)\s*Gbits/sec', result.stdout)
        if match:
            return float(match.group(1))
        else:
            print("Error: Could not parse iperf output")
            return None

    except Exception as e:
        print(f"Error running iperf test: {str(e)}")
        return None

def main():
    parser = argparse.ArgumentParser(description='Test NIC speed using iperf2')
    parser.add_argument('receiver_ip', help='IP address of the receiver NIC')
    args = parser.parse_args()

    print(f"Testing NIC speed with receiver IP: {args.receiver_ip}")
    rate = run_iperf_test(args.receiver_ip)
    
    if rate is not None:
        print(f"Transmission rate: {rate} Gbits/sec")
    else:
        print("Failed to measure transmission rate")
        sys.exit(1)

if __name__ == "__main__":
    main() 