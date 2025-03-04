# ERSAP Actors Development Container

This directory contains configuration files for setting up a development container for the ERSAP Actors project. The development container provides a consistent environment with all the necessary dependencies pre-installed.

## Features

- Ubuntu 22.04 base image
- OpenJDK 11
- Gradle 7.4.2
- ERSAP framework pre-installed
- Build tools for pcap2stream
- Network tools for testing

## Getting Started

1. Install [Visual Studio Code](https://code.visualstudio.com/) and the [Dev Containers extension](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers).

2. Open the project folder in VS Code.

3. Click on the green icon in the bottom-left corner of the VS Code window and select "Reopen in Container".

4. Wait for the container to build and start. This may take a few minutes the first time.

5. Once the container is running, the setup script will automatically:
   - Create the ERSAP_USER_DATA directory structure
   - Copy the services.yaml file to the config directory
   - Build the ERSAP actors project
   - Build the pcap2stream tools if they're not already built

## Testing the ERSAP Actors

### Using the Command Line

1. Start the stream server:
   ```bash
   cd /workspace/pcap2stream/server/build
   ./stream_server 0.0.0.0 5000 3
   ```

2. In another terminal, send PCAP data to the server:
   ```bash
   cd /workspace/pcap2stream/sender/build
   ./pcap2stream /path/to/capture.pcap 127.0.0.1 5000
   ```

3. Run the ERSAP shell:
   ```bash
   ersap-shell
   ```

### Using Docker Compose

You can also use Docker Compose to run the stream server and pcap sender:

1. Start the stream server:
   ```bash
   cd /workspace/src/utilities/java/ersapActors/.devcontainer
   docker-compose up stream-server
   ```

2. In another terminal, send PCAP data to the server:
   ```bash
   cd /workspace/src/utilities/java/ersapActors/.devcontainer
   docker-compose run pcap-sender
   ```

## Environment Variables

- `ERSAP_HOME`: Set to `/opt/ersap`
- `ERSAP_USER_DATA`: Set to `/workspace/ersap-data`

## Customization

You can customize the development container by modifying the following files:

- `devcontainer.json`: VS Code Dev Container configuration
- `Dockerfile`: Container image definition
- `docker-compose.yml`: Multi-container setup
- `setup.sh`: Environment initialization script 