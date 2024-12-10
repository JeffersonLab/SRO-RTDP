#!/bin/bash

# For debug
set -x # -e: exit on first error; -x: echo the command

INFLUXDB_IFARM_PORT=42900

cd ${HOME}/projects/SRO-RTDP   # <==== Update ROOT dir

#################### Sbatch scripts ##################
## <==== Update the paths here!!!
SCRIPTS_PATH=farm-tests/slurm-podio2tcp-influxdb-demo
APPTAINER_INFLUXDB_WRAPPER=apptainer_influxdb_wrapper.bash

# Init the InfluxDB locally
bash ${SCRIPTS_PATH}/${APPTAINER_INFLUXDB_WRAPPER} ${INFLUXDB_IFARM_PORT} &

bash ${SCRIPTS_PATH}/submit2jobs.bash ${INFLUXDB_IFARM_PORT}
