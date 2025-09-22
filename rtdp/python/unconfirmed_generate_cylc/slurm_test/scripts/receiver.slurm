#!/bin/bash
#SBATCH --job-name=rtdp-receiver
#SBATCH --output=receiver_%j.log
#SBATCH --error=receiver_%j.log
#SBATCH --partition=ifarm
#SBATCH --cpus-per-task=4
#SBATCH --mem=4G
#SBATCH --time=2:00:00

# Set environment variables
export OUTPUT_DIR="$PWD/../output"
export CONFIG_DIR="$PWD/../config"
mkdir -p ${OUTPUT_DIR}/received_data

# Get hostname and IP for other tasks
HOSTNAME=$(hostname -f)
IP=$(hostname -i | awk '{print $1}')
echo "$HOSTNAME" > receiver_hostname
echo "$IP" > receiver_ip

# Get emulator IP from file
EMULATOR_IP=$(cat emulator_ip)

# Start receiver with configuration
apptainer exec \
    --bind ${OUTPUT_DIR}:/data \
    --bind ${CONFIG_DIR}:/config \
    ../sifs/rtdp-components.sif \
    python3 -c "from components.receiver import main; main()" \
    --config /config/receiver_config.yml \
    --emulator-ip ${EMULATOR_IP} 