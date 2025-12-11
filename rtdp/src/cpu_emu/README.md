# Top Level Python orchestrator

The RTDP user should use the rtdp.py python, e.g.

	$ python3
	>>> from rtdp import RTDP
	>>> rtdp = RTDP(rng_seed=37)
	>>> rtdp.<method()>

Python dependies are

	import os
	import math
	import random
	import time
	import pandas as pd
	import numpy as np
	import matplotlib.pyplot as plt
	import seaborn as sns
	import re
	import yaml
	import subprocess
	#import pexpect
	import shlex
	import random
	import glob
	
	from typing import Optional, Union, List
	from datetime import datetime
	from scipy.stats import skew, kurtosis
	from pandas import json_normalize

# Emulation

## Build C++ cpu_emu component:

	./buildp cpu_emu

## Build C++ zmq-frame-emu-clnt sender:

	./buildp zmq-frame-emu-clnt

where <i>./buildp</i> assumes <i>.cc</i> suffix and shows you any build errors via 'less' - just hit 'q' to exit. 


## Emulation components may be run idividually:

### Data source:

	./zmq-frame-emu-clnt 
		-a <stream_id> 
		-f <frame count> 
		-p <ZMQ pub port> 
		-r <bit rate Gbps> 
		-s <frame_sz MB> 
		-y yaml config file 
		-v <verbosity> 

where <i>zmq-frame-emu-clnt</i> may also be passed only a yaml file designation, e.g., 

	./zmq-frame-emu-clnt -y <yaml_file>

the structure of <yaml_file> is as follows:

	stream_id:          0
	frame_cnt:          100
	pub_port:           6000
	avg_bit_rt_Gbps:    0.01
	frame_sz_MB:        0.0586
	verbosity:          1

### cpu emu components:

	./cpu_emu 
		-h help  
		-b Processing latency in nsec/byte frame size 
		-f total frames sender will send  
		-i subscription address (string)  
		-m thread memory footprint in GB  
		-o output size in GB  
		-p subscription port 
		-r publish port
		-s sleep versus burn cpu = 0/1 (false = 0)  
		-t num threads 
		-v verbose = 0/1  
		-y yaml config file  
		-z act as terminal node = 0/1 (false = 0)  

where <i>cpu_emu</i> may also be passed only a yaml file designation, e.g., 

	./cpu_emu -y <yaml_file>

The -z switch  facilitates a multi component daisey chain of cpu_emu instances (all with -z 0) terminated by a last cpu_emu instance with -z 1.
        
the structure of <yaml_fil</i>e> is as follows:

	latency:		100				# Processing latency in nsec/byte frame size: 500 yields 30mS for 60kB frame calibrated from CLAS12/ERSAP
	mem_footprint:	0.01			# Thread Memory footprint in GB
	output_size:	0.0000572		# Destination Output size in GB
	sbscriptn_ip:	"127.0.0.1"		# ZMQ subsciption IP
	sub_port:       8888            # ZMQ subsciption port
	pub_port:       8888			# ZMQ puclication port
	sleep:			0               # if 1, sleep versus burn cpu cycles
	threads:		1               # number of independent threads to spawn
	verbose:		1				# verbosity level (currently only 0/1) if > 0, write something to stdout
	terminal:		0				# if 1, do not forward result to a destination
	frame_cnt:		1000			# number of frames expected in the run

Any of the <yaml_file> settings may be overidden at the command line via the command line option.

To run the emulation via rtdp.py:

	$ python3
	>>> from rtdp import RTDP
	>>> rtdp = RTDP(rng_seed=37)
	>>> rtdp.emulate(login_pause=True, emu_config="emulate.yaml", sleep_time=2)
	>>> time.sleep(10) # MUST wait enough time for emulation to finish
	>>> rtdp.parse_emu_logs()

Plots can then be produced individually as follows:

	>>> rtdp.plot_rcv_bit_rate()

or in toto as:

	>>> rtdp.plot_all()
	
the structure of <yaml_file> passed to the <i>emulate()</i> method is as follows:

	progs:
	  - "cpu_emu"
	  - "cpu_emu"
	  - "cpu_emu"
	  - "zmq-frame-emu-clnt"
	emu_yamls:
	  - "cpu_emu_1.yaml"
	  - "cpu_emu_2.yaml"
	  - "cpu_emu_3.yaml"
	  - "zmq-frame-emu-clnt.yaml"
	hosts:
	  - 127.0.0.1
	  - 127.0.0.1
	  - 127.0.0.1
	  - 127.0.0.1

The <i>progs</i> section lists the emulation components with the last being a frame generator.
The <i>emu_yamls</i> section lists the yamls associated with each emulation component.
The <i>hosts</i> section lists the IPV4 addresses where each component is to be executed.

The <i>emulate()</i> method will stage each component on the indicated host in the users home directory along with the associated yamls config files.
The <i>parse_emu_logs()</i> logs will retrieve the output log files for each deployed component, and parse them into an internal database for plotting and statistcs.

<b>Note</b> the use of the <i>login_pause=True,  sleep_time=2</i>  parameters for <i>emulate()</i>.  This is necessary to accomodate remote logins to the IPV4 hosts and the  <i>sleep_time</i> parameter must be generous enough to accomodate the login process for each host.  E.g. a two factor login with a required token rollover may require 30 seconds or more.  Network security should be setup to obviate this need.

# Simulation

The simulation is available in the python file rtdp.py and excuted as follows:

	$ python3
	>>> from rtdp import RTDP
	>>> rtdp = RTDP(rng_seed=7)
	>>> rtdp.simulate(sim_config="simulate.yaml")

Note that numpy may need to be upgraded as

	python3 -m pip install --upgrade numpy

the structure of <yaml_file> passed to the <i>simulate()</i> method is as follows:

	daq_frame_cnt:        100   # Numbers frames sender will send
	daq_frame_sz_MB:      0.06  # Frame Size MB
	daq_avg_bit_rt_Gbps:  0.01  # Sender bit rate Gbps #0.015
	cmp_nic_Gbps:   # Outbound NIC/Network speed in Gbps for sim_mode
	  - 10
	  - 10
	  - 10
	cmp_ltnc_nS_B:  # Processing latency in nsec/byte input: 500 calibrated from 60kB CLAS12
	  - 100
	  - 100
	  - 100
	cmp_output_size_GB: # Output size in GB
	  - 0.00006
	  - 0.000057
	  - 0.000057 

where the first three lines are sender parameters, the list <i>cmp_nic_Gbps</i> are the NIC speeds for each component, the <i>cmp_ltnc_nS_B</i> list are the computational latencies for each component, and the <i>cmp_output_size_GB</i> list are the forwarding frame sizes for each component.

The simulation plots can be produced individually as follows:

	>>> rtdp.plot_rcv_bit_rate()

or in toto as:

	>>> rtdp.plot_all()


