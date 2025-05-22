# simulate_sender-zmq-emu.py
# python simulate_sender-zmq-emu.py --port 5555 --avg-rate-mbps 100 --rms-fraction 0.2 --duty-cycle 0.5 --nic-limit-gbps 100
# This sends data at an average 100 Mbps, with 20% RMS chunk size variation, a 50% duty cycle, and assumes a 100 Gbps NIC.

import zmq
import time
import argparse
import random
import numpy as np
from buffer_packet_zmq_emu import serialize_buffer

def wait_for_connection(socket):
    """Attach monitor and wait until the peer is connected."""
    monitor = socket.get_monitor_socket()
    while True:
        evt = zmq.utils.monitor.parse_monitor_message(monitor)
        if evt['event'] == zmq.EVENT_CONNECTED:
            print("✅ Connected to receiver!")
            break

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
    zmq_socket = context.socket(zmq.REQ)
    # Send will never block
    # optional: disable high water marksocket.setsockopt(zmq.LINGER, 0)  # don't wait on closesocket.bind("tcp://*:5555")
    zmq_socket.setsockopt(zmq.SNDHWM, 0) #int(1e2))  # Set send high water mark to 0 messages

    zmq_socket.connect(f"tcp://localhost:{port}")
    time.sleep(1)  # Give receiver time to bind

    # Enable socket monitor for connection events
    #print("[simulate_stream:] Monitoring connection ...")
    #zmq_socket.monitor("inproc://monitor.push", zmq.EVENT_CONNECTED)
    #wait_for_connection(zmq_socket)
    #print("[simulate_stream:] Connected ...")
    
    avg_rate_bps = avg_rate_mbps * 1_000_000
    nic_limit_bps = nic_limit_gbps * 1_000_000_000
    chunk_size_mean = 60e3*10 # CLAS12 # avg_rate_bps / 100     # bits - Send in 100 chunks per second
    std_dev = chunk_size_mean * rms_fraction # bits
    print(f"[simulate_stream:] avg_rate(Gbps) = {avg_rate_bps/1e9}, nic_limit(Gbps) = {nic_limit_bps/1e9}, chunk_size_mean(Mb) = {chunk_size_mean/1e6}, std_dev(Mb) = {std_dev/1e6}")
    
    cycle_period = 1.0  # seconds
    on_time = 1 # duty_cycle * cycle_period #disable duty cycle for now
    off_time = cycle_period - on_time
    print(f"[simulate_stream:] duty_cycle = {duty_cycle}, cycle_period = {cycle_period}, on_time = {on_time}, off_time = {off_time}")
    num_sent = 0
    lost_chunks = 0
    start_time = time.time()
    # Derived sleep time between messages
    rate_sleep = chunk_size_mean / avg_rate_bps  # in seconds
    smClk = 0xFFFFFFFFFFFFFFFF  #ensure is 64bits
    smClk = 0 #master simulation clock in nanoseconds
    while True:
#        tl0 = time.time()
        # -----------------------
        # ON phase: Send data
        # -----------------------
        start_on = time.time()
        while time.time() - start_on < on_time:
            # Calculate chunk size from normal distribution
            if rms_fraction > 0:
                chunk_size = max(1, int(np.random.normal(chunk_size_mean, std_dev)))
            else:
                chunk_size = int(chunk_size_mean)
            buffer = serialize_buffer(size=chunk_size, timestamp=int(smClk), stream_id=99)
            num_sent = num_sent + 1
            print(f"{smClk} [simulate_stream:] Sending chunk; size = {chunk_size} chunk_num = ({num_sent})")            
            zmq_socket.send(buffer)
            reply = zmq_socket.recv_string() #ACK
                
            # Delay to throttle sending rate
            smClk += int(rate_sleep*1e9)
            
        # Apply duty cycle
        # -----------------------
        # OFF phase: Sleep
        # -----------------------
        if off_time > 0:
            print(f"{smClk} [simulate_stream:] Sleeping for {off_time:.3f}s (duty cycle off phase)")
            time.sleep(off_time)
        print(f"{smClk} [simulate_stream:] Estimated chunk rate (Hz): {float(num_sent)/float(smClk*1e-9)} num_sent {num_sent}")
        print(f"{smClk} [simulate_stream:] Estimated bit rate (Gbps): {1e-9*num_sent*chunk_size_mean/float(smClk*1e-9)} num_sent {num_sent}")
        print(f"{smClk} [simulate_stream:] Estimated bit rate (MHz): {1e-6*float(num_sent*chunk_size_mean)/float(smClk*1e-9)} num_sent {num_sent}")
        print(f"{smClk} [simulate_stream:] Lost Chunks: {lost_chunks}", flush=True)

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

