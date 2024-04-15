#!/bin/bash

cd /work
source setenv.sh

# Create unique job dir for this
job_dir="/work/job_$1"
mkdir -p "$job_dir"
cd "$job_dir"

eicrecon -Pplugins=podiostream -Ppodiostream:host=${PODIOHOST} -Ppodio:output_file=podio_output${PID}.root podiostreamSource

