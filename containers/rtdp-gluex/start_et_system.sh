#!/bin/bash

# Command to run the cMsg server inside the container
export GLUEX_TOP=/group/halld/Software/builds/Linux_Ubuntu20.04-x86_64-gcc9.4.0
export CMD="et_start -f /work/et_sys_mon"

# Run server in background
docker run -v ${PWD}:/work -d --rm --net host --name et_server rtdp-gluex:latest ${CMD}



