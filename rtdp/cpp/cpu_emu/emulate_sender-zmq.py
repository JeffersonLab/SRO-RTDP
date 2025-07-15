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
    port:          int,
    avg_rate_mbps: float,
    rms_fraction:  float,
    duty_cycle:    float,
    frame_cnt:     int #,
    #verbosity:      int
):

    oneToU = 1e6
    uToOne = 1/oneToU
    KtoOne = 1e3
    MtoOne = 1e6
    GtoOne = 1e9
    oneToK = 1/KtoOne
    oneToM = 1/MtoOne
    oneToG = 1/GtoOne
    GtoK   = 1e6
    KtoG   = 1/GtoK
    GtoM   = 1e3
    MtoG   = 1/GtoM
    btoB   = 1e-1
    Btob   = 1/btoB
    
    context    = zmq.Context()
    zmq_socket = context.socket(zmq.PUB)
    zmq_socket.bind(f"tcp://*:{port}")
    # Send will never block
    # optional: disable high water marksocket.setsockopt(zmq.LINGER, 0)  # don't wait on closesocket.bind("tcp://*:5555")
    zmq_socket.setsockopt(zmq.SNDHWM, 0) #int(1e2))  # Set send high water mark to 0 messages

    time.sleep(1)  # Give receiver time to bind

    
    avg_rate_bps      = avg_rate_mbps * MtoOne
    frame_size_mean_B = 60*KtoOne # CLAS12 bytes
    std_dev           = 1e-1 #0.1 # 10 percent
    #print(f"[emulate_stream:] avg_rate(Gbps) = {avg_rate_mbps*MtoG}, frame_size_mean(MB) = {frame_size_mean_B*oneToM}, std_dev(MB) = {std_dev*frame_size_mean_B*oneToM}")
    
    frame_num = 0
    # Derived sleep time between messages
    #rate_sleep_S = frame_size_mean_B*Btob / avg_rate_bps  # in seconds
    clk0_S = clk_S = time.time()                    #seconds since epoch
    clk_uS = int(clk_S*MtoOne)
    while frame_cnt > frame_num:
        frame_num += 1
        # Calculate frame size from clamped normal distribution
        frame_size_fctr = np.random.normal(1.0, std_dev)
        frame_size_fctr = max(0.7, frame_size_fctr)
        frame_size_fctr = min(1.3, frame_size_fctr)
        #frame_size_fctr = math.clamp(frame_size_fctr, 0.7, 1.3)        
        frame_size_B = int(frame_size_mean_B*frame_size_fctr)
        payload = bytearray(int(frame_size_B))
        print(f"{clk_uS} [emulate_stream:] Sending frame; size = {frame_size_B} frame_num = ({frame_num})")            
        print(f"{clk_uS} [emulate_stream:] serialize_packet: Serializing frame: size = {frame_size_B} timestamp = {int(clk_S*MtoOne)} stream_id = {99} frame_num = ({frame_num}) ...")            
        buffer = serialize_buffer(size=frame_size_B, timestamp=int(clk_S*MtoOne), stream_id=99, frame_num=frame_num, payload=payload)
        
        # Extract and unpack the header part
        header = buffer[:HEADER_SIZE]
        fields = struct.unpack(HEADER_FORMAT, header)

        zmq_socket.send(buffer)
                
        rate_sleep_S = frame_size_B * Btob / avg_rate_bps  # seconds
        # Delay to throttle sending rate
        print(f"{clk_uS+3} [emulate_stream:] Rate Sleeping for: {rate_sleep_S} seconds")
        time.sleep(rate_sleep_S)
        clk_S = time.time()   #seconds since epoch
        clk_uS = int(clk_S*MtoOne)
        elpsd_tm_us = int((clk_S-clk0_S)*MtoOne) #usec
        print(f"{clk_uS+3} [emulate_stream:] Read Raw clock as: {clk_S}")

        print(f"{clk_uS+1} [emulate_stream:] Estimated frame rate (Hz): {float(frame_num)/float(elpsd_tm_us*oneToM)} frame_num {frame_num} elpsd_tm_us {elpsd_tm_us}")
        print(f"{clk_uS+2} [emulate_stream:] Estimated bit rate (Gbps): {frame_num*frame_size_mean_B*Btob*oneToG/float(elpsd_tm_us*oneToM)} frame_num {frame_num} elpsd_tm_us {elpsd_tm_us}")
        print(f"{clk_uS+2} [emulate_stream:] Estimated bit rate (bps): {frame_size_mean_B*Btob/rate_sleep_S} frame_num {frame_num} elpsd_tm_us {elpsd_tm_us}")

            
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
    print(f"[emulate_sender-zmq:] port          = {args.port}...")
    print(f"[emulate_sender-zmq:] avg_rate_mbps = {args.avg_rate_mbps}...")
    print(f"[emulate_sender-zmq:] rms_fraction  = {args.rms_fraction}...")
    print(f"[emulate_sender-zmq:] duty_cycle    = {args.duty_cycle}...")
    print(f"[emulate_sender-zmq:] frame_cnt     = {args.frame_cnt}")
    emulate_stream(
        args.port,
        args.avg_rate_mbps,
        args.rms_fraction,
        args.duty_cycle,
        args.frame_cnt
    )

