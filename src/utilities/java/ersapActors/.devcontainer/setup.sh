#!/bin/bash

# This script sets up the development environment after the container starts

# Create ERSAP_USER_DATA directory structure
mkdir -p $ERSAP_USER_DATA/config
mkdir -p $ERSAP_USER_DATA/data/input
mkdir -p $ERSAP_USER_DATA/data/output
mkdir -p $ERSAP_USER_DATA/log

# Copy services.yaml to the config directory
cp /workspace/main/resources/services.yaml $ERSAP_USER_DATA/config/

# Build the ERSAP actors project
cd /workspace
gradle deploy

# Create samples directory
mkdir -p /workspace/samples

# Check if pcap2stream directory exists
if [ ! -d "/workspace/pcap2stream" ]; then
    echo "Warning: pcap2stream directory not found!"
    echo "The full pipeline test cannot be run without pcap2stream."
    echo "Please make sure the pcap2stream directory is properly mounted in the devcontainer."
    echo "You can still build and deploy the ERSAP actors, but the full pipeline test will be skipped."
else
    # Build pcap2stream tools if they're not already built
    if [ ! -f "/workspace/pcap2stream/server/build/stream_server" ]; then
        echo "Building pcap2stream server..."
        cd /workspace/pcap2stream/server
        mkdir -p build && cd build
        cmake ..
        make
    fi

    if [ ! -f "/workspace/pcap2stream/sender/build/pcap2stream" ]; then
        echo "Building pcap2stream sender..."
        cd /workspace/pcap2stream/sender
        mkdir -p build && cd build
        cmake ..
        make
    fi
fi

# Generate a test PCAP file if it doesn't exist
if [ ! -f "/workspace/samples/test.pcap" ]; then
    echo "Generating test PCAP file..."
    cd /workspace
    ./samples/generate_test_pcap.sh
fi

# Set up Git if not already initialized
cd /workspace
if [ ! -d ".git" ]; then
    echo "Initializing Git repository..."
    git init
    git config --global --add safe.directory /workspace
    
    # Create .gitignore file
    cat > .gitignore << EOF
# Gradle
.gradle/
build/
!gradle/wrapper/gradle-wrapper.jar

# Eclipse
.classpath
.project
.settings/

# IntelliJ IDEA
.idea/
*.iml
*.iws
*.ipr

# VS Code
.vscode/
!.vscode/settings.json
!.vscode/tasks.json
!.vscode/launch.json
!.vscode/extensions.json

# Compiled class files
*.class

# Log files
*.log

# Package files
*.jar
*.war
*.ear

# ERSAP data
ersap-data/

# Generated PCAP files
samples/test.pcap

# OS-specific files
.DS_Store
Thumbs.db
EOF

    git add .
    git commit -m "Initial commit"
    echo "Git repository initialized."
fi

echo "Environment setup complete!"
echo "ERSAP_HOME: $ERSAP_HOME"
echo "ERSAP_USER_DATA: $ERSAP_USER_DATA"
echo ""
echo "To run the ERSAP shell:"
echo "  ersap-shell"
echo ""

if [ -d "/workspace/pcap2stream" ]; then
    echo "To start the stream server:"
    echo "  cd /workspace/pcap2stream/server/build"
    echo "  ./stream_server 0.0.0.0 5000 3"
    echo ""
    echo "To send PCAP data to the server:"
    echo "  cd /workspace/pcap2stream/sender/build"
    echo "  ./pcap2stream /workspace/samples/test.pcap 127.0.0.1 5000"
    echo ""
fi

echo "To run the test pipeline:"
echo "  cd /workspace"
echo "  ./test_pipeline.sh" 