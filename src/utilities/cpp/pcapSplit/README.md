
## pcapSplit

This program will take a `.pcap` file as input and split it into
individual pcap files corresponding to the destination port of the
packet. A directory is automatically created to hold the split
files and those are automatically created as new ports are
encountered in the input file.

Build like this:
~~~bash
g++ -g -std=c++2a -o pcapSplit pcapSplit.cc -lpcap
~~~

Example:

~~~bash
> pcapSplit CLAS12_ECAL_PCAL_DC_2024-05-16_09-07-18.pcap

...

> ls CLAS12_ECAL_PCAL_DC_2024-05-16_09-07-18.pcap_split
port7001.pcap  port7003.pcap  port7005.pcap  port7007.pcap  port7009.pcap  port7011.pcap  port7013.pcap  port7015.pcap  port7017.pcap  port7019.pcap  port7021.pcap  port7023.pcap
port7002.pcap  port7004.pcap  port7006.pcap  port7008.pcap  port7010.pcap  port7012.pcap  port7014.pcap  port7016.pcap  port7018.pcap  port7020.pcap  port7022.pcap  port7024.pcap
~~~

