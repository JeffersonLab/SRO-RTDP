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

# Helper function to get IP from node_info.txt
function get_ip_from_info() {
    local role=$1
    while ! grep -q "${role} IP:" node_info.txt; do
        sleep 1
    done
    grep "${role} IP:" node_info.txt | cut -d: -f2 | tr -d ' '
}

# Submit receiver job
echo "Submitting receiver job..."
sbatch_output=$(sbatch ifarm_receiver.slurm ${RECV_PORT})
recv_job_id=$(echo $sbatch_output | awk '{print $4}')
recv_node=$(slurm_get_nodelist ${recv_job_id})
recv_ip=$(get_ip_from_info "Receiver")
echo "Receiver node: ${recv_node} (IP: ${recv_ip})"

# Wait a moment for receiver to start
sleep 5

# Submit emulator job
echo "Submitting emulator job..."
sbatch_output=$(sbatch ifarm_emulator.slurm ${EMU_PORT} ${RECV_PORT} ${recv_ip})
emu_job_id=$(echo $sbatch_output | awk '{print $4}')
emu_node=$(slurm_get_nodelist ${emu_job_id})
emu_ip=$(get_ip_from_info "Emulator")
echo "Emulator node: ${emu_node} (IP: ${emu_ip})"

# Wait a moment for emulator to start
sleep 5

# Submit sender job
echo "Submitting sender job..."
sbatch_output=$(sbatch ifarm_sender.slurm ${EMU_PORT} ${emu_ip})
send_job_id=$(echo $sbatch_output | awk '{print $4}')
send_node=$(slurm_get_nodelist ${send_job_id})
send_ip=$(get_ip_from_info "Sender")
echo "Sender node: ${send_node} (IP: ${send_ip})"

echo "All jobs submitted:"
echo "Receiver: Job ${recv_job_id} on ${recv_node} (IP: ${recv_ip})"
echo "Emulator: Job ${emu_job_id} on ${emu_node} (IP: ${emu_ip})"
echo "Sender: Job ${send_job_id} on ${send_node} (IP: ${send_ip})" 