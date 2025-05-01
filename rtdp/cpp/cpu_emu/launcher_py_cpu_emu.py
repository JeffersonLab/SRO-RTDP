# launcher_py_cpu_emu.py
# To Run the Full Simulation

#   python launcher_py_cpu_emu.py --components 5 --base-port 55555 --avg-rate 50 --rms 0.3 --duty 0.7 --nic 100

import subprocess
import argparse
import time

def launch_component(index, base_port, avg_rate, rms, duty, nic):
    recv_port = base_port + index
    send_port = recv_port + 1 if index < (5-1) else None  # Last one doesn't send

    # Launch receiver
    print(f"[launcher_py_cpu_emu] Starting cpu_emu #{index} on port {recv_port}...")
    subprocess.Popen([
        "./cpu_emu",
        "-i", str("127.0.0.1"),
        "-r", str(recv_port),
        "-s",
        "-x",
        "-z",
        "-v", str(1),
        "-m", str(1),        
        "-t", str(1)        
    ])
    time.sleep(0.05)  # Slight delay to avoid race conditions

    print(f"[launcher_py_cpu_emu] Starting simulate_sender-zmq-emu.py #{index} on port {recv_port}...")
    subprocess.Popen(["python", "simulate_sender-zmq-emu.py",
        "--port", str(recv_port),
        "--avg-rate-mbps", str(avg_rate),
        "--rms-fraction", str(rms),
        "--duty-cycle", str(duty),
        "--nic-limit-gbps", str(nic)
    ])

def main():
    parser = argparse.ArgumentParser(description="Launcher for simulation components")
    parser.add_argument("--components", type=int, default=50, help="Number of components to simulate")
    parser.add_argument("--base-port", type=int, default=55555, help="Base port number")
    parser.add_argument("--avg-rate", type=float, default=10, help="Average rate in Mbps per component")
    parser.add_argument("--rms", type=float, default=0.1, help="RMS fraction")
    parser.add_argument("--duty", type=float, default=1.0, help="Duty cycle")
    parser.add_argument("--nic", type=float, default=100.0, help="NIC bandwidth in Gbps")
    args = parser.parse_args()

    for i in range(args.components):
        print(f"[launcher_py_cpu_emu: main:] launch_component #{i}...")
        launch_component(i, args.base_port, args.avg_rate, args.rms, args.duty, args.nic)

if __name__ == "__main__":
    main()

