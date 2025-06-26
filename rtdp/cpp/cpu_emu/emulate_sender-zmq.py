# emulate_sender-zmq.py
# python emulate_sender-zmq.py --port 5555 --avg-rate-mbps 100 --rms-fraction 0.2 --duty-cycle 0.5 --nic-limit-gbps 100
# This sends data at an average 100 Mbps, with 20% RMS frame size variation, a 50% duty cycle, and assumes a 100 Gbps NIC.

import zmq
import time
import argparse
import random
import numpy as np
import sys

from buffer_packet_zmq_emu import serialize_buffer

def emulate_stream(
    port: int,
    avg_rate_mbps: float,
    rms_fraction: float,
    duty_cycle: float,
    frame_cnt:      int #,
    #verbosity:      int
):
    print(f"[emulate_stream:] port = {port}...")
    print(f"[emulate_stream:] avg_rate_mbps = {avg_rate_mbps}...")
    print(f"[emulate_stream:] rms_fraction = {rms_fraction}...")
    print(f"[emulate_stream:] duty_cycle = {duty_cycle}...")
    print(f"[emulate_stream:] frame_cnt = {frame_cnt}")

    context = zmq.Context()
    zmq_socket = context.socket(zmq.PUB)
    # Send will never block
    # optional: disable high water marksocket.setsockopt(zmq.LINGER, 0)  # don't wait on closesocket.bind("tcp://*:5555")
    zmq_socket.setsockopt(zmq.SNDHWM, 0) #int(1e2))  # Set send high water mark to 0 messages

    zmq_socket.connect(f"tcp://localhost:{port}")
    time.sleep(1)  # Give receiver time to bind

    # Enable socket monitor for connection events
    #print("[emulate_stream:] Monitoring connection ...")
    #zmq_socket.monitor("inproc://monitor.push", zmq.EVENT_CONNECTED)
    #wait_for_connection(zmq_socket)
    #print("[emulate_stream:] Connected ...")
    
    avg_rate_bps = avg_rate_mbps * 1_000_000
    frame_size_mean = 60e3*10 # CLAS12 # avg_rate_bps / 100     # bits - Send in 100 frames per second
    std_dev = frame_size_mean * rms_fraction # bits
    print(f"[emulate_stream:] avg_rate(Gbps) = {avg_rate_bps/1e9}, rame_size_mean(Mb) = {frame_size_mean/1e6}, std_dev(Mb) = {std_dev/1e6}")
    
    cycle_period = 1.0  # seconds
    on_time = 1 # duty_cycle * cycle_period #disable duty cycle for now
    off_time = cycle_period - on_time
    print(f"[emulate_stream:] duty_cycle = {duty_cycle}, cycle_period = {cycle_period}, on_time = {on_time}, off_time = {off_time}", flush=True)
    frame_num    = 0
    # Derived sleep time between messages
    rate_sleep = frame_size_mean / avg_rate_bps  # in seconds
    while frame_cnt > frame_num:
        frame_num += 1
        # Calculate frame size from normal distribution
        if rms_fraction > 0:
            frame_size = max(1, int(np.random.normal(frame_size_mean, std_dev)))
        else:
            frame_size = int(frame_size_mean)
        payload = bytearray(int(frame_size/8))
        if frame_num == 1:
            clk0 = int(time.time()*1e6) #microseconds *1e9 #nanoseconds
        clk = int(time.time()*1e6)  #microseconds *1e9 #nanoseconds
        elpsd_tm = (clk-clk0) #usec
        print(f"{elpsd_tm} [emulate_stream:] Sending frame; size = {frame_size} frame_num = ({frame_num})")            
        buffer = serialize_buffer(size=frame_size, timestamp=int(clk), stream_id=99, frame_num=frame_num, payload=payload)
        #print(f"{float(clk)} [emulate_stream:] Sending frame; size = {frame_size} frame_num = ({frame_num})", flush=True)            
        zmq_socket.send(buffer)
                
        # Delay to throttle sending rate
        rate_sleep = frame_size / avg_rate_bps  # in seconds
        time.sleep(rate_sleep)
            
        clk = int(time.time()*1e6) #microseconds *1e9 #nanoseconds
        #frame_rate_hz = frame_count / (elapsed_time_us / 1_000_000)
        elpsd_tm = (clk-clk0) #usec
        print(f"{elpsd_tm} [emulate_stream:] Estimated frame rate (Hz): {1e6*float(frame_num)/float(elpsd_tm)} frame_num {frame_num}")
        print(f"{elpsd_tm} [emulate_stream:] Estimated bit rate (Gbps): {1e3*frame_num*frame_size_mean/float(elpsd_tm)} frame_num {frame_num}")
        print(f"{elpsd_tm} [emulate_stream:] Estimated bit rate (MHz): {float(frame_num)*frame_size_mean/float(elpsd_tm)} frame_num {frame_num}", flush=True)

if __name__ == "__main__":
    print(f"[emulate_sender-zmq: main:]")
    parser = argparse.ArgumentParser(description="emulated data sender using ZeroMQ")
    parser.add_argument("--port", type=int, required=True, help="Port to connect to")
    parser.add_argument("--avg-rate-mbps", type=float, required=True, help="Average data rate in Mbps")
    parser.add_argument("--rms-fraction", type=float, default=0.0, help="RMS of frame sizes as fraction of average")
    parser.add_argument("--duty-cycle", type=float, default=1.0, help="Duty cycle (0 to 1)")
    parser.add_argument("--frame_cnt", type=int, default=1, help="Toal count of frames to send")
    args = parser.parse_args()

    print(f"[emulate_sender-zmq: main:] emulate_stream...")
    emulate_stream(
        args.port,
        args.avg_rate_mbps,
        args.rms_fraction,
        args.duty_cycle,
        args.frame_cnt
    )

