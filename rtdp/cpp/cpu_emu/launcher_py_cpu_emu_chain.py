# launcher_py_cpu_emu_chain.py
# To Run the Full Simulation

#   python launcher_py_cpu_emu.py --components 5 --base-port 6000 --avg-rate 50 --rms 0.3 --duty 0.7 --nic 100

import subprocess
import argparse
import time

def launch_component(index, ref_port):

    # Launch receiver
    if index > 1:
        print(f"[launcher_py_cpu_emu] Starting cpu_emu #{index} listening on port {ref_port + index}, forwarding to port {ref_port + index - 1}")
        subprocess.Popen([
            "./cpu_emu",
            "-i", str("127.0.0.1"),
            "-r", str(ref_port + index),
            "-p", str(ref_port + index - 1),
            "-s",
            "-x",
            "-v", str(1),
            "-m", str(1),        
            "-t", str(1)        
        ])
    else:
        print(f"[launcher_py_cpu_emu] Starting cpu_emu #{index} listening on port {ref_port + index}")
        subprocess.Popen([
            "./cpu_emu",
            "-i", str("127.0.0.1"),
            "-r", str(ref_port + index),
            "-s",
            "-x",
            "-z",
            "-v", str(1),
            "-m", str(1),        
            "-t", str(1)        
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

    for i in range(args.components, 0, -1):
        print(f"[launcher_py_cpu_emu: main:] launch_component #{i} with ref_port = {args.base_port + args.components}")
        launch_component(i, args.base_port)
        
    print(f"[launcher_py_cpu_emu] Starting simulate_sender-zmq-emu.py on port {args.base_port + args.components}...")
    subprocess.Popen(["python", "simulate_sender-zmq-emu.py",
        "--port", str(args.base_port + args.components),
        "--avg-rate-mbps", str(args.avg_rate),
        "--rms-fraction", str(args.rms),
        "--duty-cycle", str(args.duty),
        "--nic-limit-gbps", str(args.nic)
    ])

if __name__ == "__main__":
    main()

