#!/usr/bin/env python3
"""
ZMQ Floating Point Data Receiver

- Mode 1: SUB (connect to host:port, subscribe to all)
- Mode 2: PULL (bind to *:port)

Prints data rate every 2 seconds (always prints, even with no data).
If --verbose, prints the first 40 bytes as 10 float32s.

Usage:
    # SUB (default), connect to 127.0.0.1:8888
    python zmq_fp_receiver.py -m 1 -p 8888

    # PULL, bind to *:8888
    python zmq_fp_receiver.py -m 2 -p 8888

TODO/BUGS:
- Now this SUB sink seems short-lived that it gave up receiving if no data for a while.
    This is true for C++ PUB/SUB too, but Python is even more sensitive.
"""

import zmq
import time
import threading
import numpy as np
import argparse

total_recv_bytes = 0
stop_flag = False
lock = threading.Lock()

def now_utc_hms_ms():
    """Return current UTC time as HH:MM:SS.mmm UTC."""
    t = time.time()
    s = time.strftime("%Y-%m-%d %H:%M:%S", time.gmtime(t))
    ms = int((t - int(t)) * 1000)
    return f"[{s}.{ms:03d} UTC]"

def rate_logger():
    """Print the receive rate every ~2 seconds."""
    global total_recv_bytes, stop_flag
    prev_bytes = 0
    prev_ts = time.monotonic()
    while not stop_flag:
        time.sleep(2)
        ts = time.monotonic()
        with lock:
            delta = total_recv_bytes - prev_bytes
            prev_bytes = total_recv_bytes
        elapsed = max(ts - prev_ts, 1e-9)
        prev_ts = ts

        if delta == 0:
            continue
        gbps = 8.0 * delta / (elapsed * 1e9)
        mbs = delta / (elapsed * 1e6)
        print(f"{now_utc_hms_ms()}  Incoming: [{gbps:.3f} Gbps] ({mbs:.3f} MB/s)  total={total_recv_bytes/1.0e6} MB")

def floats_preview_10(buf):
    """Return first 10 float32s (from first 40 bytes) if available, else None."""
    if len(buf) < 40:
        return None
    return np.frombuffer(memoryview(buf)[:40], dtype=np.float32, count=10)

def main():
    global total_recv_bytes, stop_flag

    parser = argparse.ArgumentParser(description="ZMQ Floating Point Data Receiver")
    parser.add_argument("-m", "--mode", type=int, choices=[1, 2], default=1,
                        help="1=SUB (default), 2=PULL")
    parser.add_argument("-p", "--port", type=int, default=55556,
                        help="Port number (default: 55556)")
    parser.add_argument("--host", default="127.0.0.1",
                        help="Host to connect to (used by SUB). Default: 127.0.0.1")
    parser.add_argument("-v", "--verbose", action="store_true",
                        help="Print first 10 floats (first 40 bytes) per message")
    args = parser.parse_args()

    endpoint_bind = f"tcp://*:{args.port}"
    endpoint_connect = f"tcp://{args.host}:{args.port}"

    context = zmq.Context()
    if args.mode == 1:
        socket = context.socket(zmq.SUB)
        socket.setsockopt_string(zmq.SUBSCRIBE, "")  # subscribe to all (no topic arg)
        socket.connect(endpoint_connect)
        how, endpoint = "connect", endpoint_connect
    else:
        socket = context.socket(zmq.PULL)
        socket.bind(endpoint_bind)
        how, endpoint = "bind", endpoint_bind

    mode_str = "SUB" if args.mode == 1 else "PULL"
    print(f"[ZMQ FP Receiver] mode={mode_str}  {how}={endpoint}")
    print("  Printing data rate every 2 seconds. Ctrl+C to stop.\n")

    # Start the periodic rate logger (prints even when idle)
    thread = threading.Thread(target=rate_logger, daemon=True)
    thread.start()

    try:
        while True:
            try:
                if args.mode == 1:
                    frames = socket.recv_multipart(flags=0)
                    payload = frames[-1]
                else:
                    payload = socket.recv(flags=0)
            except zmq.Again:
                # timeout: no data yet (or reconnect in progress) — just loop
                continue

            with lock:
                total_recv_bytes += len(payload)

            if args.verbose:
                preview = floats_preview_10(payload)
                if preview is not None:
                    np.set_printoptions(precision=6, suppress=True, linewidth=120)
                    print(f"First 10 floats: {preview}")
                else:
                    print("First 10 floats: <payload < 40 bytes>")

    except KeyboardInterrupt:
        print("\nTerminating receiver.")
        stop_flag = True
        thread.join()
    finally:
        socket.close(linger=0)
        context.term()

if __name__ == "__main__":
    main()
