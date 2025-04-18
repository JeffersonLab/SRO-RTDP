## Directory Structure

### Helper scripts

- [hello_server.py](./hello_server.py): A file to easily detect whether a port is available for remote connection.
- [query_job_nodelist.bash](./query_job_nodelist.bash): Helper script to locate a Slurm job to the execution hosts.

### Workflow Demo
- [workflow/](./workflow/): Using Cylc or Fireworks workflows to automate demos on JLab's `ifarm` cluster.

### Slurm Demos
Demos for a 3-node system:
1. Sender node (allocated with `sbatch`);
2. Receiver node (allocated with `sbatch`);
3. Control node: Centrailized metrics DB with Prometheus or InfluxDB to moniter the sender and rceiver node. If it's a compute node, it is allocated with the interactive mode.

May need minor updates (such as path) to run the scripts on ifarm.

#### Demo list
- [slurm-iperf3-prom-demo/](./slurm-iperf3-prom-demo/): The centralized metric DB is Prometheus. Process-exporters run on the sender (native `iperf3` client) and receiver (native `iperf3` server) node.

- [slurm-podio2tcp-influxdb-demo/](./slurm-podio2tcp-influxdb-demo/): The centralized metric DB is InfluxDB. The sender node runs `podio2tcp`. The receiver node runs `tcp2podio`.

### Containers
- [sifs/](./sifs/): The Singularity/Apptainer containers and the method to pull them.

### Configuration files
- [config/](./config/) Configuration files or configuration helper files for Prometheus exporters, Prometheus, etc.
