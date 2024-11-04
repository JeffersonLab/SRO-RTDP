#!/bin/bash

# Parse command line arguments
PROMETHEUS_PORT=$1
WORKDIR_PREFIX=$2
PROMETHEUS_SIF=$3
PROM_DATA_DIR=$4
CONFIG_DIR=${5:-"config"}

# Validate required parameters
if [ -z "$PROMETHEUS_PORT" ] || [ -z "$WORKDIR_PREFIX" ] || [ -z "$PROMETHEUS_SIF" ] || [ -z "$PROM_DATA_DIR" ]; then
    echo "Usage: $0 <prometheus_port> <workdir_prefix> <prometheus_sif> <prom_data_dir> [config_dir]"
    exit 1
fi

# For debug usage
set -x

PROMETHEUS_SIF_PATH=${WORKDIR_PREFIX}/sifs/${PROMETHEUS_SIF}

apptainer exec \
  --bind ${WORKDIR_PREFIX}/${CONFIG_DIR}:/config \
  --bind ${WORKDIR_PREFIX}/${PROM_DATA_DIR}:/prometheus \
  ${PROMETHEUS_SIF_PATH} \
  prometheus \
    --web.listen-address=":${PROMETHEUS_PORT}" \
    --config.file=/config/prometheus-config.yml \
    --storage.tsdb.path=/prometheus