//
//  Basic ZeoMQ client in C++
//  Connects PUB socket to tcp://localhost:5555
//  Sends "events" to server
//
#include <unistd.h>
#include <zmq.hpp>
#include <string>
#include <iostream>
#include <netinet/in.h>
#include "buffer_packet_emu.hh"
#include <random>
#include <cassert>
#include <thread>

using namespace std;
using namespace zmq;
using namespace chrono;

#define DBG 0	//print extra verbosity apart from -v switch
    
void   Usage()
{
    char usage_str[] =
        "\nUsage: \n\
        -h help  \n\
        -a stream/channel id (0) \n\
        -p publication port (7000) \n\
        -r bit rate to send (Gbps) (1)\n\
        -c event count (1e3) \n\
        -v verbose = 0/1 (0)  \n\
        -s event size (MB) (1) \n\n";

    cout << " [zmq-event-clnt]: " << usage_str;
}


int main (int argc, char *argv[])
{
    int optc;

    uint16_t pub_prt       = 0;     // target port
    uint16_t stream_id     = 0;  // stream or channel id
    uint64_t evnt_cnt      = 1e3;    // event count
    float    evnt_szMB     = 1;    // event size (MB)
    float    bit_rate_gbps = 1; // sending bit rate in Gbps
    bool     vrbs          = false; // verbose ?

    while ((optc = getopt(argc, argv, "a:hp:c:r:s:v:")) != -1)
    {
        switch (optc)
        {
        case 'h':
            Usage();
            exit(1);
        case 'a':
            stream_id = (uint16_t) atoi((const char *) optarg) ;
            if(DBG) cout << " -a " << stream_id;
            break;
        case 'p':
            pub_prt = (uint16_t) atoi((const char *) optarg) ;
            if(DBG) cout << " -p " << pub_prt;
            break;
        case 'c':
            evnt_cnt = (uint16_t) atoi((const char *) optarg) ;
            if(DBG) cout << " -c " << evnt_cnt;
            break;
        case 'r':
            bit_rate_gbps = (uint16_t) atof((const char *) optarg) ;
            if(DBG) cout << " -r " << bit_rate_gbps;
            break;
        case 's':
            evnt_szMB = (uint16_t) atof((const char *) optarg) ;
            if(DBG) cout << " -s " << evnt_szMB;
            break;
        case 'v':
            vrbs = (bool) atoi((const char *) optarg) ;
            if(DBG) cout << " -v " << vrbs << endl;
            break;
         case '?':
            cout << " [zmq-event-clnt]: Unrecognised option: " << optopt;
            Usage();
            exit(1);
        }
    }

    // RNG for latency variance generation using a Gaussian (normal) distribution to generate a scaling factor 
    // centered around 1.0, with a standard deviation chosen so that ~99.7% of values fall 
    // in the 70% to 130% range (i.e., ±3σ ≈ 30%):

    static std::random_device rd;
    static std::mt19937 gen(rd());

    // Mean = 1.0, Std Dev = 0.1 gives 99.7% of samples in [0.7, 1.3]
    static std::normal_distribution<> sd_10pcnt(1.0, 0.1);


    //  Prepare our publication context and socket
    if(vrbs) cout << "[zmq-event-clnt-emu " << pub_prt << "]: " << " Publishing on port " << to_string(pub_prt) << endl;
    context_t pub_cntxt(1);
    socket_t pub_sckt(pub_cntxt, socket_type::pub);
    pub_sckt.bind(string("tcp://*:") + to_string(pub_prt));
    pub_sckt.set(zmq::sockopt::sndhwm, int(0)); // queue length

    std::this_thread::sleep_for(std::chrono::seconds(1)); //  # Give receiver time to bind

    auto now = high_resolution_clock::now();
    auto us = duration_cast<microseconds>(now.time_since_epoch());
    uint64_t tsr_base    = us.count(); // base system hi-res clock in microseconds since epoch

    //  Do evnt_cnt requests
    for (uint64_t frame_num = 1; frame_num <= evnt_cnt; frame_num++) {
        std::cout << " [zmq-event-clnt]: Sending  " << frame_num << "..." << std::endl;
        uint64_t tsr       =   us.count()-tsr_base;  //zero based clock usecs: system hi-res clock in microseconds since tsr_base

        send_result_t sr;

        // Send  "frame"
        //represents harvested data
        auto x = std::clamp(sd_10pcnt(gen), 0.7, 1.3);  //+/- 3 sd
        std::vector<uint8_t> payload(size_t(evnt_szMB*x));  //represents harvested data
        if(DBG) cout << tsr+1 << " [zmq-event-clnt-emu " << pub_prt << "]: " << "serializing packet for frame_num " << frame_num << endl;
        auto data = serialize_packet(tsr, pub_prt, 8*payload.size(), tsr, stream_id, frame_num, payload);
        if(DBG) cout << tsr+2 << " [zmq-event-clnt-emu " << pub_prt << "]: " << "serializing success for frame_num " << frame_num << endl;
        zmq::message_t message(data.size());
        std::memcpy(message.data(), data.data(), data.size());
        sr = pub_sckt.send(message, zmq::send_flags::none);
        if (!sr) std::cerr << tsr << " [zmq-event-clnt-emu " << pub_prt << "]: Failed to send" << endl;
        if (vrbs && sr.has_value()) std::cout << tsr << " [zmq-event-clnt-emu " << pub_prt << "]: Bytes sent = " << sr.value() << endl;

        if(vrbs) cout << tsr+3 << " [zmq-event-clnt-emu " << pub_prt << "]:  Sending frame size = " << payload.size() << " (" 
                      << frame_num << ')' << " to " << pub_prt << " at " << tsr << " with code " << endl;
        if(DBG) cout << tsr+4 << "[zmq-event-clnt-emu " << pub_prt << "]: output Num written (" << frame_num << ") "  
                     << sr.value() << " (" << frame_num << ')' << endl;
        if(sr.value() != HEADER_SIZE + payload.size()) cout << tsr+3 << "[zmq-event-clnt-emu " << pub_prt << "]:  publication data incorrect size(" << frame_num << ") "  << endl;


        std::cout << tsr+5 << " [zmq-event-clnt-emu " << pub_prt << "]: sent: size=" 
                  << HEADER_SIZE + payload.size() << std::endl;

        float rate_sleep = 1e-9*payload.size() / bit_rate_gbps;  // in seconds
        auto cms = chrono::microseconds(size_t(round(1e6*rate_sleep))); //reqd timespan in microseconds
        this_thread::sleep_for(cms);

    }
    return 0;
}
