#!/bin/bash

# NOTE: Some things are set up in the Dockerfile like symlinks for
#       cmake to cmake3 and the libxerces-c.so library. See that for
#       details.


# Setup build_scripts/GlueX environment
# n.b. the BMS_OSNAME_OVERRIDE envar is set in the Dockerfile since it
# will automatically be set to include "-cntr" if osrelease.pl is run
# from a container (due to the presence of /.dockerenv) but will not include
# that when building the image since that file does not yet exist.
source /opt/build_scripts/gluex_env_boot_jlab.sh --bs /opt/build_scripts
gxenv /opt/version.xml

# Setup ROOT environment
cd /opt/root
source bin/thisroot.sh

# Ugghh! build_scripts is not complete for builds outside of JLab. The above
# is the only way to set up all of the environment variables, but it sets
# up most everything to point to /group/halld. Update our environment to replace
# this with /opt/builds.
#eval `env | grep "/group/halld/Software" | while IFS='=' read -r name value; do new_value="${value//\/group\/halld\/Software/\/opt}"; echo export "$name=$new_value"; done`


mkdir -p /group/halld


# JANA makefile tries to get from JANA repository instead of JANA2
# sed -i 's|JeffersonLab/JANA/archive|JeffersonLab/JANA2/archive|g' $BUILD_SCRIPTS/Makefile_jana
# sed -i 's|refs/tags/|refs/tags/v|g' $BUILD_SCRIPTS/Makefile_jana
# sed -i 's|TARFILE\ =\ |TARFILE\ =\ v|g' $BUILD_SCRIPTS/Makefile_jana

# Build packages
export NTHREADS=12
mkdir -p $GLUEX_TOP
cd $GLUEX_TOP


mkdir -p ${GLUEX_TOP}/sqlite
mkdir -p ${GLUEX_TOP}/sqlitecpp
mkdir -p ${GLUEX_TOP}/ccdb
mkdir -p ${GLUEX_TOP}/rcdb
mkdir -p ${GLUEX_TOP}/xercesc_3
mkdir -p ${GLUEX_TOP}/evio
mkdir -p ${GLUEX_TOP}/hddm
mkdir -p ${GLUEX_TOP}/hdds

make -C ${GLUEX_TOP}/sqlite    -j 1         -f $BUILD_SCRIPTS/Makefile_sqlite
make -C ${GLUEX_TOP}/sqlitecpp -j 1         -f $BUILD_SCRIPTS/Makefile_sqlitecpp
make -C ${GLUEX_TOP}/ccdb      -j $NTHREADS -f $BUILD_SCRIPTS/Makefile_ccdb
make -C ${GLUEX_TOP}/rcdb      -j $NTHREADS -f $BUILD_SCRIPTS/Makefile_rcdb
make -C ${GLUEX_TOP}/xercesc_3 -j $NTHREADS -f $BUILD_SCRIPTS/Makefile_xercesc_3
make -C ${GLUEX_TOP}/evio      -j $NTHREADS -f $BUILD_SCRIPTS/Makefile_evio
make -C ${GLUEX_TOP}/hddm      -j 1         -f $BUILD_SCRIPTS/Makefile_hddm
make -C ${GLUEX_TOP}/hdds      -j 1         -f $BUILD_SCRIPTS/Makefile_hdds

# A couple of builds below require scons that is python2 compatible.
# These install this.
wget https://bootstrap.pypa.io/pip/2.7/get-pip.py
python2 get-pip.py
pip2 install 'scons==3.1.2'

# No Makefile in build_scripts for ET. Need to do manually.
# n.b. we must use scons to do this since cmake does not build
# the required libraries. This also requires JDK for the et_jni
# library which we don't need, but gets built regardless.
export JAVA_HOME=/usr/lib/jvm/default-java
export ETINSTALL=${GLUEX_TOP}/et/et-16.3
export ETROOT=${ETINSTALL}/Linux-x86_64
export LD_LIBRARY_PATH=${LD_LIBRARY_PATH}:$GLUEX_TOP/et/et-16.3/Linux-x86_64/lib
mkdir -p ${GLUEX_TOP}/et
git clone https://github.com/JeffersonLab/et -b et-16.3 ${ETINSTALL}.src
cd ${ETINSTALL}.src
python2 /usr/local/bin/scons -j $NTHREADS --prefix=${ETINSTALL} install


