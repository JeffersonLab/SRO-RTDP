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

def main():
    parser = argparse.ArgumentParser(description="ZMQ Floating Point Data Sender")
    parser.add_argument("-a",
                        "--ip-addr", required=True, help="IP address to send data to")
    parser.add_argument("-p",
                        "--port", type=int, default=55555, help="Port number to send data to (default: 55555)")
    parser.add_argument("-i",
                        "--all-ones", action="store_true", help="Send all ones if enabled (default: random values)")
    parser.add_argument(
                        "--group-size", type=int, default=DATA_NUMPY_WIDTH,
                        help=f"The number of float number each ZMQ msg contains (default: {DATA_NUMPY_WIDTH})")
    parser.add_argument("-r",
                        "--rate", type=float, default=500.0,
                        help="Average MB/s to send (default = 500)")
    
    args = parser.parse_args()
    
    context = zmq.Context()
    socket = context.socket(zmq.PUSH)
    socket.setsockopt(zmq.SNDHWM, 1000)  # set send high-water mark to 1000 messages
    socket.connect(f"tcp://{args.ip_addr}:{args.port}")
    
    print(f"Sending data to {args.ip_addr}:{args.port} {'(all ones)' if args.all_ones else '(random values)'}")

    if args.rate > 0:
        print(f"Target send rate: {args.rate} MB/s\n")

    # Calculate time interval between sends to maintain the desired MB/s rate
    if args.rate > 0:
        interval = (args.group_size * 4.0) / (args.rate * 1e6)
        print(f"Each message needs: {interval * 1000} ms")
    else:
        print(f"--rate should be a positive number!!!")
        exit(-1)

    next_send_time = time.perf_counter()

    try:
        while True:
            start = time.time()
            if args.all_ones:
                data = np.ones(args.group_size, dtype=np.float32)
            else:
                data = np.random.rand(args.group_size).astype(np.float32)

            socket.send(data.tobytes())  # blocks until all data is sent
            duration = time.time() - start  # data is handled to ZMQ queue but not fully sent out

            remaining = 1.0 - duration
            if remaining > 0:
                print(f"\tSent {data.nbytes / 1e6} MB, ",
                    f"curr_send_rate={max(args.rate, data.nbytes / (duration * 1e6))} MB/s, duration={duration * 1000} ms")
                print(f"\tSleep for {remaining * 1000} ms...\n")
                time.sleep(remaining)
            else:
                print(f"\tSent {data.nbytes / 1e6} MB, curr_send_rate={data.nbytes / (duration * 1e6)} MB/s, duration={duration * 1000} ms")

    except KeyboardInterrupt:
        print("\nTerminating sender.")
    finally:
        socket.close()
        context.term()

if __name__ == "__main__":
    main()
