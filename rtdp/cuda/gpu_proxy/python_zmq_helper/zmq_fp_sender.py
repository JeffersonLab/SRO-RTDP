"""
ZMQ Floating Point Data Sender

This script sends floating-point data continuously to a specified IP address and port using ZeroMQ (ZMQ).
It supports sending either random floating-point numbers or an array of ones.

Usage:
    # Send random floating-point data to 127.0.0.1:55555
    python zmq_fp_sender.py --ip-addr 127.0.0.1 --port 55555

    # Send all ones instead of random data
    python zmq_fp_sender.py --ip-addr 127.0.0.1 --port 55555 --all-ones

    # Show help information
    python zmq_fp_sender.py --help

Dependencies:
    - pyzmq
    - numpy

Author: ChatGPT, Cissie
Date: 2025-03-10
"""

import zmq
import argparse
import numpy as np


DATA_NUMPY_WIDTH = 2048   # Sending data grouped by this dimension

def main():
    parser = argparse.ArgumentParser(description="ZMQ Floating Point Data Sender")
    parser.add_argument("-a",
                        "--ip-addr", required=True, help="IP address to send data to")
    parser.add_argument("-p",
                        "--port", type=int, default=55555, help="Port number to send data to (default: 55555)")
    parser.add_argument("--group-size", type=int, default=DATA_NUMPY_WIDTH,
                        help="Group data to this width (default: 2048)")
    parser.add_argument("-i",
                        "--all-ones", action="store_true", help="Send all ones if enabled (default: random values)")
    
    args = parser.parse_args()
    
    context = zmq.Context()
    socket = context.socket(zmq.PUSH)
    socket.connect(f"tcp://{args.ip_addr}:{args.port}")
    
    print(f"Sending data to {args.ip_addr}:{args.port} {'(all ones)' if args.all_ones else '(random values)'}")

    try:
        while True:
            if args.all_ones:
                data = np.ones(args.group_size, dtype=np.float32)  # Sending an array of ones
            else:
                data = np.random.rand(args.group_size).astype(np.float32)  # Sending random floating point values
            
            socket.send(data.tobytes())
    except KeyboardInterrupt:
        print("\nTerminating sender.")
    finally:
        socket.close()
        context.term()

if __name__ == "__main__":
    main()
