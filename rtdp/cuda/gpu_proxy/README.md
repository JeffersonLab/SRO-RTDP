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


#### Test the functionality on ifarm.
1. Run the GPU-proxy on the GPU node `scimlxxxx`.
   ```bash
   bash-5.1$ pwd   # on the GPU node
   /home/xmei/projects/SRO-RTDP/rtdp/cuda/gpu_proxy/build
   bash-5.1$ ./gpu_emu -a 129.57.138.18 -t
   RECV - ZeroMQ pulling from: tcp://*:55555
   SEND - ZeroMQ pushing to: tcp://129.57.138.18:55556

   Waiting for data ...

   Received [6553600] bytes from ZeroMQ socket.
            Input matrix dimension, (#columns)x(#rows): 2048x800
            Random matrix dimension, (#columns)x(#rows): 1024x2048
   First 10 elements of h_out:
   517.654 513.569 519.397 527.431 502.34 520.252 501.336 511.633 506.323 520.5 


   First 10 elements of CPU computed matrix multiplication result:
   517.654 513.569 519.398 527.431 502.34 520.251 501.336 511.633 506.322 520.5 

            Output matrix dimension, (#columns)x(#rows): 1024x800
   Sent [3276800] bytes via ZeroMQ socket.
   ...

   ```

2. Prepare a sender (chained before GPU proxy) with Python venv.
   ```bash
   (zmq) bash-5.1$ pip install pyzmq numpy  # dependecies of python_zmq_helper
   (zmq) bash-5.1$ python python_zmq_helper/zmq_fp_sender.py -a <GPU_NODE_IP>  # send to <GPU_NODE_IP>:55555
   ```

3. Prepare a receiver (chained after GPU Proxy) with Python helper
   ```bash
   (zmq) bash-5.1$ pip install pyzmq numpy
   (zmq) bash-5.1$ python python_zmq_helper/zmq_fp_receiver.py  # receive at localhost:55556
   Received: [517.65393 513.569   519.39746 527.43134 502.33954 520.2516  501.3357
 511.63293 506.3226  520.5004 ]
   ```
