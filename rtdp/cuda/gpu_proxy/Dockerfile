#
# To build the image on ifarm do this:
#--------------------------------------------------------------------------------
# docker build -t rtdp-gpu_proxy -f Dockerfile .
#
#
# To push to the JLab container image registry:
#--------------------------------------------------------------------------------
# docker tag rtdp-gpu_proxy code.jlab.org/epsci/rtdp-gpu_proxy
# docker login code.jlab.org
# docker push code.jlab.org/epsci/rtdp-gpu_proxy
# 

FROM docker.io/nvidia/cuda:12.6.0-devel-ubuntu22.04 AS builder

LABEL maintainer="Jeng-Yuan Tsai"
LABEL version="1.0.1"

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

# Create and set working directory
WORKDIR /work

# Copy the application files
COPY CMakeLists.txt gpuEmu.cu python_zmq_helper /work/
COPY python_zmq_helper /work/python_zmq_helper/

# Build the application
RUN cmake -S . -B build -DCMAKE_INSTALL_PREFIX=/app && \
    cmake --build build --target install -j$(nproc)

# Runtime stage
FROM docker.io/nvidia/cuda:12.6.0-runtime-ubuntu22.04 AS runtime

# Copy the application files
COPY --from=builder /app/ /app/

# Install Python dependencies
RUN apt-get update && apt-get install -y python3-pip libzmq5 \
    && pip3 install pyzmq numpy \
    && rm -rf /var/lib/apt/lists/*

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
Proxy Mode Options:"\n\
    echo "  --in-port <port>     Input port for ZeroMQ (default: 55555)"\n\
    echo "  -a, --out-ip <ip>    Output IP address for ZeroMQ"\n\
    echo "  --out-port <port>    Output port for ZeroMQ (default: 55556)"\n\
    echo "  -t, --test          Enable test mode with result verification"\n\
    echo "  -r, --rate <value>  Control output/input volume ratio (default: 0.5)"\n\
    echo "  -w, --width <size>  Set GPU input matrix column size (default: 2048)"\n\
    echo "  -v, --verbose       Enable verbose output with detailed monitoring"\n\
    echo "\n\
Sender Mode Options:"\n\
    echo "  -a, --ip-addr <ip>  Target IP address for ZeroMQ communication"\n\
    echo "  -p, --port <port>   Port number to send data to (default: 55555)"\n\
    echo "  -r, --rate <value>  Target send rate in MB/s (default: 25)"\n\
    echo "  --group-size <size> Group size for sending (default: 2048)"\n\
    echo "  --hwm <value>      Socket high water mark (default: 1000)"\n\
    echo "  -v, --verbose      Enable verbose output with detailed monitoring"\n\
    echo "  --all-ones         Send all ones instead of random values"\n\
    echo "\n\
Receiver Mode Options:"\n\
    echo "  -p, --port <port>   Port number to receive data on (default: 55556)"\n\
    echo "  -v, --verbose      Enable verbose output with detailed monitoring"\n\
    echo "\n\
Common Options:"\n\
    echo "  -h, --help         Show this help message"\n\
    exit 0\n\
fi\n\
\n\
mode=${1:-proxy}\n\
shift\n\
\n\
case $mode in\n\
    proxy)\n\
        exec /app/bin/gpu_emu "$@"\n\
        ;;\n\
    sender)\n\
        exec python3 /app/bin/zmq_fp_sender.py "$@"\n\
        ;;\n\
    receiver)\n\
        exec python3 /app/bin/zmq_fp_receiver.py "$@"\n\
        ;;\n\
    *)\n\
        echo "Error: Unknown mode $mode. Use --help for available modes."\n\
        exit 1\n\
        ;;\n\
esac' > /entrypoint.sh && \
    chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"] 