# Original JANA can only be compiled with python2 compatible scons which
# is not available via apt install. Use this suggestion from ChatGPT:
mkdir -p ${GLUEX_TOP}/jana
cd ${GLUEX_TOP}/jana
git clone https://github.com/JeffersonLab/JANA latest
# svn co https://phys12svn.jlab.org/repos/JANA ${GLUEX_TOP}/jana/v0.8.2 
cd ${GLUEX_TOP}/jana/latest
python2 /usr/local/bin/scons -j $NTHREADS install
source $GLUEX_TOP/jana/latest/${BMS_OSNAME}/setenv.sh 

# cMsg
export CODA=$GLUEX_TOP/coda
export CMSGROOT=$CODA/Linux-x86_64
mkdip -p $CODA
mkdir -p $GLUEX_TOP/cmsg
git clone --depth 1 https://github.com/JeffersonLab/cMsg -b cMsg-5.2 $GLUEX_TOP/cmsg/cMsg-5.2.src
cd $GLUEX_TOP/cmsg
cmake -S cMsg-5.2.src -B cMsg-5.2.build
cmake --build cMsg-5.2.build --target install -j $NTHREADS
ln -s libcMsg.so $GLUEX_TOP/coda/Linux-x86_64/lib/libcmsg.so
ln -s libcMsgxx.so $GLUEX_TOP/coda/Linux-x86_64/lib/libcmsgxx.so
# ln -s /usr/lib/x86_64-linux-gnu/libprotobuf.so $GLUEX_TOP/coda/Linux-x86_64/lib/libcodaObject.so


# xMsg-cpp
mkdir -p $GLUEX_TOP/xmsg
cd $GLUEX_TOP/xmsg
git clone --depth 1 https://github.com/JeffersonLab/xmsg-cpp -b v2.3 $GLUEX_TOP/xmsg/xmsg-cppv2.3.src
cmake -S xmsg-cppv2.3.src -B xmsg-cppv2.3.build -DXMSG_BUILD_TESTS=OFF
cmake --build xmsg-cppv2.3.build --target install -j $NTHREADS
export XMSG_ROOT=/usr/local

# xmsg-java
cd $GLUEX_TOP/xmsg
git clone --depth 1 https://github.com/JeffersonLab/xmsg-java -b v2.3 $GLUEX_TOP/xmsg/xmsg-javav2.3.src
cd xmsg-javav2.3.src
sed -i 's/http/https/g' build.gradle
export JAVA_HOME_SAVE=$JAVA_HOME
export JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk-amd64
./gradlew -PciMode=true build jar
export JAVA_HOME=$JAVA_HOME_SAVE
mkdir -p xmsg-javav2.3/jars
cp -f ./build/libs/xmsg-2.3.jar xmsg-javav2.3/jars

# RootSpy
mkdir -p $GLUEX_TOP/rootspy
svn co https://halldsvn.jlab.org/repos/trunk/online/packages/RootSpy $GLUEX_TOP/rootspy/latest
cd $GLUEX_TOP/rootspy/latest
# Some patches are needed to the RootSpy code (sigh ...)
sed -i '/#include <TMessage.h>/a #include <TObjString.h>' src/libRootSpy-client/rs_cmsg.h
sed -i '/#include <TROOT.h>/a #include <TObjString.h>' src/libRootSpy/DRootSpy.cc
sed -i 's/->connect(bind_to)/->connect(xmsg::ProxyAddress(bind_to))/g' src/libRootSpy-client/rs_xmsg.cc
sed -i 's/->connect(bind_to)/->connect(xmsg::ProxyAddress(bind_to))/g' src/libRootSpy/DRootSpy.cc
sed -i '/#include <TROOT.h>/a #include <TVirtualX.h>' src/RootSpy/rs_mainframe.cc
sed -i '/^env.PrependUnique(LIBS/a env.AppendUnique(LIBS=['\''xmsg'\'', '\''protobuf'\''])' src/RSAI/SConscript
# sed -i '/codaObject/d' src/RSAI/SConscript
# sed -i '/codaObject/d' src/RSTimeSeries/SConscript
unset CODA
# python2 /usr/local/bin/scons -j $NTHREADS install
python2 /usr/local/bin/scons -j $NTHREADS Linux_Ubuntu20.04-x86_64-gcc9.4.0/plugins/rootspy.so
python2 /usr/local/bin/scons -j $NTHREADS Linux_Ubuntu20.04-x86_64-gcc9.4.0/lib/libRootSpy-client.a 
python2 /usr/local/bin/scons -j $NTHREADS Linux_Ubuntu20.04-x86_64-gcc9.4.0/lib/libRootSpy.a 
python2 /usr/local/bin/scons -j $NTHREADS Linux_Ubuntu20.04-x86_64-gcc9.4.0/bin/RSAI
python2 /usr/local/bin/scons -j $NTHREADS Linux_Ubuntu20.04-x86_64-gcc9.4.0/bin/RSMonitor
python2 /usr/local/bin/scons -j $NTHREADS Linux_Ubuntu20.04-x86_64-gcc9.4.0/include
export ROOTSPY=$GLUEX_TOP/rootspy/latest/${BMS_OSNAME}

