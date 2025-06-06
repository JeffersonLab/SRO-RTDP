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

        rcv_port: 8888              # listen port
        destination: "129.57.177.8" # destination IP
        dst_port: 8888              # destination port
        sleep: 0                    # disables/enables sleep versus cpu burn mode.
        threads: 5                  # number of independent threads
        latency: 500                # Processing latency in nsec/byte input: 500 calibrated from 60kB CLAS12/ERSAP
        mem_footprint: 0.05         # Thread Memory footprint in GB
        output_size: 0.001          # Destination Output size in GB
        verbose: 1                  # disables/enables verbose mode.
        terminal: 0                 # if 1 do not forward result to destination

Any of the <yaml_file> settings may be overidden at the command line via the following options:


## cpu_emu Usage: 

        -h help
        -b thread latency in nsec/byte input
        -i destination address (string)
        -m thread memory footprint in GB
        -o output size in GB
        -p destination port (default = 8888)
        -r receive port (default = 8888)
        -s sleep versus burn cpu
        -t num threads (default = 5)
        -y <yaml_file>
        -v verbose (= 0/1 - default = false (0))
        -z act as terminal node


## Source data system:

        <some ZeroMQ based sender> e.g.,

	./zmq-event-clnt


