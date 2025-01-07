#!/bin/bash
#SBATCH --job-name=cpu-emu-send     # Job name
#SBATCH --output=slurm_send_%j.log
#SBATCH --error=slurm_send_%j.log
#SBATCH --ntasks=1                  # Number of tasks
#SBATCH --cpus-per-task=4           # CPU cores per task
#SBATCH --mem=8G                    # Memory
#SBATCH --time=02:00:00            # Time limit
#SBATCH --partition=ifarm          # Partition name

set -x

# Define variables
EMU_PORT=$1
EMU_HOST=$2
APP_SIF="../../cpu-emu.sif"
DATA_SIZE="100M"

# Get node information
node_name=$(hostname)
node_ip=$(hostname -i)
echo "Hostname: $node_name"
echo -e "IPv4 address: $node_ip\n"

# Record IP in node list file
echo "SEND_IP=${node_ip}" >> node_list.txt

# Create input directory
mkdir -p input
INPUT_FILE="input/random_data.bin"

# Generate test data
dd if=/dev/urandom bs=${DATA_SIZE} count=1 of=${INPUT_FILE} 2>/dev/null

# Send data
apptainer run --bind input:/data \
    ${APP_SIF} send "/data/$(basename ${INPUT_FILE})" ${EMU_HOST} ${EMU_PORT}

SEND_PID=$!
echo -e "Sender process started with PID $SEND_PID \n"

# Keep process running
wait $SEND_PID 