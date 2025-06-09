#!/bin/bash
# NOTE: Run this script from the project root directory (e.g., ~/RTDP/cylc-generator)
set -e

# Get the directory where the script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

# Change to the project root directory (assuming script is in rtdp/cylc/chain_workflow/)
cd "$SCRIPT_DIR/../../../"

# Verify we're in the correct directory
if [ ! -d "rtdp" ]; then
    echo "Error: Could not find project root directory"
    exit 1
fi

# Example: Generate the Chain Workflow
python -m rtdp.cli.rtdpcli generate \
  --config rtdp/cylc/chain_workflow/cli-config-example.yml \
  --output rtdp/cylc/chain_workflow/generated \
  --template rtdp/cylc/chain_workflow/flow.cylc.j2

echo "Workflow generated in rtdp/cylc/chain_workflow/generated"

# Example: Run the generated workflow
python -m rtdp.cli.rtdpcli run \
  --workflow rtdp/cylc/chain_workflow/generated 