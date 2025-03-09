# ERSAP Orchestration Script Reference

This document provides a quick reference guide for the scripts used in the ERSAP orchestration workflow.

## Overview

The ERSAP orchestration workflow uses several scripts to automate the process of setting up the environment, building the application, and running the orchestrator. These scripts are located in the `/workspace/src/utilities/java/pcap-ersap/scripts` directory.

## Script Reference

### run_ersap_orchestrator.sh

The main script that orchestrates the entire workflow.

**Usage:**
```bash
cd /workspace/src/utilities/java/pcap-ersap
chmod +x scripts/run_ersap_orchestrator.sh
./scripts/run_ersap_orchestrator.sh
```

**What it does:**
1. Sets up the ERSAP environment variables
2. Rebuilds the ERSAP environment using `rebuild_ersap.sh`
3. Fixes package structure and imports using `fix_package_structure.sh` and `fix_imports.sh`
4. Checks if `pcap2streams` is running and starts it if needed
5. Compiles the application using Gradle
6. Verifies the JAR files
7. Starts the ERSAP orchestrator with the configuration file
8. Waits for processing to complete
9. Checks the output files
10. Stops `pcap2streams` if it was started by the script

**Source Code:**
```bash
#!/bin/bash

# This script runs the ERSAP orchestrator with our rebuilt environment

# Set environment variables
export ERSAP_HOME="/workspace/src/utilities/java/ersapActors/ersap-java"
export ERSAP_USER_DATA="/workspace/src/utilities/java/pcap-ersap"

# Rebuild the ERSAP environment
echo "Rebuilding ERSAP environment..."
./scripts/rebuild_ersap.sh

# Fix package structure and imports
echo "Fixing package structure..."
./scripts/fix_package_structure.sh
echo "Fixing imports..."
./scripts/fix_imports.sh

# Check if pcap2streams is running
if ! pgrep -f "pcap2streams" > /dev/null; then
    echo "Starting pcap2streams..."
    cd /workspace/src/utilities/java/pcap2streams
    ./pcap2streams -f /workspace/src/utilities/java/pcap2streams/pcap/test.pcap -c /workspace/src/utilities/java/pcap2streams/custom-config/ip-based-config.json &
    PCAP2STREAMS_PID=$!
    cd $ERSAP_USER_DATA
    # Wait for pcap2streams to start
    sleep 2
else
    echo "Pcap2Streams is already running"
    PCAP2STREAMS_PID=""
fi

# Create output directory if it doesn't exist
mkdir -p $ERSAP_USER_DATA/output

# Compile the application
echo "Compiling application..."
cd $ERSAP_USER_DATA
gradle clean build -x test

# Verify JAR files
echo "Verifying JAR files..."
ls -la $ERSAP_HOME/lib/ersap/
ls -la $ERSAP_USER_DATA/lib/

# Start the ERSAP orchestrator
echo "Starting ERSAP orchestrator..."
$ERSAP_HOME/scripts/unix/ersap-orchestrator -f $ERSAP_USER_DATA/config/pcap-services.yaml

# Wait for processing to complete
echo "Waiting for processing to complete..."
sleep 60

# Check output files
echo "Checking output files..."
ls -la $ERSAP_USER_DATA/output/

# Stop the orchestrator and pcap2streams if we started it
if [ ! -z "$PCAP2STREAMS_PID" ]; then
    echo "Stopping pcap2streams..."
    kill $PCAP2STREAMS_PID
fi

echo "ERSAP orchestrator completed successfully."
```

### rebuild_ersap.sh

Rebuilds the ERSAP environment from scratch.

**Usage:**
```bash
cd /workspace/src/utilities/java/pcap-ersap
chmod +x scripts/rebuild_ersap.sh
./scripts/rebuild_ersap.sh
```

**What it does:**
1. Sets up the ERSAP environment variables
2. Cleans up existing JAR files
3. Creates necessary directories
4. Downloads the JSON library if needed
5. Creates the Orchestrator class
6. Creates the IEngine interface
7. Creates the EngineData class
8. Compiles the Java files
9. Creates the JAR files
10. Creates the ersap-orchestrator script
11. Creates a logging.properties file
12. Creates symbolic links for the ERSAP libraries
13. Updates the build.gradle file

