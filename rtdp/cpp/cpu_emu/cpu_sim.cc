#define DBG 0	//print extra verbosity apart from -v switch
  
//
//  CPU Simulator for Real Time Development Program (RTDP)
//
#include <stdio.h> 
#include <stdlib.h> 
#include <string.h> 
#include <fstream>
#include <iostream>
#include <unistd.h>
#include <thread>
#include <math.h>
#include <vector>
#include <chrono>
#include <csignal>
#include <sys/time.h>
#include <algorithm>
#include <yaml.h>
#include <stack>
#include <map>
#include <zmq.hpp>
#include <netinet/in.h>
#include <new> // for bad_alloc
#include <cstdlib> // Required for exit()
#include <cmath> // Needed for round()
#include "buffer_packet.hh"
#include <random>
#include <cassert>

#ifdef __linux__
    #define HTONLL(x) ((1==htonl(1)) ? (x) : (((uint64_t)htonl((x) & 0xFFFFFFFFUL)) << 32) | htonl((uint32_t)((x) >> 32)))
    #define NTOHLL(x) ((1==ntohl(1)) ? (x) : (((uint64_t)ntohl((x) & 0xFFFFFFFFUL)) << 32) | ntohl((uint32_t)((x) >> 32)))
#endif

using namespace std;
using namespace zmq;
using namespace chrono;
  
// Power of ten scaling constants
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
        -b seconds thread latency per GB input \n\
        -f total frames sender will send  \n\
        -i destination address (string)  \n\
        -m thread memory footprint in GB  \n\
        -n out going NIC speed in Gbps  \n\
        -o output size in GB  \n\
        -p destination port (default = 8888)  \n\
        -r receive port (default = 8888)  \n\
        -v verbose = 0/1 (default = false = 0)  \n\
        -y yaml config file  \n\
        -z act as terminal node = 0/1 (default = false = 0)  \n\n";

    cout << "[cpu_sim]: " << usage_str;
}

map<string,string> mymap;

void parse_yaml(const char *filename, uint16_t tag, bool vrbs=false) {
    FILE *file = fopen(filename, "r");
    if (!file) {
        perror("Failed to open file");
        return;
    }

    yaml_parser_t parser;
    yaml_event_t event;

    if (!yaml_parser_initialize(&parser)) {
        cerr << "Failed to initialize parser! " << endl;
        fclose(file);
        return;
    }

    yaml_parser_set_input_file(&parser, file);
    stack<string> lbl_stk;
    string s, s1;

    vector<string> lbls;
    lbls.push_back("destination"); lbls.push_back("dst_port"); lbls.push_back("rcv_port");
    lbls.push_back("latency");
    lbls.push_back("mem_footprint"); lbls.push_back("output_size"); lbls.push_back("verbose");
    lbls.push_back("terminal"); lbls.push_back("out_nic"); lbls.push_back("frame_cnt");
    
    auto it = lbls.begin(); //a hack to get the type

    while (yaml_parser_parse(&parser, &event)) {
        switch (event.type) {
        case YAML_NO_EVENT:
            break;
        case YAML_STREAM_START_EVENT:
            if(DBG) cout << "[cpu_emu]: Stream started " << endl;
            break;
        case YAML_STREAM_END_EVENT:
            if(DBG) cout << "[cpu_emu]: Stream ended " << endl;
            break;
        case YAML_DOCUMENT_START_EVENT:
            if(DBG) cout << "[cpu_emu]: Document started " << endl;
            break;
        case YAML_DOCUMENT_END_EVENT:
            if(DBG) cout << "[cpu_emu]: Document ended " << endl;
            break;
        case YAML_MAPPING_START_EVENT:
            if(DBG) cout << "[cpu_emu]: Mapping started " << endl;
            break;
        case YAML_MAPPING_END_EVENT:
            if(DBG) cout << "[cpu_emu]: Mapping ended " << endl;
            break;
        case YAML_SEQUENCE_START_EVENT:
            if(DBG) cout << "[cpu_emu]: Sequence started " << endl;
            break;
        case YAML_SEQUENCE_END_EVENT:
            if(DBG) cout << "[cpu_emu]: Sequence ended " << endl;
            break;
        case YAML_SCALAR_EVENT:
            s = (const char*)event.data.scalar.value;
            it = find(lbls.begin(), lbls.end(), s);
            if (it != lbls.end()) {
                if(DBG) cout << "[cpu_sim " << tag << " ]: " << " Label: " << s << '\n';
                lbl_stk.push(s);
            } else {
                s1 = lbl_stk.top();
                if(DBG) cout << "[cpu_sim " << tag << " ]: " << " Label: " << s1 << " Datum: " << s << '\n';
                mymap[s1] = s;
                lbl_stk.pop();
            }
            break;
        default:
            if(DBG) cout << "[cpu_sim " << tag << " ]: " << " (Default)" << endl;
            break;
        }

        if(event.type == YAML_STREAM_END_EVENT) break;
        yaml_event_delete(&event);
    }
    if(DBG) cout << "[cpu_sim " << tag << " ]: " << " All done parsing, got this:" << endl;
    if(DBG) for (map<string,string>::iterator it=mymap.begin(); it!=mymap.end(); ++it)
        cout << it->first << " => " << it->second << '\n';
    
    yaml_parser_delete(&parser);
    fclose(file);
}
  
