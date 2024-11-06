#!/bin/bash

# --------------------------- #
#      Define Variables       #
# --------------------------- #
# Parse command line arguments
PROCESS_EXPORTER_PORT=$1
IPERF3_SERVER_HOSTNAME=$2
APP_PORT=$3
WORKDIR_PREFIX=$4
PROCESS_EXPORTER_SIF=$5
IPERF3_PATH=$6
IPERF3_LIB_PATH=$7
TEST_DURATION=$8
CONFIG_DIR=${9:-"config"}  # Default to "config" if not provided

# Validate required parameters
if [ -z "$PROCESS_EXPORTER_PORT" ] || [ -z "$IPERF3_SERVER_HOSTNAME" ] || [ -z "$APP_PORT" ] || \
   [ -z "$WORKDIR_PREFIX" ] || [ -z "$PROCESS_EXPORTER_SIF" ] || [ -z "$IPERF3_PATH" ] || \
   [ -z "$TEST_DURATION" ] || [ -z "$IPERF3_LIB_PATH" ]; then
    echo "Usage: $0 <process_exporter_port> <server_hostname> <app_port> <workdir_prefix> <process_exporter_sif> <iperf3_path> <iperf3_lib_path> <test_duration> [config_dir]"
    exit 1
fi

# Set derived variables

node_name=$(hostname)
node_ip=$(hostname -i)
echo "Hostname: $node_name"
echo -e "IPv4 address: $node_ip\n"

cd $WORKDIR_PREFIX

# --------------------------- #
#   Run iperf client
# --------------------------- #
echo -e "The iperf3 server is at: ${IPERF3_SERVER_HOSTNAME}\n"

# A bare-metal iperf3 client instance
#   apptainer run ${IPERF3_PATH} -c \
#   ${IPERF3_SERVER_HOSTNAME} \
#   -p ${APP_PORT} \
#   -t ${TEST_DURATION} &

# add lib to LD_LIBRARY_PATH
export LD_LIBRARY_PATH=${IPERF3_LIB_PATH}:$LD_LIBRARY_PATH
${IPERF3_PATH} -c \
  ${IPERF3_SERVER_HOSTNAME} \
  -p ${APP_PORT} \
  -t ${TEST_DURATION} &

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

# -----------------------------#
#     Keep processes running   #
# -----------------------------#
ps
wait $IPERF3_PID $PROCESS_EXPORTER_PID