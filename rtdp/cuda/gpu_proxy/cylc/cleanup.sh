#!/bin/bash

# Exit on error
set -e

# Remove containers
rm -rf containers

# Remove workflow run directories
rm -rf log
rm -rf share
rm -rf work

echo "Cleanup completed successfully." 