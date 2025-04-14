## GPU Emulator with ZMQ Sender & Receiver

### Build and compile on ifarm `scimlxxxx` nodes

Luckily we have the `ZMQ` (`so` only) and `sqlite` dependency on the native OS, so a self-built Apptainer/Singularity container is not required.

1. Ask for a GPU node `scimlxxxx` via Slurm. Prefer T4, A100 and A800 nodes equipped with Tensor Cores.

   ```bash
    # Only ask for 1 GPU on node sciml2302
    bash-5.1$ srun -p gpu --gres=gpu:A100:1 --mem=100G --pty bash
    srun: job xxxxxxxx queued and waiting for resources
    srun: job xxxxxxxx has been allocated resources
    bash-5.1$ hostname
    sciml2302.jlab.org

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
   bash-5.1$ cmake -DCMAKE_INSTALL_PREFIX=.. ..  # set install path
   ...
   -- The CUDA compiler identification is NVIDIA 12.8.61
   ...
   -- Build files have been written to: /home/xmei/projects/SRO-RTDP/rtdp/cuda/gpu_proxy/build

   bash-5.1$ make -j64
   ...
   [100%] Built target gpu_emu
   bash-5.1$ make install  # install without sudo
   ```


#### Test the functionality on ifarm.
1. Run the GPU-proxy on the GPU node `scimlxxxx`.
   ```bash
   bash-5.1$ pwd   # on the GPU node
   /home/xmei/projects/SRO-RTDP/rtdp/cuda/gpu_proxy/build
   bash-5.1$ ../bin/gpu_emu -a 172.17.1.54 -v
   RECV - ZeroMQ pulling from: tcp://*:55555
   SEND - ZeroMQ pushing to: tcp://172.17.1.54:55556

   Waiting for data ...

   Received [8192] bytes from ZeroMQ socket.
   First 10 elements of h_in:
   0.351711 0.852162 0.300457 0.894459 0.0352142 0.0829234 0.700032 0.681391 0.0781673 0.242668 

            Input matrix dimension, (#columns)x(#rows): 2048x1
            Random matrix dimension, (#columns)x(#rows): 1024x2048
   First 10 elements of h_out:
   513.019 525.493 512.337 528.763 523.848 540.64 535.584 519.227 519.087 513.447 


   First 10 elements of CPU computed matrix multiplication result:
   513.019 525.493 512.338 528.763 523.848 540.64 535.584 519.227 519.086 513.447 

            Output matrix dimension, (#columns)x(#rows): 1024x1
   Sent [4096] bytes via ZeroMQ socket.
   ...
   [Monitor] Incoming: [0.012288 MB/s], Outgoing: [0.006144 MB/s]
   ...

   ```

2. Prepare a sender (chained before GPU proxy) with Python venv.
   ```bash
   (zmq) bash-5.1$ pip install pyzmq numpy  # dependecies of python_zmq_helper
   (zmq) bash-5.1$ python python_zmq_helper/zmq_fp_sender.py -a <GPU_NODE_IP> -r 25 --group-size 2048 # send to <GPU_NODE_IP>:55555
   Sending data to 172.17.1.13:55555 (random values)
   Target send rate: 25.0 MB/s

   Each message needs: 0.32768 ms
         Sent 0.008192 MB,  curr_send_rate=25.0 MB/s, duration=7.764577865600586 ms
         Sleep for 992.2354221343994 ms...
         ...
   ```

3. Prepare a receiver (chained after GPU Proxy) with Python helper
   ```bash
   (zmq) python rtdp/cuda/gpu_proxy/python_zmq_helper/zmq_fp_receiver.py -v
   Receiving data on port 55556...
   Received [4096] bytes
         First 10 floats: [513.01874 525.4927  512.33746 528.76263 523.84845 540.64    535.58386
   519.22736 519.0868  513.44714]
   ...
   curr_recv_rate = 0.006144 MB/s
   ```
