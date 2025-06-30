#!/bin/bash

python3 -m rtdp.cli.rtdpcli generate --config multi_mixed/example_config.yml --output multi_mixed --workflow-type multi_mixed
python3 -m rtdp.cli.rtdpcli run --workflow multi_mixed
