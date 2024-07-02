#!/bin/bash

# Directory with evio file and evio file
# (make symlink in local diractory and assume it is bound to /work)
export EVIO_FILE_FULLPATH=/Users/davidl/work/2024.02.23.DataReduction/hd_rawdata_121090_050_100MB.evio
export EVIO_FILE_DIR=`dirname $EVIO_FILE_FULLPATH`
export EVIO_FILE=`basename $EVIO_FILE_FULLPATH`

# Get IP address of docker container running et_start
# export ET_HOST=`docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' et_server`
export ET_HOST=localhost

# Command to run the cMsg server inside the container
export GLUEX_TOP=/group/halld/Software/builds/Linux_Ubuntu20.04-x86_64-gcc9.4.0
export CMD="file2et -H ${ET_HOST} -p 11111 -loop -f /work/et_sys_mon /data/${EVIO_FILE}"

# Run in foreground
exec docker run -v ${PWD}:/work -v ${EVIO_FILE_DIR}:/data -it --rm --net host --name file2et rtdp-gluex:latest ${CMD}


