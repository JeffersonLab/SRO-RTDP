#!/bin/bash

# Check if PCAP file is provided
if [ $# -lt 1 ]; then
    echo "Usage: $0 <pcap_file> [timeout_seconds]"
    exit 1
fi

PCAP_FILE="$1"
TIMEOUT="${2:-30}"  # Default timeout is 30 seconds

# Check if PCAP file exists
if [ ! -f "$PCAP_FILE" ]; then
    echo "PCAP file not found: $PCAP_FILE"
    exit 1
fi

# Create a temporary directory for the test
TEMP_DIR=$(mktemp -d)
echo "Using temporary directory: $TEMP_DIR"

# Create configuration file
CONFIG_FILE="$TEMP_DIR/multi-socket-config.json"
echo '{"connections":[{"host":"localhost","port":9000,"connection_timeout":5000,"read_timeout":30000,"buffer_size":1024},{"host":"localhost","port":9001,"connection_timeout":5000,"read_timeout":30000,"buffer_size":1024}]}' > "$CONFIG_FILE"
echo "Created configuration file: $CONFIG_FILE"

# Start mock PCAP servers
echo "Starting mock PCAP servers..."
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BUILD_DIR="$PROJECT_DIR/build"
CLASSES_DIR="$BUILD_DIR/classes/java/main"
SCRIPTS_DIR="$BUILD_DIR/classes/java/scripts"

# Set up classpath
ERSAP_HOME=${ERSAP_HOME:-/usr/local/ersap}
JSON_JAR="$PROJECT_DIR/lib/json-20231013.jar"
DISRUPTOR_JAR="$PROJECT_DIR/lib/disruptor-3.4.4.jar"
YAML_JAR="$PROJECT_DIR/lib/snakeyaml-2.0.jar"

# Download JSON library if it doesn't exist
if [ ! -f "$JSON_JAR" ]; then
    echo "Downloading JSON library..."
    mkdir -p "$(dirname "$JSON_JAR")"
    curl -s -o "$JSON_JAR" "https://repo1.maven.org/maven2/org/json/json/20231013/json-20231013.jar"
fi

# Download Disruptor library if it doesn't exist
if [ ! -f "$DISRUPTOR_JAR" ]; then
    echo "Downloading Disruptor library..."
    mkdir -p "$(dirname "$DISRUPTOR_JAR")"
    curl -s -o "$DISRUPTOR_JAR" "https://repo1.maven.org/maven2/com/lmax/disruptor/3.4.4/disruptor-3.4.4.jar"
fi

# Download SnakeYAML library if it doesn't exist
if [ ! -f "$YAML_JAR" ]; then
    echo "Downloading SnakeYAML library..."
    mkdir -p "$(dirname "$YAML_JAR")"
    curl -s -o "$YAML_JAR" "https://repo1.maven.org/maven2/org/yaml/snakeyaml/2.0/snakeyaml-2.0.jar"
fi

COMPILE_CLASSPATH="$CLASSES_DIR:$JSON_JAR:$DISRUPTOR_JAR:$YAML_JAR:$ERSAP_HOME/lib/ersap-java-*.jar"
RUNTIME_CLASSPATH="$CLASSES_DIR:$SCRIPTS_DIR:$JSON_JAR:$DISRUPTOR_JAR:$YAML_JAR:$ERSAP_HOME/lib/ersap-java-*.jar:$ERSAP_HOME/lib/jeromq-*.jar"

# Ensure the scripts directory exists
mkdir -p "$SCRIPTS_DIR"

# Compile the scripts if needed
if [ ! -f "$SCRIPTS_DIR/scripts/MockPcapServer.class" ]; then
    echo "Compiling scripts..."
    javac -d "$SCRIPTS_DIR" -cp "$COMPILE_CLASSPATH" "$SCRIPT_DIR/MockPcapServer.java"
fi

if [ ! -f "$SCRIPTS_DIR/scripts/SimpleMultiSocketTest.class" ]; then
    echo "Compiling test client..."
    javac -d "$SCRIPTS_DIR" -cp "$COMPILE_CLASSPATH" "$SCRIPT_DIR/SimpleMultiSocketTest.java"
fi

# Start servers in the background
java -cp "$RUNTIME_CLASSPATH" scripts.MockPcapServer 9000 "$PCAP_FILE" &
SERVER1_PID=$!
java -cp "$RUNTIME_CLASSPATH" scripts.MockPcapServer 9001 "$PCAP_FILE" &
SERVER2_PID=$!

# Wait for servers to start
echo "Waiting for servers to start..."
sleep 2

# Run test client
echo "Running test client with ${TIMEOUT}s timeout..."
java -cp "$RUNTIME_CLASSPATH" scripts.SimpleMultiSocketTest "$CONFIG_FILE" "$TIMEOUT"

# Clean up
echo "Cleaning up..."
kill $SERVER1_PID $SERVER2_PID 2>/dev/null
rm -rf "$TEMP_DIR"

echo "Test completed." 