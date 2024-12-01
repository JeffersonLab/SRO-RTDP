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

# Initialize log file
PROM_LOG_FILE="${WORKDIR_PREFIX}/prometheus_collection.log"
echo "Prometheus Data Collection Log - Started at $(date)" > ${PROM_LOG_FILE}
echo "----------------------------------------" >> ${PROM_LOG_FILE}

# Ensure the data directory exists and is writable
mkdir -p "${WORKDIR_PREFIX}/${PROM_DATA_DIR}"

# Verify the config file exists
if [ ! -f "${CONFIG_DIR}/prometheus-config.yml" ]; then
    echo "ERROR: Prometheus config file not found at ${CONFIG_DIR}/prometheus-config.yml"
    exit 1
fi

echo "Starting Prometheus with configuration:"
echo "----------------------------------------"
echo "Port: ${PROMETHEUS_PORT}"
echo "Data directory: ${PROM_DATA_DIR}"
echo "Config file contents:"
cat "${CONFIG_DIR}/prometheus-config.yml"
echo "----------------------------------------"

apptainer exec \
  --bind ${CONFIG_DIR}:/config \
  --bind ${PROM_DATA_DIR}:/prometheus \
  ${PROMETHEUS_SIF} \
  prometheus \
    --web.listen-address=":${PROMETHEUS_PORT}" \
    --config.file=/config/prometheus-config.yml \
    --storage.tsdb.path=/prometheus \
    --web.enable-lifecycle &

PROMETHEUS_PID=$!

# Wait for Prometheus to start
echo "Waiting for Prometheus to start..."
COUNTER=0
while ! curl -s "http://localhost:${PROMETHEUS_PORT}/-/ready" > /dev/null; do
    sleep 1
    ((COUNTER++))
    if [ $((COUNTER % 5)) -eq 0 ]; then
        echo "Still waiting for Prometheus... Attempt ${COUNTER}"
    fi
    if [ $COUNTER -ge 30 ]; then
        echo "ERROR: Prometheus failed to start within 30 seconds"
        echo "Process status:"
        ps -f -p $PROMETHEUS_PID
        echo "Port status:"
        netstat -tuln | grep ${PROMETHEUS_PORT}
        kill $PROMETHEUS_PID
        exit 1
    fi
done

echo "Prometheus is running and ready"
echo "Testing target scraping..."

# Wait a bit for the first scrape
sleep 15

# Check if we're getting metrics from both exporters
echo -e "\nInitial Target Status ($(date)):" >> ${PROM_LOG_FILE}
curl -s "http://localhost:${PROMETHEUS_PORT}/api/v1/targets" >> ${PROM_LOG_FILE}

# Get initial metrics samples
echo -e "\nInitial Metrics Samples:" >> ${PROM_LOG_FILE}
echo "Process Metrics from Server:" >> ${PROM_LOG_FILE}
curl -s "http://localhost:${PROMETHEUS_PORT}/api/v1/query?query=namedprocess_namegroup_cpu_seconds_total" >> ${PROM_LOG_FILE}

echo -e "\nProcess Metrics from Client:" >> ${PROM_LOG_FILE}
curl -s "http://localhost:${PROMETHEUS_PORT}/api/v1/query?query=namedprocess_namegroup_memory_bytes" >> ${PROM_LOG_FILE}

curl -s "http://localhost:${PROMETHEUS_PORT}/api/v1/targets" | \
    grep -q "process-exporter" && \
    echo "Successfully scraping process-exporter targets" || \
    (echo "ERROR: Not scraping process-exporter targets"; exit 1)

# Monitor Prometheus
while kill -0 $PROMETHEUS_PID 2>/dev/null; do
    if [ $((SECONDS % 60)) -eq 0 ]; then
        echo "----------------------------------------"
        echo "STATUS UPDATE ($(date))"
        echo "----------------------------------------"
        echo "Prometheus process status:"
        ps -f -p $PROMETHEUS_PID
        echo "Target status:"
        curl -s "http://localhost:${PROMETHEUS_PORT}/api/v1/targets" | grep "process-exporter"
        echo "----------------------------------------"
        
        # Log detailed collection status
        echo -e "\nCollection Status at $(date)" >> ${PROM_LOG_FILE}
        echo "----------------------------------------" >> ${PROM_LOG_FILE}
        
        # Log target health
        echo "Target Health Status:" >> ${PROM_LOG_FILE}
        curl -s "http://localhost:${PROMETHEUS_PORT}/api/v1/targets" | \
            grep -A 5 "process-exporter" >> ${PROM_LOG_FILE}
        
        # Log sample metrics
        echo -e "\nSample Metrics:" >> ${PROM_LOG_FILE}
        echo "CPU Usage:" >> ${PROM_LOG_FILE}
        # Get CPU metrics for each target
        for target in $(curl -s "http://localhost:${PROMETHEUS_PORT}/api/v1/targets" | grep -o '"instance":"[^"]*"' | cut -d'"' -f4); do
            echo "Target: $target" >> ${PROM_LOG_FILE}
            curl -s "http://localhost:${PROMETHEUS_PORT}/api/v1/query?query=rate(namedprocess_namegroup_cpu_seconds_total{instance=\"$target\"}[1m])" >> ${PROM_LOG_FILE}
            echo >> ${PROM_LOG_FILE}
        done
        
        echo -e "\nMemory Usage:" >> ${PROM_LOG_FILE}
        # Get memory metrics for each target
        for target in $(curl -s "http://localhost:${PROMETHEUS_PORT}/api/v1/targets" | grep -o '"instance":"[^"]*"' | cut -d'"' -f4); do
            echo "Target: $target" >> ${PROM_LOG_FILE}
            curl -s "http://localhost:${PROMETHEUS_PORT}/api/v1/query?query=namedprocess_namegroup_memory_bytes{instance=\"$target\"}" >> ${PROM_LOG_FILE}
            echo >> ${PROM_LOG_FILE}
        done
        
        # Log scrape statistics
        echo -e "\nScrape Statistics:" >> ${PROM_LOG_FILE}
        echo "Scrape Durations:" >> ${PROM_LOG_FILE}
        curl -s "http://localhost:${PROMETHEUS_PORT}/api/v1/query?query=scrape_duration_seconds" >> ${PROM_LOG_FILE}
        echo -e "\nScrape Successes:" >> ${PROM_LOG_FILE}
        curl -s "http://localhost:${PROMETHEUS_PORT}/api/v1/query?query=scrape_samples_scraped" >> ${PROM_LOG_FILE}
        echo -e "\nLast Scrape Times:" >> ${PROM_LOG_FILE}
        curl -s "http://localhost:${PROMETHEUS_PORT}/api/v1/query?query=scrape_samples_post_metric_relabeling" >> ${PROM_LOG_FILE}
        
        echo -e "\n----------------------------------------\n" >> ${PROM_LOG_FILE}
    fi
    sleep 5
done

# Log termination
echo -e "\nPrometheus terminated at $(date)" >> ${PROM_LOG_FILE}
echo "Final target status:" >> ${PROM_LOG_FILE}
curl -s "http://localhost:${PROMETHEUS_PORT}/api/v1/targets" >> ${PROM_LOG_FILE}

echo "Prometheus process has terminated"
exit 1