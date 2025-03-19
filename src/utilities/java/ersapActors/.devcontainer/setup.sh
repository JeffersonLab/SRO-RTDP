#!/bin/bash

# Exit on error
set -e

# Set ERSAP_HOME
export ERSAP_HOME=$HOME/ersap-install

# Create ERSAP directories
mkdir -p $ERSAP_HOME/{lib,plugins/jni/lib,bin,config}

# Clone ersap-java if not exists
if [ ! -d "$HOME/ERSAP/ersap-java" ]; then
    mkdir -p $HOME/ERSAP
    cd $HOME/ERSAP
    git clone https://github.com/JeffersonLab/ersap-java.git
    cd ersap-java
    git checkout upgradeGradle
fi

# Build and deploy ersap-java
cd $HOME/ERSAP/ersap-java
./gradlew deploy
./gradlew publishToMavenLocal

# Create ersap-shell script
cat > $ERSAP_HOME/bin/ersap-shell << 'EOF'
#!/bin/bash
echo "ERSAP shell started"
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
export CLASSPATH=$ERSAP_HOME/lib/*
while true; do
  sleep 1
done
EOF

chmod +x $ERSAP_HOME/bin/ersap-shell

echo "ERSAP setup completed successfully!"

# This script sets up the development environment after the container starts

# Create ERSAP_USER_DATA directory structure
mkdir -p $ERSAP_USER_DATA/config
mkdir -p $ERSAP_USER_DATA/data/input
mkdir -p $ERSAP_USER_DATA/data/output
mkdir -p $ERSAP_USER_DATA/log

# Copy services.yaml to the config directory
cp /workspace/src/utilities/java/ersapActors/main/resources/services.yaml $ERSAP_USER_DATA/config/

# Build the ERSAP actors project
cd /workspace/src/utilities/java/ersapActors
gradle deploy

# Create samples directory
mkdir -p /workspace/src/utilities/java/ersapActors/samples

# Check if pcap2stream directory exists
if [ ! -d "/workspace/src/utilities/cpp/pcap2stream" ]; then
    echo "Warning: pcap2stream directory not found!"
    echo "The full pipeline test cannot be run without pcap2stream."
    echo "Please make sure the pcap2stream directory is properly mounted in the devcontainer."
    echo "You can still build and deploy the ERSAP actors, but the full pipeline test will be skipped."
else
    # Build pcap2stream tools if they're not already built
    if [ ! -f "/workspace/src/utilities/cpp/pcap2stream/server/build/stream_server" ]; then
        echo "Building pcap2stream server..."
        cd /workspace/src/utilities/cpp/pcap2stream/server
        mkdir -p build && cd build
        cmake ..
        make
    fi

    if [ ! -f "/workspace/src/utilities/cpp/pcap2stream/sender/build/pcap2stream" ]; then
        echo "Building pcap2stream sender..."
        cd /workspace/src/utilities/cpp/pcap2stream/sender
        mkdir -p build && cd build
        cmake ..
        make
    fi
fi

# Generate a test PCAP file if it doesn't exist
if [ ! -f "/workspace/src/utilities/java/ersapActors/samples/test.pcap" ]; then
    echo "Generating test PCAP file..."
    cd /workspace/src/utilities/java/ersapActors
    ./samples/generate_test_pcap.sh
fi

# Configure Git to recognize the workspace as safe
git config --global --add safe.directory /workspace

echo "Environment setup complete!"
echo "ERSAP_HOME: $ERSAP_HOME"
echo "ERSAP_USER_DATA: $ERSAP_USER_DATA"
echo ""
echo "To run the ERSAP shell:"
echo "  ersap-shell"
echo ""

if [ -d "/workspace/src/utilities/cpp/pcap2stream" ]; then
    echo "To start the stream server:"
    echo "  cd /workspace/src/utilities/cpp/pcap2stream/server/build"
    echo "  ./stream_server 0.0.0.0 5000 3"
    echo ""
    echo "To send PCAP data to the server:"
    echo "  cd /workspace/src/utilities/cpp/pcap2stream/sender/build"
    echo "  ./pcap2stream /workspace/src/utilities/java/ersapActors/samples/test.pcap 127.0.0.1 5000"
    echo ""
fi

echo "To run the test pipeline:"
echo "  cd /workspace/src/utilities/java/ersapActors"
echo "  ./test_pipeline.sh" 