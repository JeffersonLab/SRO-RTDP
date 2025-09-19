//
//  Basic ZeoMQ client in C++
//  Sends frames to subscriber
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

//scaling constants
const float  B_b   = 1e1;
const float  b_B   = 1/B_b;
const float  G_1   = 1e9;
const float  one_G = 1/G_1;
const float  G_K   = 1e6;
const float  K_G   = 1/G_K;
const float  G_M   = 1e3;
const float  M_G   = 1/G_M;
const float  K_1   = 1e3;
const float  one_K = 1/K_1;
const float  M_1   = 1e6;
const float  one_M = 1/M_1;
const float  m_1   = 1e-3;
const float  one_m = 1/m_1;
const float  m_u   = 1e3 ;
const float  u_m   = 1/m_u;
const float  u_1   = 1e-6;
const float  one_u = 1/u_1;
const float  n_1   = 1e-9;
const float  one_n = 1/n_1;
const float  n_m   = 1e-6;
const float  m_n   = 1/n_m;

const size_t sz1K   = 1024;
const size_t sz1M   = sz1K*sz1K;
const size_t sz1G   = sz1M*sz1K;
    
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

    cout << " [emulate_stream]: " << usage_str;
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

    cout << std::fixed << std::setprecision(1);

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
            bit_rate_gbps = (float) atof((const char *) optarg) ;
            if(DBG) cout << " -r " << bit_rate_gbps;
            break;
        case 's':
            evnt_szMB = (float) atof((const char *) optarg) ;
            if(DBG) cout << " -s " << evnt_szMB;
            break;
        case 'v':
            vrbs = (bool) atoi((const char *) optarg) ;
            if(DBG) cout << " -v " << vrbs << endl;
            break;
         case '?':
            cout << " [zmq-event-emu-clnt]: Unrecognised option: " << optopt;
            Usage();
            exit(1);
        }
    }

    // RNG for latency variance generation using a Gaussian (normal) distribution to generate a scaling factor 
    // centered around 1.0, with a standard deviation chosen so that ~99.7% of values fall 
    // in the 70% to 130% range (i.e., ±3σ ≈ 30%):

    static random_device rd;
    static mt19937 gen(rd());

    // Mean = 1.0, Std Dev = 0.1 gives 99.7% of samples in [0.7, 1.3]
    static normal_distribution<> nd_10pcnt(1.0, 0.1);

    //  Prepare our publication context and socket
    if(vrbs) cout << "[zmq-event-emu-clnt " << pub_prt << "]" << endl;
    if(vrbs) cout << "[emulate_sender-zmq " << pub_prt << "]: Publishing on port " << to_string(pub_prt) << endl;
    context_t pub_cntxt(1);
    socket_t pub_sckt(pub_cntxt, socket_type::pub);
    pub_sckt.bind(string("tcp://*:") + to_string(pub_prt));
    pub_sckt.set(zmq::sockopt::sndhwm, int(0)); // queue length

    this_thread::sleep_for(chrono::seconds(1)); //  # Give receiver time to bind

    auto now_hrc   = high_resolution_clock::now();
    auto clk_uSd   = duration_cast<microseconds>(now_hrc.time_since_epoch());
    uint64_t start_uS  = clk_uSd.count();
    uint64_t now_uS = start_uS;
    if(vrbs) cout << "[emulate_sender-zmq " << pub_prt << "]: start_uS " << start_uS << endl;

    double   mnBfSz_B    = 0; //mean receive Size bytes

    //  Do evnt_cnt requests
    for (uint64_t frame_num = 1; frame_num <= evnt_cnt; frame_num++) {
        //cout << " [emulate_stream]: Sending  " << frame_num << "..." << endl;

        send_result_t sr;

        // Send  "frame"
        auto x = clamp(nd_10pcnt(gen), 0.7, 1.3);  //+/- 3 sd
        vector<uint8_t> payload(size_t(M_1*evnt_szMB*x));
        
        if(DBG) cout << now_uS+1 << " [emulate_stream:] serializing packet for frame_num " << frame_num << endl;
        auto data = serialize_packet(now_uS, pub_prt, payload.size(), now_uS, stream_id, frame_num, payload);
        if(DBG) cout << now_uS+2 << " [emulate_stream:] serializing success for frame_num " << frame_num << endl;
        zmq::message_t message(data.size());
        memcpy(message.data(), data.data(), data.size());
        sr = pub_sckt.send(message, zmq::send_flags::none);
        if (!sr) {cerr << now_uS << " [emulate_stream:] Failed to send" << endl; exit(1);}
        
        float bufSiz_B = sr.value();
        //if (vrbs && sr.has_value()) cout << now_uS << " [emulate_stream:] Sending frame size = " << bufSiz_B << " frame_num = " << frame_num << endl;

        if(vrbs) cout << now_uS+3 << " [emulate_stream:] Sending frame size = " << payload.size() << " (" 
                      << frame_num << ')' << " to " << pub_prt << " at " << now_uS << " with code " << endl;
        if(DBG) cout << now_uS+4 << " [emulate_stream:] output Num written (" << frame_num << ") "  
                     << bufSiz_B << " (" << frame_num << ')' << endl;
        if(bufSiz_B != HEADER_SIZE + payload.size()) cout << now_uS+3 << " [emulate_stream:] data incorrect size(" << frame_num << ") "  << endl;


        if(vrbs) cout << now_uS+5 << " [emulate_stream:] sent: size=" 
                  << HEADER_SIZE + payload.size() << endl;

        float rate_sleep_S = one_G*payload.size()*B_b / bit_rate_gbps;  // in seconds
        auto cms = chrono::microseconds(size_t(round(one_u*rate_sleep_S))); //reqd timespan in microseconds
        if(vrbs) cout << now_uS+3 << " [emulate_stream:] Rate sleep for " << rate_sleep_S << " S"  
                      << " Payload size = " << payload.size() << " bit rate Mbps " << G_M*bit_rate_gbps << endl;

        this_thread::sleep_for(cms);

        now_hrc = high_resolution_clock::now();
        auto clk_uSd        = duration_cast<microseconds>(now_hrc.time_since_epoch());
        //if(vrbs) cout << now_uS << " [emulate_stream:] Updating clock from " << now_uS << " to ";
        now_uS = clk_uSd.count();
        if(vrbs) cout << now_uS << " [emulate_stream:]  " << now_uS << endl;
        
        if(vrbs) cout << now_uS + 6 << " [emulate_stream:] Estimated frame rate (Hz): "
                           << float(frame_num)/(float(now_uS-start_uS)*u_1) 
                           << " frame_num " << frame_num << " elpsd_tm_uS " << now_uS-start_uS << endl;

        mnBfSz_B = (frame_num-1)*mnBfSz_B/frame_num + bufSiz_B/frame_num; //incrementally update mean receive size
        if(vrbs) cout << now_uS + 7 << " [emulate_stream:] Estimated bit rate (Gbps): " 
                           << float(frame_num*mnBfSz_B*B_b*one_G)/(float(now_uS-start_uS)*u_1)
                           << " frame_num " << frame_num << " elpsd_tm_uS " << now_uS-start_uS << endl;
        if(vrbs) cout << now_uS + 7 << " [emulate_stream:] Estimated bit rate (bps): " 
                           << float(frame_num*mnBfSz_B*B_b)/(float(now_uS-start_uS)*u_1)
                           << " frame_num " << frame_num << " elpsd_tm_uS " << now_uS-start_uS << endl;

    }
    return 0;
}
