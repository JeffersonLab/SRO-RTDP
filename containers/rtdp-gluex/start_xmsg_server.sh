#!/bin/bash

# Command to run the xMsg server inside the container
export GLUEX_TOP=/group/halld/Software/builds/Linux_Ubuntu20.04-x86_64-gcc9.4.0
export CMD="$GLUEX_TOP/xmsg/xmsg-cppv2.3.build/bin/cx_proxy"

# Run server in background
exec docker run -it --rm --net host --name xmsg_server rtdp-gluex:latest $CMD