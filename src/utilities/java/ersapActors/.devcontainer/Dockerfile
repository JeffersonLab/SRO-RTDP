FROM ubuntu:22.04

# Avoid prompts from apt
ENV DEBIAN_FRONTEND=noninteractive

# Set up locale
RUN apt-get update && apt-get install -y locales && \
    locale-gen en_US.UTF-8 && \
    update-locale LANG=en_US.UTF-8

ENV LANG=en_US.UTF-8
ENV LANGUAGE=en_US:en
ENV LC_ALL=en_US.UTF-8

# Install basic tools
RUN apt-get update && apt-get install -y \
    curl \
    wget \
    git \
    unzip \
    zip \
    vim \
    nano \
    sudo \
    build-essential \
    cmake \
    pkg-config \
    libpcap-dev \
    tcpdump \
    iputils-ping \
    net-tools \
    && rm -rf /var/lib/apt/lists/*

# Install Java
RUN apt-get update && apt-get install -y \
    openjdk-11-jdk \
    && rm -rf /var/lib/apt/lists/*

# Install Gradle
ENV GRADLE_VERSION=7.6
RUN wget -q https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip -O /tmp/gradle.zip && \
    unzip -q /tmp/gradle.zip -d /opt && \
    rm /tmp/gradle.zip && \
    ln -s /opt/gradle-${GRADLE_VERSION} /opt/gradle

ENV PATH=$PATH:/opt/gradle/bin

# Create ERSAP directory
ENV ERSAP_HOME=/opt/ersap
RUN mkdir -p $ERSAP_HOME/plugins $ERSAP_HOME/bin

# Create a non-root user
ARG USERNAME=developer
ARG USER_UID=1000
ARG USER_GID=$USER_UID

RUN groupadd --gid $USER_GID $USERNAME && \
    useradd --uid $USER_UID --gid $USER_GID -m $USERNAME && \
    echo $USERNAME ALL=\(root\) NOPASSWD:ALL > /etc/sudoers.d/$USERNAME && \
    chmod 0440 /etc/sudoers.d/$USERNAME

# Set up Git configuration for the user
RUN mkdir -p /home/$USERNAME/.config/git && \
    chown -R $USERNAME:$USERNAME /home/$USERNAME/.config

# Set up ERSAP_USER_DATA
ENV ERSAP_USER_DATA=/workspace/ersap-data
RUN mkdir -p $ERSAP_USER_DATA && \
    chown -R $USERNAME:$USERNAME $ERSAP_USER_DATA && \
    chown -R $USERNAME:$USERNAME $ERSAP_HOME

# Switch to the non-root user
USER $USERNAME

# Configure Git
RUN git config --global init.defaultBranch main && \
    git config --global core.editor "nano"

# Set the working directory
WORKDIR /workspace

# Set up a simple ERSAP shell script
RUN echo '#!/bin/bash\necho "ERSAP shell started"\nwhile true; do\n  sleep 1\ndone' > /tmp/ersap-shell && \
    sudo mv /tmp/ersap-shell $ERSAP_HOME/bin/ersap-shell && \
    sudo chmod +x $ERSAP_HOME/bin/ersap-shell

CMD ["/bin/bash"] 