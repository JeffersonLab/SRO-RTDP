FROM mcr.microsoft.com/devcontainers/python:3.10

# Install system dependencies for PyQt6 and graphviz
RUN apt-get update && export DEBIAN_FRONTEND=noninteractive \
    && apt-get -y install --no-install-recommends \
    qt6-base-dev \
    libgl1-mesa-dev \
    graphviz \
    graphviz-dev \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies
COPY requirements.txt /tmp/
RUN pip install --no-cache-dir -r /tmp/requirements.txt \
    && pip install --no-cache-dir \
    jupyter \
    jupyterlab \
    ipywidgets \
    graphviz \
    pydot \
    networkx

# Set up X11 forwarding for GUI
ENV QT_X11_NO_MITSHM=1
ENV DISPLAY=:0

# Create non-root user
ARG USERNAME=vscode
ARG USER_UID=1000
ARG USER_GID=$USER_UID

RUN groupadd --gid $USER_GID $USERNAME \
    && useradd --uid $USER_UID --gid $USER_GID -m $USERNAME \
    && apt-get update \
    && apt-get install -y sudo \
    && echo $USERNAME ALL=\(root\) NOPASSWD:ALL > /etc/sudoers.d/$USERNAME \
    && chmod 0440 /etc/sudoers.d/$USERNAME

# Set the default user
USER $USERNAME

# Set working directory
WORKDIR /workspace 