#!/bin/bash
# NOTE: Run this script from the project root directory (e.g., ~/RTDP/cylc-generator)
set -e

# Example: Generate the Chain Workflow
python -m rtdp.cli.rtdpcli generate \
  --config rtdp/cylc/chain_workflow/cli-config-example.yml \
  --output rtdp/cylc/chain_workflow/generated \
  --template rtdp/cylc/chain_workflow/flow.cylc.j2

echo "Workflow generated in rtdp/cylc/chain_workflow/generated"

# Example: Run the generated workflow
python -m rtdp.cli.rtdpcli run \
  --workflow rtdp/cylc/chain_workflow/generated 