#!/usr/bin/bash

source /opt/setenv.sh

# Start xmsg name server
# /group/halld/Software/builds/Linux_Ubuntu20.04-x86_64-gcc9.4.0/xmsg/xmsg-cppv2.3.build/bin/cx_proxy &

# Start hdmon
sleep 2
hdmon -PPLUGINS=occupancy_online -PRUNNUMBER=121090 ET:/work/tmp/et_sys_mon:MON:127.0.0.1:11111 

# Start RSAI
# sleep 6
# mkdir -p /work/rootspy_out /work/rootspy_links 
# RSAI -v -v -v -d /work/rootspy_out -l /work/rootspy_links -R 121090 -u xMsg://127.0.0.1  2>&1 | grep -v "Context was terminated" | grep -v '^[[:space:]]*$'
