//
//  Basic ZeoMQ client in C++
//  Connects REQ socket to tcp://localhost:5555
//  Sends 10MB "event" to server, expects back
//
#include <unistd.h>
#include <zmq.hpp>
#include <string>
#include <iostream>
#include <netinet/in.h>

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
        -p destination port  \n\n";

    cout <<  usage_str;
}


int main (int argc, char *argv[])
{
    int optc;

    bool     psdI=false, psdP=false;
    char     dst_ip[INET6_ADDRSTRLEN] = "127.0.0.1";	// target ip
    uint16_t dst_prt = 0; // target port

    while ((optc = getopt(argc, argv, "hi:p:")) != -1)
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
        case '?':
            cout << "Unrecognised option: " << optopt;
            Usage();
            exit(1);
        }
    }

    if(!(psdI && psdP)) {Usage(); exit(1);}
    //  Prepare our context and socket
    zmq::context_t context (1);
    zmq::socket_t socket (context, zmq::socket_type::req);

    std::cout << "Connecting to server..." << std::endl;
    socket.connect (string("tcp://") + dst_ip + ':' +  to_string(dst_prt));

    //  Do 10 requests, waiting each time for a response
    for (int request_nbr = 0; request_nbr != 2; request_nbr++) {
	// Send 10MB "event"
        zmq::message_t request (10*1024*1024);
        std::cout << "Sending  " << request_nbr << "..." << std::endl;
        socket.send (request, zmq::send_flags::none);

        //  Get the reply.
        zmq::message_t reply;
        zmq::recv_result_t rtcd = socket.recv (reply, zmq::recv_flags::none);
        //std::cout << "Connect return code: " << rtcd << '\n';
        std::cout << "Received  " << request_nbr << "rtcd = " << rtcd.value() << " Actual reply: " << reply << std::endl;
    }
    return 0;
}
