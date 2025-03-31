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
Check-in: 2025-03-10
Last update: 2025-03-31
"""

import zmq
import time
import argparse
import numpy as np


DATA_NUMPY_WIDTH = 20480000  # Sending data grouped by this dimension. 40960000 does not work!
BYTES_PER_MESSAGE = DATA_NUMPY_WIDTH * 4  # float32 = 4 bytes

def main():
    parser = argparse.ArgumentParser(description="ZMQ Floating Point Data Sender")
    parser.add_argument("-a",
                        "--ip-addr", required=True, help="IP address to send data to")
    parser.add_argument("-p",
                        "--port", type=int, default=55555, help="Port number to send data to (default: 55555)")
    parser.add_argument("-i",
                        "--all-ones", action="store_true", help="Send all ones if enabled (default: random values)")
    parser.add_argument("--rate", type=float, default=500.0,
                        help="Average MB/s to send (default = 500)")
    
    args = parser.parse_args()
    
    context = zmq.Context()
    socket = context.socket(zmq.PUSH)
    socket.connect(f"tcp://{args.ip_addr}:{args.port}")
    
    print(f"Sending data to {args.ip_addr}:{args.port} {'(all ones)' if args.all_ones else '(random values)'}")

    if args.rate > 0:
        print(f"Target send rate: {args.rate} MB/s\n")

    # Calculate time interval between sends to maintain the desired MB/s rate
    interval = 0
    if args.rate > 0:
        interval = max(1.0, BYTES_PER_MESSAGE / (args.rate * 1e6))
        print(f"Sent interval: {interval * 1000} ms")
    else:
        print(f"--rate should be a positive number!!!")
        exit(-1)

    next_send_time = time.perf_counter()

    try:
        while True:
            if args.all_ones:
                data = np.ones(DATA_NUMPY_WIDTH, dtype=np.float32)
            else:
                data = np.random.rand(DATA_NUMPY_WIDTH).astype(np.float32)

            socket.send(data.tobytes())

            if interval > 0:
                next_send_time += interval
                now = time.perf_counter()
                sleep_duration = next_send_time - now
                if sleep_duration > 0:
                    print(f"\tSent {data.nbytes / 1e6} MB, sleep for {sleep_duration * 1000} ms...")
                    time.sleep(sleep_duration)  
                else:
                    # Falling behind: skip sleep to catch up
                    next_send_time = now
    except KeyboardInterrupt:
        print("\nTerminating sender.")
    finally:
        socket.close()
        context.term()

if __name__ == "__main__":
    main()
