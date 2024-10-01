
# PODIO Streaming with EICrecon

These instructions were made using ifarm9.jlab.org

### eic-shell environment
The EICrecon code should be run from inside the eic-shell contianer since it includes all of the dependencies already compiled. All of the build instructions below should be run from inside an eic-shell container. Start it up like this:

~~~bash
apptainer shell \
    -B /cache,/volatile,/scratch,/work,/w \
    /cvmfs/singularity.opensciencegrid.org/eicweb/jug_xl:nightly
~~~

Setup the full environment inside the container with:
~~~bash
source /opt/detector/epic-main/bin/thisepic.sh
~~~

This should put root, jana, eicrecon, etc. in your path.


### PODIO
The podio package was modified to allow this to work. The changes were made on a fork of the podio repository and a pull request made to merge them back into the official repository. (See [PR#579](https://github.com/AIDASoft/podio/pull/579)).
Once the PR is accepted, the official repository should be used. Until then, the customized code in the `davidl_RootReader_TDirectory` branch can be used. Get it with this:

~~~bash
git clone git@github.com:faustus123/podio -b davidl_RootReader_TDirectory podio.src
export podio_ROOT=${PWD}/podio
cmake -S podio.src -B podio.build -DCMAKE_INSTALL_PREFIX=${podio_ROOT} -DCMAKE_CXX_STANDARD=20
cmake --build podio.build --target install -j16
export LD_LIBRARY_PATH=${podio_ROOT}/lib:${LD_LIBRARY_PATH}
~~~


### podio2tcp

The `podiotcp`and `tcp2podio` programs live in the SRO-RTDP repository for now. They are in a subdirectory with its own stand-alone CMakeLists.txt file. Get it and compile it with the commands below. 

NOTE: despite the name, these programs do not actually use the podio package at all and rely only on ROOT.

~~~bash
git clone git@github.com:JeffersonLab/SRO-RTDP
cmake -S SRO-RTDP/src/utilities/cpp/podio2tcp -B podio2tcp.build -DCMAKE_CXX_STANDARD=20
cmake --build podio2tcp.build
~~~

### Input podio file

A podio file of simulated events already exists here that can be used. Instructions for producing it are given below in case it needs to be recreated. 

~~~
/work/eic/users/davidl/2024.02.22.RTDP_stream/eic/simout.100.edm4hep.root
~~~

The above file was produced with commands similar to this:

~~~bash
export infile=/work/eic2/EPIC/EVGEN/SIDIS/pythia6-eic/1.0.0/10x100/q2_0to1/pythia_ep_noradcor_10x100_q2_0.000000001_1.0_run48.ab.hepmc3.tree.root

ddsim --compactFile $DETECTOR_PATH/$DETECTOR_CONFIG.xml \
    --numberOfEvents 100 \
    --inputFiles $infile \
    --outputFile simout.100.edm4hep.root
~~~

### Testing

A send/receive test can be done using the programs in the build.podio2tcp. In one terminal run the following. Note that the "--loop" option will continuously reopen the input file and send events forever until the program is killed.

~~~bash
# Sender
export SIMFILE=/work/eic/users/davidl/2024.02.22.RTDP_stream/eic/simout.100.edm4hep.root
./podio2tcp.build/podio2tcp --loop $SIMFILE
~~~

Start the example receiver program like this:
~~~bash
# Receiver
./podio2tcp.build/tcp2podio
~~~

The rate at which events may be prepared and sent is limited. On ifarm2402 only about 130Hz (580Mbps) could be sustained. This is almost entirely due to the ROOT method used to copy a subset of events from a TTree into another. This can be sped up considerably by saving the serialized buffers to a separate file and reading from that instead. A mechanism for doing this exists in podio2tcp for doing this.

~~~bash
# Convert the podio file into a file of pre-serialized buffers of 10 events each
./podio2tcp.build/podio2tcp -o ${SIMFILE}.podiostr -g 10  $SIMFILE

# Read from the newly created file
./podio2tcp.build/podio2tcp --loop ${SIMFILE}.podiostr
~~~

The above will transfer events at rates of a few kHz

### JANA2
JANA2 must be rebuilt against the custom version of podio.

~~~bash
git clone git@github.com:JeffersonLab/JANA2 JANA2.src
export JANA_ROOT=${PWD}/JANA2
cmake -S JANA2.src -B JANA2.build \
    -DUSE_PODIO=1 \
    -DUSE_ROOT=1 \
    -DUSE_ZEROMQ=1 \
    -DCMAKE_INSTALL_PREFIX=${JANA_ROOT} \
    -DCMAKE_CXX_STANDARD=20
cmake --build JANA2.build --target install -j32
export LD_LIBRARY_PATH=${JANA_ROOT}/lib:${LD_LIBRARY_PATH}
~~~


### EICrecon

~~~bash
git clone git@github.com:eic/EICrecon EICrecon.src
cmake -S EICrecon.src -B EICrecon.build \
    -DCMAKE_INSTALL_PREFIX=${PWD}/EICrecon \
    -DCMAKE_CXX_STANDARD=20
cmake --build EICrecon.build --target install -j32
source ./EICrecon/bin/eicrecon-this.sh
~~~

### JANA2 plugin podiostream

Use the following to build the podiostream plugin that implements a JANA2 event source that can read from the tcp stream. Also set the JANA_PLUGIN_PATH envar so eicrecon can find it.

~~~bash
cmake -S SRO-RTDP/src/utilities/cpp/podiostream -B podiostream.build \
    -DCMAKE_POLICY_DEFAULT_CMP0074=NEW \
    -DCMAKE_CXX_STANDARD=20

cmake --build podiostream.build

export JANA_PLUGIN_PATH=${PWD}/podiostream.build:${JANA_PLUGIN_PATH}
~~~

To use the plugin, specify it when running eicrecon as well as the special filename "podiostreamSource" (pay attention to the capitalization).

~~~bash
eicrecon -Pplugins=podiostream podiostreamSource
~~~

## Summary

Here is a summary of the environment setup from the above. This is useful if you have already built everything and want to use it in a new terminal.

NOTE: The order of the following is important since sourcing the `eicrecon-this.sh` script puts /usr/local/lib near the front of the LD_LIBRARY_PATH which then superceeds the use of our podio and JANA libraries.

~~~bash
apptainer shell \
    -B /cache,/volatile,/scratch,/work,/w \
    /cvmfs/singularity.opensciencegrid.org/eicweb/jug_xl:nightly
source /opt/detector/setup.sh
source ./EICrecon/bin/eicrecon-this.sh
source ${JANA_ROOT}/bin/jana-this.sh
export podio_ROOT=${PWD}/podio
export LD_LIBRARY_PATH=${podio_ROOT}/lib:${LD_LIBRARY_PATH}
export SIMFILE=/work/eic/users/davidl/2024.02.22.RTDP_stream/eic/simout.100.edm4hep.root
export JANA_ROOT=${PWD}/JANA2
export LD_LIBRARY_PATH=${JANA_ROOT}/lib:${LD_LIBRARY_PATH}
export JANA_PLUGIN_PATH=${PWD}/podiostream.build:${JANA_PLUGIN_PATH}
~~~

Start the sender in one terminal:
~~~bash
./podio2tcp.build/podio2tcp --loop ${SIMFILE}.podiostr
~~~

Run one or more instance of eicrecon using:
~~~bash
eicrecon -Pplugins=podiostream podiostreamSource
~~~
