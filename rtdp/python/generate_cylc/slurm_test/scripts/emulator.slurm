#!/bin/bash
#SBATCH --job-name=rtdp-emulator
#SBATCH --output=emulator_%j.log
#SBATCH --error=emulator_%j.log
#SBATCH --partition=ifarm
#SBATCH --cpus-per-task=8
#SBATCH --mem=16G
#SBATCH --time=2:00:00

# Set environment variables
export OUTPUT_DIR="$PWD/../output"
export CONFIG_DIR="$PWD/../config"
mkdir -p ${OUTPUT_DIR}

# Get hostname and IP for other tasks
HOSTNAME=$(hostname -f)
IP=$(hostname -i | awk '{print $1}')
echo "$HOSTNAME" > emulator_hostname
echo "$IP" > emulator_ip

# Get sender IP from file
SENDER_IP=$(cat sender_ip)

# Start emulator with configuration
apptainer run --bind ${OUTPUT_DIR}:/output \
    ../sifs/cpu-emu.sif \
    /usr/local/bin/cpu_emu \
    -y ${CONFIG_DIR}/emulator_config.yml \
    --sender-ip ${SENDER_IP} 