≈#!/bin/bash

# Use the hostname as an argument
hostname=$1

# Execute the command with the appropriate path for each machine
case $hostname in
  ejfat-2)
    /home/ayan/tmpFiles/starter.sh
    ;;
  ejfat-4)
    /home/ayan/tmpFiles/starter.sh
    ;;
  *)
    echo "Unknown hostname: $hostname"
    ;;
esac
