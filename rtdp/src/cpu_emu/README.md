# Emulation

## Build C++ cpu_emu component:

        ./buildp cpu_emu

## Build C++ zmq-event-emu-clnt sender:

        ./buildp zmq-event-emu-clnt

where ./buildp assumes .cc suffix which shows you any build errors via 'less' - just hit 'q' to exit. 


## Testing cpu_emu component:
### Data source:

        <some ZeroMQ based sender>, e.g., 
		./zmq-event-emu-clnt -a <stream_id> -p <ZMQ pub port> -r <bit rate Gbps>  -v <verbosity> -s <frame_sz MB> -c <frame count>

### cpu emu components:

        <cpu_emu component>, e.g.,
		./cpu_emu -i <ZMQ sub ip> -p <ZMQ sub port> -r <ZMQ pub port> -t <num threads> -v <verbosity> -z <terminal node?> -s <sleep or compute?> -f <frame count>  -o <out frame size GB>

The -z switch  facilitates a multi component daisey chain of cpu_emu instances (all with -z 0) terminated by a last cpu_emu instance with -z 1.
        
where cpu_emu may also be passed only a yaml file designation, e.g., 

	./cpu_emu -y <yaml_file>


the structure of <yaml_file> is as follows:

        latency:		100				# Processing latency in nsec/byte frame size: 500 yields 30mS for 60kB frame calibrated from CLAS12/ERSAP
        mem_footprint:	0.01			# Thread Memory footprint in GB
        output_size:	0.0000572		# Destination Output size in GB
        sbscriptn_ip:	"127.0.0.1"		# ZMQ subsciption IP
        sub_port:		8888			# ZMQ subsciption port port
        pub_port:		8888			# ZMQ puclication port port
        sleep:			0				# if 1, sleep versus burn cpu cycles
        threads:		5				# number of independent threads to spawn
        verbose:		1				# verbosity level (currently only 0/1) if > 0, write something chat to stdout
        terminal:		0				# if 1, do not forward result to a destination
		frame_cnt:		1000			# number of frames expected in the run

Any of the <yaml_file> settings may be overidden at the command line via the following options:

### cpu_emu Usage: 

	cpu_emu Usage: 
	        -h help  
	        -b Processing latency in nsec/byte frame size	(default 500)
	        -f total frames sender will send				(default 100)
	        -i subscription address							(default 127.0.0.1)  
	        -m thread memory footprint in GB				(deafult 0.01)
	        -o output size in GB							(default 0.000057)
	        -p subscription port							(default = 8888)  
	        -r publish port									(default = 8889)  
	        -s sleep versus burn cpu = 0/1					(default = false = 0)  
	        -t num threads									(default = 1)  
	        -v verbose = 0/1								(default = false = 0)  
	        -y yaml config file								(default cpu_emu.yaml)
	        -z act as terminal node = 0/1					(default = false = 0)  


### Source data system:

	zmq-event-emu-clnt Usage: 
	        -h help  
	        -a stream/channel id		(default 0) 
	        -p publication port			(default 8888) 
	        -r bit rate to send (Gbps)	(default 1)
	        -c event count				(default 100) 
	        -v verbose = 0/1			(default 1 = true)  
	        -s event size (MB)			(default 1) 
## Report Processing

The sender and each cpu_emu component should be executed with -v 1 and stdout redirected to a file.  All component output files plus the sender output file should be combined into a single file (order arbitray).  This conglomerate output file  is the input file to the python notebook file emu_rprt.ipynb that will produce various graphs and statistics of the run metrics.

# Simulation

The simulation is available in the python notebook file rtdp.py

Python dependies are

	pandas matplotlib  seaborn scipy

Note that numpy may need to be upgraded as

	python3 -m pip install --upgrade numpy

Parameters for the simulation are set in the yaml file cpu_sim.yaml with structure as follows:

	cmp_ltnc_nS_B:    500		# Processing latency in nsec/byte input: 500 calibrated from 60kB CLAS12
	output_size_GB:   0.000057	# Output size in GB
	nic_Gbps:         100		# Outbound NIC/Network speed in Gbps for sim_mode
	frame_sz_MB:      0.06		# Frame Size MB
	frame_cnt:        100		# Numbers frames sender will send
	cmpnt_cnt:        3			# Number of cpu_sim components
	avg_bit_rt_Gbps:  0.01		# Sender bit rate Gbps

The simulation is excuted as follows:

	$ python3
	>>> from rtdp import RTDP
	>>> rtdp = RTDP(rng_seed=7, log_file="z.txt")
	>>> rtdp.sim()
	>>> rtdp.plot_all()

.png and .txt files are produced.  Alternatively, individual plots may be performed such as:

	>>> rtdp.plot_rcv_bit_rate()
