#!/bin/bash

# Set -e to exit on error
set -e

# Set fixed workflow name
WORKFLOW_NAME="rtdp-workflow"

# Get the directory of this script and set up paths
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
COMPONENTS_DIR="$( cd "${SCRIPT_DIR}/.." &> /dev/null && pwd )"
CYLC_DIR="${COMPONENTS_DIR}/example/cpu-emulator/cylc"
WF_GENERATOR_DIR="${COMPONENTS_DIR}/wf-generator"

# Clean existing directories
echo "Cleaning up existing directories..."
rm -rf "${CYLC_DIR}/sifs" "${CYLC_DIR}/etc/config" "${CYLC_DIR}/scripts" "${CYLC_DIR}/share"

# Create necessary directories in Cylc directory
echo "Creating directories..."
mkdir -p "${CYLC_DIR}/sifs" "${CYLC_DIR}/etc/config" "${CYLC_DIR}/scripts" "${CYLC_DIR}/share"

# Run workflow generator first
echo "Generating workflow configuration..."
if [ -d "${WF_GENERATOR_DIR}" ]; then
    cd "${WF_GENERATOR_DIR}"
    if [ -f "workflow_config.yml" ]; then
        python generate_workflow.py workflow_config.yml --output-dir generated
        if [ $? -ne 0 ]; then
            echo "Error: Failed to generate workflow configuration"
            exit 1
        fi
        echo "Workflow configuration generated successfully"
        
        # Copy generated configuration files to Cylc share directory
        echo "Copying configuration files to Cylc directory..."
        if [ -d "${WF_GENERATOR_DIR}/generated/share" ]; then
            cp -r "${WF_GENERATOR_DIR}/generated/share/"* "${CYLC_DIR}/share/"
            echo "Configuration files copied successfully"
        else
            echo "Error: Generated share directory not found at ${WF_GENERATOR_DIR}/generated/share"
            exit 1
        fi
    else
        echo "Error: workflow_config.yml not found in ${WF_GENERATOR_DIR}"
        exit 1
    fi
else
    echo "Error: Workflow generator directory not found at ${WF_GENERATOR_DIR}"
    exit 1
fi

# Build container images and convert to SIF format
echo "Building and converting container images..."
if [ -f "${CYLC_DIR}/build.sh" ]; then
    cd "${CYLC_DIR}"
    
    # Convert CPU emulator image
    echo "Converting CPU emulator image..."
    ./build.sh -i jlabtsai/rtdp-cpu_emu:v0.1 -o cpu-emu.sif
    if [ $? -ne 0 ]; then
        echo "Error: Failed to convert CPU emulator image"
        exit 1
    fi
    
    # Convert RTDP components image
    echo "Converting RTDP components image..."
    ./build.sh -i jlabtsai/rtdp-components:latest -o rtdp-components.sif
    if [ $? -ne 0 ]; then
        echo "Error: Failed to convert RTDP components image"
        exit 1
    fi
else
    echo "Error: build.sh not found at ${CYLC_DIR}/build.sh"
    exit 1
fi

# Make sure we're in the correct directory for Cylc operations
cd "${CYLC_DIR}"
CYLC_RUN_DIR=~/cylc-run/${WORKFLOW_NAME}
mkdir -p ${CYLC_RUN_DIR}

# Install the workflow using Cylc
echo "Installing workflow..."
cylc install --workflow-name=${WORKFLOW_NAME}

# Validate the workflow
echo "Validating workflow..."
cylc validate .

# Verify SIF files and configuration
echo "Verifying files..."
if [ ! -f "${CYLC_DIR}/sifs/cpu-emu.sif" ]; then
    echo "Warning: cpu-emu.sif not found!"
fi
if [ ! -f "${CYLC_DIR}/sifs/rtdp-components.sif" ]; then
    echo "Warning: rtdp-components.sif not found!"
fi
if [ ! -d "${CYLC_DIR}/share" ] || [ -z "$(ls -A ${CYLC_DIR}/share)" ]; then
    echo "Warning: No configuration files found in ${CYLC_DIR}/share"
fi

echo "Workflow installed and validated."
echo "Before running, please ensure:"
echo "1. Both SIF files are present in ./sifs/:"
echo "   - cpu-emu.sif"
echo "   - rtdp-components.sif"
echo "2. Component configuration files are in ./share/"
echo "3. Configure environment variables in flow.cylc if needed"
echo ""
echo "To run the workflow:"
echo "cylc play ${WORKFLOW_NAME}"
echo ""
echo "To monitor the workflow:"
echo "cylc tui ${WORKFLOW_NAME}"
echo ""
echo "To stop the workflow:"
echo "cylc stop ${WORKFLOW_NAME}"
echo ""
echo "Workflow installed at: ${CYLC_RUN_DIR}"

# Optionally start the workflow (commented out by default)
# cylc play ${WORKFLOW_NAME} 