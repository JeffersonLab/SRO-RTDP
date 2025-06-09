# launcher_py_cpu_sim_chain.py
# To Run the Full Simulation

#   python launcher_py_cpu_sim.py --components 5 --base-port 6000 --avg-rate 50 --rms 0.3 --duty 0.7 --nic 100 --frame_cnt 30000
# `date +%s.%N`

import subprocess
import argparse
import time

def launch_component_0(index, port):

    # Launch receiver
    if index > 1:
        print(f"[launcher_py_cpu_sim] Starting cpu_sim #{index} listening on port {ref_port + index - 1}, forwarding to port {ref_port + index - 2}")
        subprocess.Popen([
            "./cpu_sim",
            "-b", str(500),
            "-i", str("127.0.0.1"),
            "-n", str(100),
            "-o", str(0.0001),
            "-p", str(port + 1),
            "-r", str(port),
            "-v", str(1)
        ])
    else:
        print(f"[launcher_py_cpu_sim] Starting cpu_sim #{index} listening on port {ref_port + index - 1}, acting as sink")
        subprocess.Popen([
            "./cpu_sim",
#            "-b", str(500),          # test of cpu_sim.yaml
#            "-i", str("127.0.0.1"),
            "-o", str(0.0001),
            "-r", str(port),
            "-v", str(1),
            "-y", str("cpu_sim.yaml"),
            "-z", str(1)
        ])
        
def launch_component(port, trmnl, frame_cnt):
    # Launch receiver
    print(f"[launch_component] Starting cpu_sim listening on port {port}, term = {trmnl}, forwarding to port {port + 1}, frame_cnt {frame_cnt}")
    subprocess.Popen([
        "./cpu_sim",
        "-b", str(500),
        "-f", str(frame_cnt),
        "-i", str("127.0.0.1"),
        "-n", str(100),
        "-o", str(0.0001),
        "-p", str(port + 1),
        "-r", str(port),
        "-v", str(1),
        "-y", str("cpu_sim.yaml"),
        "-z", str(trmnl)
    ])

def main():
    parser = argparse.ArgumentParser(description="Launcher for simulation components")
    parser.add_argument("--components", type=int, default=50, help="Number of components to simulate")
    parser.add_argument("--base-port", type=int, default=5000, help="Base port number")
    parser.add_argument("--avg-rate", type=float, default=10, help="Average rate in Mbps per component")
    parser.add_argument("--rms", type=float, default=0.1, help="RMS fraction")
    parser.add_argument("--duty", type=float, default=1.0, help="Duty cycle")
    parser.add_argument("--nic", type=float, default=100.0, help="NIC bandwidth in Gbps")
    parser.add_argument("--frame_cnt", type=int, default=1, help="Toal count of frames to send")
    args = parser.parse_args()

    print(f"[launcher_py_cpu_sim] args {args.components}, {args.base_port}, {args.avg_rate}, {args.rms}, {args.duty}, {args.nic}, {args.frame_cnt}")
    #for p in range(args.base_port, args.base_port + args.components):  # interval [args.base_port, args.base_port + args.components]
    for i in range(0, args.components):  # interval [args.base_port, args.base_port + args.components]
        print(f"[launcher_py_cpu_sim: main:] launch_component {i} with ports {args.base_port + i} -> {args.base_port + i + 1}, frame_cnt {args.frame_cnt}")
        print(f"[launcher_py_cpu_sim: main:] i == (args.components-1)  = {i == (args.components-1)}")
        launch_component(args.base_port + i, 1 if i == (args.components-1) else 0, args.frame_cnt)
        
    print(f"[launcher_py_cpu_sim] Starting simulate_sender-zmq.py on port {args.base_port}...")
    subprocess.Popen(["python", "simulate_sender-zmq.py",
        "--port", str(args.base_port),
        "--avg-rate-mbps", str(args.avg_rate),
        "--rms-fraction", str(args.rms),
        "--duty-cycle", str(args.duty),
        "--nic-limit-gbps", str(args.nic),
        "--frame_cnt", str(args.frame_cnt)
    ])

if __name__ == "__main__":
    main()

