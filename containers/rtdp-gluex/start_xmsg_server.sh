#!/bin/bash

# Command to run the xMsg server inside the container
export GLUEX_TOP=/group/halld/Software/builds/Linux_Ubuntu20.04-x86_64-gcc9.4.0
# export JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk-amd64
# export CMD1="$JAVA_HOME/bin/java -cp $GLUEX_TOP/xmsg/xmsg-javav2.3/jars/\''*'\' org.jlab.coda.xmsg.sys.xMsgRegistrar"
# export CMD2="$JAVA_HOME/bin/java -cp $GLUEX_TOP/xmsg/xmsg-javav2.3/jars/'*' org.jlab.coda.xmsg.sys.xMsgProxy"

export CMD="$GLUEX_TOP/xmsg/xmsg-cppv2.3.build/bin/cx_proxy"

# Run server in background
# docker run -d --rm --net host --name xmsg_server rtdp-gluex:latest $CMD1
# echo $CMD1
docker run -it --rm --net host --name xmsg_server rtdp-gluex:latest $CMD