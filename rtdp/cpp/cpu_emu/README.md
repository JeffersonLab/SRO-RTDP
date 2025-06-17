# Build cpu_emu component (program):

        ./buildp cpu_emu

(assumes .cc suffix) which shows you any build errors via 'less' - just hit 'q' to exit. 


# Build cpu_emu component (program):

        ./buildp cpu_emu

(assumes .cc suffix) which shows you any build errors via 'less' - just hit 'q' to exit. 


# Testing cpu_emu component:

## cpu emu destination system:

        <some ZeroMQ based receiver> 

 e.g., ./cpu_emu itself can serve as the destination system by inclusion of the -z switch or use of the yaml file setting for terminal: (see below). This facilitates using a single cpu_emu instance as a data sink, or multiple daisey chained cpu_emu instances (all without the -z switch) terminated by a last cpu_emu instance with the -z switch.


## cpu emu host system:

In typical server fashion cpu_emu binds to all available resident interfaces and listen to the inidcated port.


## typical invocation:

        $(which time) -f '\t%E real,\t%U user,\t%S sys,\t%K amem,\t%M mmem,\t%W swps,\t%c cws' ./cpu_emu -y <yaml_file>
        
where the structure of <yaml_file> is as follows:

        latency: 500                # Processing latency in nsec/byte input: 500 calibrated from 60kB CLAS12/ERSAP
        destination: "129.57.177.8" # destination IP
        mem_footprint: 0.05         # Thread Memory footprint in GB
        output_size: 0.001          # Destination Output size in GB
        dst_port: 8888              # destination port
        rcv_port: 8888              # listen port
        sleep: 0                    # if 1, sleep versus burn cpu cycles
        threads: 5                  # number of independent threads to spawn
        verbose: 1                  # if 1, write verbose chat to stdout
        sim_mode: 0                 # if 1, run in 'sim' mode
        terminal: 0                 # if 1, do not forward result to a destination

Any of the <yaml_file> settings may be overidden at the command line via the following options:


## cpu_emu Usage: 

        -b seconds thread latency per GB input
        -i destination address (string)
        -m thread memory footprint in GB
        -o output size in GB
        -p destination port (default = 8888)
        -r receive port (default = 8888)
        -s sleep versus burn cpu = 0/1 (default = false = 0)
        -t num threads (default = 10)
        -v verbose = 0/1 (default = false = 0)
        -x run in sim mode = 0/1 (default = false = 0)
        -y yaml config file
        -z act as terminal node = 0/1 (default = false = 0)


## Source data system:

        <some ZeroMQ based sender> e.g.,

	./zmq-event-clnt

## Simulation Mode:

	t=$(mktemp); tx=$(mktemp); echo $t; python launcher_py_cpu_emu_chain.py --components 3 --base-port 7000 --avg-rate 500 --rms 0.1 --duty 0.9 --nic 100 > $t 2> $tx
 
 then use:

        ./sim_rprt.sh $t

With verbose mode asserted, trace prints for sender and receivers will be in the file $t.

### Daisy Chain launcher script launcher_py_cpu_emu_chain.py:

        --components <Number of components to simulate in a daisy chain>
        --base-port  <base_port> (i.e., use port range (<base_port> + 1, <base_port> + <num components to daisy chain>)
        --avg-rate   <Average rate in Mbps per component>
        --rms        <RMS fraction>
        --duty       <Duty cycle>
        --nic        <NIC bandwidth in Gbps>
 
### Streamer script simulate_sender-zmq-emu.py:

        --port           <destination port>
        --avg-rate-mbps  <Average rate in Mbps per component>
        --rms-fraction   <RMS fraction>
        --duty-cycle     <Duty cycle>
        --nic-limit-gbps <NIC bandwidth in Gbps>

