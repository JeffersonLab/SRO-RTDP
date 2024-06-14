# RTDP Container Image for GlueX Monitoring

This container will build the necessary software components to implement the online monitoring system used in Hall-D. This provides an ET + cMsg + RootSpy enabled software suite that can be used to read events from a remote ET system and generate PNG files suitable for Hydra consumption.

To build the image with Docker:

~~~bash
docker build -t rtdp-gluex -f Dockerfile .
~~~

RootSpy can communicate via cMsg or xMsg. xMsg is newer and preferred. 

n.b. At this time neither cMsg or xMsg seems to be fully working. Below are some preliminary instructions for starting up the cMsg server and trying to use that.

To run the cMsg server (n.b. this runs it in the background):

~~~bash
docker run -it --rm --net host rtdp-gluex  bash
source /opt/setenv.sh 
java -cp java/jars/java8/cMsg-5.2.jar -server -Dtimeorder -Ddebug=info org/jlab/coda/cMsg/cMsgDomain/server/cMsgNameServer >& /dev/null &
~~~

To run the hdmon program in the same container:

~~~bash
export ROOTSPY_UDL="cMsg://127.0.0.1/cMsg/rootspy"
hdmon ET:/tmp/et_mon
~~~
