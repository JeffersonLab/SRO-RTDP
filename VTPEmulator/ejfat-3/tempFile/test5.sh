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

# Set the file containing the paths to the script files
SCRIPT_FILE_LIST="commands.txt"

# Read script file paths from the separate file
mapfile -t SCRIPT_FILES < "$SCRIPT_FILE_LIST"

# Check if at least one script file is specified
if [ "${#SCRIPT_FILES[@]}" -eq 0 ]; then
    echo "Error: No script files specified in $SCRIPT_FILE_LIST."
    exit 1
fi

# Build the command with a timeout of 0 and --inline option
COMMAND="$PARALLEL_SSH_COMMAND -h $HOST_LIST_FILE -l $USERNAME -o $OUTPUT_LOG -e $ERROR_LOG -t 0 --inline"

# Add script files to the command
for SCRIPT_FILE in "${SCRIPT_FILES[@]}"; do
    COMMAND+=" '$SCRIPT_FILE && wait'"
done

# Print the command to the console
echo "Running the following command:"
echo "$COMMAND"

# Run the command
eval "$COMMAND"
