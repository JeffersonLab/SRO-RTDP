# ERSAP Actors

A Java-based project for processing SAMPA data streams using the ERSAP framework.

## Overview

ERSAP Actors is a data processing framework built on top of the ERSAP (Event Reconstruction System for Parallel Architecture) framework. It is designed to process data streams from SAMPA (a front-end ASIC for particle detectors) in real-time.

The project provides a set of services for:
- Reading data streams from a network source
- Decoding SAMPA data packets
- Processing and analyzing the decoded data
- Writing the processed data to disk

## Prerequisites

- Java 11 or higher
- Gradle 7.0 or higher
- ERSAP framework
- pcap2stream utility (for testing)

## Development Environment

This project includes a devcontainer configuration for consistent development environments. To use it:

1. Install Docker and VS Code with the Remote - Containers extension
2. Open the project folder in VS Code
3. When prompted, click "Reopen in Container"

The devcontainer includes all necessary dependencies and tools.

## Project Structure

```
ersapActors/
├── main/
│   ├── java/
│   │   └── org/jlab/ersap/actor/
│   │       ├── datatypes/
│   │       │   └── JavaObjectType.java
│   │       ├── rtdp/
│   │       │   ├── engine/
│   │       │   ├── source/
│   │       │   └── util/
│   │       └── sampa/
│   │           ├── SampaStreamReader.java
│   │           ├── SampaDecoder.java
│   │           └── SampaProcessor.java
│   └── resources/
│       └── services.yaml
├── samples/
│   ├── generate_test_pcap.sh
│   └── README.md
├── .devcontainer/
│   ├── devcontainer.json
│   ├── Dockerfile
│   ├── docker-compose.yml
│   ├── setup.sh
│   └── README.md
├── build.gradle
├── settings.gradle
├── README.md
└── test_pipeline.sh
```

## Building the Project

```bash
gradle build
```

## Deploying the Services

```bash
gradle deploy
```

## Running the Test Pipeline

The project includes a test pipeline script that:
1. Sets up the necessary environment
2. Generates a test PCAP file if one doesn't exist
3. Starts the stream server
4. Starts the ERSAP shell
5. Sends PCAP data to the server for processing

To run the test pipeline:

```bash
./test_pipeline.sh
```

## Configuration

The services are configured using the `services.yaml` file in the `main/resources` directory. This file defines:

- Input/output services
- Processing services
- Service configuration parameters
- MIME types for data exchange

## License

This project is licensed under the Apache License 2.0.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request. 