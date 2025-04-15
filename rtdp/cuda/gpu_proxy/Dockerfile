FROM nvidia/cuda:12.6.0-devel-ubuntu22.04

LABEL maintainer="Jeng-Yuan Tsai"
LABEL version="1.0.0"

# Set environment variables
ENV PATH=/usr/local/cuda/bin:$PATH
ENV LD_LIBRARY_PATH=/usr/local/cuda/lib64:$LD_LIBRARY_PATH
ENV PYTHONPATH=/app/python_zmq_helper

# Install system dependencies
RUN apt-get update && apt-get install -y \
    cmake \
    make \
    g++ \
    libzmq3-dev \
    libsqlite3-dev \
    python3 \
    python3-pip \
    git \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies
RUN pip3 install pyzmq numpy

# Create and set working directory
WORKDIR /app

# Copy the application files
COPY . /app/

# Build the application
RUN mkdir -p build && cd build && \
    cmake .. && \
    make -j$(nproc)

# Create a simple entrypoint script
RUN echo '#!/bin/bash\n\
if [ "$1" = "--help" ]; then\n\
    echo "GPU Proxy Container Usage:"\n\
    echo "  docker run gpu-proxy [mode] [options]"\n\
    echo "\n\
Modes:"\n\
    echo "  proxy    Run the GPU proxy (default)"\n\
    echo "  sender   Run the sender script"\n\
    echo "  receiver Run the receiver script"\n\
    echo "\n\
Common Options:"\n\
    echo "  -a <ip_address>  Specify the target IP address for ZeroMQ communication"\n\
    echo "  -t              Enable test mode (for proxy)"\n\
    echo "  -h              Show help for specific mode"\n\
    exit 0\n\
fi\n\
\n\
mode=${1:-proxy}\n\
shift\n\
\n\
case $mode in\n\
    proxy)\n\
        if [ $# -eq 0 ]; then\n\
            echo "Error: No arguments provided for proxy mode. Use -h for help."\n\
            exit 1\n\
        fi\n\
        exec /app/build/gpu_emu "$@"\n\
        ;;\n\
    sender)\n\
        exec python3 /app/python_zmq_helper/zmq_fp_sender.py "$@"\n\
        ;;\n\
    receiver)\n\
        exec python3 /app/python_zmq_helper/zmq_fp_receiver.py "$@"\n\
        ;;\n\
    *)\n\
        echo "Error: Unknown mode $mode. Use --help for available modes."\n\
        exit 1\n\
        ;;\n\
esac' > /entrypoint.sh && \
    chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"] 