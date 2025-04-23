#!/bin/bash

# Build the Docker image
docker build -t jlabtsai/rtdp-farm-nic-test .

# Push the Docker image to Docker Hub
docker push jlabtsai/rtdp-farm-nic-test

# Run the test
echo "Usage: docker run --network=host jlabtsai/rtdp-farm-nic-test <receiver_ip>"
echo "Example: docker run --network=host jlabtsai/rtdp-farm-nic-test 192.168.1.100" 