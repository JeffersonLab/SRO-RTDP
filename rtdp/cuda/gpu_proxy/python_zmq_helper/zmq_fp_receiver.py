"""
ZMQ Floating Point Data Receiver

This script listens for floating-point data sent via ZeroMQ (ZMQ) using a PULL socket.
It continuously receives binary-encoded NumPy arrays from a sender and prints them to the screen.

Usage:
    # Run with the default port (55556)
    python zmq_fp_receiver.py

    # Run with a custom port
    python zmq_fp_receiver.py -p 60000
    python zmq_fp_receiver.py --port 60000

    # Show help information
    python zmq_fp_receiver.py --help

Dependencies:
    - pyzmq
    - numpy

Author: ChatGPT, Cissie
Date: 2025-03-10
"""

import zmq
import numpy as np
import argparse

def main():
    parser = argparse.ArgumentParser(description="ZMQ Floating Point Data Receiver")
    parser.add_argument("-p", "--port", type=int, default=55556, help="Port number to receive data on (default: 55555)")
    
    args = parser.parse_args()
    
    context = zmq.Context()
    socket = context.socket(zmq.PULL)
    socket.bind(f"tcp://*:{args.port}")

    print(f"Receiving data on port {args.port}...")

    try:
        while True:
            message = socket.recv()
            data = np.frombuffer(message, dtype=np.float32)
            print(f"Received bytes: {len(message)}")
            print(f"First 10 floats: {data[:10]}\n")
    except KeyboardInterrupt:
        print("\nTerminating receiver.")
    finally:
        socket.close()
        context.term()

if __name__ == "__main__":
    main()
