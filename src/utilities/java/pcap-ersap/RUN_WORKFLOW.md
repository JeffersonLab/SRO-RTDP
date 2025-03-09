# Running the PCAP Processing Workflow with ERSAP Orchestration

This guide provides step-by-step instructions for running the PCAP processing workflow using ERSAP orchestration.

## Prerequisites

- Java 11 or later
- Gradle
- pcap2streams tool

## Steps to Run the Workflow

1. **Clone the repository** (if you haven't already):
   ```bash
   git clone <repository-url>
   cd <repository-directory>
   ```

2. **Navigate to the pcap-ersap directory**:
   ```bash
   cd /workspace/src/utilities/java/pcap-ersap
   ```

3. **Make the scripts executable**:
   ```bash
   chmod +x scripts/rebuild_ersap.sh scripts/run_ersap_orchestrator.sh scripts/fix_package_structure.sh scripts/fix_imports.sh
   ```

4. **Run the orchestrator script**:
   ```bash
   ./scripts/run_ersap_orchestrator.sh
   ```

   This script will:
   - Set up the ERSAP environment
   - Rebuild the ERSAP libraries
   - Fix package structure and imports
   - Start pcap2streams if it's not already running
   - Compile the application
   - Start the ERSAP orchestrator
   - Process packets
   - Check output files

5. **Check the output**:
   The processed packets will be written to files in the `/workspace/src/utilities/java/pcap-ersap/output` directory.

## Troubleshooting

If you encounter any issues, please refer to the [Troubleshooting section in the README](README.md#troubleshooting) or the [Script Reference](docs/SCRIPT_REFERENCE.md#troubleshooting).

## Configuration

You can customize the workflow by modifying the following configuration files:

- **pcap-services.yaml**: Located at `/workspace/src/utilities/java/pcap-ersap/config/pcap-services.yaml`. This file configures the ERSAP services.
- **ip-based-config.json**: Located at `/workspace/src/utilities/java/pcap2streams/custom-config/ip-based-config.json`. This file configures the pcap2streams tool.

## Documentation

For more detailed information, please refer to:

- [README.md](README.md): Overview of the project
- [ERSAP Orchestration Architecture](docs/ERSAP_ORCHESTRATION.md): Detailed explanation of the ERSAP orchestration architecture
- [Script Reference](docs/SCRIPT_REFERENCE.md): Quick reference guide for the scripts used in the workflow 