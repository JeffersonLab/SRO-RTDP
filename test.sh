#!/bin/bash

echo "=== Testing Multi-Component Workflows with Logging Options ==="

echo ""
echo "1. Testing GPU workflow with consolidated logging (default):"
python3 -m rtdp.cli.rtdpcli generate --config rtdp/cylc/multi_gpu_proxy/example_config.yml --output gpu_workflow_consolidated --workflow-type multi_gpu_proxy --consolidated-logging

echo ""
echo "2. Testing GPU workflow with separate logging:"
python3 -m rtdp.cli.rtdpcli generate --config rtdp/cylc/multi_gpu_proxy/example_config.yml --output gpu_workflow_separate --workflow-type multi_gpu_proxy --no-consolidated-logging

echo ""
echo "3. Testing CPU emulator workflow with consolidated logging (default):"
python3 -m rtdp.cli.rtdpcli generate --config rtdp/cylc/multi_cpu_emu/test_config.yml --output cpu_workflow_consolidated --workflow-type multi_cpu_emu --consolidated-logging

echo ""
echo "4. Testing CPU emulator workflow with separate logging:"
python3 -m rtdp.cli.rtdpcli generate --config rtdp/cylc/multi_cpu_emu/test_config.yml --output cpu_workflow_separate --workflow-type multi_cpu_emu --no-consolidated-logging

echo ""
echo "5. Testing mixed components workflow with consolidated logging (default):"
python3 -m rtdp.cli.rtdpcli generate --config rtdp/cylc/multi_mixed/test_config.yml --output mixed_workflow_consolidated --workflow-type multi_mixed --consolidated-logging

echo ""
echo "6. Testing mixed components workflow with separate logging:"
python3 -m rtdp.cli.rtdpcli generate --config rtdp/cylc/multi_mixed/test_config.yml --output mixed_workflow_separate --workflow-type multi_mixed --no-consolidated-logging

echo ""
echo "=== CLI Option Summary ==="
echo "✅ --consolidated-logging: Enable consolidated logging (default)"
echo "📁 --no-consolidated-logging: Use separate log files for each component"
echo ""
echo "Generated workflows:"
echo "- gpu_workflow_consolidated (consolidated logging enabled)"
echo "- gpu_workflow_separate (consolidated logging disabled)"
echo "- cpu_workflow_consolidated (consolidated logging enabled)"
echo "- cpu_workflow_separate (consolidated logging disabled)"
echo "- mixed_workflow_consolidated (consolidated logging enabled)"
echo "- mixed_workflow_separate (consolidated logging disabled)"
echo ""
echo "To run a workflow, use:"
echo "python3 -m rtdp.cli.rtdpcli run --workflow gpu_workflow_consolidated"
echo "python3 -m rtdp.cli.rtdpcli run --workflow gpu_workflow_separate"
echo "python3 -m rtdp.cli.rtdpcli run --workflow cpu_workflow_consolidated"
echo "python3 -m rtdp.cli.rtdpcli run --workflow cpu_workflow_separate"
echo "python3 -m rtdp.cli.rtdpcli run --workflow mixed_workflow_consolidated"
echo "python3 -m rtdp.cli.rtdpcli run --workflow mixed_workflow_separate"
echo ""
echo "Logging differences:"
echo "- Consolidated logging: All component logs combined into a single file with timestamps and component markers"
echo "- Separate logging: Each component has its own log directory with stdout.log, stderr.log, and apptainer.log files"
