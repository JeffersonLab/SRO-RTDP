#!/bin/bash

# set -xe  # for debug

#====================== Inside the container
ZMQ_PORT_NUM=$1
INFLUXDB_PORT=$2

# Activate env
. /opt/detector/epic-main/bin/thisepic.sh

# Create the SQLite DB file
SCRIPTS_PATH_PREFIX=${PWD}/farm-tests/slurm-podio2tcp-influxdb-demo
## Usage: bash <createdb>.sh -s|c <dbname_prefix>
output=$(bash ${SCRIPTS_PATH_PREFIX}/sqlite3db_create.bash -s test)

if [ $? -ne 0 ]; then
  echo "Database creation failed."
  exit 1
fi

dbname=$(echo $output | sed -n "s/.*'\([^']*\)'.*/\1/p")
echo "SQLite DB name is: ${dbname}"

# Setup the scraper at the background
bash ${SCRIPTS_PATH_PREFIX}/sqlite3db_receiver_scraper.bash $dbname ${INFLUXDB_PORT} &

# Launch the tcp2podio app with SQLite DB name
# "-i $(hostname)" is a must-have for accepting remote connection
./podio2tcp.build/tcp2podio -p $ZMQ_PORT_NUM -s ${dbname} -i "$(hostname)"
