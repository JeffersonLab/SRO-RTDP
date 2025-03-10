## GPU Emulator with ZMQ Sender & Receiver

### Build and compile on ifarm `scimlxxxx` nodes

Luckily we have the `ZMQ` (`so` only) and `sqlite` dependency on the native OS, so a self-built Apptainer/Singularity container is not required.

1. Ask for a GPU node `scimlxxxx` via Slurm. Prefer T4, A100 and A800 nodes equipped with Tensor Cores.

   ```bash
    # Only ask for 1 GPU on node sciml2401
    bash-5.1$ srun -p gpu --gres=gpu:A800:1 --mem=24G --pty bash
    srun: job xxxxxxxx queued and waiting for resources
    srun: job xxxxxxxx has been allocated resources
    bash-5.1$ hostname
    sciml2401.jlab.org

    # zmq lib (no heeder files) and sqlite3 are available on the GPU node
    bash-5.1$ ldconfig -p | grep zmq
        libzmq.so.5 (libc6,x86-64) => /lib64/libzmq.so.5
    bash-5.1$ ldconfig -p | grep sql
        libsqlite3.so.0 (libc6,x86-64) => /lib64/libsqlite3.so.0
        libsqlite3.so (libc6,x86-64) => /lib64/libsqlite3.so
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

3. Build and compile the project using `cmake`.
   ```bash
   bash-5.1$ pwd
   /home/xmei/projects/SRO-RTDP/rtdp/cuda/gpu_proxy/  # change dir as needed
   bash-5.1$ rm -rf build  # clean the existing build
   bash-5.1$ mkdir build && cd build
   bash-5.1$ cmake ..
   ...
   -- The CUDA compiler identification is NVIDIA 12.8.61
   ...
   -- Build files have been written to: /home/xmei/projects/SRO-RTDP/rtdp/cuda/gpu_proxy/build

   bash-5.1$ make -j64
   ...
   [100%] Built target gpu_emu
   ```


#### Test the functionality on 
1. Run the GPU-proxy on the GPU node `scimlxxxx`.

2. Prepare a sender, probably with `podio2tcp`.
