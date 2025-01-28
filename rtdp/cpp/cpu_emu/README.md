# Build cpu_emu component (program):

        ./buildp cpu_emu

(assumes .cc suffix) which shows you any build errors via 'less' - just hit 'q' to exit. 


# Testing cpu_emu component:

## cpu emu destination system:

        nc -l <nic ip> <port> > <file> 

e.g.,

        nc -l 129.57.177.6 8080 > /tmp/junk


## cpu emu host system:

In typical server fashion cpu_emu binds to all available resident interfaces and lsiten to the inidcated port.


## typical invocation:

        $(which time) -f '\t%E real,\t%U user,\t%S sys,\t%K amem,\t%M mmem,\t%W swps,\t%c cws' ./cpu_emu -y <yaml_file>
        
where the structure of <yaml_file> is as follows:

rcv_port: 8888              # listen port
destination: "129.57.177.8" # destination IP
dst_port: 8888              # destination port
sleep: 0                    # disables/enables sleep versus cpu burn mode.
threads: 4                  # number of independent threads
latency: 50                 # Thread Processing latency secs per input GB
mem_footprint: 0.05         # Thread Memory footprint in GB
output_size: 0.001          # Destination Output size in GB
verbose: 1                  # disables/enables verbose mode.


Any of the <yaml_file> settigs may be overidden at the commamnd line via the following options:


## cpu_emu Usage: 

        -h help
        -b thread seconds thread latency per GB input
        -i destination address (string)
        -m thread memory footprint in GB
        -o output size in GB
        -p destination port (default = 8888)
        -r receive port (default = 8888)
        -s sleep versus burn cpu
        -t num threads (default = 10)
        -v verbose (= 0/1 - default = false (0))


## Source data system:

        cat <file> | nc -N -q 0 <cpu emu host nic> <port> 

e.g., 

        cat <file> | nc -N -q 0 ejfat-5-daq.jlab.org 8888

## Notes

netcat (nc) switches vary somewhat by Linux distro.



