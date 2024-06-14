#!/bin/bash

# Command to run the cMsg server inside the container
export GLUEX_TOP=/group/halld/Software/builds/Linux_Ubuntu20.04-x86_64-gcc9.4.0
export CMD="RSAI -v -v -v -d /work/rootspy_out -l /work/rootspy_links -R 121090"

echo "Making directory: ${PWD}/rootspy_out"
mkdir -p rootspy_out
echo "Making directory: ${PWD}/rootspy_links"
mkdir -p rootspy_links


# export ROOTSPY_UDL="cMsg://192.168.65.6/cMsg/rootspy"
export ROOTSPY_UDL="xMsg://192.168.65.6/xMsg/rootspy"


# Run server in background
docker run -v ${PWD}:/work -it --rm --net host --name RSAI rtdp-gluex:latest bash -c ROOTSPY_UDL="xMsg://192.168.65.6/xMsg/rootspy" ${CMD}



