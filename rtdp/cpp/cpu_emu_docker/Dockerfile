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

# Create a script that can act as both CPU emulator and receiver
RUN echo '#!/bin/bash\n\
if [ "$1" = "receive" ]; then\n\
    shift\n\
    PORT="$1"\n\
    echo "Starting netcat listener on port $PORT..."\n\
    # Use netcat-traditional for better compatibility\n\
    if command -v nc.traditional >/dev/null 2>&1; then\n\
        exec nc.traditional -l -p "$PORT"\n\
    else\n\
        exec nc -l "$PORT"\n\
    fi\n\
else\n\
    exec ./cpu_emu "$@"\n\
fi' > /app/entrypoint.sh && \
    chmod +x /app/entrypoint.sh

# Default command (can be overridden via command line)
ENTRYPOINT ["/app/entrypoint.sh"]
CMD ["-h"] 