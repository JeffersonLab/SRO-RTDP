FROM ubuntu:22.04

# Prevent interactive prompts during package installation
ENV DEBIAN_FRONTEND=noninteractive

# Install dependencies
RUN apt-get update && apt-get install -y \
    build-essential \
    cmake \
    g++ \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Create app directory
WORKDIR /app

# Create output directory
RUN mkdir -p output

# Copy source files
COPY . .

# Build the application
RUN mkdir -p build && \
    cd build && \
    cmake .. && \
    make

# Expose ports (default range, can be overridden)
EXPOSE 5000-5010

# Set the entrypoint
ENTRYPOINT ["/app/build/stream_server"]

# Default command (can be overridden)
CMD ["0.0.0.0", "5000", "3"]