#!/bin/bash
#SBATCH --job-name=iperf-server           # Job name
#SBATCH --output=slurm_server_%j.log
#SBATCH --error=slurm_server_%j.log
#SBATCH --ntasks=1           # Number of tasks (usually 1 for container jobs)
#SBATCH --cpus-per-task=4                         # Number of CPU cores per task
#SBATCH --mem=8G                                  # Memory per node
#SBATCH --time=02:00:00                           # Time limit hrs:min:sec
#SBATCH --partition=ifarm                        # Partition name (adjust as needed)

set -x

# --------------------------- #
#      Define Variables       #
# --------------------------- #
PROCESS_EXPORTER_SIF="process-exporter.sif"
PROCESS_EXPORTER_PORT=$1

APP_SIF="iperf3.sif"
APP_PORT="32901"    # TCP port only

## UPDATE THIS LOCATION
WORKDIR_PREFIX="/w/epsci-sciwork18/xmei/projects/SRO-RTDP/farm-tests"

PROCESS_EXPORTER_SIF=${WORKDIR_PREFIX}/sifs/${PROCESS_EXPORTER_SIF}
IPERF3_CONTAINER_SIF=${WORKDIR_PREFIX}/sifs/${APP_PORT}

node_name=$(hostname)
node_ip=$(hostname -i)
echo "Hostname: $node_name"
echo -e "IPv4 address: $node_ip\n"

# WORKDIR=${WORKDIR_PREFIX}/job_${SLURM_JOB_ID}
# mkdir -p $WORKDIR
cd $WORKDIR_PREFIX

# --------------------------- #
#   Run iperf server #
# --------------------------- #

# echo "Starting iperf3 server container..."

# Replace the following command with the appropriate command for your second container
# For example, running a simple web server or any other application
# singularity exec $IPERF3_CONTAINER_SIF iperf3 --server -p ${APP_PORT} &

# A bare-metal instance
/w/epsci-sciwork18/xmei/projects/iperf3/bin/iperf3 --server -p ${APP_PORT} &

IPERF3_PID=$!
echo -e "iperf3 process started with PID $IPERF3_PID \n"

# --------------------------- #
#    Run Process Exporter        #
# --------------------------- #

# The docker command to run process-exporter:
#  docker run -d --rm -p 9256:9256 --privileged\
#       -v /proc:/host/proc -v `pwd`:/config ncabatoff/process-exporter\
#       --procfs /host/proc -config.path /config/filename.yml

# Start process-exporter with the updated configuration
echo "Starting Process Exporter container..."

# Must use `apptainer exec` other than `apptainer run`
apptainer exec \
  --bind /proc:/host_proc \
  --bind ${WORKDIR_PREFIX}/config:/config \
  ${PROCESS_EXPORTER_SIF} \
  process-exporter \
    -procfs /host_proc \
    -config.path /config/process-exporter-config.yml \
    -web.listen-address=:${PROCESS_EXPORTER_PORT} &
PROCESS_EXPORTER_PID=$!
echo -e "Process-Exporter started with PID $PROCESS_EXPORTER_PID on port ${PROCESS_EXPORTER_PORT}.\n"

# -----------------------------#
#     Keep processes running   #
# -----------------------------#
ps
wait $IPERF3_PID $PROCESS_EXPORTER_PID

