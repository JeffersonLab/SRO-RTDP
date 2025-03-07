#!/bin/bash

# Integrated script to run the ERSAP test using Pcap2Streams as the data server
# This script combines the functionality of run_ersap_pcap2streams_test.sh and run_integration_test.sh

# Set the project directories
INTEGRATION_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PCAP_STREAM_DIR="/workspace/src/utilities/java/ersapActors/pcap-stream-source"
PCAP2STREAMS_DIR="/workspace/src/utilities/java/pcap2streams"
SCRIPTS_DIR="$INTEGRATION_DIR/scripts"
CONFIG_DIR="$INTEGRATION_DIR/config"
RESULTS_DIR="$INTEGRATION_DIR/results"

# Default PCAP file location
PCAP_FILE="/scratch/jeng-yuantsai/CLAS12_ECAL_PCAL_DC_2024-05-15_17-12-30.pcap"
# For testing with a smaller file
TEST_PCAP_FILE="$PCAP_STREAM_DIR/test-data/test.pcap"

# Use test file by default for faster testing
DEFAULT_PCAP_FILE="$TEST_PCAP_FILE"

# Default values for monitoring
MONITOR=true
MONITOR_INTERVAL=5
MONITOR_DURATION=300

# Function to display usage
usage() {
    echo "Usage: $0 [options]"
    echo "Options:"
    echo "  --pcap FILE            Use specified PCAP file"
    echo "  --test                 Use small test PCAP file (default)"
    echo "  --full                 Use full PCAP file from /scratch"
    echo "  --no-monitor           Run test without monitoring"
    echo "  --monitor-interval N   Set monitoring interval in seconds (default: 5)"
    echo "  --monitor-duration N   Set monitoring duration in seconds (default: 300)"
    echo "  --help, -h             Show this help message"
    exit 0
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --pcap)
            if [ -f "$2" ]; then
                PCAP_FILE="$2"
                USE_TEST_FILE=false  # Set USE_TEST_FILE to false when --pcap is used
                shift 2
            else
                echo "Error: PCAP file not found at $2"
                echo "Please provide a valid PCAP file path"
                exit 1
            fi
            ;;
        --test)
            USE_TEST_FILE=true
            shift
            ;;
        --full)
            USE_TEST_FILE=false
            shift
            ;;
        --no-monitor)
            MONITOR=false
            shift
            ;;
        --monitor-interval)
            MONITOR_INTERVAL="$2"
            shift 2
            ;;
        --monitor-duration)
            MONITOR_DURATION="$2"
            shift 2
            ;;
        --help|-h)
            usage
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Determine which PCAP file to use
if [[ "$USE_TEST_FILE" == "false" ]]; then
    SELECTED_PCAP_FILE="$PCAP_FILE"
    echo "Using full PCAP file: $SELECTED_PCAP_FILE"
else
    SELECTED_PCAP_FILE="$DEFAULT_PCAP_FILE"
    echo "Using test PCAP file: $SELECTED_PCAP_FILE"
fi

# Check if the PCAP file exists
if [ ! -f "$SELECTED_PCAP_FILE" ]; then
    echo "Error: PCAP file not found at $SELECTED_PCAP_FILE"
    exit 1
fi

# Create directories if they don't exist
mkdir -p "$CONFIG_DIR" "$RESULTS_DIR"

# Function to clean up processes on exit
cleanup() {
    echo "Cleaning up processes..."
    # Kill Pcap2Streams process
    pkill -f "java.*Pcap2Streams" 2>/dev/null
    echo "Cleanup complete."
}

# Set up trap to ensure cleanup on script exit
trap cleanup EXIT

