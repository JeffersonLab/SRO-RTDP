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

# Install necessary packages
RUN apt-get update && apt-get install -y \
    openjdk-11-jdk \
    git \
    curl \
    wget \
    unzip \
    zip \
    vim \
    nano \
    sudo \
    build-essential \
    cmake \
    pkg-config \
    libpcap-dev \
    netcat \
    tcpdump \
    net-tools \
    iputils-ping \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Install Gradle
ENV GRADLE_VERSION=7.6
RUN wget -q https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip -O /tmp/gradle.zip \
    && unzip -q /tmp/gradle.zip -d /opt \
    && rm /tmp/gradle.zip \
    && ln -s /opt/gradle-${GRADLE_VERSION} /opt/gradle
ENV PATH=$PATH:/opt/gradle/bin

# Set up ERSAP environment
ENV ERSAP_HOME=/opt/ersap
ENV ERSAP_USER_DATA=/workspace/ersap-data
RUN mkdir -p $ERSAP_HOME/plugins/rtdp/lib \
    && mkdir -p $ERSAP_HOME/bin

# Clone and build ersap-java
WORKDIR /tmp
RUN git clone https://github.com/JeffersonLab/ersap-java.git \
    && cd ersap-java \
    && ./gradlew deploy \
    && ./gradlew publishToMavenLocal \
    && cd .. \
    && rm -rf ersap-java

# Create a simple ersap-shell script for testing
RUN echo '#!/bin/bash\necho "ERSAP shell started"\nwhile true; do\n  sleep 1\ndone' > /tmp/ersap-shell && \
    sudo mv /tmp/ersap-shell $ERSAP_HOME/bin/ersap-shell && \
    sudo chmod +x $ERSAP_HOME/bin/ersap-shell

# Set up working directory
WORKDIR /workspace

# Set up user
ARG USERNAME=developer
ARG USER_UID=1000
ARG USER_GID=$USER_UID

# Create the user
RUN groupadd --gid $USER_GID $USERNAME \
    && useradd --uid $USER_UID --gid $USER_GID -m $USERNAME \
    && echo $USERNAME ALL=\(root\) NOPASSWD:ALL > /etc/sudoers.d/$USERNAME \
    && chmod 0440 /etc/sudoers.d/$USERNAME \
    && chown -R $USERNAME:$USERNAME $ERSAP_HOME

# Set the default user
USER $USERNAME

# Set environment variables
ENV JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
ENV PATH=$PATH:$JAVA_HOME/bin:$ERSAP_HOME/bin 