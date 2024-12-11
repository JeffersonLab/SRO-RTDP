# Testing cpu_emu component:

## cpu emu destination system:

nc -l 8080 > \<file> 


## cpu emu host system:

$(which time) -f '\t%E real,\t%U user,\t%S sys,\t%K amem,\t%M mmem,\t%W swps,\t%c cws' ./cpu_emu -r \<rcv port> -v \<verbosity> -t \<num_thrds> -i "\<dest ip>" -p \<dest port> 


## Source data system:

cat \<file> | nc -N -q 0 \<cpu emu host> \<port> 
