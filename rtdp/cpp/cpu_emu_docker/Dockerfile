FROM ubuntu:22.04

# Install required build tools and dependencies
RUN apt-get update && apt-get install -y \
    g++ \
    make \
    netcat \
    time \
    less \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy source files
COPY cpu_emu.cc .
COPY buildp .

# Make build script executable
RUN chmod +x buildp

# Build the application
RUN ./buildp cpu_emu

# Default command (can be overridden via command line)
ENTRYPOINT ["./cpu_emu"]
CMD ["-h"] 