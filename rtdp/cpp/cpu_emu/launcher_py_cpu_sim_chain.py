# launcher_py_cpu_sim_chain.py
# To Run the Full Simulation

#   python launcher_py_cpu_sim.py --components 5 --base-port 6000 --avg-rate 50 --rms 0.3 --duty 0.7 --nic 100
# `date +%s.%N`

import subprocess
import argparse
import time

def launch_component(index, ref_port):

    # Launch receiver
    if index > 1:
        print(f"[launcher_py_cpu_sim] Starting cpu_sim #{index} listening on port {ref_port + index - 1}, forwarding to port {ref_port + index - 2}")
        subprocess.Popen([
            "./cpu_sim",
            "-b", str(500),
            "-i", str("127.0.0.1"),
            "-n", str(100),
            "-o", str(0.0001),
            "-p", str(ref_port + index - 2),
            "-r", str(ref_port + index - 1),
            "-v", str(1)
        ])
    else:
        print(f"[launcher_py_cpu_sim] Starting cpu_sim #{index} listening on port {ref_port + index - 1}, acting as sink")
        subprocess.Popen([
            "./cpu_sim",
#            "-b", str(500),          # test of cpu_sim.yaml
#            "-i", str("127.0.0.1"),
            "-o", str(0.0001),
            "-r", str(ref_port + index - 1),
            "-v", str(1),
            "-y", str("cpu_sim.yaml"),
            "-z", str(1)
        ])

#    time.sleep(0.05)  # Slight delay to avoid race conditions


def main():
    parser = argparse.ArgumentParser(description="Launcher for simulation components")
    parser.add_argument("--components", type=int, default=50, help="Number of components to simulate")
    parser.add_argument("--base-port", type=int, default=5000, help="Base port number")
    parser.add_argument("--avg-rate", type=float, default=10, help="Average rate in Mbps per component")
    parser.add_argument("--rms", type=float, default=0.1, help="RMS fraction")
    parser.add_argument("--duty", type=float, default=1.0, help="Duty cycle")
    parser.add_argument("--nic", type=float, default=100.0, help="NIC bandwidth in Gbps")
    args = parser.parse_args()

    print(f"[launcher_py_cpu_sim] Starting simulate_sender-zmq.py with args {args.components}, {args.base_port}, {args.avg_rate}, {args.rms}, {args.duty}, {args.nic}")

    for i in range(args.components, 0, -1):
        print(f"[launcher_py_cpu_sim: main:] launch_component #{i} with ref_port = {args.base_port + args.components - 1}")
        launch_component(i, args.base_port)
        
    print(f"[launcher_py_cpu_sim] Starting simulate_sender-zmq.py on port {args.base_port + args.components - 1}...")
    subprocess.Popen(["python", "simulate_sender-zmq.py",
        "--port", str(args.base_port + args.components - 1),
        "--avg-rate-mbps", str(args.avg_rate),
        "--rms-fraction", str(args.rms),
        "--duty-cycle", str(args.duty),
        "--nic-limit-gbps", str(args.nic)
    ])

if __name__ == "__main__":
    main()

