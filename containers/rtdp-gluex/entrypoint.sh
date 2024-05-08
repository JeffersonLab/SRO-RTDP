#!/bin/bash

#
# This entrypoint is used to source the /opt/setenv.sh script when the
# container first starts while filtering any messages it may print.
#
# The primary motivation for this is to remove two unwanted warnings
# that gxenv /opt/version.xml  produces:
#
#   info: no version and no url for prereq xerces-c of hdds
#   info: no version and no url for prereq root of hdds
#

source /opt/setenv.sh 2>&1 > /dev/null

exec "$@"
