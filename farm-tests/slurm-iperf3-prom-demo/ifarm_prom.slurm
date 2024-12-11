#!/bin/bash
#SBATCH --job-name=prometheus           # Job name
#SBATCH --output=slurm_prom_%j.log
#SBATCH --error=slurm_prom_%j.log
#SBATCH --ntasks=1           # Number of tasks (usually 1 for container jobs)
#SBATCH --cpus-per-task=4                         # Number of CPU cores per task
#SBATCH --mem=8G                                  # Memory per node
#SBATCH --time=02:00:00                           # Time limit hrs:min:sec
#SBATCH --partition=ifarm                        # Partition name (adjust as needed)

# For debug usage
set -x

## UPDATE THIS LOCATION
WORKDIR_PREFIX="/w/epsci-sciwork18/xmei/projects/SRO-RTDP/farm-tests"
PROMETHEUS_SIF=$WORKDIR_PREFIX/sifs/prom.sif
PROMETHEUS_PORT=$1

apptainer exec \
  --bind ${WORKDIR_PREFIX}/config:/config \
  --bind ${WORKDIR_PREFIX}/prom-data:/prometheus \
  ${PROMETHEUS_SIF} \
  prometheus \
    --web.listen-address=":${PROMETHEUS_PORT}" \
    --config.file=/config/prometheus-config.yml \
    --storage.tsdb.path=/prometheus \
    > prom.log 2>&1 &
