#!/bin/bash

# Check if JSON file is provided
if [ $# -lt 1 ]; then
    echo "Usage: $0 <json_file>"
    exit 1
fi

JSON_FILE="$1"

# Check if JSON file exists
if [ ! -f "$JSON_FILE" ]; then
    echo "JSON file not found: $JSON_FILE"
    exit 1
fi

# Set up paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BUILD_DIR="$PROJECT_DIR/build"
CLASSES_DIR="$BUILD_DIR/classes/java/main"
SCRIPTS_DIR="$BUILD_DIR/classes/java/scripts"

# Set up classpath
JSON_JAR="$PROJECT_DIR/lib/json-20231013.jar"

# Download JSON library if it doesn't exist
if [ ! -f "$JSON_JAR" ]; then
    echo "Downloading JSON library..."
    mkdir -p "$(dirname "$JSON_JAR")"
    curl -s -o "$JSON_JAR" "https://repo1.maven.org/maven2/org/json/json/20231013/json-20231013.jar"
fi

COMPILE_CLASSPATH="$CLASSES_DIR:$JSON_JAR"
RUNTIME_CLASSPATH="$CLASSES_DIR:$SCRIPTS_DIR:$JSON_JAR"

# Ensure the scripts directory exists
mkdir -p "$SCRIPTS_DIR"

# Compile the test class
echo "Compiling JsonTest..."
javac -d "$SCRIPTS_DIR" -cp "$COMPILE_CLASSPATH" "$SCRIPT_DIR/JsonTest.java"

# Run the test
echo "Running JsonTest with $JSON_FILE..."
java -cp "$RUNTIME_CLASSPATH" scripts.JsonTest "$JSON_FILE" 