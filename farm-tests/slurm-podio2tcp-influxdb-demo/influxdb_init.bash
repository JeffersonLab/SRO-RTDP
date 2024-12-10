#!/bin/bash
set -x

# Confirm mount
# mount | grep -E 'influxdb' 

INFLUXDB_PORT=$1

INFLUXDB_LOGFILE=influxdb_$(date -u +%s).log  # logfile ends with timestamp in seconds
touch ${INFLUXDB_LOGFILE}

# Set InfluxDB params via env vars
WORKDIR_PREFIX="${HOME}/projects/SRO-RTDP/farm-tests"
source ${WORKDIR_PREFIX}/slurm-podio2tcp-influxdb-demo/influxdb_setenv.bash

node_ip=$(hostname -I | awk '{print $1}')

# Start InfluxDB in the background with custom HTTP port
# Need "&" at the end
influxd --log-level=info \
        --bolt-path /var/lib/influxdb2/influxd.bolt \
        --engine-path /var/lib/influxdb2/engine \
        --http-bind-address=:${INFLUXDB_PORT} > "${INFLUXDB_LOGFILE}" 2>&1

# Wait for InfluxDB to be fully up
sleep 10

# Initialize InfluxDB with necessary variables.
# This will create an auth file at etc/influxdb2/influx-configs
influx setup \
  --username "${INFLUXDB_USERNAME}" \
  --password "${INFLUXDB_PASSWORD}" \
  --org "${INFLUXDB_ORG}" \
  --bucket "${INFLUXDB_BUCKET}" \
  --retention "${INFLUXDB_RETENTION}" \
  --host http://${node_ip}:${INFLUXDB_PORT} \
  --force

echo -e "\nInfluxDB setup completed, see logs at: ${INFLUXDB_LOGFILE}\n"
