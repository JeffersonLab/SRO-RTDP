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
#include <new> // for std::bad_alloc
#include <cstdlib> // Required for exit()
#include <cmath> // Needed for round()
#include "buffer_packet.hh"
#include <random>

#ifdef __linux__
    #define HTONLL(x) ((1==htonl(1)) ? (x) : (((uint64_t)htonl((x) & 0xFFFFFFFFUL)) << 32) | htonl((uint32_t)((x) >> 32)))
    #define NTOHLL(x) ((1==ntohl(1)) ? (x) : (((uint64_t)ntohl((x) & 0xFFFFFFFFUL)) << 32) | ntohl((uint32_t)((x) >> 32)))
#endif

using namespace std;
using namespace zmq;
using namespace chrono;

#define DBG 1	//print extra verbosity apart from -v switch
  
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
        fprintf(stderr, "Failed to initialize parser!\n");
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
            if(0) printf("[cpu_sim]: Stream started\n");
            break;
        case YAML_STREAM_END_EVENT:
            if(0) printf("[cpu_sim]: Stream ended\n");
            break;
        case YAML_DOCUMENT_START_EVENT:
            if(0) printf("[cpu_sim]: Document started\n");
            break;
        case YAML_DOCUMENT_END_EVENT:
            if(0) printf("[cpu_sim]: Document ended\n");
            break;
        case YAML_MAPPING_START_EVENT:
            if(0) printf("[cpu_sim]: Mapping started\n");
            break;
        case YAML_MAPPING_END_EVENT:
            if(0) printf("[cpu_sim]: Mapping ended\n");
            break;
        case YAML_SEQUENCE_START_EVENT:
            if(0) printf("[cpu_sim]: Sequence started\n");
            break;
        case YAML_SEQUENCE_END_EVENT:
            if(0) printf("[cpu_sim]: Sequence ended\n");
            break;
        case YAML_SCALAR_EVENT:
            s = (const char*)event.data.scalar.value;
            it = find(lbls.begin(), lbls.end(), s);
            if (it != lbls.end()) {
                if(0) cout << "[cpu_sim " << tag << " ]: " << " Label: " << s << '\n';
                lbl_stk.push(s);
            } else {
                s1 = lbl_stk.top();
                if(0) cout << "[cpu_sim " << tag << " ]: " << " Label: " << s1 << " Datum: " << s << '\n';
                mymap[s1] = s;
                lbl_stk.pop();
            }
            break;
        default:
            if(0) cout << "[cpu_sim " << tag << " ]: " << " (Default)" << endl;
            break;
        }

        if(event.type == YAML_STREAM_END_EVENT) break;
        yaml_event_delete(&event);
    }
    if(0) cout << "[cpu_sim " << tag << " ]: " << " All done parsing, got this:" << endl;
    if(0) for (map<string,string>::iterator it=mymap.begin(); it!=mymap.end(); ++it)
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
    double   cmpLt_sGB  = 500;   // seconds/(input GB) computational latency
    double   cmpLt_usB  = 0.5;   // usec/(input B) computational latency
    double   cmpLt_sMB  = 0.5;   // seconds/(input MB) computational latency
    double   memGB      = 10;    // thread memory footprint in GB
    double   otmemGB    = 0.01;  // program output in GB
    double   outNicSpd  = 10;    // outgoing NIC speed in Gbps
    uint16_t zed        = 0;     //terminal node ?
    uint64_t frame_cnt  = 0;     //total frames sender will send

    std::cout << std::fixed << std::setprecision(7);  // 6 decimal places            

    while ((optc = getopt(argc, argv, "hb:i:f:m:n:o:p:r:v:y:z:")) != -1)
    {
        switch (optc)
        {
        case 'h':
            Usage();
            exit(1);
        case 'b':
            cmpLt_sGB = (double) atof((const char *) optarg) ;
            psdB = true;
            if(DBG) cout << " -b " << cmpLt_sGB;
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
            memGB = (double) atof((const char *) optarg) ;
            psdM = true;
            if(DBG) cout << " -m " << memGB;
            break;
        case 'n':
            outNicSpd = (double) atof((const char *) optarg) ;
            psdN = true;
            if(DBG) cout << " -n " << outNicSpd;
            break;
        case 'o':
            otmemGB = (double) atof((const char *) optarg) ;
            psdO = true;
            if(DBG) cout << " -o " << otmemGB;
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
        if(!psdB) cmpLt_sGB = stof(mymap["latency"]);
        if(!psdI) strcpy(dst_ip, mymap["destination"].c_str());
        if(!psdM) memGB    = stof(mymap["mem_footprint"]);
        if(!psdN) outNicSpd= stof(mymap["out_nic"]);
        if(!psdO) otmemGB  = stof(mymap["output_size"]);
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
                << "\tcmpLt_sGB = " << cmpLt_sGB
                << "\tdst_ip = "   << (trmnl?"N/A":string(dst_ip)) << "\tmemGB = "       << memGB
                << "\totmemGB = "  << otmemGB                     << "\tdst_prt = "     << (trmnl?"N/A":to_string(dst_prt))
                << "\trcv_prt = "  << rcv_prt
                << "\toutNicSpd  = "  << outNicSpd
                << "\tverbose = "   << vrbs << "\tyfn = " << (psdY?yfn:"N/A")
                << "\tterminal = " << trmnl << '\n';

    // RNG for latency variance generation using a Gaussian (normal) distribution to generate a scaling factor 
    // centered around 1.0, with a standard deviation chosen so that ~99.7% of values fall 
    // in the 70% to 130% range (i.e., ±3σ ≈ 30%):

    static std::random_device rd;
    static std::mt19937 gen(rd());
    
    // Mean = 1.0, Std Dev = 0.1 gives 99.7% of samples in [0.7, 1.3]
    static std::normal_distribution<> sd_10pcnt(1.0, 0.1);

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
    double mnBfSz        = 0; //mean receive Size (bits)
    uint64_t bufSiz      = 0; //bits
    uint32_t stream_id   = 0;
    uint32_t frame_num   = 0;
    
    float tsr = 0; // sim clock from sender
    float tsc = 0; // computational latency
    float tsn = 0; // outbound network latency
    
    std::string ack = "ACK";
    zmq::message_t ack_msg(ack.begin(), ack.end());  // ensures correct sizing and content

    while (true) {
wait_for_frame:
        //if(vrbs) cout << "[cpu_sim " << rcv_prt << "]: " << " Setting up request message ..." << endl;
        message_t request;

        //  Wait for next request from client
        if(vrbs) cout << tsr << " [cpu_sim " << rcv_prt << "]: " << " Waiting for source ..." << endl;
        
        recv_result_t rtcd;
        
        {
            rtcd = rcv_sckt.recv (request, recv_flags::none);
            message_t reply (3);
            memcpy (reply.data (), "ACK", 3);
            rcv_sckt.send (reply, send_flags::none);
            //rcv_sckt.send(ack_msg, zmq::send_flags::none);
        }
        
        BufferPacket pkt = BufferPacket::from_message(request);
        bufSiz    = pkt.size; //bits
        stream_id = pkt.stream_id;
        frame_num = pkt.frame_num;
        if(frame_num == 0) {if(vrbs) cout << tsr + 10 << " [cpu_sim " << rcv_prt << "]: " << " going to all_done " << endl; goto all_done;}
        //reqd transmission timespan in usec    
        {
            // Clamp to [1.0, 1.3] to enforce bounds
            auto lb = 1e-3*double(bufSiz)/outNicSpd; //usec
            auto x = std::clamp(sd_10pcnt(gen), 1.0, 1.3);
            tsn = lb*x; //usec
            //advance the sim clock for netwok latency
            float tsr1 = pkt.timestamp + tsn;
            if(vrbs) std::cout << tsr1 << " [cpu_sim " << rcv_prt << "]: " << " recd " << frame_num << endl;
            if(DBG) cout << tsr1 + 0.1 << " [cpu_sim " << rcv_prt << "]: " << " Received request "
                      << frame_num << " from port " + string("tcp://") + dst_ip + ':' +  to_string(rcv_prt)
                      << " rtcd = " << int(rtcd.value()) << " from client" << endl;
                      
            if(vrbs) cout << tsr1 + 0.2 << " [cpu_sim " << rcv_prt << "]: " << " frame size = "
                      << "(Spec'd) " << bufSiz << " bits " << bufSiz*1e-9 << " Gb "
                      << " from client " << "ts = " << tsr1 + 0.3 << " (" << frame_num << ')' << endl;
        
            if(DBG) cout << tsr1 + 0.01 << " [cpu_sim " << rcv_prt << "]: Sending ACK  (" << frame_num << ')' << endl;
            if(vrbs) cout << tsr1 + 0.001 << " [cpu_sim " << rcv_prt << "]: Calculating tsn as " << tsn
                                                << " for bufSiz " << bufSiz << " outNicSpd " << outNicSpd
                                                << " (" << frame_num << ')' << " using x " << x << " lb " << lb << endl;
            if(tsr>tsr1) {
                if(vrbs) {cout << tsr1 + 0.3 << " [cpu_sim " << rcv_prt << "]:  dropped (" << frame_num << ')'  
                               << " request_nbr " << request_nbr << "(tsr,tsr1) (" << tsr << ',' << tsr1 << ')' << endl;}
                if(frame_num != 0) {if(vrbs) cout << tsr - 0.01 << " [cpu_sim " << rcv_prt << "]: " << " going to wait_for_frame " << endl; goto wait_for_frame;} //continue; //
            } else {
                tsr = tsr1 + 1; // advance the clock
            }
        }
        
        request_nbr++;
        
        if(frame_num == 0) { // all done
all_done:
            std::cout.flush();
            std::cerr.flush();
            if(trmnl) {cout  << tsr + 11 << " [cpu_sim " << rcv_prt << "]:  Terminal exiting" << endl; exit(0);} // no terminate signal to send forward

            {
                send_result_t sr;
                BufferPacket pkt;
                pkt.size = 0;
                pkt.timestamp = tsr;
                pkt.stream_id = stream_id;
                pkt.frame_num = 0;
	            // Send "frame" spec
                if(vrbs) cout << tsr + 11 << " [cpu_sim " << rcv_prt << "]:  Sending frame size = " << 0 << " (" 
                              << frame_num << ')' << " to " << dst_prt << " at " << tsr << " to forward sim termination" << endl;
                sr = dst_sckt.send(pkt.to_message(), zmq::send_flags::none);

                // Receive the reply from the destination
                //  Get the reply.
                message_t reply;
                if(DBG) cout << tsr + 11  << " [cpu_sim " << rcv_prt << "]: Waiting for destination ACK (" << frame_num << ')' << endl;
                recv_result_t rtcd = dst_sckt.recv (reply, recv_flags::none);
                if(DBG) cout << tsr + 11  << " [cpu_sim " << rcv_prt << "]: Destination Actual reply (" << frame_num << ") " 
                              << reply << " With rtcd = " << rtcd.value() << endl;

                if(DBG) cout << tsr + 11  << " [cpu_sim " << rcv_prt << "]: " << " output Num written  (" << frame_num << ") " << sr.value()  << endl;
                if(sr.value() != pkt.PACKET_SIZE) cout << tsr  << " Destination data incorrect size (" << frame_num << ") " << endl;
            }        
            cout  << tsr + 11 << " [cpu_sim " << rcv_prt << "]:  Non-Terminal exiting" << endl;
            std::cout.flush();
            std::cerr.flush();
            exit(0);
        }
        //  Do some 'work'
        // simulate load on system for ensuing work
        {
            //reqd computational timespan in usec with 10% std dev
            auto x = std::clamp(sd_10pcnt(gen), 0.7, 1.3);
            tsc = cmpLt_usB*(float(bufSiz)/8)*x; //usec
            tsr += tsc; // advance the clock
            if(vrbs) cout << tsr << " [cpu_sim " << rcv_prt << "]:  added tsc " << tsc << " (" << frame_num << ')' 
                        << " for bufSiz " << bufSiz << " cmpLt_usB " << cmpLt_usB << " x " << x << endl;
        }

        if(!trmnl) {
            if(DBG) cout << tsr  << " [cpu_sim " << rcv_prt << "]: " << " Forwarding "
                          << " request " << frame_num << " from port " + string("tcp://") + dst_ip + ':' +  to_string(rcv_prt)
                          << " to port " + string("tcp://") + dst_ip + ':' +  to_string(dst_prt) << " (" << frame_num << ')' << endl;
            //forward to next hop
            // Send a message to the destination
            size_t outSz = 8*otmemGB*1.024*1.024*1.024*1e9; //output size in bits

            {
                send_result_t sr;
                BufferPacket pkt;
                //represents harvested data
                pkt.size = outSz;
                pkt.timestamp = tsr;
                pkt.stream_id = stream_id;
                pkt.frame_num = frame_num;
	            // Send "frame" spec
                if(vrbs) cout << tsr << " [cpu_sim " << rcv_prt << "]:  Sending frame size = " << outSz << " (" 
                              << frame_num << ')' << " to " << dst_prt << " at " << tsr << endl;
                sr = dst_sckt.send(pkt.to_message(), zmq::send_flags::none);

                // Receive the reply from the destination
                //  Get the reply.
                message_t reply;
                if(DBG) cout << tsr  << " [cpu_sim " << rcv_prt << "]: Waiting for destination ACK (" << frame_num << ')' << endl;
                recv_result_t rtcd = dst_sckt.recv (reply, recv_flags::none);
                if(DBG) cout << tsr  << " [cpu_sim " << rcv_prt << "]: Destination Actual reply (" << frame_num << ") " 
                              << reply << " With rtcd = " << rtcd.value() << endl;

                if(DBG) cout << tsr  << " [cpu_sim " << rcv_prt << "]: " << " output Num written  (" << frame_num << ") " << sr.value()  << endl;
                if(sr.value() != pkt.PACKET_SIZE) cout << tsr  << " Destination data incorrect size (" << frame_num << ") " << endl;
            }        
        }
        if(vrbs) cout << tsr + 1 << " [cpu_sim " << rcv_prt << "]:  done (" << frame_num << ')' << endl;
        //cout  << tsr << " [cpu_sim " << rcv_prt << "]:  clock advanced ...(" << frame_num << ')' <<  " request_nbr " << request_nbr  << endl;

        mnBfSz = (request_nbr-1)*mnBfSz/request_nbr + bufSiz/request_nbr; //incrementally update mean receive size
        cout  << tsr + 2 << " [cpu_sim " << rcv_prt << "]:  computing stats ...(" << frame_num << ')' << endl;

        if(vrbs) std::cout << tsr + 3 << " [cpu_sim " << rcv_prt << "]: " << " Computed latencies: tsc = " << tsc << " tsn = " << tsn 
                           << " (" << frame_num << ')' << endl;
        if(vrbs) std::cout << tsr + 4 << " [cpu_sim " << rcv_prt << "]: " << " Measured frame rate " << float(request_nbr)/(1e-6*float(tsr)) 
                           << " frame Hz." << " for " << frame_num << " frames" << endl;
        if(vrbs) std::cout << tsr + 5 << " [cpu_sim " << rcv_prt << "]: " << " Measured bit rate " << 1e-6*float(request_nbr*mnBfSz)/(1e-6*float(tsr)) 
                           << " MHz mnBfSz " << mnBfSz << " (" << frame_num << ')' << endl;
        if(vrbs) cout << tsr + 6 << " [cpu_sim " << rcv_prt << "]:  Missed frames: " << frame_num-request_nbr << endl;
        if(vrbs) cout << tsr + 7 << " [cpu_sim " << rcv_prt << "]:  Missed frame ratio: " << float(frame_num-request_nbr)/float(frame_num) << " frame_num " << frame_num  << " request_nbr " << request_nbr << endl;
        cout  << tsr + 8 << " [cpu_sim " << rcv_prt << "]:  stats computed ..." << endl;
        tsr += 10; // advance the clock
    }
    cout  << tsr << " [cpu_sim " << rcv_prt << "]:  return exiting" << endl;
    return 0;
}
