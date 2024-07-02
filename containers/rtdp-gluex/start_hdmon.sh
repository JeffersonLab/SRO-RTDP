#!/bin/bash

# Command to run the cMsg server inside the container
export GLUEX_TOP=/group/halld/Software/builds/Linux_Ubuntu20.04-x86_64-gcc9.4.0
# export CMD="hdmon -PPLUGINS=occupancy_online ET:/work/et_sys_mon:MON:localhost:11111"
# export CMD="hdmon -PPLUGINS=occupancy_online ET:/work/et_sys_mon:MON:192.168.65.3:11111"
# export CMD="hdmon -PPLUGINS=occupancy_online ET:/work/et_sys_mon:MON:127.0.0.1:11111"
export CMD="hdmon -PPLUGINS=occupancy_online ET:/work/et_sys_mon:MON:192.168.65.6:11111"
# export CMD="hdmon -PPLUGINS=occupancy_online ET:/work/et_sys_mon:MON:239.200.0.0:11111"


# Run in foreground
# exec docker run -v ${PWD}:/work -it --rm --net host --name hdmon rtdp-gluex:latest ${CMD}
# exec docker run -it --rm --net host --name hdmon rtdp-gluex:latest ${CMD}
exec docker exec -it xmsg_server ${CMD}



