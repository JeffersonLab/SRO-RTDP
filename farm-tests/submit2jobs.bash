#!/bin/bash

## Run this script on a farm compute node in the interactive mode.

# For debug
set -xe  pipefail # -e: exit on first error; -x: echo the command

## Script to submit 3 jobs to 3 nodes on ifarm.

# NOTES: On ifarm, use ports 32768-60999
export PROCESS_EXPORTER_PORT=32801
export PROMETHEUS_PORT=32900

CURR_DIR="/w/epsci-sciwork18/xmei/projects/SRO-RTDP/farm-tests"
cd $CURR_DIR
PROM_DATA_TIMESIG=$(date -u +%s)   # UTC seconds
PROM_DATA=prom-data-${PROM_DATA_TIMESIG}
mkdir -p ${PROM_DATA}

WORKER_NODES=worker.log

########################### Helper functions for Slurm ####################################
function slurm_get_nodelist() {
  # Kernel function of extracting "NodeList" based on a Slurm job_id

  local job_id=$1

  while true; do
    # Run scontrol show job and extract the line start with "   Nodelist="
    local node_list=$(scontrol show job $job_id | grep -oP '(?<=\s|^)NodeList=\K\S+');

    # Check if node_list is not empty
    if [[ -n "$node_list" ]]; then
        echo "$node_list";  # Output the node list
        break;
    fi
    sleep 1 # Wait for 1 second before checking again
  done
}

##########################################################


#+++++++++++++++++++++++++++++++++++++++
# Step 1: Submit the first job

sbatch_output=$(sbatch ifarm_receiver.slurm ${PROCESS_EXPORTER_PORT})
# Extract the job ID
# After calling sbatch, it outputs "Submitted batch job xxxxxxx"
job_id=$(echo $sbatch_output | awk '{print $4}')
recv_nodename=$(slurm_get_nodelist ${job_id})

# echo -e for special character "\n"
echo -e "The RECV node is: ${recv_nodename}\n"
echo ${recv_nodename} > ${WORKER_NODES}

#---------------------------------------

#+++++++++++++++++++++++++++++++++++++++
# Step 2: Submit the second job
#         We need the RECEIVER's nodename

sbatch_output=$(sbatch ifarm_sender.slurm ${PROCESS_EXPORTER_PORT} ${recv_nodename})
# Extract the job ID
# After calling sbatch, it outputs "Submitted batch job xxxxxxx"
job_id=$(echo $sbatch_output | awk '{print $4}')
send_nodename=$(slurm_get_nodelist ${job_id})

# echo -e for special character "\n"
echo -e "The CLIE node is: ${send_nodename}\n"
echo ${send_nodename} >> ${WORKER_NODES}

#---------------------------------------

#+++++++++++++++++++++++++++++++++++++++
# Step 3: Submit the Prometheus job
#         We need nodename of both the SERVER and the CLIENT

# Locate the Prometheus config file
# Confirm the current path is at farm_tests
prom_config_file_path="config/prometheus-config.yml"

# Append to the Prometheus config file
cat >> ${prom_config_file_path} <<EOL
scrape_configs:
  - job_name: 'process-exporter'
    static_configs:
      - targets:
EOL

# Generate Process-exporter targets of the worker ndoes

# Remove the duplicates of the worker nodes
sort -u ${WORKER_NODES} -o ${WORKER_NODES}
while read -r node; do
  echo "        - '${node}:${PROCESS_EXPORTER_PORT}'" >> ${prom_config_file_path}
done < ${WORKER_NODES}

cat >> ${prom_config_file_path} <<EOL
        - 'localhost:${PROCESS_EXPORTER_PORT}'
        labels:
          group: 'process-exporter'
          cluster: 'ifarm'

  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:${PROMETHEUS_PORT}']
        labels:
          group: 'prometheus'
          cluster: 'ifarm'

EOL

###########
# sbatch_output=$(sbatch ifarm_prom.slurm ${PROMETHEUS_PORT})
# # Extract the job ID
# # After calling sbatch, it outputs "Submitted batch job xxxxxxx"
# job_id=$(echo $sbatch_output | awk '{print $4}')
# prom_nodename=$(slurm_get_nodelist ${job_id})

# # echo -e for special character "\n"
# echo -e "The PROM node is: ${prom_nodename}"
# echo -e 'The Prometheus metric is at "http://${prom_nodename}:${PROMETHEUS_PORT}/metrics" \n'

###########
# On the Interactive node
# ssh -fN -R 9200:localhost:32900 xmei@scilogin  # port forwarding requires 2FA

# Curl localhost:32900/metrics to read the metrics
apptainer exec \
  --bind config:/config \
  --bind ${PROM_DATA}:/prometheus \
  sifs/prom.sif \
  prometheus \
    --web.listen-address=":32900" \
    --config.file=/config/prometheus-config.yml \
    --storage.tsdb.path=/prometheus     > prom.log 2>&1 &
