FROM mcr.microsoft.com/devcontainers/cpp:1-ubuntu-22.04

# Install additional packages
RUN apt-get update && export DEBIAN_FRONTEND=noninteractive \
    && apt-get -y install --no-install-recommends \
    libpcap-dev \
    tcpdump \
    cmake \
    build-essential \
    gdb \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# The base image already includes the vscode user and sudo setup,
# so we don't need to create it again

# Set the default user
USER vscode 