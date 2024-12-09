#!/bin/bash

## Run this script on a farm compute node in the interactive mode.

# For debug
set -xe  pipefail # -e: exit on first error; -x: echo the command

ZMQ_PORT_NUM=55577

cd ${HOME}/projects/SRO-RTDP   # <==== Update ROOT dir


################### Helper functions for Slurm #####
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
    sleep 5 # Wait for 5 second before checking again
  done
}
######################################################

# TODO: Launch Grafana DB etc


SCRIPTS_PATH=farm-tests/slurm-podio2tcp-influxdb-demo
APPTAINER_WRAPPER=apptainer_sbatch_wrapper.slurm
RECV_SCRIPT=podio2tcp-receiver.bash
SEND_SCRIPT=podio2tcp-sender.bash

# 2. Sumbit the receiver job
sbatch_output=$(sbatch --partition ifarm \
       --output slurm_%j_%N_recv.log \
       --job-name podio2tcp-recv \
       ${SCRIPTS_PATH}/${APPTAINER_WRAPPER} ${SCRIPTS_PATH}/${RECV_SCRIPT} ${ZMQ_PORT_NUM})

job_id=$(echo $sbatch_output | awk '{print $4}')
recv_nodename=$(slurm_get_nodelist ${job_id})

# echo ${recv_nodename}

# 3. Submit the sender job
sbatch --partition ifarm \
       --output slurm_%j_%N_sender.log \
       --job-name podio2tcp-send \
       ${SCRIPTS_PATH}/${APPTAINER_WRAPPER} ${SCRIPTS_PATH}/${SEND_SCRIPT} ${ZMQ_PORT_NUM} ${recv_nodename}
