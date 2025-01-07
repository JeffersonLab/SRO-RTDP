#!/bin/bash
#SBATCH --job-name=cpu-emu-recv      # Job name
#SBATCH --output=slurm_recv_%j.log
#SBATCH --error=slurm_recv_%j.log
#SBATCH --ntasks=1                   # Number of tasks
#SBATCH --cpus-per-task=4            # CPU cores per task
#SBATCH --mem=8G                     # Memory
#SBATCH --time=02:00:00             # Time limit
#SBATCH --partition=ifarm           # Partition name

set -x

# Define variables
RECV_PORT=$1
OUTPUT_FILE="received_data.bin"
APP_SIF="../../cpu-emu.sif"

# Get node information
node_name=$(hostname)
node_ip=$(hostname -i)
echo "Hostname: $node_name"
echo -e "IPv4 address: $node_ip\n"

# Record IP in node list file
echo "RECV_IP=${node_ip}" > node_list.txt

# Start receiver
apptainer run ${APP_SIF} receive ${RECV_PORT} ${node_ip} > ${OUTPUT_FILE} &
RECV_PID=$!
echo -e "Receiver process started with PID $RECV_PID \n"

# Keep process running
wait $RECV_PID 