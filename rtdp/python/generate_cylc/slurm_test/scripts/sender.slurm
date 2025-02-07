#!/bin/bash
#SBATCH --job-name=rtdp-sender
#SBATCH --output=sender_%j.log
#SBATCH --error=sender_%j.log
#SBATCH --partition=ifarm
#SBATCH --cpus-per-task=4
#SBATCH --mem=4G
#SBATCH --time=2:00:00

# Set environment variables
export OUTPUT_DIR="$PWD/../output"
export CONFIG_DIR="$PWD/../config"
mkdir -p ${OUTPUT_DIR}

# Get hostname and IP for other tasks
HOSTNAME=$(hostname -f)
IP=$(hostname -i | awk '{print $1}')
echo "$HOSTNAME" > sender_hostname
echo "$IP" > sender_ip

# Start sender with configuration
apptainer run --bind ${OUTPUT_DIR}:/data \
    ../sifs/rtdp-components.sif \
    python3 -m components.sender \
    --config ${CONFIG_DIR}/sender_config.yml 