#!/bin/bash

export SIMFILE=${PWD}/simout.100.edm4hep.root

# Convert the podio file into a file of pre-serialized buffers of 10 events each
./podio2tcp.build/podio2tcp -o ${SIMFILE}.podiostr -g 10  $SIMFILE

# Read from the newly created file
./podio2tcp.build/podio2tcp --loop ${SIMFILE}.podiostr
