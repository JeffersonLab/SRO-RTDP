#!/bin/bash

python3 -m rtdp.cli.rtdpcli generate --config rtdp/cylc/multi_mixed/example_config.yml --output mixed_workflow --workflow-type multi_mixed
python3 -m rtdp.cli.rtdpcli run --workflow mixed_workflow
