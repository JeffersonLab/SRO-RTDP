# emulate_sender-zmq.py
# python emulate_sender-zmq.py --port 5555 --avg-rate-mbps 100 --rms-fraction 0.2 --duty-cycle 0.5 --nic-limit-gbps 100
# This sends data at an average 100 Mbps, with 20% RMS frame size variation, a 50% duty cycle, and assumes a 100 Gbps NIC.

import zmq
import time
import argparse
import random
import numpy as np
import sys
import struct
import math

from buffer_packet_zmq_emu import serialize_buffer, HEADER_FORMAT, HEADER_SIZE

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
    ######zmq_socket.bind("tcp://129.57.177.4:6333")
    #zmq_socket.bind(f"tcp://localhost:{port}")  #fails
    #zmq_socket.bind(f"tcp://127.0.0.1:{port}")  #fails
    zmq_socket.bind(f"tcp://*:{port}")
    # Send will never block
    # optional: disable high water marksocket.setsockopt(zmq.LINGER, 0)  # don't wait on closesocket.bind("tcp://*:5555")
    zmq_socket.setsockopt(zmq.SNDHWM, 0) #int(1e2))  # Set send high water mark to 0 messages

    #time.sleep(1)  # Give receiver time to bind

    # Enable socket monitor for connection events
    #print("[emulate_stream:] Monitoring connection ...")
    #zmq_socket.monitor("inproc://monitor.push", zmq.EVENT_CONNECTED)
    #wait_for_connection(zmq_socket)
    #print("[emulate_stream:] Connected ...")
    
    avg_rate_bps = avg_rate_mbps * 1_000_000
    frame_size_mean = 60e3*10 # CLAS12 bits
    std_dev = 0.1 # 10 percent
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
        # Calculate frame size from clamped normal distribution
        frame_size_fctr = np.random.normal(1.0, std_dev)
        frame_size_fctr = max(0.7, frame_size_fctr)
        frame_size_fctr = min(1.3, frame_size_fctr)
        #frame_size_fctr = math.clamp(frame_size_fctr, 0.7, 1.3)        
        frame_size = int(frame_size_mean*frame_size_fctr)
        payload = bytearray(int(frame_size/8))
        clk = int(time.time()*1e6)  #microseconds *1e9 #nanoseconds
        if frame_num == 1:  clk0 = clk #establish zero offset clock
        elpsd_tm = (clk-clk0) #usec
        print(f"{elpsd_tm} [emulate_stream:] Sending frame; size = {frame_size} frame_num = ({frame_num})")            
        print(f"{elpsd_tm} [emulate_stream:] serialize_packet: Serializing frame: size = {frame_size} timestamp = {elpsd_tm} stream_id = {99} frame_num = ({frame_num}) ...")            
        buffer = serialize_buffer(size=frame_size, timestamp=clk, stream_id=99, frame_num=frame_num, payload=payload)
        
        # Extract and unpack the header part
        header = buffer[:HEADER_SIZE]
        fields = struct.unpack(HEADER_FORMAT, header)

        print(f" {elpsd_tm} [emulate_stream:] serialized_packet fields:")
        print(f"  size      : {fields[0]/8}")
        print(f"  timestamp : {fields[1]}")
        print(f"  stream_id : {fields[2]}")
        print(f"  frame_num : {fields[3]}")

        #print(f"{float(clk)} [emulate_stream:] Sending frame; size = {frame_size} frame_num = ({frame_num})", flush=True)            
        zmq_socket.send(buffer)
                
        rate_sleep = frame_size / avg_rate_bps  # in seconds

        if frame_num > 10:
            print(f"{elpsd_tm+1} [emulate_stream:] Estimated frame rate (Hz): {float(frame_num)/float((1e-6*elpsd_tm)+rate_sleep)} frame_num {frame_num} elpsd_tm sec {1e-6*elpsd_tm}")
            print(f"{elpsd_tm+2} [emulate_stream:] Estimated bit rate (Gbps): {1e-6*frame_num*frame_size_mean/float((1e-6*elpsd_tm)+rate_sleep)} frame_num {frame_num} elpsd_tm sec {1e-6*elpsd_tm}")

        # Delay to throttle sending rate
        time.sleep(rate_sleep)
            
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

