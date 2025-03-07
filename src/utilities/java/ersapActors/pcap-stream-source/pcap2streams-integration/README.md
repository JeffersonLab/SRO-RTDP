# Pcap2Streams Integration with ERSAP

This directory contains scripts and configuration files for integrating the Pcap2Streams application with the ERSAP framework. The integration allows ERSAP to use Pcap2Streams as a data source for processing network packets from PCAP files.

## Overview

The integration connects the following components:

1. **Pcap2Streams**: Analyzes PCAP files, identifies unique IP addresses, and creates IP-specific servers.
2. **ERSAP Client**: Connects to the IP-specific servers and processes the streamed packets.

## Directory Structure

```
pcap2streams-integration/
├── config/                 # Configuration files
├── results/                # Test results and logs
├── scripts/                # Test scripts
│   ├── run_ersap_pcap2streams_test.sh  # Main integration test script
│   ├── monitor_test.sh                 # Monitoring script
│   └── config_adapter.py               # Configuration adapter
└── README.md               # This file
```

## Prerequisites

- Java 11 or higher
- Python 3.6 or higher
- Pcap2Streams application (located at `/workspace/src/utilities/java/pcap2streams`)
- ERSAP framework with pcap-stream-source module

## Running the Integration Test

The main script `run_ersap_pcap2streams_test.sh` performs the following steps:

1. Starts Pcap2Streams with a specified PCAP file
2. Waits for Pcap2Streams to analyze the file and create IP-specific servers
3. Adapts the Pcap2Streams configuration for use with ERSAP
4. Runs the ERSAP client to connect to the IP-specific servers
5. Collects and saves test results

### Basic Usage

```bash
cd /workspace/src/utilities/java/ersapActors/pcap-stream-source/pcap2streams-integration/scripts
./run_ersap_pcap2streams_test.sh
```

By default, the script uses a small test PCAP file for quick testing.

### Command-Line Options

```
Usage: ./run_ersap_pcap2streams_test.sh [options]
Options:
  --pcap FILE    Use specified PCAP file
  --test         Use small test PCAP file (default)
  --full         Use full PCAP file from /scratch
  --help, -h     Show this help message
```

### Examples

Run with the default test PCAP file:
```bash
./run_ersap_pcap2streams_test.sh
```

Run with the full PCAP file:
```bash
./run_ersap_pcap2streams_test.sh --full
```

Run with a custom PCAP file:
```bash
./run_ersap_pcap2streams_test.sh --pcap /path/to/custom.pcap
```

## Monitoring the Test

The `monitor_test.sh` script provides real-time monitoring of the integration test:

```bash
./monitor_test.sh
```

### Monitoring Options

```
Usage: ./monitor_test.sh [options]
Options:
  --interval, -i SECONDS   Update interval in seconds (default: 5)
  --duration, -d SECONDS   Total monitoring duration in seconds (default: 300)
  --help, -h               Show this help message
```

## Configuration Adapter

The `config_adapter.py` script converts the Pcap2Streams IP-based configuration to the format expected by the ERSAP client:

```bash
python3 config_adapter.py <input_config> <output_config>
```

## Results

Test results and monitoring logs are saved in the `results/` directory with timestamps in the filenames.

## Troubleshooting

### Common Issues

1. **Configuration file not found**: Ensure Pcap2Streams has enough time to analyze the PCAP file and generate the configuration.

2. **Connection errors**: Check that the IP addresses in the PCAP file are accessible from the test environment.

3. **Process termination**: The cleanup function should terminate all processes, but you can manually kill them if needed:
   ```bash
   pkill -f "java.*Pcap2Streams"
   pkill -f "java.*SimpleMultiSocketTest"
   ```

4. **Permission issues**: Ensure all scripts have execute permissions:
   ```bash
   chmod +x scripts/*.sh scripts/*.py
   ```

### Logs

Check the following logs for troubleshooting:

- Test results: `results/test_results_*.log`
- Monitoring logs: `results/monitor_log_*.txt` 