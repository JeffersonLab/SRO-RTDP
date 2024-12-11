#!/bin/bash
#SBATCH --ntasks=1                     # Number of tasks (adjust as necessary)
#SBATCH --time=01:00:00                # Time limit (adjust as necessary)
#SBATCH --mem=4G                       # Memory (adjust as necessary)

# For debug usage
set -ex

## Assume current path is at SRO-RTDP root
## podio2tcp built file is at podio2tcp.build

SCRIPT_NAME=$1
ZMQ_PORT_NUM=$2
INFLUXDB_PORT=$3
if [[ -n "$4" ]]; then
    RECV_NODENAME="$4"
fi

echo "PWD: ${PWD}"

# Run the container
# SENDER side
if [ -n "$4" ]; then
    echo -e "Run the sender side...\n"
    RECV_NODENAME="$4"
    apptainer exec \
        -B /cache,/volatile,/scratch,/work,/w \
        /cvmfs/singularity.opensciencegrid.org/eicweb/jug_xl:nightly \
        bash ${SCRIPT_NAME} ${ZMQ_PORT_NUM} ${INFLUXDB_PORT} ${RECV_NODENAME}
# RECEIVER side
else    
    echo -e "Run the receiver side...\n"
    apptainer exec \
        -B /cache,/volatile,/scratch,/work,/w \
        /cvmfs/singularity.opensciencegrid.org/eicweb/jug_xl:nightly \
        bash ${SCRIPT_NAME} ${ZMQ_PORT_NUM} ${INFLUXDB_PORT}
fi
