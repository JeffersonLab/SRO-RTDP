

#
# docker build --platform linux/amd64 -t rtdp-gluex -f Dockerfile .
# 
# FROM rootproject/root:6.30.06-ubuntu22.04
FROM rootproject/root:6.26.14-ubuntu20.04 as builder 

ADD https://pki.jlab.org/JLabCA.crt /usr/local/share/ca-certificates

RUN update-ca-certificates \
    && apt update \
    && apt install -y libxml-simple-perl libxml-writer-perl libfile-slurp-perl \
                      git libboost-dev bc subversion scons autoconf libtool \
                      python2 python3-pip libbz2-dev libxerces-c-dev \
                      default-jdk openjdk-8-jdk libczmq-dev libprotobuf-dev protobuf-compiler \
                      libcurl4-openssl-dev doxygen \
    && pip3 install future \
    && ln -s /usr/bin/cmake /usr/bin/cmake3 \
    && ln -s /usr/lib/x86_64-linux-gnu/libxerces-c.so /usr/lib/libxerces-c.so \
    && git clone https://github.com/jeffersonlab/build_scripts /opt/build_scripts

COPY run_build.sh /opt/run_build.sh
COPY version.xml /opt/version.xml

# Build everything
RUN chmod +x /opt/run_build.sh \
    && /opt/run_build.sh

# Remove binaries that take up a lot of space. This will help reduce the size
# of the image when whole directories are copied to the final stage image below.
ENV BMS_OSNAME Linux_Ubuntu20.04-x86_64-gcc9.4.0
RUN cd /group/halld/Software/builds/${BMS_OSNAME}/halld_recon/latest/${BMS_OSNAME} \
    && rm -rf lib \
    && rm -rf bin/hdview2 \
    && rm -rf bin/hd_eventfilter \
    && rm -rf bin/hdbeam_current \
    && rm -rf bin/hd_ana \
    && rm -rf bin/hdevio_scan \
    && rm -rf bin/hd_dump \
    && rm -rf bin/hddm_merge_files \
    && rm -rf bin/hddm_cull_events \
    && rm -rf bin/hdskims \
    && rm -rf bin/hd2root \
    && rm -rf bin/hdevio_sample \
    && rm -rf bin/root2email \
    && rm -rf bin/root_merge \
    && mv plugins junk \
    && mkdir plugins \
    && cp junk/occupancy_online.so plugins \
    && cp junk/monitoring_hists.so plugins \
    && rm -rf junk

# Stage 2: Final stage
# FROM ubuntu:20.04
# # FROM rootproject/root:6.26.14-ubuntu20.04 


# ADD https://pki.jlab.org/JLabCA.crt /usr/local/share/ca-certificates

# # Allow the timezone to be set during the build
# ENV DEBIAN_FRONTEND=noninteractive
# ARG TZ=America/New_York
# ENV TZ=$TZ

# RUN apt update \
#     && apt-get install -y tzdata \
#     && ln -fs /usr/share/zoneinfo/$TZ /etc/localtime \
#     && dpkg-reconfigure --frontend noninteractive tzdata \
#     && apt install -y ca-certificates \
#         libc6 \
#         libxml-simple-perl  libfile-slurp-perl \
#         libxerces-c3.2 \
#         libmysqlclient21 \
#         libblas3 \
#         libfreetype6 \
#         libprotobuf17 \
#         libczmq-dev \
#         openjdk-8-jre-headless \
#         build-essential \
#     && update-ca-certificates \
#     && mkdir -p /group \
#     && mkdir -p /usr/local

# COPY --from=builder /opt /opt
# COPY --from=builder /group /group
# COPY --from=builder /usr/local /usr


COPY setenv.sh /opt/setenv.sh
RUN chmod +x /opt/setenv.sh

COPY Dockerfile /container/Dockerfile
RUN ln -s /root /home/root
RUN ln -s /root /home/0

ENV BMS_OSNAME_OVERRIDE Linux_Ubuntu20.04-x86_64-gcc9.4.0
COPY entrypoint.sh /container/entrypoint.sh
RUN chmod +x /container/entrypoint.sh
ENTRYPOINT ["/container/entrypoint.sh"]

CMD ["/bin/bash", "-l"]