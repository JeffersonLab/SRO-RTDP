//
//  Basic ZeoMQ client in C++
//  Connects REQ socket to tcp://localhost:5555
//  Sends "events" to server
//
#include <unistd.h>
#include <zmq.hpp>
#include <string>
#include <iostream>
#include <netinet/in.h>
#include "buffer_packet.hh"

using namespace std;
using namespace zmq;
using namespace chrono;

#define DBG 0	//print extra verbosity apart from -v switch
    
void   Usage()
{
    char usage_str[] =
        "\nUsage: \n\
        -h help  \n\
        -i destination address (string)  \n\
        -p destination port  \n\
        -c event count (10) \n\
        -x run in sim mode  \n\
        -s event size (MB) (10) \n\n";

    cout << "[zmq-event-clnt]: " << usage_str;
    cout << "[zmq-event-clnt]: Required: -i -p\n";
}


int main (int argc, char *argv[])
{
    int optc;

    bool     psdI=false, psdP=false, psdC=false, psdS=false, psdX=false;
    char     dst_ip[INET6_ADDRSTRLEN] = "127.0.0.1";	// target ip
    uint16_t dst_prt   = 0;  // target port
    uint16_t evnt_cnt  = 10; // event count
    uint16_t evnt_szMB = 10; // event size (MB)

    while ((optc = getopt(argc, argv, "hi:p:c:s:x")) != -1)
    {
        switch (optc)
        {
        case 'h':
            Usage();
            exit(1);
        case 'i':
            strcpy(dst_ip, (const char *) optarg) ;
            psdI = true;
            if(DBG) cout << " -i " << dst_ip;
            break;
        case 'p':
            dst_prt = (uint16_t) atoi((const char *) optarg) ;
            psdP = true;
            if(DBG) cout << " -p " << dst_prt;
            break;
        case 'c':
            evnt_cnt = (uint16_t) atoi((const char *) optarg) ;
            psdC = true;
            if(DBG) cout << " -c " << evnt_cnt;
            break;
        case 's':
            evnt_szMB = (uint16_t) atoi((const char *) optarg) ;
            psdS = true;
            if(DBG) cout << " -s " << evnt_szMB;
            break;
        case 'x':
            psdX = true;
            if(DBG) cout << " -x ";
            break;
        case '?':
            cout << "[zmq-event-clnt]: Unrecognised option: " << optopt;
            Usage();
            exit(1);
        }
    }

    if(!(psdI && psdP)) {Usage(); exit(1);}
    //  Prepare our context and socket
    zmq::context_t context (1);
    zmq::socket_t socket (context, zmq::socket_type::push);

    std::cout << "[zmq-event-clnt]: Connecting to server..." << std::endl;
    socket.connect (string("tcp://") + dst_ip + ':' +  to_string(dst_prt));

    BufferPacket pkt;
    pkt.size = evnt_szMB*1024*1024;
    pkt.timestamp = std::chrono::duration<double>(
        std::chrono::system_clock::now().time_since_epoch()).count();
    pkt.stream_id = 99;
    //  Do evnt_cnt requests
    for (int request_nbr = 0; request_nbr != evnt_cnt; request_nbr++) {
        std::cout << "[zmq-event-clnt]: Sending  " << request_nbr << "..." << std::endl;
        if(psdX) {
            socket.send(pkt.to_message(), zmq::send_flags::none);
        } else {
	        // Send sized frame
            zmq::message_t request (pkt.size);
            socket.send (request, zmq::send_flags::none);
        }
        std::cout << "[zmq-event-clnt]: sent: size=" 
                  << (psdX?"(Spec'd) ":"(actual) ") << pkt.size << std::endl;
    }
    return 0;
}
