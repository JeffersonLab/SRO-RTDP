

#
# docker build -t rtdp-gluex -f Dockerfile .
# 
# FROM rootproject/root:6.30.06-ubuntu22.04
FROM rootproject/root:6.26.14-ubuntu20.04

ADD https://pki.jlab.org/JLabCA.crt /usr/local/share/ca-certificates

RUN update-ca-certificates \
    && apt update \
    && apt install -y libxml-simple-perl libxml-writer-perl libfile-slurp-perl \
                      git libboost-dev bc subversion scons autoconf libtool \
                      python2 python3-pip libbz2-dev libxerces-c-dev \
                      default-jdk libczmq-dev libprotobuf-dev protobuf-compiler \
                      libcurl4-openssl-dev doxygen \
    && pip3 install future \
    && ln -s /usr/bin/cmake /usr/bin/cmake3 \
    && ln -s /usr/lib/x86_64-linux-gnu/libxerces-c.so /usr/lib/libxerces-c.so \
    && git clone https://github.com/jeffersonlab/build_scripts /opt/build_scripts

COPY run_build.sh /opt/run_build.sh
COPY version.xml /opt/version.xml
COPY setenv.sh /opt/setenv.sh

RUN chmod +x /opt/run_build.sh \
    && /opt/run_build.sh

COPY Dockerfile /container/Dockerfile
RUN ln -s /root /home/root
RUN ln -s /root /home/0

ENV BMS_OSNAME_OVERRIDE Linux_Ubuntu20.04-x86_64-gcc9.4.0

CMD ["/bin/bash"]