FROM ubuntu:22.04

# Install required build tools and dependencies
RUN apt-get update && apt-get install -y \
    g++ \
    make \
    time \
    less \
    libzmq3-dev \
    libyaml-dev \
    python3 \
    python3-pip \
    && rm -rf /var/lib/apt/lists/*

# Install Python ZMQ library
RUN pip3 install pyzmq

# Set working directory
WORKDIR /app

# Copy source files
COPY cpu_emu.cc .
COPY cpu_emu.yaml .
COPY buildp .

# Create Python scripts for sender and receiver
RUN echo 'import zmq\nimport sys\nimport time\n\ndef send_data(host, port, data):\n    context = zmq.Context()\n    socket = context.socket(zmq.REQ)\n    socket.connect(f"tcp://{host}:{port}")\n    socket.send(data)\n    reply = socket.recv()\n    print(f"Received reply: {reply}")\n\nif __name__ == "__main__":\n    if len(sys.argv) != 3:\n        print("Usage: python3 sender.py HOST PORT")\n        sys.exit(1)\n    host = sys.argv[1]\n    port = sys.argv[2]\n    data = sys.stdin.buffer.read()\n    send_data(host, port, data)' > sender.py

RUN echo 'import zmq\nimport sys\n\ndef receive_data(port, bind_ip="*"):\n    context = zmq.Context()\n    socket = context.socket(zmq.REP)\n    socket.bind(f"tcp://{bind_ip}:{port}")\n    while True:\n        message = socket.recv()\n        sys.stdout.buffer.write(message)\n        sys.stdout.buffer.flush()\n        socket.send(b"ACK")\n\nif __name__ == "__main__":\n    if len(sys.argv) < 2:\n        print("Usage: python3 receiver.py PORT [BIND_IP]")\n        sys.exit(1)\n    port = sys.argv[1]\n    bind_ip = sys.argv[2] if len(sys.argv) > 2 else "*"\n    receive_data(port, bind_ip)' > receiver.py

# Make build script executable
RUN chmod +x buildp

# Build the application
RUN ./buildp cpu_emu

# Create output directory
RUN mkdir -p /output && chmod 777 /output

# Create a script that can act as CPU emulator, sender, or receiver
RUN echo '#!/bin/bash\n\
if [ "$1" = "send" ]; then\n\
    shift\n\
    HOST="$1"\n\
    PORT="$2"\n\
    python3 /app/sender.py "$HOST" "$PORT"\n\
elif [ "$1" = "receive" ]; then\n\
    shift\n\
    PORT="$1"\n\
    BIND_IP="${2:-*}"\n\
    python3 /app/receiver.py "$PORT" "$BIND_IP"\n\
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