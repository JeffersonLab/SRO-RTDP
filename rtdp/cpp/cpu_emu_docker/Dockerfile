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

# Create output directory
RUN mkdir -p /output && chmod 777 /output

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
    # Parse arguments for output directory\n\
    ARGS=("$@")\n\
    OUTPUT_DIR="/output"\n\
    for ((i=0; i<${#ARGS[@]}; i++)); do\n\
        if [ "${ARGS[i]}" = "--output-dir" ]; then\n\
            OUTPUT_DIR="${ARGS[i+1]}"\n\
            unset "ARGS[i]" "ARGS[i+1]"\n\
            break\n\
        fi\n\
    done\n\
    # Remove empty elements and recreate array\n\
    ARGS=("${ARGS[@]}")\n\
    cd "$OUTPUT_DIR" || exit 1\n\
    exec /app/cpu_emu "${ARGS[@]}"\n\
fi' > /app/entrypoint.sh && \
    chmod +x /app/entrypoint.sh

# Default command (can be overridden via command line)
ENTRYPOINT ["/app/entrypoint.sh"]
CMD ["-h"] 