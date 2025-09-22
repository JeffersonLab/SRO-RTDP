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

### Command Line Options

#### Proxy Mode Options:
- `--in-port <port>`: Input port for ZeroMQ (default: 55555)
- `--out-ip <ip>`: Output IP address for ZeroMQ
- `--out-port <port>`: Output port for ZeroMQ (default: 55556)
- `-t, --test`: Enable test mode with result verification
- `-d, --debug`: Enable debug mode
- `-r, --rate <value>`: Control output/input volume ratio (default: 0.5)
- `-w, --width <size>`: Set GPU input matrix column size (default: 2048)
- `-s, --sqlfile <file>`: Specify SQL rate logger file

#### Sender Mode Options:
- `-a, --address <ip>`: Target IP address for ZeroMQ communication
- `-r, --rate <value>`: Target send rate in MB/s (default: 25)
- `--group-size <size>`: Group size for sending (default: 2048)

#### Receiver Mode Options:
- `-v, --verbose`: Enable verbose output with detailed monitoring

#### Common Options:
- `-h, --help`: Show help message

### Multi-Node Testing Configuration

#### 3-Node Test Setup

1. **GPU Proxy Node Setup** (Node A - GPU node):
```bash
# Request a GPU node
srun -p gpu --gres=gpu:A100:1 --mem=100G --pty bash

# Run the GPU proxy with specific ports and matrix width
docker run --gpus all gpu-proxy proxy \
    --in-port 55555 \
    --out-ip <RECEIVER_NODE_IP> \
    --out-port 55556 \
    -t \
    -w 2048
```

2. **Sender Node Setup** (Node B):
```bash
# Run sender targeting GPU proxy node with rate control
docker run gpu-proxy sender \
    -a <GPU_PROXY_NODE_IP> \
    -r 25 \
    --group-size 2048
```

3. **Receiver Node Setup** (Node C):
```bash
# Run receiver with verbose output
docker run gpu-proxy receiver -v
```

### Example with Specific IPs

Assuming:
- GPU Proxy Node IP: 192.168.1.100
- Receiver Node IP: 192.168.1.101

1. On GPU Proxy Node:
```bash
docker run --gpus all gpu-proxy proxy \
    --in-port 55555 \
    --out-ip 192.168.1.101 \
    --out-port 55556 \
    -t \
    -w 2048
```

2. On Sender Node:
```bash
docker run gpu-proxy sender \
    -a 192.168.1.100 \
    -r 25 \
    --group-size 2048
```

3. On Receiver Node:
```bash
docker run gpu-proxy receiver -v
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
- Matrix width is set to 2048 by default
- Send rate is set to 25 MB/s by default
- Group size is set to 2048 by default

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

Received [8192] bytes from ZeroMQ socket.
First 10 elements of h_in:
0.351711 0.852162 0.300457 0.894459 0.0352142 0.0829234 0.700032 0.681391 0.0781673 0.242668 

        Input matrix dimension, (#columns)x(#rows): 2048x1
        Random matrix dimension, (#columns)x(#rows): 1024x2048
First 10 elements of h_out:
513.019 525.493 512.337 528.763 523.848 540.64 535.584 519.227 519.087 513.447 

First 10 elements of CPU computed matrix multiplication result:
513.019 525.493 512.338 528.763 523.848 540.64 535.584 519.227 519.086 513.447 

        Output matrix dimension, (#columns)x(#rows): 1024x1
Sent [4096] bytes via ZeroMQ socket.
...
[Monitor] Incoming: [0.012288 MB/s], Outgoing: [0.006144 MB/s]
...
```

2. **Sender Node Setup** (Node B):
```bash
# Run sender targeting GPU proxy node with specific rate and group size
apptainer run gpu_proxy.sif sender -a <GPU_PROXY_NODE_IP> -r 25 --group-size 2048 > sender.log
```
Expected output:
```
Sending data to <GPU_PROXY_NODE_IP>:55555 (random values)
Target send rate: 25.0 MB/s

Each message needs: 0.32768 ms
      Sent 0.008192 MB,  curr_send_rate=25.0 MB/s, duration=7.764577865600586 ms
      Sleep for 992.2354221343994 ms...
      ...
```

3. **Receiver Node Setup** (Node C):
```bash
# Run receiver with verbose output
apptainer run gpu_proxy.sif receiver -v > receiver.log
```
Expected output:
```
Receiving data on port 55556...
Received [4096] bytes
      First 10 floats: [513.01874 525.4927  512.33746 528.76263 523.84845 540.64    535.58386
519.22736 519.0868  513.44714]
...
curr_recv_rate = 0.006144 MB/s
```

### Example with Specific IPs

Assuming:
- GPU Proxy Node IP: 192.168.1.100
- Receiver Node IP: 192.168.1.101

1. On GPU Proxy Node:
```bash
apptainer run --nv gpu_proxy.sif proxy --in-port 55555 --out-ip 192.168.1.101 --out-port 55556 -t
```

2. On Sender Node:
```bash
apptainer run gpu_proxy.sif sender -a 192.168.1.100 -r 25 --group-size 2048 > sender.log
```

3. On Receiver Node:
```bash
apptainer run gpu_proxy.sif receiver -v > receiver.log
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
- The sender supports rate control with `-r` option and group size specification with `--group-size`
- The receiver supports verbose output with `-v` option for detailed monitoring

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