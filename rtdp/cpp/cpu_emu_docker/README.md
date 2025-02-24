# CPU Emulator Docker Image

This Docker image packages the CPU emulator program that can simulate CPU and memory load for testing purposes. It uses ZeroMQ for communication and supports YAML configuration.

## Building the Image

To build the Docker image, run:

```bash
./build.sh
```

This will create an image tagged as `cpu-emu`.

## Using the Image

### Basic Usage

Show help message:
```bash
docker run cpu-emu
```

### Configuration

The CPU emulator can be configured either through command-line arguments or a YAML configuration file.

#### YAML Configuration

Create a `cpu_emu.yaml` file:
```yaml
destination: "127.0.0.1"
dst_port: 8888
rcv_port: 8888
sleep: 0
threads: 5
latency: 100         # Processing latency in nsec/byte input
mem_footprint: 10    # Memory footprint in GB
output_size: 0.01    # Output size in GB
verbose: 0
terminal: 0          # if 1 do not forward result to destination
```

Then run with:
```bash
docker run -v $(pwd)/cpu_emu.yaml:/app/cpu_emu.yaml cpu-emu -y cpu_emu.yaml
```

#### Command Line Arguments

1. Basic example with 5 threads, 100 seconds latency per GB:
```bash
docker run cpu-emu -b 100 -i "127.0.0.1" -m 10 -o 0.01 -t 5 -r 8888 -p 8888
```

2. Using sleep mode instead of CPU burn:
```bash
docker run cpu-emu -b 100 -i "127.0.0.1" -m 10 -o 0.01 -t 5 -r 8888 -p 8888 -s
```

3. Running as a terminal node (no forwarding):
```bash
docker run cpu-emu -b 100 -i "127.0.0.1" -m 10 -o 0.01 -t 5 -r 8888 -p 8888 -z
```

### Parameters

Required parameters (if not using YAML config):
- `-b` : seconds thread latency per GB input
- `-i` : destination address (string)
- `-m` : thread memory footprint in GB
- `-o` : output size in GB
- `-t` : number of threads

Optional parameters:
- `-p` : destination port (default = 8888)
- `-r` : receive port (default = 8888)
- `-s` : sleep versus burn cpu
- `-v` : verbose (0/1, default = 0)
- `-y` : YAML config file path
- `-z` : act as terminal node (don't forward data)

### Network Configuration

When running the container, you might need to configure the network depending on your use case:

1. Using host network:
```bash
docker run --network host cpu-emu [parameters]
```

2. Exposing specific ports:
```bash
docker run -p 8888:8888 cpu-emu [parameters]
```

### Testing Setup

1. Start a terminal node:
```bash
docker run cpu-emu -b 100 -m 10 -t 5 -r 8888 -z -v 1
```

2. Start an intermediate node:
```bash
docker run cpu-emu -b 100 -i "127.0.0.1" -m 10 -o 0.01 -t 5 -r 7777 -p 8888 -v 1
```

3. Send data using a ZMQ client (example Python script):
```python
import zmq

context = zmq.Context()
socket = context.socket(zmq.PUSH)
socket.connect("tcp://localhost:7777")
socket.send(b"test data")
```

## Notes

- The container uses ZeroMQ (ZMQ) for communication instead of raw sockets
- YAML configuration support makes it easier to manage complex setups
- Memory limits might need to be adjusted using Docker's `-m` flag depending on the memory footprint specified
- For production use, consider setting resource limits using Docker's runtime flags
- The default ports are set to 8888 but can be changed via configuration



