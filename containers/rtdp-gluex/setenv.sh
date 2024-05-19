

source /opt/build_scripts/gluex_env_boot_jlab.sh --bs /opt/build_scripts
gxenv /opt/version.xml

cd /opt/root
source bin/thisroot.sh

export NTHREADS=12
export JAVA_HOME=/usr/lib/jvm/default-java
export ETINSTALL=${GLUEX_TOP}/et/et-16.3
export ETROOT=${ETINSTALL}/Linux-x86_64
export CODA=$GLUEX_TOP/coda
export CMSGROOT=$CODA/Linux-x86_64
export ROOTSPY=$GLUEX_TOP/rootspy/latest/${BMS_OSNAME}
export HALLD_RECON_HOME=$GLUEX_TOP/halld_recon/latest

export PATH=${PATH}:${GLUEX_TOP}/halld_recon/latest/${BMS_OSNAME}/bin
export PATH=${PATH}:${GLUEX_TOP}/hddm/hddm/bin:${GLUEX_TOP}/hdds/hdds-*/Linux_Ubuntu20.04-x86_64-gcc9.4.0/bin
export PATH=${PATH}:${GLUEX_TOP}/monitoring/${BMS_OSNAME}/bin
export PATH=${PATH}:${GLUEX_TOP}/rootspy/latest/${BMS_OSNAME}/bin
export PATH=${PATH}:${GLUEX_TOP}/etUtils/${BMS_OSNAME}/bin
export PATH=${PATH}:${GLUEX_TOP}/et/et-16.3/Linux-x86_64/bin
export LD_LIBRARY_PATH=${LD_LIBRARY_PATH}:$GLUEX_TOP/et/et-16.3/Linux-x86_64/lib
export LD_LIBRARY_PATH=${LD_LIBRARY_PATH}:$GLUEX_TOP/coda/Linux-x86_64/lib/
export LD_LIBRARY_PATH=${LD_LIBRARY_PATH}:/usr/local/lib

source $GLUEX_TOP/jana/latest/${BMS_OSNAME}/setenv.sh 

export ROOTSPY_UDL="cMsg://127.0.0.1/cMsg/rootspy"

# For ET
export SESSION=mon

unset CODA
