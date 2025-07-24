# simulate_sender-zmq.py
# python simulate_sender-zmq.py --port 5555 --avg-rate-mbps 100 --rms-fraction 0.2 --duty-cycle 0.5 --nic-limit-gbps 100
# This sends data at an average 100 Mbps, with 20% RMS frame size variation, a 50% duty cycle, and assumes a 100 Gbps NIC.

import zmq
import time
import argparse
import random
import numpy as np
import sys

from buffer_packet_zmq_sim import serialize_buffer

#Power of ten scaling constants
B_b   = 1e1
b_B   = 1/B_b
G_1   = 1e9
one_G = 1/G_1
G_K   = 1e6
K_G   = 1/G_K
G_M   = 1e3
M_G   = 1/G_M
K_1   = 1e3
one_K = 1/K_1
M_1   = 1e6
one_M = 1/M_1
m_1   = 1e-3
one_m = 1/m_1
m_u   = 1e3 
u_m   = 1/m_u
u_1   = 1e-6
one_u = 1/u_1
n_1   = 1e-9
one_n = 1/n_1
n_m   = 1e-6
m_n   = 1/n_m

sz1K  = 1024
sz1M  = sz1K*sz1K
sz1G  = sz1M*sz1K

def flight_time_microseconds(frameSz_b, wire_speed_Gb_S):
    # Convert wire speed to bits per second
    wire_speed_b_S = wire_speed_Gb_S * G_1
    # Time in seconds = bits / bits per second
    time_S = frameSz_b / wire_speed_b_S
    # Convert seconds to microseconds
    time_uS = time_S * one_u
    #print(f"flight_time_microseconds[flight_time_microseconds:] {frameSz_b}, {wire_speed_Gb_S} -> {time_uS}", flush=True)
    return time_uS


def simulate_stream(
    port:           int,
    avg_rate_Mb_S:  float,
    rms_fraction:   float,
    duty_cycle:     float,
    nic_limit_Gb_S: float,
    frame_cnt:      int #,
    #verbosity:      int
):
    print(f"[simulate_stream:] port = {port}...")
    print(f"[simulate_stream:] avg_rate_Mb_S = {avg_rate_Mb_S}...")
    print(f"[simulate_stream:] rms_fraction = {rms_fraction}...")
    print(f"[simulate_stream:] duty_cycle = {duty_cycle}...")
    print(f"[simulate_stream:] nic_limit_Gb_S = {nic_limit_Gb_S}...")
    print(f"[simulate_stream:] frame_cnt = {frame_cnt}")

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
    
    avg_rate_b_S = avg_rate_Mb_S * M_1
    nic_limit_b_S = nic_limit_Gb_S * G_1
    frameSzMn_b = 60e3*10 # CLAS12 # bits
    stdDev_b = frameSzMn_b * rms_fraction # bits
    print(f"[simulate_stream:] avg_rate(Gbps) = {avg_rate_b_S*one_G}, nic_limit(Gbps) = {nic_limit_b_S*one_G}, frameSzMn_b(Mb) = {frameSzMn_b*one_M}, stdDev_b(Mb) = {stdDev_b*one_M}")
    
    cyclPrd_S = 1.0  # seconds
    onTm_S      = 1 # duty_cycle * cyclPrd_S #disable duty cycle for now
    offTm_S     = cyclPrd_S - onTm_S
    print(f"[simulate_stream:] duty_cycle = {duty_cycle}, cyclPrd_S = {cyclPrd_S}, onTm_S = {onTm_S}, offTm_S = {offTm_S}", flush=True)
    frame_num    = 1
    # Derived sleep time between messages
    rtSlp_S = frameSzMn_b / avg_rate_b_S  # in seconds
    smClk_uS = float(0) #master simulation clock in usec
    while frame_cnt >= frame_num:
        # Calculate frame size from normal distribution
        if rms_fraction > 0:
            frmSz_b = max(1, int(np.random.normal(frameSzMn_b, stdDev_b)))
        else:
            frmSz_b = int(frameSzMn_b)
        buffer = serialize_buffer(size=frmSz_b, timestamp=int(smClk_uS), stream_id=99, frame_num=frame_num)
        print(f"{float(smClk_uS)} [simulate_stream:] Sending frame; size = {frmSz_b} frame_num = ({frame_num})", flush=True)            
        zmq_socket.send(buffer)
        reply = zmq_socket.recv_string() #ACK
        ft_uS = flight_time_microseconds(frmSz_b, nic_limit_Gb_S)
        print(f"{float(smClk_uS + 2*ft_uS)} [simulate_stream:] Recvd ACK: frame_num = ({frame_num}), 2*ft_uS = {2*ft_uS}", flush=True)
                                
        # Delay to throttle sending rate
        rtSlp_S = frmSz_b / avg_rate_b_S  # in seconds
        smClk_uS += int(rtSlp_S*one_u) #usec
        print(f"{float(smClk_uS)} [simulate_stream:] Added rate latency uS = {int(rtSlp_S*one_u)}: frame_num = ({frame_num})", flush=True)
        
        print(f"{float(smClk_uS+ 2 + ft_uS)} [simulate_stream:] Estimated frame rate (Hz): {float(frame_num)/float(smClk_uS*u_1)} frame_num {frame_num}", flush=True)
        print(f"{float(smClk_uS+ 3 + ft_uS)} [simulate_stream:] Estimated bit rate (Gbps): {one_G*frame_num*frameSzMn_b/float(smClk_uS*u_1)} frame_num {frame_num}", flush=True)
        print(f"{float(smClk_uS+ 4 + ft_uS)} [simulate_stream:] Estimated bit rate (MHz): {one_M*float(frame_num*frameSzMn_b)/float(smClk_uS*u_1)} frame_num {frame_num}", flush=True)
        frame_num += 1


if __name__ == "__main__":
    print(f"[simulate_sender-zmq: main:]")
    parser = argparse.ArgumentParser(description="Simulated data sender using ZeroMQ")
    parser.add_argument("--port", type=int, required=True, help="Port to connect to")
    parser.add_argument("--avg-rate-mbps", type=float, required=True, help="Average data rate in Mbps")
    parser.add_argument("--rms-fraction", type=float, default=0.0, help="RMS of frame sizes as fraction of average")
    parser.add_argument("--duty-cycle", type=float, default=1.0, help="Duty cycle (0 to 1)")
    parser.add_argument("--nic-limit-gbps", type=float, default=100.0, help="Simulated NIC bandwidth in Gbps")
    parser.add_argument("--frame_cnt", type=int, default=1, help="Toal count of frames to send")
    args = parser.parse_args()

    print(f"[simulate_sender-zmq: main:] simulate_stream...", flush=True)
    simulate_stream(
        args.port,
        args.avg_rate_mbps,
        args.rms_fraction,
        args.duty_cycle,
        args.nic_limit_gbps,
        args.frame_cnt
    )

