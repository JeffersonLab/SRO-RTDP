#!/bin/bash
#SBATCH --job-name=cpu-emu          # Job name
#SBATCH --output=slurm_emu_%j.log
#SBATCH --error=slurm_emu_%j.log
#SBATCH --ntasks=1                  # Number of tasks
#SBATCH --cpus-per-task=4           # CPU cores per task
#SBATCH --mem=8G                    # Memory
#SBATCH --time=02:00:00            # Time limit
#SBATCH --partition=ifarm          # Partition name

set -x

# Define variables
RECV_PORT=$1
DEST_PORT=$2
DEST_IP=$3
APP_SIF="../../cpu-emu.sif"

# Default emulator parameters
THREADS=10
LATENCY=100
MEM_FOOTPRINT=0.1
OUTPUT_SIZE=0.001

# Get node information
node_name=$(hostname)
node_ip=$(hostname -i)
echo "Hostname: $node_name"
echo -e "IPv4 address: $node_ip\n"

# Create output directory
mkdir -p output

# Start CPU emulator
apptainer run --bind output:/output \
    ${APP_SIF} --output-dir /output \
    -t ${THREADS} -b ${LATENCY} -m ${MEM_FOOTPRINT} -o ${OUTPUT_SIZE} \
    -r ${RECV_PORT} -p ${DEST_PORT} -i ${DEST_IP} -v 1 &

EMU_PID=$!
echo -e "CPU Emulator process started with PID $EMU_PID \n"

# Keep process running
wait $EMU_PID 