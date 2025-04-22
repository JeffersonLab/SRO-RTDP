FROM python:3.9-slim

# Install system dependencies
RUN apt-get update && apt-get install -y \
    git \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy requirements first to leverage Docker cache
COPY requirements.txt .

# Install Python dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Copy the script
COPY generate_flow.py .

# Create cylc directory
RUN mkdir -p cylc

# Set the entrypoint
ENTRYPOINT ["python", "generate_flow.py"] 