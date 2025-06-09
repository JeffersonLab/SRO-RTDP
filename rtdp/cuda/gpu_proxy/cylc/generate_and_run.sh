#!/bin/bash
# NOTE: Run this script from the project root directory (e.g., ~/RTDP/cylc-generator)
# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

# Change to the project root directory (assuming script is in rtdp/cuda/gpu_proxy/cylc/)
cd "$SCRIPT_DIR/../../../../"

# Verify we're in the correct directory
if [ ! -d "rtdp" ]; then
    echo "Error: Could not find project root directory"
    exit 1
fi


set -e

# Example: Generate the GPU Proxy workflow
python -m rtdp.cli.rtdpcli generate \
  --config rtdp/cuda/gpu_proxy/cylc/cli-config-example.yml \
  --output rtdp/cuda/gpu_proxy/cylc/generated \
  --template rtdp/cuda/gpu_proxy/cylc/flow.cylc.j2

echo "Workflow generated in rtdp/cuda/gpu_proxy/cylc/generated"

# Example: Run the generated workflow
python -m rtdp.cli.rtdpcli run \
  --workflow rtdp/cuda/gpu_proxy/cylc/generated 