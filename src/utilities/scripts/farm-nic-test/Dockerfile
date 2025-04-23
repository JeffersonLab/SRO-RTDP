FROM python:3.9-slim

# Install iperf2 and other dependencies
RUN apt-get update && apt-get install -y \
    iperf \
    && rm -rf /var/lib/apt/lists/*

# Copy the Python script
COPY nic_test.py /usr/local/bin/nic_test.py
RUN chmod +x /usr/local/bin/nic_test.py

# Set the working directory
WORKDIR /app

# Remove the default entrypoint to allow running either client or server
# ENTRYPOINT ["python3", "/usr/local/bin/nic_test.py"] 