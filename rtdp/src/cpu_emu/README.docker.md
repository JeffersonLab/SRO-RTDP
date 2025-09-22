# CPU Emulator Docker Container

This Docker container provides three modes of operation for the CPU emulator:
1. Emulator mode (default)
2. Receiver mode
3. Sender mode

## Building the Container

```bash
docker build -t cpu-emu .
```

## Running the Container

### Emulator Mode
```bash
# Using YAML configuration
docker run -p 8888:8888 cpu-emu emulator -y cpu_emu.yaml

# Using command-line parameters
docker run -p 8888:8888 cpu-emu emulator -i <destination_ip> -p <port> [options]
```

Options for emulator mode:
- -b: thread latency in nsec/byte input
- -i: destination address
- -m: thread memory footprint in GB
- -o: output size in GB
- -p: destination port (default = 8888)
- -r: receive port (default = 8888)
- -s: sleep versus burn cpu
- -t: num threads (default = 5)
- -y: yaml config file
- -v: verbose (0/1, default = 0)

### Receiver Mode
```bash
# Using -z flag
docker run -p 8888:8888 cpu-emu receiver -z -i <destination_ip> -p <port> [options]

# Using YAML configuration (must have terminal: 1)
docker run -p 8888:8888 cpu-emu receiver -y cpu_emu.yaml
```

### Sender Mode
```bash
docker run -p 5555:5555 cpu-emu sender -i <destination_ip> -p <port> [-c <count>] [-s <size>]
```

Options for sender mode:
- -i: destination IP (required)
- -p: destination port (required)
- -c: event count (default: 10)
- -s: event size in MB (default: 10)

## Example YAML Configuration

```yaml
destination: "129.57.177.5" #ejfat-5-daq
dst_port: 5555
rcv_port: 5555
sleep: 0
threads: 1
latency: 500         # Processing latency in nsec/byte input
mem_footprint: 0.05  # Memory footprint in GB
output_size: 0.001   # Output size in GB
verbose: 1
terminal: 0          # if 1 do not forward result to destination
```

## Test Script

A test script is available in the `example` directory to run all three components together:

```bash
# Make the script executable
chmod +x example/test_components.sh

# Run the test
./example/test_components.sh
```

The test script:
1. Starts a receiver on port 3000
2. Starts an emulator on port 3001
3. Starts a sender that sends data to the emulator
4. Uses host networking for container communication
5. Captures and displays logs from all components
6. Automatically cleans up resources on exit

The script creates a temporary YAML configuration for the emulator with the following settings:
- Destination: 127.0.0.1 (receiver)
- Destination port: 3000
- Receive port: 3001
- Sleep: 0 (burn CPU)
- Threads: 1
- Latency: 500 nsec/byte
- Memory footprint: 0.05 GB
- Output size: 0.001 GB
- Verbose: 1

## Notes

- The container exposes ports 8888 (for cpu_emu) and 5555 (for sender)
- For receiver mode, either the -z flag must be used or the YAML file must have terminal: 1
- Command-line parameters override YAML settings
- The sender mode only accepts command-line parameters, not YAML configuration 