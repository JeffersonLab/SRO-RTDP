#!/bin/bash

# Set the path to the parallel-ssh command
PARALLEL_SSH_COMMAND="parallel-ssh"

# Set the path to the host list file
HOST_LIST_FILE="hostList.txt"

# Set the username
USERNAME="ayan"

# Set the output log file
OUTPUT_LOG="output.log"

# Set the error log file
ERROR_LOG="error.log"

# Set the paths to the tcpreplay script files
SCRIPT_FILE1="/home/ayan/tmpFiles/tcpreplay_script.sh"
SCRIPT_FILE2="/home/ayan/tmpFiles/tcpreplay_script.sh"

# Build the command
COMMAND="$PARALLEL_SSH_COMMAND -h $HOST_LIST_FILE -l $USERNAME -o $OUTPUT_LOG -e $ERROR_LOG -t 0 '$SCRIPT_FILE1' '$SCRIPT_FILE2'"

# Print the command to the console
echo "Running the following command:"
echo "$COMMAND"

# Run the command
eval "$COMMAND"