# Function to monitor the test execution
monitor_test() {
    local interval=$1
    local duration=$2
    
    # Colors for output
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[0;33m'
    BLUE='\033[0;34m'
    NC='\033[0m' # No Color
    
    # Function to check if a process is running
    is_process_running() {
        pgrep -f "$1" >/dev/null
        return $?
    }
    
    # Function to count active connections
    count_active_connections() {
        netstat -tn | grep -E ":(9[0-9]{3})" | grep ESTABLISHED | wc -l
    }
    
    # Function to display server status
    display_server_status() {
        if is_process_running "java.*Pcap2Streams"; then
            echo -e "${GREEN}Pcap2Streams is running${NC}"
            
            # Get server ports
            PORTS=$(netstat -tlnp 2>/dev/null | grep -E ":(9[0-9]{3})" | awk '{print $4}' | cut -d':' -f2 | sort -n)
            
            if [ -n "$PORTS" ]; then
                echo -e "${BLUE}Active server ports:${NC}"
                for PORT in $PORTS; do
                    CONN_COUNT=$(netstat -tn | grep ":$PORT" | grep ESTABLISHED | wc -l)
                    if [ $CONN_COUNT -gt 0 ]; then
                        echo -e "  Port ${PORT}: ${GREEN}$CONN_COUNT active connections${NC}"
                    else
                        echo -e "  Port ${PORT}: ${YELLOW}No active connections${NC}"
                    fi
                done
            else
                echo -e "${YELLOW}No active server ports detected${NC}"
            fi
        else
            echo -e "${RED}Pcap2Streams is not running${NC}"
        fi
    }
    
    # Function to display client status
    display_client_status() {
        if is_process_running "java.*SimpleMultiSocketTest"; then
            echo -e "${GREEN}SimpleMultiSocketTest client is running${NC}"
            CONN_COUNT=$(count_active_connections)
            echo -e "${BLUE}Total active connections: ${GREEN}$CONN_COUNT${NC}"
        else
            echo -e "${YELLOW}SimpleMultiSocketTest client is not running${NC}"
        fi
    }
    
    # Function to display system resource usage
    display_resource_usage() {
        echo -e "${BLUE}System Resource Usage:${NC}"
        echo -e "  CPU Usage: $(top -bn1 | grep "Cpu(s)" | awk '{print $2}')%"
        echo -e "  Memory Usage: $(free -m | awk 'NR==2{printf "%.2f%%", $3*100/$2}')"
        
        # Java process memory usage
        if is_process_running "java"; then
            JAVA_PID=$(pgrep -f "java" | head -1)
            if [ -n "$JAVA_PID" ]; then
                JAVA_MEM=$(ps -o rss= -p $JAVA_PID | awk '{printf "%.2f MB", $1/1024}')
                echo -e "  Java Process Memory: $JAVA_MEM"
            fi
        fi
    }
    
    local elapsed=0
    
    echo -e "${BLUE}Starting monitoring (update every ${interval}s, duration: ${duration}s)${NC}"
    
    # Create a timestamp for the log file
    TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
    LOG_FILE="$RESULTS_DIR/monitor_log_$TIMESTAMP.txt"
    
    echo "Monitoring started at $(date)" > "$LOG_FILE"
    
    while [ $elapsed -lt $duration ]; do
        clear
        echo -e "${BLUE}=== Pcap2Streams Integration Test Monitor ===${NC}"
        echo -e "${BLUE}Elapsed time: ${elapsed}s / ${duration}s${NC}"
        echo ""
        
        echo -e "${BLUE}=== Server Status ===${NC}"
        display_server_status
        echo ""
        
        echo -e "${BLUE}=== Client Status ===${NC}"
        display_client_status
        echo ""
        
        echo -e "${BLUE}=== Resource Usage ===${NC}"
        display_resource_usage
        echo ""
        
        # Log the current status
        {
            echo "=== Status at $(date) (Elapsed: ${elapsed}s) ==="
            echo "Server running: $(is_process_running "java.*Pcap2Streams" && echo "Yes" || echo "No")"
            echo "Client running: $(is_process_running "java.*SimpleMultiSocketTest" && echo "Yes" || echo "No")"
            echo "Active connections: $(count_active_connections)"
            echo ""
        } >> "$LOG_FILE"
        
        # Check if both processes have exited
        if ! is_process_running "java.*Pcap2Streams" && ! is_process_running "java.*SimpleMultiSocketTest"; then
            echo -e "${YELLOW}Both server and client have exited. Monitoring stopped.${NC}"
            echo "Both server and client exited at $(date) (Elapsed: ${elapsed}s)" >> "$LOG_FILE"
            break
        fi
        
        sleep $interval
        elapsed=$((elapsed + interval))
    done
    
    echo -e "${BLUE}Monitoring completed. Log saved to: ${LOG_FILE}${NC}"
}

# Main test execution function
run_test() {
    # Step 1: Start Pcap2Streams to analyze the PCAP file and create servers
    echo "Starting Pcap2Streams with PCAP file: $SELECTED_PCAP_FILE"
    cd "$PCAP2STREAMS_DIR"
    ./scripts/run_pcap2streams.sh "$SELECTED_PCAP_FILE" &
    PCAP2STREAMS_PID=$!
    
    # Wait for Pcap2Streams to initialize and generate the configuration
    echo "Waiting for Pcap2Streams to initialize and generate configuration..."
    sleep 10
    
    # Step 2: Check if the IP-based configuration file was generated
    PCAP2STREAMS_CONFIG="$PCAP2STREAMS_DIR/custom-config/ip-based-config.json"
    if [ ! -f "$PCAP2STREAMS_CONFIG" ]; then
        echo "Error: Pcap2Streams configuration file not found at $PCAP2STREAMS_CONFIG"
        cleanup
        exit 1
    fi
    
    # Step 3: Create the configuration adapter script
    echo "Creating configuration adapter..."
    cat > "$SCRIPTS_DIR/config_adapter.py" << 'EOF'
#!/usr/bin/env python3
import json
import sys
import os

def adapt_config(input_file, output_file):
    """
    Adapt the Pcap2Streams IP-based configuration to the format expected by SimpleMultiSocketTest
    """
    try:
        with open(input_file, 'r') as f:
            ip_config = json.load(f)
        
        # Create the multi-socket configuration format
        multi_socket_config = {"connections": []}
        
        # Convert each IP-based connection to the multi-socket format
        for conn in ip_config.get("connections", []):
            multi_socket_config["connections"].append({
                "host": conn.get("host", "localhost"),
                "port": conn.get("port", 9000),
                "buffer_size": conn.get("buffer_size", 8192),
                "read_timeout": conn.get("read_timeout", 1000),
                "connection_timeout": conn.get("connection_timeout", 1000)
            })
        
        # Write the adapted configuration
        with open(output_file, 'w') as f:
            json.dump(multi_socket_config, f, indent=2)
        
        print(f"Successfully adapted configuration from {input_file} to {output_file}")
        print(f"Created configuration with {len(multi_socket_config['connections'])} connections")
        return True
    
    except Exception as e:
        print(f"Error adapting configuration: {str(e)}")
        return False

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python config_adapter.py <input_config> <output_config>")
        sys.exit(1)
    
    input_file = sys.argv[1]
    output_file = sys.argv[2]
    
    if not os.path.exists(input_file):
        print(f"Error: Input file {input_file} not found")
        sys.exit(1)
    
    success = adapt_config(input_file, output_file)
    sys.exit(0 if success else 1)
EOF
    
    # Make the adapter script executable
    chmod +x "$SCRIPTS_DIR/config_adapter.py"
    
    # Step 4: Run the configuration adapter to create the multi-socket configuration
    MULTI_SOCKET_CONFIG="$CONFIG_DIR/multi-socket-config.json"
    echo "Adapting Pcap2Streams configuration to multi-socket format..."
    python3 "$SCRIPTS_DIR/config_adapter.py" "$PCAP2STREAMS_CONFIG" "$MULTI_SOCKET_CONFIG"
    
    if [ ! -f "$MULTI_SOCKET_CONFIG" ]; then
        echo "Error: Failed to create multi-socket configuration"
        cleanup
        exit 1
    fi
    
    echo "Using adapted configuration file: $MULTI_SOCKET_CONFIG"
    
    # Step 5: Compile the test client
    echo "Compiling the test client..."
    cd "$PCAP_STREAM_DIR"
    
    # First, make sure the source directory exists
    mkdir -p build/classes/java/scripts
    
    # Compile with the correct classpath
    javac -d build/classes/java/scripts -cp "build/classes/java/main:lib/json-20231013.jar:lib/disruptor-3.4.4.jar:lib/snakeyaml-2.0.jar:src/main/java" "$INTEGRATION_DIR/scripts/SimpleMultiSocketTest.java"
    
    # Step 6: Run the SimpleMultiSocketTest with the adapted configuration
    echo "Running SimpleMultiSocketTest with the adapted configuration..."
    TEST_DURATION=60  # seconds
    
    if [ "$MONITOR" = true ]; then
        # Run the test in the background if monitoring is enabled
        java -cp "build/classes/java/scripts:build/classes/java/main:lib/json-20231013.jar:lib/disruptor-3.4.4.jar:lib/snakeyaml-2.0.jar:src/main/java" scripts.SimpleMultiSocketTest "$MULTI_SOCKET_CONFIG" $TEST_DURATION &
        TEST_PID=$!
        
        # Wait a moment for the test to start
        sleep 2
        
        # Start the monitoring
        monitor_test $MONITOR_INTERVAL $MONITOR_DURATION
        
        # Wait for the test to complete if it's still running
        if kill -0 $TEST_PID 2>/dev/null; then
            echo "Test is still running. Waiting for it to complete..."
            wait $TEST_PID
        fi
    else
        # Run the test in the foreground
        java -cp "build/classes/java/scripts:build/classes/java/main:lib/json-20231013.jar:lib/disruptor-3.4.4.jar:lib/snakeyaml-2.0.jar:src/main/java" scripts.SimpleMultiSocketTest "$MULTI_SOCKET_CONFIG" $TEST_DURATION
    fi
    
    # Step 7: Save test results
    TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
    RESULT_FILE="$RESULTS_DIR/test_results_$TIMESTAMP.log"
    echo "Test completed at $(date)" > "$RESULT_FILE"
    echo "PCAP file used: $SELECTED_PCAP_FILE" >> "$RESULT_FILE"
    echo "Configuration:" >> "$RESULT_FILE"
    cat "$MULTI_SOCKET_CONFIG" >> "$RESULT_FILE"
    echo "Test duration: $TEST_DURATION seconds" >> "$RESULT_FILE"
    
    echo "Test completed. Results saved to $RESULT_FILE"
}

# Run the test
echo "Starting integrated Pcap2Streams test..."
if [ "$MONITOR" = true ]; then
    echo "Monitoring enabled (interval: ${MONITOR_INTERVAL}s, duration: ${MONITOR_DURATION}s)"
else
    echo "Monitoring disabled"
fi

run_test

echo "Cleaning up..."
# Cleanup is handled by the trap 