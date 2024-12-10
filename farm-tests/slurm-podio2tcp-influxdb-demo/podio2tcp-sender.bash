#!/bin/bash

# set -xe  # for debug

#====================== Inside the container
ZMQ_PORT_NUM=$1
RECV_NODENAME=$2

# Activate env
. /opt/detector/epic-main/bin/thisepic.sh

# Create the SQLite DB file
SCRIPTS_PATH_PREFIX=${PWD}/farm-tests/slurm-podio2tcp-influxdb-demo
## Usage: bash <createdb>.sh -s|c <dbname_prefix>
output=$(bash ${SCRIPTS_PATH_PREFIX}/sqlite3db_create.bash -c test)

if [ $? -ne 0 ]; then
  echo "Database creation failed."
  exit 1
fi

echo $output
dbname=$(echo $output | sed -n "s/.*'\([^']*\)'.*/\1/p")
echo "SQLite DB name is: ${dbname}"

# Launch the tcp2podio app with SQLite DB name
# Send to remote node
ROOT_FILE=${PWD}/containers/podio-eicrecon/simout.100.edm4hep.root
./podio2tcp.build/podio2tcp -p $ZMQ_PORT_NUM -l -s ${dbname} -i "${RECV_NODENAME}.jlab.org" ${ROOT_FILE} &

# TODO: Setup the scraper
