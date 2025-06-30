#!/bin/bash

python3 -m rtdp.cli.rtdpcli generate --config cpu_config.yml --output cpu_workflow --workflow-type multi_cpu_emu
python3 -m rtdp.cli.rtdpcli run --workflow cpu_workflow
