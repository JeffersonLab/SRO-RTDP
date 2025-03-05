# ERSAP Actors

This repository contains ERSAP (Event Reconstruction Software Architecture Platform) actors for various data processing tasks.

## Directory Structure

- **pcap-stream-source**: The main project for streaming PCAP (Packet Capture) data into ERSAP. This includes:
  - Ring buffer monitoring capabilities
  - Mock PCAP server for testing
  - Test client with built-in monitoring
  - Comprehensive documentation (README.md, SUMMARY.md, MONITORING.md)
  - Custom configuration files in `custom-config/`
  - Testing scripts in `scripts/`
  - Test data in `test-data/`

- **ersap-java**: Core ERSAP Java library that provides the foundation for ERSAP applications.

- **ersap-data**: Data directory for ERSAP applications, containing configuration files, plugins, and output data.

- **pet-sro**: PET (Positron Emission Tomography) SRO (Streaming Reconstruction and Online) project, which provides utilities for ERSAP actors.

- **.devcontainer**: Development container configuration for consistent development environment:
  - Ubuntu 22.04 base image
  - OpenJDK 11
  - Gradle 7.4.2
  - ERSAP framework pre-installed
  - Build tools for pcap2stream
  - Network tools for testing

## Getting Started

To use the PCAP Stream Source, refer to the comprehensive documentation in the `pcap-stream-source` directory:
- `README.md`: Main documentation
- `SUMMARY.md`: Project summary and overview
- `MONITORING.md`: Details about monitoring capabilities

## Development Environment

This project uses a devcontainer for development. To set up the development environment:

1. Install Docker and Visual Studio Code with the Remote - Containers extension.
2. Open the repository in Visual Studio Code.
3. Click on the green icon in the bottom-left corner of the VS Code window and select "Reopen in Container".
4. Wait for the container to build and start. This may take a few minutes the first time.
5. Once the container is running, the setup script will automatically:
   - Create the ERSAP_USER_DATA directory structure
   - Copy the services.yaml file to the config directory
   - Build the ERSAP actors project

## Git Setup

A helper script is provided to assist with Git operations in the devcontainer:

```bash
./git-setup.sh
```

This script will:
1. Configure Git to recognize the workspace as a safe directory
2. Check if the Git repository is properly mounted
3. Configure your Git user name and email if not already set
4. Show the current Git status

For more detailed information about Git usage in this project, refer to the `GIT_USAGE.md` file.

## Building

Each project has its own build.gradle file. To build a specific project:

```bash
cd <project-directory>
./gradlew build
```

For the pcap-stream-source project specifically:

```bash
cd pcap-stream-source
./gradlew build
```

## Testing

The `pcap-stream-source` project includes comprehensive testing scripts in the `scripts/` directory. You can use these scripts to:

1. Start the stream server
2. Send PCAP data to the server
3. Run the ERSAP shell with the appropriate configuration

## Environment Variables

- `ERSAP_HOME`: Set to `/opt/ersap` in the devcontainer
- `ERSAP_USER_DATA`: Set to `/workspace/ersap-data` in the devcontainer
