# SRO-RTDP

Repository for the Streaming Readout Real-time Development Platform, JLab's LDRD project LD2411 and LD2512.

This repository contains a mixture of simulation tooling (Python), C++ emulators that mimic CPU load and network behavior, container artifacts, example configurations and experiments, and helper scripts for running end-to-end demos.

## Top-level layout

- [rtdp](./rtdp/): The CPU emulator and GPU proxy to simulate the effects of data flows across multiple components and multiple nodes.

- [containers](./containers/): Dockerfiles and scripts to build the Docker images and run experiments for `podio-eicrecon`, `rtdp-gluex`.

- [examples](./examples/):
  - [EICrecon](./examples/fabric_eicrecon/): Run the `eicrecon` application on the FABRIC testbed.
  - [farm-tests](./examples/farm-tests/): Run `iperf3`, `podiotcp` on JLab ifarm with Prometheus/Grafana/InfluxDB monitoring and Cycl workflow management.
  - [beam-capture](./examples/beam-capture/): Jupyter notebooks to analyze the beam capture results and gnerate plots.
  - others...


## TODOs
- [] An (integrated) Cycl workflow to launch an all-in-one workflow on the 100 Gbps "ejfat" testbed.
