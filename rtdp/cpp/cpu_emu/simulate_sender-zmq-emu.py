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
    host: str,
    port: int,
    avg_rate_mbps: float,
    rms_fraction: float,
    duty_cycle: float,
    nic_limit_gbps: float
):
    print(f"[simulate_stream:] host = {host}...")
    print(f"[simulate_stream:] port = {port}...")
    print(f"[simulate_stream:] avg_rate_mbps = {avg_rate_mbps}...")
    print(f"[simulate_stream:] rms_fraction = {rms_fraction}...")
    print(f"[simulate_stream:] duty_cycle = {duty_cycle}...")
    print(f"[simulate_stream:] nic_limit_gbps = {nic_limit_gbps}...")
    context = zmq.Context()
    socket = context.socket(zmq.PUSH)
    socket.connect(f"tcp://{host}:{port}")
    
    avg_rate_bps = avg_rate_mbps * 1_000_000
    nic_limit_bps = nic_limit_gbps * 1_000_000_000
    chunk_size_mean = avg_rate_bps / 100  # Send in 100 chunks per second
    
    sleep_interval = 1 / 100  # 100 messages/sec as baseline
    
    while True:
        # Calculate chunk size from normal distribution
        if rms_fraction > 0:
            std_dev = chunk_size_mean * rms_fraction
            chunk_size = max(1, int(np.random.normal(chunk_size_mean, std_dev)))
        else:
            chunk_size = int(chunk_size_mean)
        
        buffer = serialize_buffer(size=chunk_size, timestamp=0, stream_id=99) #time.time()
        
        # Simulate network interface bottleneck
        send_time = chunk_size * 8 / nic_limit_bps  # seconds to send this chunk at NIC limit
        
        # Apply duty cycle
        on_time = duty_cycle * sleep_interval
        off_time = sleep_interval - on_time
        
        print(f"[simulate_stream:] Sending buffer; size = {chunk_size}, send_time = {send_time}, on_time = {off_time}, off_time = {off_time}")
        socket.send(buffer)
        time.sleep(max(send_time, on_time))  # Sleep to simulate actual NIC delay
        if off_time > 0:
            time.sleep(off_time)


if __name__ == "__main__":
    print(f"[simulate_sender-zmq-emu: main:]")
    parser = argparse.ArgumentParser(description="Simulated data sender using ZeroMQ")
    parser.add_argument("--host", type=str, default="localhost", help="Host to connect to")
    parser.add_argument("--port", type=int, required=True, help="Port to connect to")
    parser.add_argument("--avg-rate-mbps", type=float, required=True, help="Average data rate in Mbps")
    parser.add_argument("--rms-fraction", type=float, default=0.0, help="RMS of chunk sizes as fraction of average")
    parser.add_argument("--duty-cycle", type=float, default=1.0, help="Duty cycle (0 to 1)")
    parser.add_argument("--nic-limit-gbps", type=float, default=100.0, help="Simulated NIC bandwidth in Gbps")
    args = parser.parse_args()

    print(f"[simulate_sender-zmq-emu: main:] simulate_stream...")
    simulate_stream(
        args.host,
        args.port,
        args.avg_rate_mbps,
        args.rms_fraction,
        args.duty_cycle,
        args.nic_limit_gbps
    )

