#!/bin/bash

# Specify the target time in HH:MM format
TARGET_TIME="08:37"

while true; do
    # Get the current time in HH:MM format
    CURRENT_TIME=$(date +"%H:%M")

    # Check if the current time matches the target time
    if [ "$CURRENT_TIME" == "$TARGET_TIME" ]; then
        # Run the tcpreplay command
        sudo tcpreplay -T gtod -i enp193s0f1np1 /path/to/your/CLAS12_ECAL_PCAL_S2_2023-12-17_08-37-33.pcap
        break  # Exit the loop after executing the command
    fi

done
