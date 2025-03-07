#!/bin/bash

# Script to monitor the Pcap2Streams and ERSAP test execution
# Provides real-time feedback on server and client status

# Set the project directories
INTEGRATION_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PCAP2STREAMS_DIR="/workspace/src/utilities/java/pcap2streams"
RESULTS_DIR="$INTEGRATION_DIR/results"

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

# Main monitoring loop
monitor() {
    local interval=${1:-5}  # Default update interval: 5 seconds
    local duration=${2:-300}  # Default duration: 5 minutes
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

# Parse command line arguments
INTERVAL=5
DURATION=300

while [[ $# -gt 0 ]]; do
    case $1 in
        --interval|-i)
            INTERVAL="$2"
            shift 2
            ;;
        --duration|-d)
            DURATION="$2"
            shift 2
            ;;
        --help|-h)
            echo "Usage: $0 [options]"
            echo "Options:"
            echo "  --interval, -i SECONDS   Update interval in seconds (default: 5)"
            echo "  --duration, -d SECONDS   Total monitoring duration in seconds (default: 300)"
            echo "  --help, -h               Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Ensure results directory exists
mkdir -p "$RESULTS_DIR"

# Start monitoring
monitor $INTERVAL $DURATION 