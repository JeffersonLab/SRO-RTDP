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
echo "----------------------------------------"
echo "IPERF CLIENT STARTUP INFORMATION"
echo "----------------------------------------"
echo "Current time: $(date)"
echo -e "The iperf3 server is at: ${IPERF3_SERVER_HOSTNAME}\n"
echo "Client hostname: $(hostname)"
echo "Client IP: $(hostname -i)"
echo "Target port: ${APP_PORT}"
echo "Test duration: ${TEST_DURATION} seconds"
echo "Working directory: $(pwd)"
echo "LD_LIBRARY_PATH will be set to: ${IPERF3_LIB_PATH}:$LD_LIBRARY_PATH"
echo "----------------------------------------"

# add lib to LD_LIBRARY_PATH
export LD_LIBRARY_PATH=${IPERF3_LIB_PATH}:$LD_LIBRARY_PATH

# Test iperf3 binary
echo "Testing iperf3 binary..."
if ! ${IPERF3_PATH} --version; then
    echo "ERROR: Failed to run iperf3 binary"
    echo "Binary path: ${IPERF3_PATH}"
    echo "File permissions: $(ls -l ${IPERF3_PATH})"
    echo "ldd output: $(ldd ${IPERF3_PATH})"
    exit 1
fi

echo "Starting iperf3 client with command:"
echo "${IPERF3_PATH} -c ${IPERF3_SERVER_HOSTNAME} -p ${APP_PORT} -t ${TEST_DURATION} -i 1 -V -f m --json"

${IPERF3_PATH} -c \
  ${IPERF3_SERVER_HOSTNAME} \
  -p ${APP_PORT} \
  -t ${TEST_DURATION} \
  -i 1 \
  -V \
  -f m \
  --json \
  2>&1 | while IFS= read -r line; do
      echo "[$(date '+%Y-%m-%d %H:%M:%S')] $line"
      echo "$line" >> iperf3_client.log
      # Check if we're receiving data
      if [[ $line == *"bits/sec"* ]]; then
          echo "----------------------------------------"
          echo "DATA TRANSFER DETECTED!"
          echo "Current transfer rate: $line"
          echo "----------------------------------------"
      fi
  done &

IPERF3_PID=$!
echo "iperf3 process started with PID $IPERF3_PID"

# Verify the process started
if ! ps -p $IPERF3_PID > /dev/null; then
    echo "ERROR: iperf3 client process failed to start"
    echo "Last few lines of log:"
    tail -n 10 iperf3_client.log
    exit 1
fi

# Wait briefly and check if process is still running
sleep 2
if ! ps -p $IPERF3_PID > /dev/null; then
    echo "ERROR: iperf3 client process died shortly after starting"
    echo "Complete log output:"
    cat iperf3_client.log
    exit 1
fi

echo "iperf3 client process is running"
ps -f -p $IPERF3_PID

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

# Wait for process-exporter to start and verify it's working
echo "Waiting for process-exporter to become available..."
COUNTER=0
while ! curl -s "http://localhost:${PROCESS_EXPORTER_PORT}/metrics" > /dev/null; do
    sleep 1
    ((COUNTER++))
    if [ $((COUNTER % 5)) -eq 0 ]; then
        echo "Still waiting for process-exporter... Attempt ${COUNTER}"
    fi
    if [ $COUNTER -ge 30 ]; then
        echo "ERROR: Process-exporter failed to start within 30 seconds"
        echo "Debug information:"
        echo "1. Process status:"
        ps -f -p $PROCESS_EXPORTER_PID
        echo "2. Port status:"
        netstat -tuln | grep ${PROCESS_EXPORTER_PORT}
        echo "3. Container logs:"
        ls -l ${CONFIG_DIR}
        cat ${CONFIG_DIR}/process-exporter-config.yml
        exit 1
    fi
done

# Test initial metrics collection
echo "Testing initial metrics collection..."
curl -v "http://localhost:${PROCESS_EXPORTER_PORT}/metrics" > initial_metrics 2>curl_debug.log
if [ $? -eq 0 ]; then
    echo "Successfully connected to process-exporter"
    echo "Sample of available metrics:"
    grep -E "namedprocess_namegroup_(cpu|memory|io|thread)" initial_metrics | head -n 5
    # Signal that the client and its exporter are ready
    cylc message -- "The iperf client and exporter are ready"
else
    echo "Failed to get initial metrics"
    echo "Curl debug output:"
    cat curl_debug.log
    exit 1
fi

# Monitor processes and log status
echo "Starting process monitoring..."

# Initialize exporter log file
echo "Process Exporter Metrics Log - Started at $(date)" > exporter.log
echo "----------------------------------------" >> exporter.log

while kill -0 $IPERF3_PID 2>/dev/null && kill -0 $PROCESS_EXPORTER_PID 2>/dev/null; do
    # Log status every 30 seconds
    if [ $((SECONDS % 30)) -eq 0 ]; then
        echo "----------------------------------------"
        echo "STATUS UPDATE ($(date))"
        echo "----------------------------------------"
        echo "iperf3 client process status:"
        ps -f -p $IPERF3_PID
        echo
        echo "Recent iperf3 output:"
        tail -n 5 iperf3_client.log
        echo
        echo "Process-exporter status:"
        ps -f -p $PROCESS_EXPORTER_PID
        echo
        echo "Network connections:"
        netstat -tn | grep ${IPERF3_SERVER_HOSTNAME}
        echo "----------------------------------------"

        # Fetch and log process-exporter metrics
        echo -e "\n----------------------------------------" >> exporter.log
        echo "Metrics at $(date)" >> exporter.log
        echo "----------------------------------------" >> exporter.log
        if curl -v "http://localhost:${PROCESS_EXPORTER_PORT}/metrics" > temp_metrics 2>>curl_debug.log; then
            # Filter and format relevant metrics
            grep -E "namedprocess_namegroup_(cpu|memory|io|thread)" temp_metrics | \
            while IFS= read -r line; do
                echo "[$(date '+%Y-%m-%d %H:%M:%S')] $line" >> exporter.log
            done
            rm temp_metrics
            echo "Process-exporter metrics collected successfully" >> exporter.log
        else
            echo "Failed to fetch metrics from process-exporter at $(date)" >> exporter.log
            echo "Curl debug output:" >> exporter.log
            tail -n 10 curl_debug.log >> exporter.log
            echo "Process-exporter status:" >> exporter.log
            ps -f -p $PROCESS_EXPORTER_PID >> exporter.log
            echo "Port status:" >> exporter.log
            netstat -tuln | grep ${PROCESS_EXPORTER_PORT} >> exporter.log
        fi
    fi
    sleep 5
done

# Check which process died
if ! kill -0 $IPERF3_PID 2>/dev/null; then
    echo "iperf3 client process has terminated"
    echo "Final output from iperf3:"
    tail -n 20 iperf3_client.log
fi

if ! kill -0 $PROCESS_EXPORTER_PID 2>/dev/null; then
    echo "Process-exporter has terminated"
fi

# Clean up
kill $IPERF3_PID $PROCESS_EXPORTER_PID 2>/dev/null || true

# Check if we completed successfully
if grep -q "Done" iperf3_client.log; then
    echo "iperf3 client completed successfully"
    exit 0
else
    echo "iperf3 client did not complete successfully"
    exit 1
fi