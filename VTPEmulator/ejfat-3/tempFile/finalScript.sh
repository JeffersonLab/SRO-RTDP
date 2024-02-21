#!/bin/bash

host_list="hostList.txt"
username="ayan"
output_log="output1.log"
error_log="error1.log"
timeout_value=0
commands_file="commands.txt"

mkdir -p "$output_log"
mkdir -p "$error_log"

# Read each host from the hostList.txt file
while IFS= read -r host; do
    # Execute the command on each host in parallel
    ssh -l "$username" "$host" "bash -s" < "$commands_file" >> "$output_log" 2>> "$error_log" &
done < "$host_list"

# Wait for all background jobs to finish
wait

echo "Parallel SSH execution completed."
