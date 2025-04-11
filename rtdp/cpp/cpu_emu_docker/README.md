# CPU Emulator Docker Image

This Docker image packages the CPU emulator program that can simulate CPU and memory load for testing purposes.

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

### Example Commands

1. Basic example with 10 threads, 100 seconds latency per GB:
```bash
docker run cpu-emu -b 100 -i "127.0.0.1" -m 0.1 -o 0.001 -t 10 -r 50888 -p 50080
```

2. Using sleep mode instead of CPU burn:
```bash
docker run cpu-emu -b 100 -i "127.0.0.1" -m 0.1 -o 0.001 -t 10 -r 50888 -p 50080 -s
```

### Parameters

Required parameters:
- `-b` : seconds thread latency per GB input
- `-i` : destination address (string)
- `-m` : thread memory footprint in GB
- `-o` : output size in GB
- `-t` : number of threads

Optional parameters:
- `-p` : destination port (default = 50080)
- `-r` : receive port (default = 50888)
- `-s` : sleep versus burn cpu
- `-v` : verbose (0/1, default = 0)

### Network Configuration

When running the container, you might need to configure the network depending on your use case:

1. Using host network:
```bash
docker run --network host cpu-emu [parameters]
```

2. Exposing specific ports:
```bash
docker run -p 50888:50888 cpu-emu [parameters]
```

### Testing Setup

1. Start a netcat listener on the destination system:
```bash
nc -l <port> > output_file
```

2. Run the CPU emulator container:
```bash
docker run cpu-emu [parameters]
```

3. Send data to the CPU emulator:
```bash
cat input_file | nc -N -q 0 <cpu_emu_host> <port>
```

## Notes

- The container needs appropriate network access to communicate with the destination address
- Memory limits might need to be adjusted using Docker's `-m` flag depending on the memory footprint specified
- For production use, consider setting resource limits using Docker's runtime flags
- The default ports (50888 and 50080) are in the dynamic/private port range (above 49152) to avoid conflicts with common services



