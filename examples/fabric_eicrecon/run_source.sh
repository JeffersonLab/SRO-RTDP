#!/bin/bash

cd /work

source setenv.sh

./podio2tcp.build/podio2tcp --loop ${SIMFILE}.podiostr

