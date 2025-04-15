# GPU Proxy Container

This container provides a complete solution for GPU-accelerated matrix multiplication operations using CUDA and ZeroMQ for communication. It includes three main components:
- GPU Proxy: The main CUDA-accelerated processing component
- Sender: Python script for sending data to the GPU proxy
- Receiver: Python script for receiving processed data

## Prerequisites

- Docker with NVIDIA Container Toolkit installed
- NVIDIA GPU with CUDA 12.6 support
- At least 4GB of GPU memory

### Verifying Docker and NVIDIA Setup

1. Check Docker installation:
```bash
docker --version
```

2. Verify NVIDIA Container Toolkit:
```bash
docker run --gpus all nvidia/cuda:12.6.0-base-ubuntu22.04 nvidia-smi
```

3. Check CUDA version compatibility:
```bash
nvidia-smi
```

## Building the Container

1. Navigate to the project directory:
```bash
cd rtdp/cuda/gpu_proxy
```

2. Build the Docker image:
```bash
docker build -t gpu-proxy .
```

### Build Troubleshooting

If you encounter build issues:
1. Ensure sufficient disk space is available
2. Check internet connectivity for package downloads
3. Verify all source files are present in the build context
4. If using a proxy, configure Docker to use it:
```bash
export http_proxy=http://your-proxy:port
export https_proxy=http://your-proxy:port
```

## Running the Container

### Available Modes

The container supports three modes of operation:

1. **proxy** (default): Run the GPU-accelerated processing component
2. **sender**: Run the data sender script
3. **receiver**: Run the data receiver script

### Basic Usage

Run the container with GPU access:
```bash
docker run --gpus all gpu-proxy [mode] [options]
```

### Multi-Node Testing Configuration

#### 3-Node Test Setup

1. **GPU Proxy Node Setup** (Node A - GPU node):
```bash
# Request a GPU node
srun -p gpu --gres=gpu:A800:1 --mem=24G --pty bash

# Run the GPU proxy with specific ports
docker run --gpus all gpu-proxy proxy --in-port 55555 --out-ip <RECEIVER_NODE_IP> --out-port 55556 -t
```

2. **Sender Node Setup** (Node B):
```bash
# Run sender targeting GPU proxy node
docker run gpu-proxy sender -a <GPU_PROXY_NODE_IP> > sender.log
```

3. **Receiver Node Setup** (Node C):
```bash
# Run receiver
docker run gpu-proxy receiver > receiver.log
```

### Example with Specific IPs

Assuming:
- GPU Proxy Node IP: 192.168.1.100
- Receiver Node IP: 192.168.1.101

1. On GPU Proxy Node:
```bash
docker run --gpus all gpu-proxy proxy --in-port 55555 --out-ip 192.168.1.101 --out-port 55556 -t
```

2. On Sender Node:
```bash
docker run gpu-proxy sender -a 192.168.1.100 > sender.log
```

3. On Receiver Node:
```bash
docker run gpu-proxy receiver > receiver.log
```

### Container Help

To see available modes and options:
```bash
docker run gpu-proxy --help
```

To see help for a specific mode:
```bash
docker run gpu-proxy [mode] -h
```

### Additional Options

The GPU proxy supports several configuration options:
- `--in-port`: Change the input port (default: 55555)
- `--out-port`: Change the output port (default: 55556)
- `-r, --rate`: Control output/input volume ratio (default: 0.5)
- `-w, --width`: Set GPU input matrix column size (default: 2048)
- `-s, --sqlfile`: Specify SQL rate logger file
- `-t, --test`: Enable result verification
- `-d, --debug`: Enable debug mode

### Troubleshooting

1. **GPU Access Issues**:
   - Ensure NVIDIA Container Toolkit is properly installed
   - Verify GPU is visible to Docker: `docker run --gpus all nvidia/cuda:12.6.0-base-ubuntu22.04 nvidia-smi`
   - Check GPU memory usage: `nvidia-smi`

2. **Network Issues**:
   - Check if required ports (55555, 55556) are available
   - Verify IP address is correct and accessible
   - Ensure firewall settings allow the required ports

3. **Container Issues**:
   - Check container logs: `docker logs <container_id>`
   - Verify environment variables: `docker exec <container_id> env`
   - Ensure proper permissions on mounted volumes

## Notes

- The container uses CUDA 12.6, matching the host system requirements
- ZeroMQ communication ports are fixed at 55555 (input) and 55556 (output)
- Test mode (`-t`) enables additional diagnostic output
- All components (proxy, sender, receiver) are included in the container
- No separate Python environment setup is required

## Running the Experiment with Apptainer

This section shows how to run the experiment using Apptainer with the new mode-based approach.

1. First, convert the Docker Hub image to Apptainer:
```bash
apptainer build gpu_proxy.sif docker://jlabtsai/gpu-proxy:latest
```

2. Request a GPU node via Slurm (preferably A800, A100, or T4 with Tensor Cores):
```bash
srun -p gpu --gres=gpu:A800:1 --mem=24G --pty bash
```

### Multi-Node Testing with Apptainer

#### 3-Node Test Setup

1. **GPU Proxy Node Setup** (Node A - GPU node):
```bash
# Run the GPU proxy with specific ports
apptainer run --nv gpu_proxy.sif proxy --in-port 55555 --out-ip <RECEIVER_NODE_IP> --out-port 55556 -t
```
Expected output:
```
RECV - ZeroMQ pulling from: tcp://*:55555
SEND - ZeroMQ pushing to: tcp://<RECEIVER_NODE_IP>:55556

Waiting for data ...
```

