#!/bin/bash

# Set -e to exit on error
set -e

# Convert container images if needed
if [ ! -f "../sifs/cpu-emu.sif" ] || [ ! -f "../sifs/rtdp-components.sif" ]; then
    echo "Converting container images..."
    ./convert_images.sh
fi

# Create output directory
mkdir -p ../output

# Submit jobs in sequence and capture job IDs
echo "Submitting sender job..."
SENDER_JOB=$(sbatch sender.slurm | awk '{print $4}')
echo "Sender job ID: $SENDER_JOB"

# Wait for sender to write its IP
sleep 5

echo "Submitting emulator job..."
EMULATOR_JOB=$(sbatch --dependency=afterok:${SENDER_JOB} emulator.slurm | awk '{print $4}')
echo "Emulator job ID: $EMULATOR_JOB"

# Wait for emulator to write its IP
sleep 5

echo "Submitting receiver job..."
RECEIVER_JOB=$(sbatch --dependency=afterok:${EMULATOR_JOB} receiver.slurm | awk '{print $4}')
echo "Receiver job ID: $RECEIVER_JOB"

echo "All jobs submitted. Use these commands to monitor:"
echo "Watch job status:    squeue -u $USER"
echo "Check sender log:    tail -f sender_${SENDER_JOB}.log"
echo "Check emulator log:  tail -f emulator_${EMULATOR_JOB}.log"
echo "Check receiver log:  tail -f receiver_${RECEIVER_JOB}.log"
echo "Cancel all jobs:     scancel ${SENDER_JOB} ${EMULATOR_JOB} ${RECEIVER_JOB}" 