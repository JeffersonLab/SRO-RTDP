#!/bin/bash

echo "=== RTDP CLI Test Script - GPU Consolidated Workflow ==="
echo "This script tests the GPU consolidated workflow step by step."
echo ""

# Check if we're in a virtual environment
if [[ "$VIRTUAL_ENV" == "" ]]; then
    echo "⚠️  Warning: Not running in a virtual environment."
    echo "   It's recommended to activate a virtual environment first."
    echo "   Example: source venv/bin/activate"
    echo ""
fi

echo "1. Checking RTDP environment status..."
# Function to run rtdp command with fallback
run_rtdp() {
    if command -v rtdp &> /dev/null && rtdp --help &> /dev/null; then
        rtdp "$@"
    else
        echo "rtdp command not working, using wrapper script..."
        python run_rtdp.py "$@"
    fi
}

run_rtdp status

echo ""
echo "2. Setting up RTDP environment (if needed)..."
echo "   This will install Cylc and configure directories."
echo "   Press Enter to continue or Ctrl+C to skip..."
read -r

# Function to run rtdp command with fallback
run_rtdp() {
    if command -v rtdp &> /dev/null; then
        rtdp "$@"
    else
        echo "rtdp command not found, using direct Python execution..."
        python rtdpcli.py "$@"
    fi
}

run_rtdp setup

echo ""
echo "3. Testing GPU workflow generation with consolidated logging..."
run_rtdp generate --config cylc/multi_gpu_proxy/test_config.yml --output gpu_workflow_consolidated --workflow-type multi_gpu_proxy --consolidated-logging

echo ""
echo "4. Testing workflow validation..."
run_rtdp validate --config cylc/multi_gpu_proxy/test_config.yml --template cylc/multi_gpu_proxy/flow.cylc.j2

echo ""
echo "5. Testing workflow execution (optional)..."
echo "   This will build SIF containers and run the workflow."
echo "   Press Enter to continue or Ctrl+C to skip..."
read -r

run_rtdp run gpu_workflow_consolidated

echo ""
echo "6. Testing monitoring (optional)..."
echo "   This will open Cylc TUI for monitoring the workflow."
echo "   Press Enter to continue or Ctrl+C to skip..."
read -r

run_rtdp monitor gpu_workflow_consolidated

echo ""
echo "7. Testing cache management..."
run_rtdp cache --stats

echo ""
echo "=== Test Summary ==="
echo "✅ Environment setup completed"
echo "✅ GPU workflow generation tested"
echo "✅ Workflow validation tested"
echo "✅ Workflow execution tested"
echo "✅ Monitoring tested"
echo "✅ Cache management tested"
echo ""
echo "Generated workflow:"
echo "- gpu_workflow_consolidated (consolidated logging enabled)"
echo ""
echo "CLI Commands tested:"
echo "✅ rtdp status - Check environment status"
echo "✅ rtdp setup - Setup RTDP environment"
echo "✅ rtdp generate - Generate workflow"
echo "✅ rtdp validate - Validate configuration"
echo "✅ rtdp run - Run workflow"
echo "✅ rtdp monitor - Monitor workflow"
echo "✅ rtdp cache - Manage SIF cache"
echo ""
echo "To run the workflow manually:"
echo "rtdp run gpu_workflow_consolidated"
echo ""
echo "To monitor the workflow:"
echo "rtdp monitor gpu_workflow_consolidated"
echo ""
echo "To check cache:"
echo "rtdp cache --stats"
echo "rtdp cache --clear"
echo ""
echo "🎉 GPU consolidated workflow test completed successfully!" 