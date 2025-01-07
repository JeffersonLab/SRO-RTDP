#!/bin/bash
#SBATCH --job-name=cpu-emu          # Job name
#SBATCH --output=slurm_emu_%j.log
#SBATCH --error=slurm_emu_%j.log
#SBATCH --ntasks=1                  # Number of tasks
#SBATCH --cpus-per-task=4           # CPU cores per task
#SBATCH --mem=16G                   # Increased memory to 16GB
#SBATCH --time=02:00:00            # Time limit
#SBATCH --partition=ifarm          # Partition name

set -x

# Define variables
RECV_PORT=$1
DEST_PORT=$2
DEST_IP=$3
APP_SIF="../../cpu-emu.sif"

# Reduced emulator parameters to use less memory
THREADS=4                  # Reduced from 10
LATENCY=50                # Reduced from 100
MEM_FOOTPRINT=0.05        # Reduced from 0.1
OUTPUT_SIZE=0.001

# Get node information
node_name=$(hostname)
node_ip=$(hostname -i)
echo "Hostname: $node_name"
echo -e "IPv4 address: $node_ip\n"

# Create output directory
mkdir -p output

# Print memory limits
echo "Memory limits:"
echo "SLURM_MEM_PER_NODE: $SLURM_MEM_PER_NODE"
echo "SLURM_MEM_PER_CPU: $SLURM_MEM_PER_CPU"

# Monitor memory usage in background
(while true; do
    echo "Memory usage at $(date):"
    free -h
    echo "Process memory details:"
    ps aux | grep apptainer
    sleep 5
done) > memory_monitor.log 2>&1 &
MONITOR_PID=$!

# Start CPU emulator with memory monitoring
apptainer run --bind output:/output \
    ${APP_SIF} --output-dir /output \
    -t ${THREADS} -b ${LATENCY} -m ${MEM_FOOTPRINT} -o ${OUTPUT_SIZE} \
    -r ${RECV_PORT} -p ${DEST_PORT} -i ${DEST_IP} -v 1 &

EMU_PID=$!
echo -e "CPU Emulator process started with PID $EMU_PID \n"

# Keep process running and capture any OOM messages
wait $EMU_PID || {
    echo "Emulator process failed with exit code $?"
    echo "Last 20 lines of memory monitoring:"
    tail -n 20 memory_monitor.log
}

# Clean up monitor
kill $MONITOR_PID