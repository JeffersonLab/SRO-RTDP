# ERSAP Actors

This repository contains ERSAP (Event Reconstruction Software Architecture Platform) actors for various data processing tasks.

## Directory Structure

- **pcap-stream-source**: The main project for streaming PCAP (Packet Capture) data into ERSAP. This includes:
  - Ring buffer monitoring capabilities
  - Mock PCAP server for testing
  - Test client with built-in monitoring
  - Comprehensive documentation

- **ersap-java**: Core ERSAP Java library that provides the foundation for ERSAP applications.

- **ersap-data**: Data directory for ERSAP applications, containing configuration files, plugins, and output data.

- **pet-sro**: PET (Positron Emission Tomography) SRO (Streaming Reconstruction and Online) project, which provides utilities for ERSAP actors.

## Getting Started

To use the PCAP Stream Source, refer to the documentation in the `pcap-stream-source` directory.

## Development Environment

This project uses a devcontainer for development. To set up the development environment:

1. Install Docker and Visual Studio Code with the Remote - Containers extension.
2. Open the repository in Visual Studio Code.
3. When prompted, click "Reopen in Container" to start the devcontainer.

## Building

Each project has its own build.gradle file. To build a specific project:

```bash
cd <project-directory>
./gradlew build
```

## Testing

The `pcap-stream-source` project includes comprehensive testing scripts in the `scripts` directory.

## License

This project is licensed under the same license as the ERSAP framework. 