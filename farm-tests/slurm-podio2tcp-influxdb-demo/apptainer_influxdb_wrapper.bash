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
# set -x

## UPDATE THIS LOCATION
WORKDIR_PREFIX="${HOME}/projects/SRO-RTDP/farm-tests"
INFLUXDB_SIF=$WORKDIR_PREFIX/sifs/influxdb.sif
INFLUXDB_PORT=$1
INFLUXDB_INIT_SCRIPT=$WORKDIR_PREFIX/slurm-podio2tcp-influxdb-demo/influxdb_init.bash

influxdb_fast_access_path=/tmp/influxdb-data   # tmp is much faster than NFS!!!

# Killing existing influxd instances
pids=$(pgrep influxd)
if [[ -n "$pids" ]]; then
  echo "Killing 'influxd' processes with PIDs: $pids"
  kill -9 $pids
  echo -e "'influxd' processes have been terminated.\n\n"
fi

## Deleting all existing influxdb setup
rm -rf ~/.influxdbv2
rm -rf ${WORKDIR_PREFIX}/influxdb-config/* # config files etc
rm -rf $influxdb_fast_access_path

# Create new instance
mkdir -p $influxdb_fast_access_path
chmod 755 $influxdb_fast_access_path

# Start InfluxDB container
# Needs "&" at the end
echo "Starting InfluxDB container..."
apptainer exec \
  -B /cache,/volatile,/scratch,/work,/w \
  --bind ${influxdb_fast_access_path}:/var/lib/influxdb2 \
  --bind ${WORKDIR_PREFIX}/config/influxdb-config.yml:/var/config \
  --bind ${WORKDIR_PREFIX}/influxdb-config:/etc/influxdb2 \
  ${INFLUXDB_SIF} \
  bash ${INFLUXDB_INIT_SCRIPT} ${INFLUXDB_PORT} &

# Inside the container, run
# Apptainer> pwd
# /w/epsci-sciwork18/xmei/projects/SRO-RTDP
# Apptainer> bash farm-tests/slurm-podio2tcp-influxdb-demo/influxdb_init.bash 
