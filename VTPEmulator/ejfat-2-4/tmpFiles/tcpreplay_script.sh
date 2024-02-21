#!/bin/bash


# Set the variables
interface="enp193s0f1np1"
pcap_file="/home/ayan/tmpFiles/CLAS12_ECAL_PCAL_S2_2023-12-17_10-00-51.pcap"

# Execute tcpreplay with the specified parameters
tcpreplay -T gtod -i "$interface" "$pcap_file"

echo "Done for ejfat-4"
