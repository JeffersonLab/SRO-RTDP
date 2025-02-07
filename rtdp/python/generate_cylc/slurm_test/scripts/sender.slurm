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
apptainer exec \
    --bind ${OUTPUT_DIR}:/data \
    --bind ${CONFIG_DIR}:/config \
    ../sifs/rtdp-components.sif \
    python3 -c "from components.sender import main; main()" \
    --config /config/sender_config.yml 