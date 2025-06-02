#!/bin/bash
# NOTE: Run this script from the project root directory (e.g., ~/RTDP/cylc-generator)
set -e

# Example: Generate the CPU Emulator workflow
python -m rtdp.cli.rtdpcli generate \
  --config rtdp/cpp/cpu_emu/cylc/cli-config-example.yml \
  --output rtdp/cpp/cpu_emu/cylc/generated \
  --template rtdp/cpp/cpu_emu/cylc/flow.cylc.j2

echo "Workflow generated in rtdp/cpp/cpu_emu/cylc/generated"

# Example: Run the generated workflow
python -m rtdp.cli.rtdpcli run \
  --workflow rtdp/cpp/cpu_emu/cylc/generated 