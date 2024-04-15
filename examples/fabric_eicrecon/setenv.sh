
source /opt/detector/setup.sh
source ./EICrecon/bin/eicrecon-this.sh
source ${JANA_ROOT}/bin/jana-this.sh
export podio_ROOT=${PWD}/podio
export LD_LIBRARY_PATH=${podio_ROOT}/lib:${LD_LIBRARY_PATH}
export SIMFILE=/work/simout.1000.edmhep.root
export JANA_ROOT=${PWD}/JANA2
export LD_LIBRARY_PATH=${JANA_ROOT}/lib:${LD_LIBRARY_PATH}
export JANA_PLUGIN_PATH=${PWD}/podiostream.build:${JANA_PLUGIN_PATH}
