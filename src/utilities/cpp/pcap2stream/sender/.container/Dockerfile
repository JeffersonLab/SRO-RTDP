FROM ubuntu:22.04

# Prevent interactive prompts during package installation
ENV DEBIAN_FRONTEND=noninteractive

# Install dependencies
RUN apt-get update && apt-get install -y \
    build-essential \
    cmake \
    libpcap-dev \
    g++ \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Create app directory
WORKDIR /app

# Copy source files
COPY . .

# Build the application
RUN mkdir -p build && \
    cd build && \
    cmake .. && \
    make

# Set the entrypoint
ENTRYPOINT ["/app/build/pcap2stream"]

# Default command (can be overridden)
CMD ["--help"]