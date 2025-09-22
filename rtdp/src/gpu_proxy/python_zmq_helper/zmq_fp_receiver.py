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
import time
import threading
import numpy as np
import argparse

total_recv_bytes = 0
stop_flag = False
lock = threading.Lock()


def rate_logger():
    """The rate logger thread. Log the recv rate every 2 seconds."""
    global total_recv_bytes, stop_flag
    while not stop_flag:
        pre_recv_bytes = total_recv_bytes
        time.sleep(2)
        with lock:
            rate = (total_recv_bytes - pre_recv_bytes) / (2.0e6)
            if rate == 0:
                continue
            print(f"curr_recv_rate = {rate} MB/s")

def main():
    global total_recv_bytes, stop_flag, lock

    parser = argparse.ArgumentParser(description="ZMQ Floating Point Data Receiver")
    parser.add_argument("-p", "--port", type=int, default=55556, help="Port number to receive data on (default: 55555)")
    parser.add_argument("-v", "--verbose", action="store_true", help="Print the first 10 floats for each msg")

    args = parser.parse_args()
    
    context = zmq.Context()
    socket = context.socket(zmq.PULL)
    socket.setsockopt(zmq.RCVHWM, 1000)   # Set high water mark
    socket.bind(f"tcp://*:{args.port}")

    print(f"Receiving data on port {args.port}...")

    # Start the rate logger thread
    thread = threading.Thread(target=rate_logger)
    thread.daemon = True
    thread.start()

    try:
        while True:
            message = socket.recv()
            data = np.frombuffer(message, dtype=np.float32)
            with lock:  # Python GIL lock
                total_recv_bytes += len(message)
            if args.verbose:
                print(f"Received [{len(message)}] bytes")
                print(f"\tFirst 10 floats: {data[:10]}\n")
    except KeyboardInterrupt:
        print("\nTerminating receiver.")
        stop_flag = True
        thread.join()
    finally:
        socket.close()
        context.term()

if __name__ == "__main__":
    main()
