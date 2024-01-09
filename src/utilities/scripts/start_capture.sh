#!/bin/bash

start_port=7001  # Replace with your start port
end_port=7018    # Replace with your end port
export CAPTURE_DIR="/nvme/proj/RTDP/2023.12.17.CLAS12/files"

# Define routine to kill any netcat instances. This will
# be run at the begining and end of this script as well
# as when a SIGINT (Ctrl-C) is received.
killall_nc() {
	echo "Killing all netcat listeners ..."
	killall nc
	set +x
}

# Trap SIGINT so we can stop all netcat programs
trap killall_nc, SIGINT

killall_nc

# Start up a netcat (nc) instance for each port we are capturing.
for ((port=$start_port; port<=$end_port; port++))
do
   nc -l -p $port > /dev/null &
done
echo "Netcat is listening on ports from $start_port to $end_port"


# Define the capture file name.
datetime=$(date +"%Y-%m-%d_%H-%M-%S")
export CAPTURE_FILE="${CAPTURE_DIR}/CLAS12_ECAL_PCAL_S2_${datetime}.pcap"

# Start tcpdump to capture all streams into a single file.
# This will block until the user stops it with Ctl-C
echo "Capturing TCP packets from ports: ${start_port}-${end_port}"
echo "Capturing into file: ${CAPTURE_FILE}"
set -x
sudo tcpdump -i enp193s0f1np1 -j adapter_unsynced --time-stamp-precision=nano \
  -s 0 -w ${CAPTURE_FILE}  portrange ${start_port}-${end_port} -B 10480000
set +x

echo "Capture stopped."
killall_nc

exit 0

