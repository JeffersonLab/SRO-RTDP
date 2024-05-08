#!/bin/bash

# Command to run the cMsg server inside the container
export GLUEX_TOP=/group/halld/Software/builds/Linux_Ubuntu20.04-x86_64-gcc9.4.0
export CMD="java -cp ${GLUEX_TOP}/cmsg/cMsg-5.2.src/java/jars/java8/cMsg-5.2.jar -server -Dtimeorder -Ddebug=info org/jlab/coda/cMsg/cMsgDomain/server/cMsgNameServer"

# Run server in background
docker run -d --rm --net host --name cmsg_server rtdp-gluex:latest $CMD