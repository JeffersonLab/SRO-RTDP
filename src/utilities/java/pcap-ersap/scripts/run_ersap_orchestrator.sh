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
    
    # Create the custom-config directory if it doesn't exist
    mkdir -p custom-config
    
    # Create the ip-based-config.json file
    echo "Creating ip-based-config.json file..."
    cat > custom-config/ip-based-config.json << 'EOF'
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
EOF
    
    # Use the PCAP file from /scratch/jeng-yuantsai/
    PCAP_FILE="/scratch/jeng-yuantsai/CLAS12_ECAL_PCAL_DC_2024-05-15_17-12-30.pcap"
    
    # Check if the file exists
    if [ -f "$PCAP_FILE" ]; then
        echo "Using PCAP file: $PCAP_FILE"
        ./pcap2streams -f "$PCAP_FILE" -c custom-config/ip-based-config.json &
    else
        echo "PCAP file not found: $PCAP_FILE"
        echo "Using default test PCAP file"
        ./pcap2streams -f pcap/test.pcap -c custom-config/ip-based-config.json &
    fi
    
    PCAP2STREAMS_PID=$!
    cd $ERSAP_USER_DATA
    
    # Wait for pcap2streams to start (increased for large files)
    echo "Waiting for pcap2streams to initialize..."
    sleep 10
else
    echo "Pcap2Streams is already running"
    PCAP2STREAMS_PID=""
fi

# Create output directory if it doesn't exist
mkdir -p $ERSAP_USER_DATA/output

# Update the config file path in pcap-services.yaml
echo "Updating config file path in pcap-services.yaml..."
sed -i 's|config_file:.*|config_file: /workspace/src/utilities/java/pcap2streams/custom-config/ip-based-config.json|g' $ERSAP_USER_DATA/config/pcap-services.yaml

# Compile the application
echo "Compiling application..."
cd $ERSAP_USER_DATA
gradle clean build -x test

# Verify JAR files
echo "Verifying JAR files..."
ls -la $ERSAP_HOME/lib/ersap/
ls -la $ERSAP_USER_DATA/lib/

# Start the ERSAP orchestrator with more memory
echo "Starting ERSAP orchestrator..."
JAVA_OPTS="-Xmx4g" $ERSAP_HOME/scripts/unix/ersap-orchestrator -f $ERSAP_USER_DATA/config/pcap-services.yaml

# Wait longer for processing to complete (5 minutes)
echo "Waiting for processing to complete..."
sleep 300

# Check output files
echo "Checking output files..."
ls -la $ERSAP_USER_DATA/output/

# Stop the orchestrator and pcap2streams if we started it
if [ ! -z "$PCAP2STREAMS_PID" ]; then
    echo "Stopping pcap2streams..."
    kill $PCAP2STREAMS_PID
fi

echo "ERSAP orchestrator completed successfully." 