#!/bin/bash

# Exit on error
set -e

# Load necessary modules
module load apptainer

# Make scripts executable
chmod +x build.sh
chmod +x cleanup.sh

echo "Installation completed successfully." 