#!/bin/bash

# Default values
TARGET_IP="129.57.70.25"
TARGET_PORT="18080"
CONTAINER_PATH="cpu_emu.sif"

# Parse command line arguments
while getopts "i:p:c:h" opt; do
    case $opt in
        i) TARGET_IP="$OPTARG" ;;
        p) TARGET_PORT="$OPTARG" ;;
        c) CONTAINER_PATH="$OPTARG" ;;
        h) echo "Usage: $0 [-i target_ip] [-p port] [-c container_path]"
           echo "  -i: Target IP to test (default: 129.57.70.25)"
           echo "  -p: Target port to test (default: 18080)"
           echo "  -c: Path to Apptainer container (default: cpu_emu.sif)"
           exit 0
           ;;
        ?) echo "Invalid option. Use -h for help."
           exit 1
           ;;
    esac
done

echo "Testing network connectivity to ${TARGET_IP}:${TARGET_PORT}..."

echo "1. Host system tests:"
echo "1.1. Ping test from host:"
ping -c 3 ${TARGET_IP}

echo -e "\n1.2. Network route test:"
echo "Route to target:"
/sbin/ip route get ${TARGET_IP}

echo -e "\n1.3. Host network interfaces:"
/sbin/ip addr show

echo -e "\n2. Container network tests:"
echo "2.1. Ping test from container:"
# Try different methods to ping from container
echo "Method 1 - Using busybox ping:"
apptainer exec ${CONTAINER_PATH} /bin/sh -c "busybox ping -c 3 ${TARGET_IP} 2>/dev/null || echo 'busybox ping not available'"

echo -e "\nMethod 2 - Using raw socket ping:"
apptainer exec ${CONTAINER_PATH} /bin/sh -c "echo '1' > /proc/sys/net/ipv4/ping_group_range 2>/dev/null && ping -c 3 ${TARGET_IP} || echo 'Cannot configure ping permissions'"

echo -e "\nMethod 3 - Using TCP connection to port 7 (echo):"
apptainer exec ${CONTAINER_PATH} /bin/sh -c "nc -w 1 -v ${TARGET_IP} 7 2>&1 || echo 'Echo port test failed'"

echo -e "\n2.2. Container network namespace info:"
apptainer exec ${CONTAINER_PATH} /bin/sh -c "echo 'Process network namespace:'; ls -l /proc/self/ns/net"

echo -e "\n2.3. Container network interfaces:"
apptainer exec ${CONTAINER_PATH} /bin/sh -c "echo 'Network interfaces:'; cat /proc/net/dev"

echo -e "\n2.4. Container routing table:"
apptainer exec ${CONTAINER_PATH} /bin/sh -c "echo 'Routing table:'; cat /proc/net/route"

echo -e "\n2.5. Container DNS and hostname info:"
apptainer exec ${CONTAINER_PATH} /bin/sh -c "echo 'Container hostname:'; hostname; echo -e '\nDNS Config:'; cat /etc/resolv.conf; echo -e '\nHosts file:'; cat /etc/hosts"

echo -e "\n2.6. Testing TCP connection to ports:"
for port in 18080 18888; do
    echo -e "\nTesting port $port using timeout and netcat:"
    apptainer exec ${CONTAINER_PATH} timeout 5 nc -v ${TARGET_IP} $port 2>&1 || echo "Connection to port $port timed out or failed"
done

echo -e "\n3. Additional network tests:"
echo "3.1. Testing if target ports are in use on host:"
for port in 18080 18888; do
    if netstat -tuln 2>/dev/null | grep -q ":$port "; then
        echo "Port $port is in use on host"
    else
        echo "Port $port is not in use on host"
    fi
done

echo -e "\n3.2. Container network bind test:"
echo "Attempting to bind to receive port (18888) in container:"
apptainer exec ${CONTAINER_PATH} timeout 5 nc -l 18888 2>&1 || echo "Bind test completed"