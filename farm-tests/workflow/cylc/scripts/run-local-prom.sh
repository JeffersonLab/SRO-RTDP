# Create a script to copy data: copy-prom-data.sh
#!/bin/bash
REMOTE_HOST="tsai@ifarm.jlab.org"
REMOTE_PATH="/home/tsai/cylc-run/iperf-test/runN/work/1/prometheus_server/prom-data-*/*"
LOCAL_PATH="./local-prometheus/data"

# create local-prometheus
mkdir -p local-prometheus/{config,data}
cp ./prometheus.yml ./local-prometheus/config/



# Stop the containers if they're running
docker-compose down

# Clear existing data
rm -rf ${LOCAL_PATH}/*

# Copy data from remote
scp -r ${REMOTE_HOST}:${REMOTE_PATH} ${LOCAL_PATH}/

# Fix permissions
chmod -R 777 ${LOCAL_PATH}

# Start the containers
docker-compose up -d