**Key Components:**
- **Orchestrator Class**: Manages the execution of services and coordinates the data flow between them.
- **IEngine Interface**: Defines the interface for ERSAP services.
- **EngineData Class**: Encapsulates the data being processed and metadata about the data.

### fix_package_structure.sh

Fixes the package structure of Java files.

**Usage:**
```bash
cd /workspace/src/utilities/java/pcap-ersap
chmod +x scripts/fix_package_structure.sh
./scripts/fix_package_structure.sh
```

**What it does:**
1. Creates necessary directories for the package structure
2. Finds all Java files and extracts their package declarations
3. Moves each file to its corresponding directory based on the package declaration

### fix_imports.sh

Fixes imports in service classes.

**Usage:**
```bash
cd /workspace/src/utilities/java/pcap-ersap
chmod +x scripts/fix_imports.sh
./scripts/fix_imports.sh
```

**What it does:**
1. Creates a directory for fixed files
2. Fixes imports in PcapSinkService.java
3. Fixes imports in PcapSourceService.java
4. Fixes imports in PcapProcessorService.java
5. Creates PacketEvent.java and PcapDataTypes.java if needed

## Configuration Files

### pcap-services.yaml

Configures the ERSAP services.

**Location:**
```
/workspace/src/utilities/java/pcap-ersap/config/pcap-services.yaml
```

**Example:**
```yaml
services:
  - class: org.jlab.ersap.actor.pcap.services.PcapSourceService
    name: PcapSource
    inputs:
      - config
    outputs:
      - packets
    configuration:
      ip: 192.168.10.1
      port: 9000

  - class: org.jlab.ersap.actor.pcap.services.PcapProcessorService
    name: PcapProcessor
    inputs:
      - packets
    outputs:
      - processed_packets
    configuration:
      filter: "ip.src == 192.168.10.1"

  - class: org.jlab.ersap.actor.pcap.services.PcapSinkService
    name: PcapSink
    inputs:
      - processed_packets
    outputs:
      - output
    configuration:
      output_dir: /workspace/src/utilities/java/pcap-ersap/output
```

### ip-based-config.json

Configures the `pcap2streams` tool.

**Location:**
```
/workspace/src/utilities/java/pcap2streams/custom-config/ip-based-config.json
```

**Example:**
```json
{
  "connections": [
    {
      "ip": "192.168.10.1",
      "port": 9000
    },
    {
      "ip": "192.168.10.2",
      "port": 9001
    },
    {
      "ip": "192.168.10.3",
      "port": 9002
    }
  ]
}
```

## Troubleshooting

### Common Issues

1. **Script permissions**: Ensure all scripts have execute permissions:
   ```bash
   chmod +x scripts/*.sh
   ```

2. **Missing JAR files**: Check that all JAR files are present in the correct locations:
   ```bash
   ls -la $ERSAP_HOME/lib/ersap/
   ls -la $ERSAP_USER_DATA/lib/
   ```

3. **pcap2streams not running**: Check if pcap2streams is running:
   ```bash
   pgrep -f "pcap2streams"
   ```

4. **Socket connection errors**: Check if the socket servers are listening on the expected ports:
   ```bash
   netstat -an | grep 900
   ```

5. **Compilation errors**: Check for compilation errors in the Gradle build:
   ```bash
   cd $ERSAP_USER_DATA
   gradle clean build -x test --info
   ```

### Logs

Check the logs for error messages:

```bash
grep -i error $ERSAP_HOME/logs/*
```

## Conclusion

These scripts automate the process of setting up the ERSAP environment, building the application, and running the orchestrator. They handle common tasks such as fixing package structure and imports, checking if pcap2streams is running, and verifying JAR files.

For more information on the ERSAP orchestration architecture, see the [ERSAP_ORCHESTRATION.md](ERSAP_ORCHESTRATION.md) document. 