#!/bin/bash
# NOTE: Run this script from the project root directory (e.g., ~/RTDP/cylc-generator)
set -e

# Get the directory where the script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

# Change to the project root directory (assuming script is in rtdp/cpp/cpu_emu/cylc/)
cd "$SCRIPT_DIR/../../../../"

# Verify we're in the correct directory
if [ ! -d "rtdp" ]; then
    echo "Error: Could not find project root directory"
    exit 1
fi

# Example: Generate the CPU Emulator workflow
python -m rtdp.cli.rtdpcli generate \
  --config rtdp/cpp/cpu_emu/cylc/cli-config-example.yml \
  --output rtdp/cpp/cpu_emu/cylc/generated \
  --template rtdp/cpp/cpu_emu/cylc/flow.cylc.j2

echo "Workflow generated in rtdp/cpp/cpu_emu/cylc/generated"

# Example: Run the generated workflow
python -m rtdp.cli.rtdpcli run \
  --workflow rtdp/cpp/cpu_emu/cylc/generated 