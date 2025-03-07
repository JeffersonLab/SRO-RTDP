#!/bin/bash

# Wrapper script to run the Pcap2Streams integration test with monitoring

# Set the project directory
INTEGRATION_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPTS_DIR="$INTEGRATION_DIR/scripts"

# Default values
MONITOR=true
TEST_ARGS=""
MONITOR_INTERVAL=5
MONITOR_DURATION=300

# Function to display usage
usage() {
    echo "Usage: $0 [options]"
    echo "Options:"
    echo "  --no-monitor           Run test without monitoring"
    echo "  --monitor-interval N   Set monitoring interval in seconds (default: 5)"
    echo "  --monitor-duration N   Set monitoring duration in seconds (default: 300)"
    echo "  --pcap FILE            Use specified PCAP file for test"
    echo "  --test                 Use small test PCAP file (default)"
    echo "  --full                 Use full PCAP file from /scratch"
    echo "  --help, -h             Show this help message"
    exit 0
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
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
        --pcap)
            if [ -f "$2" ]; then
                TEST_ARGS="$TEST_ARGS --pcap $2"
                shift 2
            else
                echo "Error: PCAP file not found at $2"
                echo "Please provide a valid PCAP file path"
                exit 1
            fi
            ;;
        --test)
            TEST_ARGS="$TEST_ARGS --test"
            shift
            ;;
        --full)
            TEST_ARGS="$TEST_ARGS --full"
            shift
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

# Check if scripts exist
if [ ! -f "$SCRIPTS_DIR/run_ersap_pcap2streams_test.sh" ]; then
    echo "Error: Test script not found at $SCRIPTS_DIR/run_ersap_pcap2streams_test.sh"
    exit 1
fi

if [ "$MONITOR" = true ] && [ ! -f "$SCRIPTS_DIR/monitor_test.sh" ]; then
    echo "Error: Monitor script not found at $SCRIPTS_DIR/monitor_test.sh"
    exit 1
fi

# Make scripts executable if they aren't already
chmod +x "$SCRIPTS_DIR/run_ersap_pcap2streams_test.sh"
[ "$MONITOR" = true ] && chmod +x "$SCRIPTS_DIR/monitor_test.sh"

# Run the test in the background if monitoring is enabled
if [ "$MONITOR" = true ]; then
    echo "Starting Pcap2Streams integration test with monitoring..."
    echo "Test arguments: $TEST_ARGS"
    echo "Monitor interval: $MONITOR_INTERVAL seconds"
    echo "Monitor duration: $MONITOR_DURATION seconds"
    
    # Start the test in the background
    "$SCRIPTS_DIR/run_ersap_pcap2streams_test.sh" $TEST_ARGS &
    TEST_PID=$!
    
    # Wait a moment for the test to start
    sleep 2
    
    # Start the monitoring
    "$SCRIPTS_DIR/monitor_test.sh" --interval $MONITOR_INTERVAL --duration $MONITOR_DURATION
    
    # Wait for the test to complete if it's still running
    if kill -0 $TEST_PID 2>/dev/null; then
        echo "Test is still running. Waiting for it to complete..."
        wait $TEST_PID
    fi
else
    # Run the test in the foreground
    echo "Starting Pcap2Streams integration test without monitoring..."
    echo "Test arguments: $TEST_ARGS"
    "$SCRIPTS_DIR/run_ersap_pcap2streams_test.sh" $TEST_ARGS
fi

echo "Integration test completed." 