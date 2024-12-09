#!/bin/bash

##SBATCH --job-name=influxdb          # Job name
##SBATCH --output=slurm_influx_%j_%N.log
##SBATCH --error=slurm_influx_%j_%N.log
##SBATCH --ntasks=1           # Number of tasks (usually 1 for container jobs)
##SBATCH --mem=16G                                  # Memory per node
##SBATCH --time=02:00:00                           # Time limit hrs:min:sec
##SBATCH --partition=ifarm                        # Partition name (adjust as needed)

# InfluxDB v2 official doc: https://docs.influxdata.com/influxdb/v2/

# For debug usage
set -x

## UPDATE THIS LOCATION
WORKDIR_PREFIX="/w/epsci-sciwork18/xmei/projects/SRO-RTDP/farm-tests"
INFLUXDB_SIF=$WORKDIR_PREFIX/sifs/influxdb.sif
INFLUXDB_PORT=$1
INFLUXDB_INIT_SCRIPT=$WORKDIR_PREFIX/slurm-podio2tcp-influxdb-demo/influxdb_init.bash

echo "Searching for existing 'influxd' processes..."
pids=$(pgrep -u `whoami` influxd)
if [[ -n "$pids" ]]; then
  echo "Killing 'influxd' processes with PIDs: $pids"
  kill -9 $pids
  echo "'influxd' processes have been terminated."
fi

# Start InfluxDB container
apptainer exec \
  -B /cache,/volatile,/scratch,/work,/w \
  --bind ${WORKDIR_PREFIX}/influxdb-data:/var/lib/influxdb2 \
  --bind ${WORKDIR_PREFIX}/config/influxdb-config.yml:/var/config \
  ${INFLUXDB_SIF} \
  bash ${INFLUXDB_INIT_SCRIPT}

