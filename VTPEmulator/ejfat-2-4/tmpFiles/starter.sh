#!/bin/bash

# Specify the target time in HH:MM format
TARGET_TIME="$1"


interface="enp193s0f1np1"
pcap_file="/home/ayan/tmpFiles/CLAS12_ECAL_PCAL_S2_2023-12-17_10-00-51.pcap"

echo "Done for ejfat-2"
while true; do
    # Get the current time in HH:MM format
    CURRENT_TIME=$(date +"%H:%M")
    # Check if the current time matches the target time
    if [ "$CURRENT_TIME" == "$TARGET_TIME" ]; then
	echo "Ejfat-2 starting to send"
        
	# Execute tcpreplay with the specified parameters
	tcpreplay -T gtod -i "$interface"  "$pcap_file"

	wait 
	break  # Exit the loop after executing the command
    fi

done
