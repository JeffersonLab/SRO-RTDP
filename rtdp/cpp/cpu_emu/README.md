# Build cpu_emu component (program):

        ./buildp cpu_emu

(assumes .cc suffix) which shows you any build errors via 'less' - just hit 'q' to exit. 


# Testing cpu_emu component:

## cpu emu destination system:

        nc -l <nic ip> <port> > <file> 

e.g.,

        nc -l 129.57.177.6 8080 > /tmp/junk


## cpu emu host system:

## cpu_emu Usage: 

        -h help  \n\
        -b seconds thread latency per GB input \n\
        -i destination address (string)  \n\
        -m thread memory footprint in GB  \n\
        -o output size in GB  \n\
        -p destination port (default = 8888)  \n\
        -r receive port (default = 8888)  \n\
        -s sleep versus burn cpu  \n\
        -t num threads (default = 10)  \n\
        -v verbose (= 0/1 - default = false (0))  \n\n";

        Required: -b -i -m -o -t

## typical invocation:

        $(which time) -f '\t%E real,\t%U user,\t%S sys,\t%K amem,\t%M mmem,\t%W swps,\t%c cws' ./cpu_emu -b <int> -i <dest ip> -p <dest port> -m <float> -o <float> -t <int> -r <listen port> -v <verbosity>

e.g.,

        $(which time) -f '\t%E real,\t%U user,\t%S sys,\t%K amem,\t%M mmem,\t%W swps,\t%c cws' ./cpu_emu -b 1000 -i 129.57.177.6 -p 8080 -m 10 -o 0.1 -t 10 -r 8888 -v 1

where the sent event is 10MB.

## Source data system:

        cat <file> | nc -N -q 0 <cpu emu host nic> <port> 

e.g., 

        cat <file> | nc -N -q 0 ejfat-5-daq.jlab.org 8888

## Notes

netcat (nc) switches vary somewhat by Linux distro.



