#!/bin/bash
# NOTE: Run this script from the project root directory (e.g., ~/RTDP/cylc-generator)
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