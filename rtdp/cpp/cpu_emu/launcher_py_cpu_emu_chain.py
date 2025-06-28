# launcher_py_cpu_emu_chain.py
# To Run the Full Emulation

#   python launcher_py_cpu_emu.py --components 5 --base-port 6000 --avg-rate 50 --rms 0.3 --duty 0.7 --frame_cnt 30000
# `date +%s.%N`

import subprocess
import argparse
import time

def launch_component(port, trmnl, frame_cnt):
    # Launch receiver
    print(f"[launch_component] Starting cpu_emu listening on port {port}, term = {trmnl}, forwarding to port {port + 1}, frame_cnt {frame_cnt}")
    subprocess.Popen([
        "./cpu_emu",
        "-b", str(2500),
        "-f", str(frame_cnt),
        "-i", str("127.0.0.1"),
        "-m", str(1),  
        "-o", str(0.000075),
        "-p", str(port + 1),
        "-r", str(port),
        "-s", str(1),  
        "-t", str(1),  
        "-v", str(1),
        "-y", str("cpu_emu.yaml"),
        "-z", str(trmnl)
        ])

def main():
    parser = argparse.ArgumentParser(description="Launcher for emulation components")
    parser.add_argument("--components", type=int, default=50, help="Number of components to emulate")
    parser.add_argument("--base-port", type=int, default=5000, help="Base port number")
    parser.add_argument("--avg-rate", type=float, default=10, help="Average rate in Mbps per component")
    parser.add_argument("--rms", type=float, default=0.1, help="RMS fraction")
    parser.add_argument("--duty", type=float, default=1.0, help="Duty cycle")
    parser.add_argument("--frame_cnt", type=int, default=1, help="Toal count of frames to send")
    args = parser.parse_args()

    print(f"[launcher_py_cpu_emu] args {args.components}, {args.base_port}, {args.avg_rate}, {args.rms}, {args.duty}, {args.frame_cnt}")
    #for p in range(args.base_port, args.base_port + args.components):  # interval [args.base_port, args.base_port + args.components]
    for i in range(0, args.components):  # interval [args.base_port, args.base_port + args.components]
        print(f"[launcher_py_cpu_emu: main:] launch_component {i} with ports {args.base_port + i} -> {args.base_port + i + 1}, frame_cnt {args.frame_cnt}")
        print(f"[launcher_py_cpu_emu: main:] i == (args.components-1)  = {i == (args.components-1)}")
        launch_component(args.base_port + i, 1 if i == (args.components-1) else 0, args.frame_cnt)

    print(f"[launcher_py_cpu_emu] Starting emulate_sender-zmq.py on port {args.base_port}...")
    subprocess.Popen(["python", "emulate_sender-zmq.py",
        "--port", str(args.base_port),
        "--avg-rate-mbps", str(args.avg_rate),
        "--rms-fraction", str(args.rms),
        "--duty-cycle", str(args.duty),
        "--frame_cnt", str(args.frame_cnt)
    ])

if __name__ == "__main__":
    main()

