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

### 1. Initial Setup (One-time setup)

First, build the Docker image and convert it to Apptainer SIF:
```bash
cd src/utilities/scripts/farm-nic-test
./build.sh
./docker_to_apptainer.sh
```

### 2. Running the Test

#### Using Convenience Scripts (Recommended)

1. On the receiver machine:
```bash
./run_receiver.sh
```

2. On the sender machine:
```bash
./run_sender.sh <receiver_ip>
```
Example:
```bash
./run_sender.sh 192.168.1.100
```

#### Manual Commands

##### Receiver Side:
```bash
apptainer run rtdp-farm-nic-test.sif iperf -s
```

##### Sender Side:
```bash
apptainer run rtdp-farm-nic-test.sif /usr/local/bin/nic_test.py <receiver_ip>
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