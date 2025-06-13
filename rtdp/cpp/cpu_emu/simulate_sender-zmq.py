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

def flight_time_microseconds(frame_bits, wire_speed_gbps):
    # Convert wire speed to bits per second
    wire_speed_bps = wire_speed_gbps * 1e9
    # Time in seconds = bits / bits per second
    time_seconds = frame_bits / wire_speed_bps
    # Convert seconds to microseconds
    time_microseconds = time_seconds * 1e6
    #print(f"flight_time_microseconds[flight_time_microseconds:] {frame_bits}, {wire_speed_gbps} -> {time_microseconds}", flush=True)
    return time_microseconds

# Example usage
#frame_bits = 60_000  # 60 kilobits
#wire_speed_gbps = 100  # 100 Gbps

#time_us = flight_time_microseconds(frame_bits, wire_speed_gbps)
#print(f"Flight time: {time_us:.3f} microseconds")

def simulate_stream(
    port:           int,
    avg_rate_mbps:  float,
    rms_fraction:   float,
    duty_cycle:     float,
    nic_limit_gbps: float,
    frame_cnt:      int #,
    #verbosity:      int
):
    print(f"[simulate_stream:] port = {port}...")
    print(f"[simulate_stream:] avg_rate_mbps = {avg_rate_mbps}...")
    print(f"[simulate_stream:] rms_fraction = {rms_fraction}...")
    print(f"[simulate_stream:] duty_cycle = {duty_cycle}...")
    print(f"[simulate_stream:] nic_limit_gbps = {nic_limit_gbps}...")
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
    
    avg_rate_bps = avg_rate_mbps * 1e6
    nic_limit_bps = nic_limit_gbps * 1e9
    frame_size_mean = 60e3*10 # CLAS12 # bits
    std_dev = frame_size_mean * rms_fraction # bits
    print(f"[simulate_stream:] avg_rate(Gbps) = {avg_rate_bps/1e9}, nic_limit(Gbps) = {nic_limit_bps/1e9}, frame_size_mean(Mb) = {frame_size_mean/1e6}, std_dev(Mb) = {std_dev/1e6}")
    
    cycle_period = 1.0  # seconds
    on_time      = 1 # duty_cycle * cycle_period #disable duty cycle for now
    off_time     = cycle_period - on_time
    print(f"[simulate_stream:] duty_cycle = {duty_cycle}, cycle_period = {cycle_period}, on_time = {on_time}, off_time = {off_time}", flush=True)
    frame_num    = 1
    # Derived sleep time between messages
    rate_sleep = frame_size_mean / avg_rate_bps  # in seconds
    smClk = float(0) #master simulation clock in usec
    while True:
        # Calculate frame size from normal distribution
        if rms_fraction > 0:
            frame_size = max(1, int(np.random.normal(frame_size_mean, std_dev)))
        else:
            frame_size = int(frame_size_mean)
        buffer = serialize_buffer(size=frame_size, timestamp=int(smClk), stream_id=99, frame_num=frame_num)
        print(f"{float(smClk)} [simulate_stream:] Sending frame; size = {frame_size} frame_num = ({frame_num})", flush=True)            
        zmq_socket.send(buffer)
        reply = zmq_socket.recv_string() #ACK
        #1e6*float(frame_size)/nic_limit_bps)
        ft = flight_time_microseconds(frame_size, nic_limit_gbps)
        print(f"{float(smClk + 2*ft)} [simulate_stream:] Recvd ACK: frame_num = ({frame_num}), 2*ft = {2*ft}", flush=True)
                                
        if frame_num == frame_cnt:
            buffer = serialize_buffer(size=0, timestamp=int(smClk), stream_id=99, frame_num=0) # signal all components to terminate
            print(f"{float(smClk + 0.1 + ft)} [simulate_stream:] Sending frame; size = {frame_size} frame_num = ({0}) for termination", flush=True)            
            zmq_socket.send(buffer)
            reply = zmq_socket.recv_string() #ACK
            sys.exit(0)
        elif frame_num > 1:
            print(f"{float(smClk+ 2 + ft)} [simulate_stream:] Estimated frame rate (Hz): {float(frame_num)/float(smClk*1e-6)} frame_num {frame_num}", flush=True)
            print(f"{float(smClk+ 3 + ft)} [simulate_stream:] Estimated bit rate (Gbps): {1e-9*frame_num*frame_size_mean/float(smClk*1e-6)} frame_num {frame_num}", flush=True)
            print(f"{float(smClk+ 4 + ft)} [simulate_stream:] Estimated bit rate (MHz): {1e-6*float(frame_num*frame_size_mean)/float(smClk*1e-6)} frame_num {frame_num}", flush=True)
        # Delay to throttle sending rate
        rate_sleep = frame_size / avg_rate_bps  # in seconds
        smClk += int(rate_sleep*1e6) #usec
        print(f"{float(smClk)} [simulate_stream:] Added rate latency = {int(rate_sleep*1e6)}: frame_num = ({frame_num})", flush=True)
        frame_num += 1
        smClk += 10 #usec


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

