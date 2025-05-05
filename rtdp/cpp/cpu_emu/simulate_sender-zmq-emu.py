# simulate_sender-zmq-emu.py
# python simulate_sender-zmq-emu.py --port 5555 --avg-rate-mbps 100 --rms-fraction 0.2 --duty-cycle 0.5 --nic-limit-gbps 100
# This sends data at an average 100 Mbps, with 20% RMS chunk size variation, a 50% duty cycle, and assumes a 100 Gbps NIC.

import zmq
import time
import argparse
import random
import numpy as np
from buffer_packet_zmq_emu import serialize_buffer

def simulate_stream(
    port: int,
    avg_rate_mbps: float,
    rms_fraction: float,
    duty_cycle: float,
    nic_limit_gbps: float
):
    print(f"[simulate_stream:] port = {port}...")
    print(f"[simulate_stream:] avg_rate_mbps = {avg_rate_mbps}...")
    print(f"[simulate_stream:] rms_fraction = {rms_fraction}...")
    print(f"[simulate_stream:] duty_cycle = {duty_cycle}...")
    print(f"[simulate_stream:] nic_limit_gbps = {nic_limit_gbps}...")

    context = zmq.Context()
    zmq_socket = context.socket(zmq.PUSH)
    zmq_socket.connect(f"tcp://localhost:{port}")
    
    avg_rate_bps = avg_rate_mbps * 1_000_000
    nic_limit_bps = nic_limit_gbps * 1_000_000_000
    chunk_size_mean = avg_rate_bps / 100  # Send in 100 chunks per second
    std_dev = chunk_size_mean * rms_fraction
    print(f"[simulate_stream:] avg_rate(Gbps) = {avg_rate_bps/1e9}, nic_limit(Gbps) = {nic_limit_bps/1e9}, chunk_size_mean(Mb) = {chunk_size_mean/1e6}, std_dev(Mb) = {std_dev/1e6}")
    
    cycle_period = 1.0  # seconds
    on_time = duty_cycle * cycle_period
    off_time = cycle_period - on_time
    print(f"[simulate_stream:] duty_cycle = {duty_cycle}, cycle_period = {cycle_period}, on_time = {on_time}, off_time = {off_time}")
    num_sent = 0
    start_time = time.time()
    while True:
        # -----------------------
        # ON phase: Send data
        # -----------------------
        print(f"[simulate_stream:] Sending for {on_time:.3f}s (duty cycle on phase)")
        start_on = time.time()
        print(f"[simulate_stream:] Times: current {time.time()}, time.time() - start_on {time.time() - start_on}, on_time {on_time}")
        while time.time() - start_on < on_time:
            # Calculate chunk size from normal distribution
            if rms_fraction > 0:
                chunk_size = max(1, int(np.random.normal(chunk_size_mean, std_dev)))
            else:
                chunk_size = int(chunk_size_mean)
            buffer = serialize_buffer(size=chunk_size, timestamp=time.time(), stream_id=99) #time.time()
            print(f"[simulate_stream:] Sending buffer; size = {chunk_size}")
            zmq_socket.send(buffer)
            # Simulate transmission delay
            td = chunk_size/nic_limit_bps
            print(f"[simulate_stream:] Simulate transmission delay (usecs) of {1e6*td}")
            time.sleep(td)
            num_sent = num_sent + 1

        print(f"[simulate_stream:] Estimated send rate (Gbps): {1e-9*num_sent*chunk_size_mean/(time.time() - start_time)} num_sent {num_sent}")
        # Apply duty cycle
        # -----------------------
        # OFF phase: Sleep
        # -----------------------
        if off_time > 0:
            print(f"[simulate_stream:] Sleeping for {off_time:.3f}s (duty cycle off phase)")
            time.sleep(off_time)

if __name__ == "__main__":
    print(f"[simulate_sender-zmq-emu: main:]")
    parser = argparse.ArgumentParser(description="Simulated data sender using ZeroMQ")
    parser.add_argument("--port", type=int, required=True, help="Port to connect to")
    parser.add_argument("--avg-rate-mbps", type=float, required=True, help="Average data rate in Mbps")
    parser.add_argument("--rms-fraction", type=float, default=0.0, help="RMS of chunk sizes as fraction of average")
    parser.add_argument("--duty-cycle", type=float, default=1.0, help="Duty cycle (0 to 1)")
    parser.add_argument("--nic-limit-gbps", type=float, default=100.0, help="Simulated NIC bandwidth in Gbps")
    args = parser.parse_args()

    print(f"[simulate_sender-zmq-emu: main:] simulate_stream...")
    simulate_stream(
        args.port,
        args.avg_rate_mbps,
        args.rms_fraction,
        args.duty_cycle,
        args.nic_limit_gbps
    )

