# emulate_sender-zmq.py
# python emulate_sender-zmq.py --port 5555 --avg-rate-mbps 100 --rms-fraction 0.2 --duty-cycle 0.5 --nic-limit-gbps 100
# This sends data at an average 100 Mbps, with 20% RMS frame size variation, a 50% duty cycle, and assumes a 100 Gbps NIC.

import zmq
import time
import argparse
import random
import numpy as np

def emulate_stream(
    port: int,
    avg_rate_mbps: float,
    rms_fraction: float,
    duty_cycle: float,
    nic_limit_gbps: float
):
    print(f"[emulate_stream:] port = {port}...")
    print(f"[emulate_stream:] avg_rate_mbps = {avg_rate_mbps}...")
    print(f"[emulate_stream:] rms_fraction = {rms_fraction}...")
    print(f"[emulate_stream:] duty_cycle = {duty_cycle}...")
    print(f"[emulate_stream:] nic_limit_gbps = {nic_limit_gbps}...")

    context = zmq.Context()
    zmq_socket = context.socket(zmq.PUB)
    # Send will never block
    # optional: disable high water marksocket.setsockopt(zmq.LINGER, 0)  # don't wait on closesocket.bind("tcp://*:5555")
    #zmq_socket.setsockopt(zmq.SNDHWM, 0) #int(1e2))  # Set send high water mark to 0 messages

    zmq_socket.connect(f"tcp://localhost:{port}")
    time.sleep(1)  # Give receiver time to bind

    # Enable socket monitor for connection events
    #print("[emulate_stream:] Monitoring connection ...")
    #zmq_socket.monitor("inproc://monitor.push", zmq.EVENT_CONNECTED)
    #wait_for_connection(zmq_socket)
    #print("[emulate_stream:] Connected ...")
    
    avg_rate_bps = avg_rate_mbps * 1_000_000
    nic_limit_bps = nic_limit_gbps * 1_000_000_000
    frame_size_mean = 60e3*10 # CLAS12 # avg_rate_bps / 100     # bits - Send in 100 frames per second
    std_dev = frame_size_mean * rms_fraction # bits
    print(f"[emulate_stream:] avg_rate(Gbps) = {avg_rate_bps/1e9}, nic_limit(Gbps) = {nic_limit_bps/1e9}, frame_size_mean(Mb) = {frame_size_mean/1e6}, std_dev(Mb) = {std_dev/1e6}")
    
    cycle_period = 1.0  # seconds
    on_time = 1 # duty_cycle * cycle_period #disable duty cycle for now
    off_time = cycle_period - on_time
    print(f"[emulate_stream:] duty_cycle = {duty_cycle}, cycle_period = {cycle_period}, on_time = {on_time}, off_time = {off_time}")
    num_sent  = 0
    num_sent0 = 0
    # Derived sleep time between messages
    rate_sleep = frame_size_mean / avg_rate_bps  # in seconds
    while True:
        # -----------------------
        # ON phase: Send data
        # -----------------------
        start_on = time.time()
        while time.time() - start_on < on_time:
            # Calculate frame size from normal distribution
            if rms_fraction > 0:
                frame_size = max(1, int(np.random.normal(frame_size_mean, std_dev)))
            else:
                frame_size = int(frame_size_mean)
            buffer = bytearray(int(frame_size/8))
            num_sent = num_sent + 1
            clk = int(time.time()*1e6)  #microseconds *1e9 #nanoseconds
            print(f"{clk} [emulate_stream:] Sending frame; size = {frame_size} frame_num = ({num_sent})")            
            zmq_socket.send(buffer)
                
            # Delay to throttle sending rate
            rate_sleep = frame_size / avg_rate_bps  # in seconds
            time.sleep(rate_sleep)
            
        # Apply duty cycle
        # -----------------------
        # OFF phase: Sleep
        # -----------------------
        if off_time > 0:
            print(f"{int(time.time()*1e6)} [emulate_stream:] Sleeping for {off_time:.3f}s (duty cycle off phase)") #microseconds *1e9 #nanoseconds
            time.sleep(off_time)
        clk = int(time.time()*1e6) #microseconds *1e9 #nanoseconds
        print(f"{clk} [emulate_stream:] Estimated frame rate (Hz): {float(num_sent-num_sent0)/float((clk-int(start_on*1e6))*1e-9)} num_sent {num_sent}")
        print(f"{clk} [emulate_stream:] Estimated bit rate (Gbps): {1e-9*(num_sent-num_sent0)*frame_size_mean/float(clk*1e-9)} num_sent {num_sent}")
        print(f"{clk} [emulate_stream:] Estimated bit rate (MHz): {1e-6*float((num_sent-num_sent0)*frame_size_mean)/float(clk*1e-9)} num_sent {num_sent}")
        #print(f"{clk} [emulate_stream:] Lost Frames: {lost_frames}", flush=True)
        num_sent0 = num_sent

if __name__ == "__main__":
    print(f"[emulate_sender-zmq: main:]")
    parser = argparse.ArgumentParser(description="emulated data sender using ZeroMQ")
    parser.add_argument("--port", type=int, required=True, help="Port to connect to")
    parser.add_argument("--avg-rate-mbps", type=float, required=True, help="Average data rate in Mbps")
    parser.add_argument("--rms-fraction", type=float, default=0.0, help="RMS of frame sizes as fraction of average")
    parser.add_argument("--duty-cycle", type=float, default=1.0, help="Duty cycle (0 to 1)")
    parser.add_argument("--nic-limit-gbps", type=float, default=100.0, help="emulated NIC bandwidth in Gbps")
    args = parser.parse_args()

    print(f"[emulate_sender-zmq: main:] emulate_stream...")
    emulate_stream(
        args.port,
        args.avg_rate_mbps,
        args.rms_fraction,
        args.duty_cycle,
        args.nic_limit_gbps
    )

