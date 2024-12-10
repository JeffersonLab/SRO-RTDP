#!/bin/bash

set -xe

INFLUXDB_PORT=$1
ZMQ_PORT_NUM=55577

SCRIPTS_PATH=farm-tests/slurm-podio2tcp-influxdb-demo
APPTAINER_JOB_WRAPPER=apptainer_sbatch_wrapper.slurm
RECV_SCRIPT=podio2tcp-receiver.bash
SEND_SCRIPT=podio2tcp-sender.bash

############ Helper functions for Slurm ############
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
    sleep 10 # Wait for 10 seconds before checking again
  done
}
######################################################

# 1. Sumbit the receiver job
sbatch_output=$(sbatch --partition ifarm \
       --output slurm_%j_%N_recv.log \
       --job-name pd-recv \
       ${SCRIPTS_PATH}/${APPTAINER_JOB_WRAPPER} ${SCRIPTS_PATH}/${RECV_SCRIPT} ${ZMQ_PORT_NUM} ${INFLUXDB_PORT})

recv_job_id=$(echo $sbatch_output | awk '{print $4}')
recv_nodename=$(slurm_get_nodelist ${recv_job_id})
echo -e "The receiver node is: ${recv_nodename} \n"

# 2. Submit the sender job
sbatch_output=$(sbatch --partition ifarm \
       --output slurm_%j_%N_send.log \
       --job-name pd-send \
       ${SCRIPTS_PATH}/${APPTAINER_JOB_WRAPPER} ${SCRIPTS_PATH}/${SEND_SCRIPT} ${ZMQ_PORT_NUM} ${INFLUXDB_PORT} ${recv_nodename} \
)
send_job_id=$(echo $sbatch_output | awk '{print $4}')
send_nodename=$(slurm_get_nodelist ${send_job_id})
echo -e "The receiver node is: ${send_nodename} \n"

sleep 1200
scancel $recv_job_id
scancel $send_job_id
