#!/bin/bash

# --------------------------- #
#      Define Variables       #
# --------------------------- #
# Parse command line arguments
PROCESS_EXPORTER_PORT=$1
APP_PORT=$2
WORKDIR_PREFIX=$3
PROCESS_EXPORTER_SIF=$4
IPERF3_PATH=$5
IPERF3_LIB_PATH=$6
CONFIG_DIR=${7:-"config"}  # Default to "config" if not provided

# Validate required parameters
if [ -z "$PROCESS_EXPORTER_PORT" ] || [ -z "$APP_PORT" ] || [ -z "$WORKDIR_PREFIX" ] || \
   [ -z "$PROCESS_EXPORTER_SIF" ] || [ -z "$IPERF3_PATH" ] || [ -z "$IPERF3_LIB_PATH" ]; then
    echo "Usage: $0 <process_exporter_port> <app_port> <workdir_prefix> <process_exporter_sif> <iperf3_path> <iperf3_lib_path> [config_dir]"
    exit 1
fi

# Set derived variables

node_name=$(hostname)
node_ip=$(hostname -i)
echo "Hostname: $node_name"
echo -e "IPv4 address: $node_ip\n"

cd $WORKDIR_PREFIX
# cat current working directory to a file
echo "$(pwd)" > current_working_directory.txt

# --------------------------- #
#   Run iperf server         #
# --------------------------- #
# A bare-metal instance
# apptainer run ${IPERF3_PATH} --server -p ${APP_PORT} &
# add lib to LD_LIBRARY_PATH
export LD_LIBRARY_PATH=${IPERF3_LIB_PATH}:$LD_LIBRARY_PATH
${IPERF3_PATH} --server -p ${APP_PORT} &

IPERF3_PID=$!
echo -e "iperf3 process started with PID $IPERF3_PID \n"

# --------------------------- #
#    Run Process Exporter    #
# --------------------------- #
echo "Starting Process Exporter container..."

# Must use `apptainer exec` other than `apptainer run`
apptainer exec \
  --bind /proc:/host_proc \
  --bind ${CONFIG_DIR}:/config \
  ${PROCESS_EXPORTER_SIF} \
  process-exporter \
    -procfs /host_proc \
    -config.path /config/process-exporter-config.yml \
    -web.listen-address=:${PROCESS_EXPORTER_PORT} &
PROCESS_EXPORTER_PID=$!
echo -e "Process-Exporter started with PID $PROCESS_EXPORTER_PID on port ${PROCESS_EXPORTER_PORT}.\n"

# curl localhost:${PROCESS_EXPORTER_PORT}/metrics every 10 seconds
while true; do
  curl -s localhost:${PROCESS_EXPORTER_PORT}/metrics
  sleep 10
done &
# -----------------------------#
#     Keep processes running   #
# -----------------------------#
ps
wait $IPERF3_PID $PROCESS_EXPORTER_PID