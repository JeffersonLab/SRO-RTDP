# FABRIC Example

Here is an example using the FABRIC Testbed (https://portal.fabric-testbed.net) to distribute events from a computer at CERN to several different computers running `eicrecon` at sites spread across the US. The multi site example utlizes 4 instance of `eicrecon` running at sites in:

* New York
* Washington D.C.
* Atlanta
* Kansas City
* Dallas
* Los Angeles
* Salt Lake City
* Seattle

Thus, a total of **32 consumers** in the **U.S.** all pulling from a single event source at **CERN**.

There are two examples:

~~~
  2024.04.14.podiotcp-multi.ipynb  - Multiple sites example (use this one)
  2024.04.13.podiotcp.ipynb  - Preliminary example with just one worker site 
~~~

The examples are in the form of Jupyter notebooks, but they rely on multiple shell scripts that are also in this directory. These will need to be in the same directory on the FABRIC home directory that the notebook is run from so they can be uploaded to the remote sites.

These use the eic-shell Docker container on the remote site (eicweb/jug_xl:nightly). They also require a special version of podio which then requires JANA2 and subsequently EICrecon to be rebuilt. This is all handled by the build_all.sh script which is run automatically in the notebook.

Two other pieces are needed, both coming from the SRO-RTDP repository: 

The `podio2tcp` tool reads ePIC events from a file and sends them via TCP. This is the server program that runs at CERN. The eicrecon instances will connect to this and pull blocks of events as needed

The `podiostream` plugin is used with eicrecon to provide a JEventSource that is capable of reading from TCP and supplying events to the JANA2 system.
