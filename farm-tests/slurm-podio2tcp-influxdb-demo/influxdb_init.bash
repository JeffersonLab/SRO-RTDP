#!/bin/bash

INFLUXDB_USERNAME=epsci
INFLUXDB_PASSWORD=epsci_EPSCI
INFLUXDB_ORG=epsci
INFLUXDB_BUCKET=bucket_podio2tcp
INFLUXDB_RETENTION=7d  # Retain for 7 days

node_ip=$(hostname -I | awk '{print $1}')

# Start InfluxDB in the background with custom HTTP port
influxd --http-bind-address=:43900 &

# Wait for InfluxDB to be fully up
sleep 10

# Initialize InfluxDB with environment variables
influx setup \
  --username "${INFLUXDB_USERNAME}" \
  --password "${INFLUXDB_PASSWORD}" \
  --org "${INFLUXDB_ORG}" \
  --bucket "${INFLUXDB_BUCKET}" \
  --retention "${INFLUXDB_RETENTION}" \
  --host http://${node_ip}:43900 \
  --force
  