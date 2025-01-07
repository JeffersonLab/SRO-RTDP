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

# Initialize node list file
echo "# Node IP addresses" > node_list.txt

# Submit receiver job
echo "Submitting receiver job..."
sbatch_output=$(sbatch ifarm_receiver.slurm ${RECV_PORT})
recv_job_id=$(echo $sbatch_output | awk '{print $4}')
recv_node=$(slurm_get_nodelist ${recv_job_id})

# Wait for receiver IP to be recorded
while ! grep -q "RECV_IP" node_list.txt; do
    sleep 1
done
recv_ip=$(grep "RECV_IP" node_list.txt | cut -d= -f2)
echo "Receiver node: ${recv_node} (IP: ${recv_ip})"

# Wait a moment for receiver to start
sleep 5

# Submit emulator job
echo "Submitting emulator job..."
sbatch_output=$(sbatch ifarm_emulator.slurm ${EMU_PORT} ${RECV_PORT} ${recv_ip})
emu_job_id=$(echo $sbatch_output | awk '{print $4}')
emu_node=$(slurm_get_nodelist ${emu_job_id})

# Wait for emulator IP to be recorded
while ! grep -q "EMU_IP" node_list.txt; do
    sleep 1
done
emu_ip=$(grep "EMU_IP" node_list.txt | cut -d= -f2)
echo "Emulator node: ${emu_node} (IP: ${emu_ip})"

# Wait a moment for emulator to start
sleep 5

# Submit sender job
echo "Submitting sender job..."
sbatch_output=$(sbatch ifarm_sender.slurm ${EMU_PORT} ${emu_ip})
send_job_id=$(echo $sbatch_output | awk '{print $4}')
send_node=$(slurm_get_nodelist ${send_job_id})

# Wait for sender IP to be recorded
while ! grep -q "SEND_IP" node_list.txt; do
    sleep 1
done
send_ip=$(grep "SEND_IP" node_list.txt | cut -d= -f2)
echo "Sender node: ${send_node} (IP: ${send_ip})"

echo "All jobs submitted:"
echo "Receiver: Job ${recv_job_id} on ${recv_node} (IP: ${recv_ip})"
echo "Emulator: Job ${emu_job_id} on ${emu_node} (IP: ${emu_ip})"
echo "Sender: Job ${send_job_id} on ${send_node} (IP: ${send_ip})"

# Save detailed node information
cat > node_info.txt <<EOL
Receiver Node: ${recv_node}
Receiver IP: ${recv_ip}
Receiver Job: ${recv_job_id}

Emulator Node: ${emu_node}
Emulator IP: ${emu_ip}
Emulator Job: ${emu_job_id}

Sender Node: ${send_node}
Sender IP: ${send_ip}
Sender Job: ${send_job_id}
EOL 