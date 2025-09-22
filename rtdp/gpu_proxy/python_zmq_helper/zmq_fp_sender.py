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
Last update: 2025-04-24
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
    parser.add_argument("--hwm", type=int, default=1000,
                        help="Socket high water mark (default = 1000)")
    parser.add_argument("-v", "--verbose", action="count", default=1,
                        help="Increase verbosity level (e.g., -v, -vv, -vvv)")
   
    args = parser.parse_args()
    
    VERBOSE = args.verbose
    
    context = zmq.Context()
    socket = context.socket(zmq.PUSH)
    socket.setsockopt(zmq.SNDHWM, args.hwm)  # set send high-water mark
    socket.connect(f"tcp://{args.ip_addr}:{args.port}")
    
    print(f"Sending data to {args.ip_addr}:{args.port} {'(all ones)' if args.all_ones else '(random values)'}")
    print(f"Socket HWM: {args.hwm}")

    if args.rate > 0:
        print(f"Target send rate: {args.rate} MB/s\n")

    # Calculate time interval between sends to maintain the desired MB/s rate
    if args.rate > 0:
        interval = (args.group_size * 4.0) / (args.rate * 1e6)
        print(f"Each message needs: {interval * 1000} ms")
    else:
        print(f"--rate should be a positive number!!!")
        exit(-1)

    try:
        while True:
            cycle_start = time.time()
            if args.all_ones:
                data = np.ones(args.group_size, dtype=np.float32)
            else:
                data = np.random.rand(args.group_size).astype(np.float32)

            # Get queue status before send
            try:
                # Check if socket is ready to send
                events = socket.getsockopt(zmq.EVENTS)
                if events & zmq.POLLOUT:
                    queue_status = "ready"
                else:
                    queue_status = "backpressure"
                if VERBOSE>1 : print(f"\tQueue status: {queue_status}")
            except zmq.error.ZMQError:
                print(f"\tQueue status: unknown")

            socket.send(data.tobytes())  # blocks until all data is sent
            send_duration = time.time() - cycle_start  # data is handled to ZMQ queue but not fully sent out

            remaining = interval - send_duration
            if remaining > 0:
                sleep_start = time.time()
                time.sleep(remaining)
                sleep_duration = time.time() - sleep_start
                total_duration = send_duration + sleep_duration
                avg_rate = data.nbytes / (total_duration * 1e6)
                print(f"\tSent {data.nbytes / 1e6} MB, "
                    f"curr_send_rate={avg_rate:.2f} MB/s, "
                    f"send_duration={send_duration * 1000:.2f} ms, "
                    f"sleep_duration={sleep_duration * 1000:.2f} ms, "
                    f"total_duration={total_duration * 1000:.2f} ms")
            else:
                print(f"\tSent {data.nbytes / 1e6} MB, "
                    f"curr_send_rate={data.nbytes / (send_duration * 1e6):.2f} MB/s, "
                    f"send_duration={send_duration * 1000:.2f} ms")

    except KeyboardInterrupt:
        print("\nTerminating sender.")
    finally:
        socket.close()
        context.term()

if __name__ == "__main__":
    main()
