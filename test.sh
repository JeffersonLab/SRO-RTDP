#!/bin/bash

python3 -m rtdp.cli.rtdpcli generate --config gpu_config.yml --output gpu_workflow --workflow-type multi_gpu_proxy
# python3 -m rtdp.cli.rtdpcli run --workflow gpu_workflow
