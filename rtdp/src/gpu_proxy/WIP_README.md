## [WIP] Build and compile GPU Proxy on ifarm `scimlxxxx` Nodes

`zmq` lib and `YAML` lib (no header files) are available on the ifarm.
```
bash-5.1$ ldconfig -p | grep zmq
    libzmq.so.5 (libc6,x86-64) => /lib64/libzmq.so.5
bash-5.1$ ldconfig -p | grep yaml
    libyaml-0.so.2 (libc6,x86-64) => /lib64/libyaml-0.so.2
```

Try to avoid a self-built Apptainer/Singularity/Docker/Podman container and use the native OS.

1. Ask for a GPU node `scimlxxxx` via Slurm. Prefer A100 and A800 nodes of higher compute compability.

   ```bash
    # Only ask for 1 GPU on a GPU node. Interactive job.
    bash-5.1$ srun -p gpu --gres=gpu:A100:1 --mem=100G --pty bash
    srun: job xxxxxxxx queued and waiting for resources
    srun: job xxxxxxxx has been allocated resources
    bash-5.1$ hostname
    sciml2302.jlab.org
   ```

2. On the GPU node, add CUDA to your path. By default CUDA is not included in the PATH so `which nvcc` will fail.
   ```bash
    # The JLab ifarm CUDA path
    bash-5.1$ export PATH=/usr/local/cuda/bin:$PATH
    bash-5.1$ export LD_LIBRARY_PATH=/usr/local/cuda/lib64:$LD_LIBRARY_PATH
    bash-5.1$ nvcc --version
    Built on Wed_Jan_15_19:20:09_PST_2025
    Cuda compilation tools, release 12.8, V12.8.61
   ```
