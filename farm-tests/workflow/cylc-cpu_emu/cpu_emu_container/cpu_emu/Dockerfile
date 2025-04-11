FROM ubuntu:22.04

# Install required packages
RUN apt-get update && apt-get install -y \
    g++ \
    netcat-openbsd \
    time \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy source files
COPY cpu_emu.cc .
COPY buildp .

# Make buildp executable
RUN chmod +x buildp

# Build the application
RUN ./buildp cpu_emu

# Set environment variables
ENV PATH="/app:${PATH}"

# Default command (can be overridden)
CMD ["bash"] 