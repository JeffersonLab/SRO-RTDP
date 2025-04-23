# Farm NIC Test

This tool helps test high-speed network interface card (NIC) performance using iperf2. It can be run using either Docker or Apptainer (formerly Singularity).

## Prerequisites

### For Docker:
- Docker installed and running on both sender and receiver
- Docker daemon has permissions to use host network

### For Apptainer:
- Apptainer installed on both sender and receiver
- Root privileges (for building the SIF file)

## Step-by-Step Instructions

### 1. Receiver Side Setup

First, on the receiver machine, start the iperf2 server:

#### Using Docker:
```bash
# On receiver machine
cd src/utilities/scripts/farm-nic-test
./build.sh
docker run -d --rm --network=host farm-nic-test iperf -s
```

#### Using Apptainer:
```bash
# On receiver machine
cd src/utilities/scripts/farm-nic-test
./build.sh
./docker_to_apptainer.sh
apptainer run rtdp-farm-nic-test.sif iperf -s
```

The server will run in the background and listen on port 5201. Keep this running while performing the test.

### 2. Sender Side Setup and Test

On the sender machine:

#### Using Docker:

1. Build the test image (if not already built):
```bash
cd src/utilities/scripts/farm-nic-test
./build.sh
```

2. Run the test:
```bash
docker run --network=host farm-nic-test <receiver_ip>
```

Example:
```bash
docker run --network=host farm-nic-test 192.168.1.100
```

#### Using Apptainer:

1. Build the Docker image (if not already built):
```bash
cd src/utilities/scripts/farm-nic-test
./build.sh
```

2. Convert to Apptainer SIF (if not already done):
```bash
./docker_to_apptainer.sh
```

3. Run the test:
```bash
apptainer run rtdp-farm-nic-test.sif <receiver_ip>
```

Example:
```bash
apptainer run rtdp-farm-nic-test.sif 192.168.1.100
```

## Test Configuration

- The test runs for 10 seconds to get a stable measurement
- Uses iperf2 for network performance testing
- Reports transmission rate in Gbits/sec
- Uses host network mode for accurate NIC testing

## Output

The test will output the transmission rate in Gbits/sec. Example output:
```
Testing NIC speed with receiver IP: 192.168.1.100
Transmission rate: 9.85 Gbits/sec
```

## Troubleshooting

1. If you get permission errors with Docker:
   - Ensure your user is in the docker group
   - Try running with sudo (not recommended for production)

2. If you get network errors:
   - Ensure the receiver IP is correct and reachable
   - Check if port 5201 is available and not blocked by firewall
   - Verify network interface permissions
   - Make sure the iperf2 server is running on the receiver side

3. If Apptainer build fails:
   - Ensure you have root privileges
   - Check if Apptainer is properly installed
   - Verify the Docker image exists and is accessible

## Notes

- The test requires both sender and receiver machines to be on the same network
- For best results, ensure no other network-intensive applications are running
- The test uses TCP for reliable transmission rate measurement
- The receiver must have an iperf2 server running before starting the test 