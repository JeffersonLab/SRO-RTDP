# Use Ubuntu 22.04 as base image
FROM ubuntu:22.04

# Install required dependencies
RUN apt-get update && apt-get install -y \
    build-essential \
    cmake \
    libzmq3-dev \
    libyaml-cpp-dev \
    libyaml-dev \
    time \
    python3 \
    python3-pip \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies
RUN pip3 install pyzmq

# Set working directory
WORKDIR /app

# Copy source files
COPY cpu_emu.cc .
COPY buffer_packet.hh .
COPY zmq-event-clnt.cc .
COPY buffer_packet_zmq_emu.py .
COPY launcher_py_cpu_emu.py .
COPY simulate_sender-zmq-emu.py .

# Build cpu_emu
RUN g++ -o cpu_emu cpu_emu.cc -lzmq -lyaml-cpp -lyaml -pthread

# Build zmq-event-clnt
RUN g++ -o zmq-event-clnt zmq-event-clnt.cc -lzmq

# Create entrypoint script
COPY <<'EOF' /app/entrypoint.sh
#!/bin/bash

# Default mode is emulator
MODE=${1:-emulator}
shift

case "$MODE" in
    emulator)
        exec ./cpu_emu "$@"
        ;;
    receiver)
        # Check if -z flag is present or if yaml file has terminal: 1
        if [[ "$*" == *"-z"* ]] || \
           ([[ "$*" == *"-y"* ]] && grep -q "terminal: 1" $(echo "$*" | grep -o -- '-y [^ ]*' | cut -d' ' -f2)); then
            exec ./cpu_emu "$@"
        else
            echo "Error: Receiver mode requires either -z flag or yaml file with terminal: 1"
            exit 1
        fi
        ;;
    sender)
        exec python3 simulate_sender-zmq-emu.py "$@"
        ;;
    launcher)
        exec python3 launcher_py_cpu_emu.py "$@"
        ;;
    *)
        echo "Usage: $0 {emulator|receiver|sender|launcher} [options]"
        echo "  emulator: ./cpu_emu [options]"
        echo "  receiver: ./cpu_emu -z [options] or ./cpu_emu -y <yaml> (with terminal: 1)"
        echo "  sender:   python3 simulate_sender-zmq-emu.py [options]"
        echo "  launcher: python3 launcher_py_cpu_emu.py [options]"
        exit 1
        ;;
esac
EOF

# Make entrypoint script executable
RUN chmod +x /app/entrypoint.sh

# Expose default ports
EXPOSE 55555-55600

# Set entrypoint
ENTRYPOINT ["/app/entrypoint.sh"] 