#!/bin/bash

# Set the path to the parallel-ssh command
PARALLEL_SSH_COMMAND="parallel-ssh"

# Set the username
USERNAME="$1"

# Set the target time 
TARGET_TIME="$2"

# Set the output log file
OUTPUT_LOG="output.log"

# Set the error log file
ERROR_LOG="error.log"

# Set the file containing the host and command mappings
HOST_COMMAND_FILE="VTPconfigDetails.txt"

# Read host and command mappings from the combined file
mapfile -t HOST_COMMANDS < "$HOST_COMMAND_FILE"                              
if [ "${#HOST_COMMANDS[@]}" -eq 0 ]; then
    echo "Error: No host and command mappings specified in $HOST_COMMAND_FILE."
    exit 1
fi

# Build the command with a timeout of 0
COMMAND="$PARALLEL_SSH_COMMAND -l $USERNAME -o $OUTPUT_LOG -e $ERROR_LOG -t 0"

# Construct a list of hosts
HOST_LIST=""
for LINE in "${HOST_COMMANDS[@]}"; do
    HOST=$(echo "$LINE" | awk '{print $1}')
    HOST_LIST+=" $HOST"
done

# Add host list to the command
COMMAND+=" -H '$HOST_LIST'"


# Loop through command mappings
COMMANDLIST=""
for LINE in "${HOST_COMMANDS[@]}"; do
    # Extract host and command from the line
    COMMAND1=$(echo "$LINE" | awk '{$1=""; print $0}')

    # Add host and command to the command
    COMMANDLIST+=" '$COMMAND1 $TARGET_TIME'"
done

COMMAND+="$COMMANDLIST"
# Print the command to the console
echo "Running the following command:"
echo "$COMMAND"

# Run the command
eval "$COMMAND"