int main (int argc, char *argv[])
{ 
    int optc;

    bool     psdB=false, psdI=false, psdF=false, psdM=false, psdO=false, psdY=false;
    bool     psdP=false, psdR=false, psdV=false;
    bool     psdZ=false, trmnl=false, psdN=false;
    string   yfn = "cpu_sim.yaml";
    char     dst_ip[INET6_ADDRSTRLEN] = "127.0.0.1";	// target ip
    uint16_t rcv_prt = 8888;  // receive port default
    uint16_t dst_prt = 8888;  // target port default
    bool     vrbs    = false; // verbose ?
    // 500 seconds/(input GB) computational latency for 60kB CLAS12
    // 0.5 microseconds/byte
    // 0.5 seconds per megabyte
    double   cmpLt_s_GB     = 500;   // seconds/(input GB) computational latency
    double   cmpLt_uS_B     = 0.5;   // usec/(input B) computational latency
    double   cmpLt_S_MB      = 0.5;   // seconds/(input MB) computational latency
    double   mem_GB         = 10;    // thread memory footprint in GB
    double   otmem_GB       = 0.01;  // program output in GB
    double   outNicSpd_Gb_S = 10;    // outgoing NIC speed in Gbps
    uint16_t zed            = 0;     //terminal node ?
    uint64_t frame_cnt      = 0;     //total frames sender will send

    cout << fixed << setprecision(7);  // 6 decimal places            

    while ((optc = getopt(argc, argv, "hb:i:f:m:n:o:p:r:v:y:z:")) != -1)
    {
        switch (optc)
        {
        case 'h':
            Usage();
            exit(1);
        case 'b':
            cmpLt_s_GB = (double) atof((const char *) optarg) ;
            psdB = true;
            if(DBG) cout << " -b " << cmpLt_s_GB;
            break;
        case 'i':
            strcpy(dst_ip, (const char *) optarg) ;
            psdI = true;
            if(DBG) cout << " -i " << dst_ip;
            break;
        case 'f':
            frame_cnt = (uint64_t) atoi((const char *) optarg) ;
            psdF = true;
            if(DBG) cout << " -f " << frame_cnt;
            break;
        case 'm':
            mem_GB = (double) atof((const char *) optarg) ;
            psdM = true;
            if(DBG) cout << " -m " << mem_GB;
            break;
        case 'n':
            outNicSpd_Gb_S = (double) atof((const char *) optarg) ;
            psdN = true;
            if(DBG) cout << " -n " << outNicSpd_Gb_S;
            break;
        case 'o':
            otmem_GB = (double) atof((const char *) optarg) ;
            psdO = true;
            if(DBG) cout << " -o " << otmem_GB;
            break;
        case 'p':
            dst_prt = (uint16_t) atoi((const char *) optarg) ;
            psdP = true;
            if(DBG) cout << " -p " << dst_prt;
            break;
        case 'r':
            rcv_prt = (uint16_t) atoi((const char *) optarg) ;
            psdR = true;
            if(DBG) cout << " -r " << rcv_prt;
            break;
        case 'v':
            vrbs = (bool) atoi((const char *) optarg) ;
            psdV = true;
            if(DBG) cout << " -v " << vrbs;
            break;
        case 'y':
            yfn = (const char *) optarg ;
            psdY = true;
            if(DBG) cout << " -y " << yfn;
            break;
        case 'z':
            psdZ = true;
            zed = (uint16_t) atoi((const char *) optarg) ;
            trmnl = zed==1?true:false;
            if(DBG) cout << " -z " << zed;
            break;
        case '?':
            cout << "[cpu_sim " << rcv_prt << "]: " << " Unrecognised option: " << optopt;
            Usage();
            exit(1);
        }
    }

    if(DBG) cout << endl;

    if (psdY) {//parse the yaml file if given        
        parse_yaml(yfn.c_str(), rcv_prt, vrbs);
        //cmd line parms overide yaml file settings (which are otherwise in the map)
        if(!psdB) cmpLt_s_GB = stof(mymap["latency"]);
        if(!psdI) strcpy(dst_ip, mymap["destination"].c_str());
        if(!psdM) mem_GB    = stof(mymap["mem_footprint"]);
        if(!psdN) outNicSpd_Gb_S= stof(mymap["out_nic"]);
        if(!psdO) otmem_GB  = stof(mymap["output_size"]);
        if(!psdP) dst_prt  = stoi(mymap["dst_port"]);
        if(!psdR) rcv_prt  = stoi(mymap["rcv_port"]);
        if(!psdV) vrbs     = stoi(mymap["verbose"]);
        if(!trmnl) trmnl   = stoi(mymap["terminal"]) == 1;
        if(!psdF) frame_cnt= stoi(mymap["frame_cnt"]) == 1;
    }    
    ////////
    if(vrbs) cout << "[cpu_sim "   << rcv_prt                     << " ]: "
                << " Operating with yaml = " << (psdY?yfn:"N/A")
                << "\tframe_cnt = " << frame_cnt
                << "\tcmpLt_s_GB = " << cmpLt_s_GB
                << "\tdst_ip = "   << (trmnl?"N/A":string(dst_ip)) << "\tmem_GB = "       << mem_GB
                << "\totmem_GB = "  << otmem_GB                     << "\tdst_prt = "     << (trmnl?"N/A":to_string(dst_prt))
                << "\trcv_prt = "  << rcv_prt
                << "\toutNicSpd_Gb_S  = "  << outNicSpd_Gb_S
                << "\tverbose = "   << vrbs << "\tyfn = " << (psdY?yfn:"N/A")
                << "\tterminal = " << trmnl << '\n';

    // RNG for latency variance generation using a Gaussian (normal) distribution to generate a scaling factor 
    // centered around 1.0, with a standard deviation chosen so that ~99.7% of values fall 
    // in the 70% to 130% range (i.e., ±3σ ≈ 30%):

    static random_device rd;
    static mt19937 gen(rd());
    
    // Mean = 1.0, Std Dev = 0.1 gives 99.7% of samples in [0.7, 1.3]
    static normal_distribution<> sd_10pcnt(1.0, 0.1);

    //  Prepare our receiving rcv_cntxt and socket
    context_t rcv_cntxt(1);
    context_t dst_cntxt(1);

    socket_t rcv_sckt(rcv_cntxt, socket_type::rep);
    rcv_sckt.set(zmq::sockopt::rcvhwm, int(0)); // queue length

    // Subscribe to all messages (empty topic)
    //rcv_sckt.set(zmq::sockopt::subscribe, "");

    socket_t dst_sckt(dst_cntxt, socket_type::req);
    dst_sckt.set(zmq::sockopt::sndhwm, int(0)); // queue length
    rcv_sckt.bind(string("tcp://*:") + to_string(rcv_prt));
    if(vrbs) cout << "[cpu_sim " << rcv_prt << "]: " << " Connecting to receiver " + string("tcp://*:") + to_string(rcv_prt) << endl;
    
    if(!trmnl) {
        //  Prepare our destination socket
        if(vrbs) cout << "[cpu_sim " << rcv_prt << "]: " << " Connecting to destination " + string("tcp://") + dst_ip + ':' +  to_string(dst_prt) << endl;
        dst_sckt.connect (string("tcp://") + dst_ip + ':' +  to_string(dst_prt));
    }
    uint32_t request_nbr = 0;
    double   mnBfSz_b   = 0; //mean receive Size (bits)
    uint64_t bufSiz_b   = 0; //bits
    uint64_t mxBufSiz_b = 0; //bits -used to estimate frame rate capacity
    uint32_t stream_id  = 0;
    uint32_t frame_num  = 0;
    float    tsr_uS     = 0; // sim clock from sender
    float    tsc_uS     = 0; // computational latency
    float    mxTsc_uS   = 0; // computational latency
    float    mxTsn_uS   = 0; // computational latency
    float    tsn_uS     = 0; // outbound network latency
    
    string ack = "ACK";
    zmq::message_t ack_msg(ack.begin(), ack.end());  // ensures correct sizing and content

    while (frame_num < frame_cnt) {
        //if(vrbs) cout << "[cpu_sim " << rcv_prt << "]: " << " Setting up request message ..." << endl;
        message_t request;

        //  Wait for next request from client
        if(vrbs) cout << tsr_uS << " [cpu_sim " << rcv_prt << "]: " << " Waiting for source ..." << endl;
        
        recv_result_t rtcd;
        
        {
            rtcd = rcv_sckt.recv (request, recv_flags::none);
            message_t reply (3);
            memcpy (reply.data (), "ACK", 3);
            rcv_sckt.send (reply, send_flags::none);
            //rcv_sckt.send(ack_msg, zmq::send_flags::none);
        }
        
        BufferPacket pkt = BufferPacket::from_message(request);
        bufSiz_b    = pkt.size; //bits
        mxBufSiz_b = max(mxBufSiz_b,bufSiz_b);
        stream_id = pkt.stream_id;
        frame_num = pkt.frame_num;
        //reqd transmission timespan in usec    
        {
            // Clamp to [1.0, 1.3] to enforce bounds
            auto lb_uS = 1e-3*double(bufSiz_b)/outNicSpd_Gb_S; //usec
            auto x = 1; //clamp(sd_10pcnt(gen), 1.0, 1.3);
            tsn_uS = lb_uS*x; //usec
            mxTsn_uS = max(mxTsn_uS,tsn_uS);
            //temporarily advance the sim clock for ready to receive check
            float tsr1_uS = pkt.timestamp + tsn_uS;
            if(vrbs) cout << tsr1_uS << " [cpu_sim " << rcv_prt << "]: " << " recd " << frame_num << endl;
            if(DBG) cout << tsr1_uS + 0.1 << " [cpu_sim " << rcv_prt << "]: " << " Received request "
                      << frame_num << " from port " + string("tcp://") + dst_ip + ':' +  to_string(rcv_prt)
                      << " rtcd = " << int(rtcd.value()) << " from client" << endl;
                      
            if(vrbs) cout << tsr1_uS + 0.2 << " [cpu_sim " << rcv_prt << "]: " << " frame size = "
                      << "(Spec'd) " << bufSiz_b << " bits " << bufSiz_b*1e-9 << " Gb "
                      << " from client " << "ts = " << tsr1_uS + 0.3 << " (" << frame_num << ')' << endl;
        
            if(DBG) cout << tsr1_uS + 0.01 << " [cpu_sim " << rcv_prt << "]: Sending ACK  (" << frame_num << ')' << endl;
            if(vrbs) cout << tsr1_uS + 0.001 << " [cpu_sim " << rcv_prt << "]: Calculating tsn_uS as " << tsn_uS
                                                << " for bufSiz_b " << bufSiz_b << " outNicSpd_Gb_S " << outNicSpd_Gb_S
                                                << " (" << frame_num << ')' << " using x " << x << " lb_uS " << lb_uS << endl;
            if(tsr_uS>tsr1_uS) {
                if(vrbs) {cout << tsr1_uS + 0.3 << " [cpu_sim " << rcv_prt << "]:  dropped (" << frame_num << ')'  
                               << " request_nbr " << request_nbr << "(tsr_uS,tsr1_uS) (" << tsr_uS << ',' << tsr1_uS << ')' << endl;}
                if(frame_num != 0) {
                    if(vrbs) cout << tsr_uS - 0.01 << " [cpu_sim " << rcv_prt << "]: " << " going to wait_for_frame " << endl; 
                    continue;
                }
            } else {
                tsr_uS = tsr1_uS + 1; // advance the clock
            }
        }
        
        request_nbr++;
        
        //  Do some 'work'
        // simulate load on system for ensuing work
        {
            //reqd computational timespan in usec with 10% std dev
            auto x = clamp(sd_10pcnt(gen), 0.7, 1.3);
            tsc_uS = cmpLt_uS_B*(float(bufSiz_b)/8)*x; //usec
            mxTsc_uS = max(mxTsc_uS,tsc_uS+10); //pad with non-tsc_uS
            tsr_uS += tsc_uS; // advance the clock
            if(vrbs) cout << tsr_uS << " [cpu_sim " << rcv_prt << "]:  added tsc_uS " << tsc_uS << " (" << frame_num << ')' 
                        << " for bufSiz_b " << bufSiz_b << " cmpLt_uS_B " << cmpLt_uS_B << " x " << x << endl;
        }

        if(!trmnl) {
            if(DBG) cout << tsr_uS  << " [cpu_sim " << rcv_prt << "]: " << " Forwarding "
                          << " request " << frame_num << " from port " + string("tcp://") + dst_ip + ':' +  to_string(rcv_prt)
                          << " to port " + string("tcp://") + dst_ip + ':' +  to_string(dst_prt) << " (" << frame_num << ')' << endl;
            //forward to next hop
            // Send a message to the destination
            size_t outSz_b = 8*otmem_GB*1.024*1.024*1.024*1e9; //output size in bits

            {
                send_result_t sr;
                BufferPacket pkt;
                //represents harvested data
                auto x = clamp(sd_10pcnt(gen), 0.7, 1.3);  //+/- 3 sd
                pkt.size = outSz_b*x;
                pkt.timestamp = tsr_uS;
                pkt.stream_id = stream_id;
                pkt.frame_num = frame_num;
	            // Send "frame" spec
                if(vrbs) cout << tsr_uS << " [cpu_sim " << rcv_prt << "]:  Sending frame size = " << outSz_b*x << " (" 
                              << frame_num << ')' << " to " << dst_prt << " at " << tsr_uS << endl;
                sr = dst_sckt.send(pkt.to_message(), zmq::send_flags::none);

                // Receive the reply from the destination
                //  Get the reply.
                message_t reply;
                if(DBG) cout << tsr_uS  << " [cpu_sim " << rcv_prt << "]: Waiting for destination ACK (" << frame_num << ')' << endl;
                recv_result_t rtcd = dst_sckt.recv (reply, recv_flags::none);
                if(DBG) cout << tsr_uS  << " [cpu_sim " << rcv_prt << "]: Destination Actual reply (" << frame_num << ") " 
                              << reply << " With rtcd = " << rtcd.value() << endl;

                if(DBG) cout << tsr_uS  << " [cpu_sim " << rcv_prt << "]: " << " output Num written  (" << frame_num << ") " << sr.value()  << endl;
                if(sr.value() != pkt.PACKET_SIZE) cout << tsr_uS  << " [cpu_sim " << rcv_prt << "]: " << " Destination data incorrect size (" << frame_num << ") " << endl;
            }        
        }
        if(vrbs) cout << tsr_uS + 1 << " [cpu_sim " << rcv_prt << "]:  done (" << frame_num << ')' << endl;
        //cout  << tsr_uS << " [cpu_sim " << rcv_prt << "]:  clock advanced ...(" << frame_num << ')' <<  " request_nbr " << request_nbr  << endl;

        mnBfSz_b = (request_nbr-1)*mnBfSz_b/request_nbr + bufSiz_b/request_nbr; //incrementally update mean receive size
        cout  << tsr_uS + 2 << " [cpu_sim " << rcv_prt << "]:  computing stats ...(" << frame_num << ')' << endl;

        if(vrbs) cout << tsr_uS + 3 << " [cpu_sim " << rcv_prt << "]: " << " Computed latencies: tsc_uS = " << tsc_uS << " tsn_uS = " << tsn_uS 
                           << " (" << frame_num << ") mxTsc_uS = " << mxTsc_uS << endl;
        if(vrbs) cout << tsr_uS + 4 << " [cpu_sim " << rcv_prt << "]: " << " Measured frame rate " << float(1)/(1e-6*float(mxTsc_uS+mxTsn_uS)) 
                           << " frame Hz." << " for " << frame_num << " frames" << endl;
        if(vrbs) cout << tsr_uS + 5 << " [cpu_sim " << rcv_prt << "]: " << " Measured bit rate " << 1e-6*float(bufSiz_b)/(1e-6*float(mxTsc_uS+mxTsn_uS)) 
                           << " MHz mnBfSz_b " << mnBfSz_b << " (" << frame_num << ')' << endl;
        if(vrbs) cout << tsr_uS + 6 << " [cpu_sim " << rcv_prt << "]:  Missed frames: " << frame_num-request_nbr << endl;
        if(vrbs) cout << tsr_uS + 7 << " [cpu_sim " << rcv_prt << "]:  Missed frame ratio: " << float(frame_num-request_nbr)/float(frame_num) 
                      << " frame_num " << frame_num  << " request_nbr " << request_nbr << endl;
        cout  << tsr_uS + 8 << " [cpu_sim " << rcv_prt << "]:  stats computed ..." << endl;
        tsr_uS += 10; // advance the clock for non-tsc_uS
    } //main loop
    cout  << tsr_uS + 11 << " [cpu_sim " << rcv_prt << "]:  " << (trmnl?"Terminal":"Non Terminal") << " exiting: mxTsc_uS = " << mxTsc_uS << endl;
    cout.flush();
    cerr.flush();
    return 0;
}
