----------------------------

# **Software Package To Replay CODA DAQ From Pcap Files**

----------------------------

This package contains software from the emu repository (emu-streaming branch):

    https://github.com/JeffersonLab/emu/tree/emu-streaming

which was written to be part of the Jefferson Lab DAQ system. It has been modified
to pull out parts relevant to replaying pcap files taken from VTP/ROC output of
a running DAQ system in Hall B.

----------------------------

# **Java**

----------------------------

This software was developed using **Java 15** and most of the associated jar files were
also compiled with it.

The accompanying jar files contain the main emu jar along with those that support it
such as cMsg, evio, lz4 compression (not used but needed for compilation), and the
disruptor which is an ultra-fast ring buffer.

To compile:
  
    cd <main dir>
    ant


To get a list of options with ant, type **ant help**:

    help: 
        [echo] Usage: ant [ant options] <target1> [target2 | target3 | ...]
    
        [echo]      targets:
        [echo]      help        - print out usage
        [echo]      env         - print out build file variables' values
        [echo]      compile     - compile java files
        [echo]      clean       - remove class files
        [echo]      cleanall    - remove all generated files
        [echo]      jar         - compile and create jar file
        [echo]      install     - create jar file and install into 'prefix'
        [echo]                    if given on command line by -Dprefix=dir',
        [echo]                    else install into CODA if defined
        [echo]      uninstall   - remove jar file previously installed into 'prefix'
        [echo]                    if given on command line by -Dprefix=dir',
        [echo]                    else installed into CODA if defined
        [echo]      all         - clean, compile and create jar file
        [echo]      javadoc     - create javadoc documentation
        [echo]      developdoc  - create javadoc documentation for developer
        [echo]      undoc       - remove all javadoc documentation
        [echo]      prepare     - create necessary directories


To generate a new rtdp-0.9.jar file, execute

    ant jar
    
which will create the file and place it in build/lib.

Included in the java/jars subdirectory are all auxiliary jar files. These are installed when executing

    ant install

and uninstalled when executing
    
    ant uninstall


----------------------------

# **Running Executables**

----------------------------

To run the server which will accept incoming TCP connections
and the data which follows (and get the HELP output):

    cd <main dir>
    java -cp 'build/lib/rtdp-0.9.jar:java/jars/*' org.jlab.coda.rtdp.Aggregator -h
 
To run a single fake ROC in order to test the server, (and get the HELP output):

    cd <main dir>
    java -cp 'build/lib/rtdp-0.9.jar:java/jars/*' org.jlab.coda.rtdp.SimRoc -h
 
----------------------------

# **Copyright**

----------------------------

For any issues regarding use and copyright, read the [license](LICENSE.txt) file.

