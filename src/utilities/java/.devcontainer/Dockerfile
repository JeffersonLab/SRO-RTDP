FROM ubuntu:22.04

# Avoid prompts from apt
ENV DEBIAN_FRONTEND=noninteractive

# Install basic dependencies
RUN apt-get update && apt-get install -y \
    software-properties-common \
    curl \
    wget \
    sudo \
    && rm -rf /var/lib/apt/lists/*

# Install Python 3.10 and other dependencies
RUN add-apt-repository -y ppa:deadsnakes/ppa \
    && add-apt-repository -y ppa:rmescandon/yq \
    && apt-get update \
    && apt-get install -y \
    python3.10 \
    python3-pip \
    build-essential \
    autoconf \
    cmake \
    libtool \
    pkg-config \
    libglib2.0-dev \
    ninja-build \
    openssl \
    libssl-dev \
    libsystemd-dev \
    protobuf-compiler \
    libre2-dev \
    gdb \
    docker.io \
    openjdk-17-jdk \
    libzmq5-dev \
    libprotobuf-dev \
    firewalld \
    maven \
    yq \
    crudini \
    && rm -rf /var/lib/apt/lists/*

# Set up Python alternatives
RUN update-alternatives --install /usr/bin/python3 python3 /usr/bin/python3.10 1

# Install Python packages
RUN pip3 install --no-cache-dir meson pybind11 scapy

# Install Docker Compose and Buildx
RUN mkdir -p /home/vscode/.docker/cli-plugins \
    && curl -SL https://github.com/docker/compose/releases/download/v2.27.0/docker-compose-linux-x86_64 -o /home/vscode/.docker/cli-plugins/docker-compose \
    && curl -SL https://github.com/docker/buildx/releases/download/v0.14.0/buildx-v0.14.0.linux-amd64 -o /home/vscode/.docker/cli-plugins/docker-buildx \
    && chmod +x /home/vscode/.docker/cli-plugins/docker-compose \
    && chmod +x /home/vscode/.docker/cli-plugins/docker-buildx

# Create non-root user
RUN useradd -m -s /bin/bash vscode || true \
    && chown -R vscode:vscode /home/vscode

# Configure sudo access for vscode user
RUN echo "vscode ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/vscode \
    && chmod 0440 /etc/sudoers.d/vscode \
    && usermod -aG sudo vscode

# Then add user to docker group
RUN usermod -a -G docker vscode

USER vscode
WORKDIR /home/vscode 