2. **Sender Node Setup** (Node B):
```bash
# Run sender targeting GPU proxy node
apptainer run gpu_proxy.sif sender -a <GPU_PROXY_NODE_IP> > sender.log
```
Expected output:
```
Sending data to <GPU_PROXY_NODE_IP>:55555 (random values)
```

3. **Receiver Node Setup** (Node C):
```bash
# Run receiver
apptainer run gpu_proxy.sif receiver > receiver.log
```
Expected output:
```
Receiving data on port 55556...
Received bytes: 3276800
First 10 floats: [508.96136 508.16055 503.5662 516.7816 506.7647 516.5677 504.39285 499.47058 526.027 516.0576]
```

### Example with Specific IPs

Assuming:
- GPU Proxy Node IP: 192.168.1.100
- Receiver Node IP: 192.168.1.101

1. On GPU Proxy Node:
```bash
apptainer run --nv gpu_proxy.sif proxy --in-port 55555 --out-ip 129.57.70.13 --out-port 55556 -t
```

2. On Sender Node:
```bash
apptainer run gpu_proxy.sif sender -a 192.168.1.100 > sender.log
```

3. On Receiver Node:
```bash
apptainer run gpu_proxy.sif receiver > receiver.log
```

### Apptainer Help

To see available modes and options:
```bash
apptainer run gpu_proxy.sif --help
```

To see help for a specific mode:
```bash
apptainer run gpu_proxy.sif [mode] -h
```

### Notes for Apptainer Usage

- The `--nv` flag is crucial for GPU access in Apptainer
- All components (proxy, sender, receiver) are included in the container
- No separate Python environment setup is required
- Make sure the ports 55555 and 55556 are accessible between the nodes
- The GPU node's IP address should be used in the sender command
- All the matrix dimensions and test values should match the original experiment

## Logging Output

### Logging Sender Output

There are several ways to log the output of the sender script:

1. **Basic redirection** (overwrites existing file):
```bash
python python_zmq_helper/zmq_fp_sender.py -a <GPU_NODE_IP> > sender.log
```

2. **Append to existing log file**:
```bash
python python_zmq_helper/zmq_fp_sender.py -a <GPU_NODE_IP> >> sender.log
```

3. **Log both stdout and stderr**:
```bash
python python_zmq_helper/zmq_fp_sender.py -a <GPU_NODE_IP> &> sender.log
```

4. **Log with timestamp**:
```bash
python python_zmq_helper/zmq_fp_sender.py -a <GPU_NODE_IP> 2>&1 | while IFS= read -r line; do echo "$(date '+%Y-%m-%d %H:%M:%S') $line"; done > sender.log
```

5. **Log and display output simultaneously**:
```bash
python python_zmq_helper/zmq_fp_sender.py -a <GPU_NODE_IP> | tee sender.log
```

### Example Usage in Experiment

When running the experiment, you can modify step 4 to include logging:

```bash
# Create and activate a Python virtual environment
python3 -m venv zmq_env
source zmq_env/bin/activate

# Install required packages
pip install pyzmq numpy

# Run the sender with logging
python python_zmq_helper/zmq_fp_sender.py -a <GPU_NODE_IP> | tee sender_$(date +%Y%m%d_%H%M%S).log
```

This will:
- Create a log file with timestamp in the filename
- Display the output in the terminal
- Save the output to the log file

### Notes on Logging

- Log files can grow large over time, consider implementing log rotation
- For long-running experiments, consider adding timestamps to log entries
- You can monitor the log file in real-time using:
```bash
tail -f sender.log
``` 

## Multi-Node Testing Configuration

### 3-Node Test Setup

This section describes how to set up a test where the sender, GPU proxy, and receiver run on different nodes.

1. **GPU Proxy Node Setup** (Node A - GPU node):
```bash
# Request a GPU node
srun -p gpu --gres=gpu:A800:1 --mem=24G --pty bash

# Run the GPU proxy with specific ports
apptainer run --nv gpu_proxy.sif --in-port 55555 --out-ip <RECEIVER_NODE_IP> --out-port 55556 -t
```

2. **Sender Node Setup** (Node B):
```bash
# Create and activate Python environment
python3 -m venv zmq_env
source zmq_env/bin/activate
pip install pyzmq numpy

# Run sender targeting GPU proxy node
python python_zmq_helper/zmq_fp_sender.py -a <GPU_PROXY_NODE_IP> > sender.log
```

3. **Receiver Node Setup** (Node C):
```bash
# Create and activate Python environment
python3 -m venv zmq_env
source zmq_env/bin/activate
pip install pyzmq numpy

# Run receiver
python python_zmq_helper/zmq_fp_receiver.py > receiver.log
```

### Network Configuration

- Ensure all nodes can communicate with each other
- Verify ports 55555 and 55556 are open between nodes
- Use actual IP addresses instead of hostnames for better reliability

### Example with Specific IPs

Assuming:
- GPU Proxy Node IP: 192.168.1.100
- Receiver Node IP: 192.168.1.101

1. On GPU Proxy Node:
```bash
apptainer run --nv gpu_proxy.sif --in-port 55555 --out-ip 192.168.1.101 --out-port 55556 -t
```