#!/bin/bash

# Pull Docker image and convert to Apptainer
echo "Converting Docker image to Apptainer format..."
sudo apptainer pull cpu_emu.sif docker://jlabtsai/rtdp-cpu_emu:latest

echo "Making scripts executable..."
chmod +x run_receiver.sh run_sender.sh run_source.sh

echo "Done! You can now use the following scripts:"
echo "  ./run_receiver.sh - to start the receiver"
echo "  ./run_sender.sh   - to start the CPU emulator"
echo "  ./run_source.sh   - to send data" 