# Make sure hddm and hdds tools can be found
export PATH=${PATH}:${GLUEX_TOP}/hddm/hddm/bin:${GLUEX_TOP}/hdds/hdds-*/Linux_Ubuntu20.04-x86_64-gcc9.4.0/bin

# SQLITECPP installs in lib but halld_recon expects it in lib64
ln -s lib $SQLITECPP_HOME/lib64

# Build halld_recon
mkdir -p ${GLUEX_TOP}/halld_recon
git clone --depth 1 https://github.com/JeffersonLab/halld_recon ${GLUEX_TOP}/halld_recon/latest
cd ${GLUEX_TOP}/halld_recon/latest/src
# sed -i 's/host\.find("239\.")==0/true/' libraries/DAQ/HDET.cc  # patch to always connect via tcp and not via shared memfile
scons -j $NTHREADS ../$BMS_OSNAME/bin/hd_root
scons -j $NTHREADS ../$BMS_OSNAME/plugins/occupancy_online.so
scons -j $NTHREADS ../$BMS_OSNAME/plugins/highlevel_online.so
scons -j $NTHREADS install
export HALLD_RECON_HOME=$GLUEX_TOP/halld_recon/latest

# hdmon
mkdir -p $GLUEX_TOP/monitoring
svn co https://halldsvn.jlab.org/repos/trunk/online/packages/monitoring $GLUEX_TOP/monitoring
svn co https://halldsvn.jlab.org/repos/trunk/online/packages/SBMS $GLUEX_TOP/SBMS
cd $GLUEX_TOP/monitoring
ln -s $GLUEX_TOP/halld_recon/latest/src/SBMS $GLUEX_TOP
# Hacky patches
echo "env.Append(LIBS=[ 'ECAL','RootSpy','cmsg','cmsgxx', 'xmsg', 'protobuf'])" >> src/hdmon/SConscript
ln -s libcMsg.so $GLUEX_TOP/coda/Linux-x86_64/lib/libcodaChannels.so
python2 /usr/local/bin/scons -j $NTHREADS $BMS_OSNAME/bin/hdmon

# etUtils
mkdir -p $GLUEX_TOP/etUtils
svn co https://halldsvn.jlab.org/repos/trunk/online/packages/etUtils $GLUEX_TOP/etUtils
cd $GLUEX_TOP/etUtils
# Hacky patches
sed -i '/^env.PrependUnique(FORTRANFLAGS/a \env.Append(LINKFLAGS="-pthread")' SConstruct
# echo "env.Append(LIBS=[ 'ECAL','RootSpy','cmsg','cmsgxx', 'xmsg', 'protobuf'])" >> src/hdmon/SConscript
# ln -s libcMsg.so $GLUEX_TOP/coda/Linux-x86_64/lib/libcodaChannels.so
python2 /usr/local/bin/scons -j $NTHREADS $BMS_OSNAME/bin/file2et

# Remove some of the build files
rm -rf  $GLUEX_TOP/jana/latest/.Linux*
rm -rf  $GLUEX_TOP/halld_recon/latest/src/.Linux*
rm -rf  $GLUEX_TOP/xmsg/xmsg-javav2.3.src

