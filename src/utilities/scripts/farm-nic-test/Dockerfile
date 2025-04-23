FROM python:3.9-slim

# Install iperf2 and other dependencies
RUN apt-get update && apt-get install -y \
    iperf \
    && rm -rf /var/lib/apt/lists/*

# Copy the Python script
COPY nic_test.py /usr/local/bin/nic_test.py
RUN chmod +x /usr/local/bin/nic_test.py

# Create a script to run the server
COPY run_server.sh /usr/local/bin/run_server.sh
RUN chmod +x /usr/local/bin/run_server.sh

# Set the working directory
WORKDIR /app

# Default to running the client test
ENTRYPOINT ["python3", "/usr/local/bin/nic_test.py"] 