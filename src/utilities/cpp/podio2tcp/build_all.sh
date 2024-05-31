#!/bin/bash

#
# This is copied to the remote nodes and run from inside the eic-shell container.
#

# The local host default directory is bound to the /work directory inside the
# container. Do all work there so it persists across multiple docker invocations.
cd /work

source /opt/detector/setup.sh

# PODIO
git clone https://github.com/faustus123/podio -b davidl_RootReader_TDirectory podio.src
export podio_ROOT=${PWD}/podio
cmake -S podio.src -B podio.build -DCMAKE_INSTALL_PREFIX=${podio_ROOT} -DCMAKE_CXX_STANDARD=20
cmake --build podio.build --target install -j4
export LD_LIBRARY_PATH=${podio_ROOT}/lib:${LD_LIBRARY_PATH}

# podio2tcp
git clone https://github.com/JeffersonLab/SRO-RTDP
cmake -S SRO-RTDP/src/utilities/cpp/podio2tcp -B podio2tcp.build -DCMAKE_CXX_STANDARD=20
cmake --build podio2tcp.build

# JANA2
git clone https://github.com/JeffersonLab/JANA2 JANA2.src
export JANA_ROOT=${PWD}/JANA2
cmake -S JANA2.src -B JANA2.build \
    -DUSE_PODIO=1 \
    -DUSE_ROOT=1 \
    -DUSE_ZEROMQ=1 \
    -DCMAKE_INSTALL_PREFIX=${JANA_ROOT} \
    -DCMAKE_CXX_STANDARD=20
cmake --build JANA2.build --target install -j4
export LD_LIBRARY_PATH=${JANA_ROOT}/lib:${LD_LIBRARY_PATH}

# EICrecon
git clone https://github.com/eic/EICrecon EICrecon.src
cmake -S EICrecon.src -B EICrecon.build \
    -DCMAKE_INSTALL_PREFIX=${PWD}/EICrecon \
    -DCMAKE_CXX_STANDARD=20
cmake --build EICrecon.build --target install -j4
source ./EICrecon/bin/eicrecon-this.sh

# podiostream
cmake -S SRO-RTDP/src/utilities/cpp/podiostream -B podiostream.build \
    -DCMAKE_POLICY_DEFAULT_CMP0074=NEW \
    -DCMAKE_CXX_STANDARD=20
cmake --build podiostream.build
export JANA_PLUGIN_PATH=${PWD}/podiostream.build:${JANA_PLUGIN_PATH}