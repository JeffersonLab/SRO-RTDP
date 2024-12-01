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

# --------------------------- #
#   Run iperf server         #
# --------------------------- #
export LD_LIBRARY_PATH=${IPERF3_LIB_PATH}:$LD_LIBRARY_PATH
${IPERF3_PATH} --server -p ${APP_PORT} &
IPERF3_PID=$!
echo "iperf3 process started with PID $IPERF3_PID"

# Wait for iperf3 to start listening
COUNTER=0
while ! netstat -tuln | grep ":${APP_PORT}" > /dev/null; do
    sleep 1
    ((COUNTER++))
    if [ $COUNTER -ge 10 ]; then
        echo "ERROR: iperf3 server failed to start within 10 seconds"
        kill $IPERF3_PID
        exit 1
    fi
done
echo "iperf3 server is listening on port ${APP_PORT}"

# --------------------------- #
#    Run Process Exporter    #
# --------------------------- #
echo "Starting Process Exporter container..."

# Create process-exporter config if it doesn't exist
mkdir -p "${CONFIG_DIR}"
cat > "${CONFIG_DIR}/process-exporter-config.yml" << EOF
process_names:
  - name: "{{.Comm}}"
    cmdline:
    - '.+'
EOF

# Start process-exporter
apptainer exec \
    --bind /proc:/host_proc \
    --bind ${CONFIG_DIR}:/config \
    ${PROCESS_EXPORTER_SIF} \
    process-exporter \
    -procfs /host_proc \
    -config.path /config/process-exporter-config.yml \
    -web.listen-address=:${PROCESS_EXPORTER_PORT} &
PROCESS_EXPORTER_PID=$!

# Wait for process-exporter to start
COUNTER=0
while ! curl -s "http://localhost:${PROCESS_EXPORTER_PORT}/metrics" > /dev/null; do
    sleep 1
    ((COUNTER++))
    if [ $COUNTER -ge 10 ]; then
        echo "ERROR: Process-exporter failed to start within 10 seconds"
        kill $IPERF3_PID $PROCESS_EXPORTER_PID
        exit 1
    fi
done
echo "Process-Exporter started successfully on port ${PROCESS_EXPORTER_PORT}"

# Monitor both processes
while kill -0 $IPERF3_PID 2>/dev/null && kill -0 $PROCESS_EXPORTER_PID 2>/dev/null; do
    # Verify iperf3 is still listening
    if ! netstat -tuln | grep ":${APP_PORT}" > /dev/null; then
        echo "ERROR: iperf3 server stopped listening on port ${APP_PORT}"
        kill $IPERF3_PID $PROCESS_EXPORTER_PID
        exit 1
    fi
    
    # Verify process-exporter is responding
    if ! curl -s "http://localhost:${PROCESS_EXPORTER_PORT}/metrics" > /dev/null; then
        echo "ERROR: Process-exporter stopped responding"
        kill $IPERF3_PID $PROCESS_EXPORTER_PID
        exit 1
    fi
    
    sleep 5
done

# If we get here, one of the processes died
echo "ERROR: A required process has terminated unexpectedly"
ps -f -p $IPERF3_PID $PROCESS_EXPORTER_PID
exit 1