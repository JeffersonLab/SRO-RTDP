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

A good place to get Java 15 on the ejfat nodes is:

    /daqfs/java/jdk-15.0.2/bin/java

The accompanying jar files contain the main emu jar along with those that support it
such as cMsg, evio, lz4 compression (not used but needed for compilation), and the
disruptor which is an ultra-fast ring buffer.

To compile:
  
    cd <top level dir>
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


To generate a new rtdp-0.9.jar file, from the top directory execute

    ant jar
    
which will create the file and place it in build/lib.

Included in the java/jars subdirectory are all auxiliary jar files. These are installed when executing

    ant install

and uninstalled when executing
    
    ant uninstall


----------------------------

# **Running the Aggregator**

----------------------------

Run the server which will accept incoming TCP connections
and the data which follows. Use -h to see all options:

    cd <top level dir>
    java -cp 'build/lib/rtdp-0.9.jar:rtdp/java/jars/*' org.jlab.epsci.rtdp.Aggregator -h
 
To run this in a way which will accept run at default port and accept a
given number of connections:

    java -cp 'build/lib/rtdp-0.9.jar:rtdp/java/jars/*' org.jlab.epsci.rtdp.Aggregator -c <# clients> 


----------------------------

# **Running other executables**

----------------------------


To test the server, run the ExampleConnector, (use the HELP output):

    java -cp 'build/lib/rtdp-0.9.jar:rtdp/java/jars/*' org.jlab.epsci.rtdp.ExampleConnector -h

The above program produces evio 4 output which is in proper streaming format. By adding the
"-control" command line option, all CODA control events will appear in the proper order as
well as the specified number of time slice buffers sent.


To run a simulated ROC which produces evio 6 data, run

    java -cp 'build/lib/rtdp-0.9.jar:rtdp/java/jars/*' org.jlab.epsci.rtdp.FakeRoc -h

The above program will cause the Aggregator to fail as there is a bug in the evio lib
which makes parsing throw an exception.

----------------------------

# **Copyright**

----------------------------

For any issues regarding use and copyright, read the [license](LICENSE.txt) file.

