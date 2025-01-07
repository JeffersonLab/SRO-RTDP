#!/bin/bash

set -xe pipefail

# Define ports
RECV_PORT=50080
EMU_PORT=50888

# Helper function to get node from job ID
function slurm_get_nodelist() {
    local job_id=$1
    while true; do
        local node_list=$(scontrol show job $job_id | grep -oP '(?<=\s|^)NodeList=\K\S+')
        if [[ -n "$node_list" ]]; then
            echo "$node_list"
            break
        fi
        sleep 1
    done
}

# Submit receiver job
echo "Submitting receiver job..."
sbatch_output=$(sbatch ifarm_receiver.slurm ${RECV_PORT})
recv_job_id=$(echo $sbatch_output | awk '{print $4}')
recv_node=$(slurm_get_nodelist ${recv_job_id})
echo "Receiver node: ${recv_node}"

# Wait a moment for receiver to start
sleep 5

# Submit emulator job
echo "Submitting emulator job..."
sbatch_output=$(sbatch ifarm_emulator.slurm ${EMU_PORT} ${RECV_PORT} ${recv_node})
emu_job_id=$(echo $sbatch_output | awk '{print $4}')
emu_node=$(slurm_get_nodelist ${emu_job_id})
echo "Emulator node: ${emu_node}"

# Wait a moment for emulator to start
sleep 5

# Submit sender job
echo "Submitting sender job..."
sbatch_output=$(sbatch ifarm_sender.slurm ${EMU_PORT} ${emu_node})
send_job_id=$(echo $sbatch_output | awk '{print $4}')
send_node=$(slurm_get_nodelist ${send_job_id})
echo "Sender node: ${send_node}"

echo "All jobs submitted:"
echo "Receiver: Job ${recv_job_id} on ${recv_node}"
echo "Emulator: Job ${emu_job_id} on ${emu_node}"
echo "Sender: Job ${send_job_id} on ${send_node}" 