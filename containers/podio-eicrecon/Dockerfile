# Use eicweb/jug_xl:nightly as the base image
FROM eicweb/jug_xl:nightly

# Set the working directory
WORKDIR /app

# Source /opt/detector/epic-main/setup.sh
RUN echo ". /opt/detector/epic-main/bin/thisepic.sh" >> /etc/profile

# Set environment variables
ENV podio_ROOT /app/podio

# Clone the repository and build podio
RUN git clone https://github.com/faustus123/podio -b davidl_RootReader_TDirectory podio.src && \
    cmake -S podio.src -B podio.build -DCMAKE_INSTALL_PREFIX=$podio_ROOT -DCMAKE_CXX_STANDARD=20 && \
    cmake --build podio.build --target install -j16 && \
    echo "export LD_LIBRARY_PATH=$podio_ROOT/lib:\$LD_LIBRARY_PATH" >> /etc/profile


# Clone the repository and build podio2tcp
RUN git clone https://github.com/JeffersonLab/SRO-RTDP && \
    cmake -S SRO-RTDP/src/utilities/cpp/podio2tcp -B podio2tcp.build -DCMAKE_CXX_STANDARD=20 && \
    cmake --build podio2tcp.build

COPY simout.100.edm4hep.root /app/simout.100.edm4hep.root


# Clone the repository and build JANA2
ENV JANA_ROOT /app/JANA2
RUN git clone https://github.com/JeffersonLab/JANA2 JANA2.src && \
    cmake -S JANA2.src -B JANA2.build \
        -DUSE_PODIO=1 \
        -DUSE_ROOT=1 \
        -DUSE_ZEROMQ=1 \
        -DCMAKE_INSTALL_PREFIX=${JANA_ROOT} \
        -DCMAKE_CXX_STANDARD=20 && \
    cmake --build JANA2.build --target install -j32 && \
    echo "export LD_LIBRARY_PATH=${JANA_ROOT}/lib:\$LD_LIBRARY_PATH" >> /etc/profile && \
    echo ". ${JANA_ROOT}/bin/jana-this.sh" >> /etc/profile

# Clone the repository and build EICrecon
RUN git clone https://github.com/eic/EICrecon EICrecon.src && \
    cmake -S EICrecon.src -B EICrecon.build \
        -DCMAKE_INSTALL_PREFIX=${PWD}/EICrecon \
        -DCMAKE_CXX_STANDARD=20 && \
    cmake --build EICrecon.build --target install -j32 && \
    echo ". ${PWD}/EICrecon/bin/eicrecon-this.sh" >> /etc/profile


# Build podiostream and set JANA_PLUGIN_PATH
RUN cmake -S SRO-RTDP/src/utilities/cpp/podiostream -B podiostream.build \
    -DCMAKE_POLICY_DEFAULT_CMP0074=NEW \
    -DCMAKE_CXX_STANDARD=20 && \
    cmake --build podiostream.build && \
    echo "export JANA_PLUGIN_PATH=${PWD}/podiostream.build:\$JANA_PLUGIN_PATH" >> /etc/profile


# COPY run-EICrecon.bash /app/run-EICrecon.bash
COPY run-EICrecon.bash /app/run-EICrecon.bash
# make it executable
RUN chmod +x /app/run-EICrecon.bash

COPY run-sender.bash /app/run-sender.bash
# make it executable
RUN chmod +x /app/run-sender.bash

RUN source /etc/profile
