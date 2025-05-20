//
//  CPU Emulator for Real Time Development Program (RTDP)
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

#ifdef __linux__
    #define HTONLL(x) ((1==htonl(1)) ? (x) : (((uint64_t)htonl((x) & 0xFFFFFFFFUL)) << 32) | htonl((uint32_t)((x) >> 32)))
    #define NTOHLL(x) ((1==ntohl(1)) ? (x) : (((uint64_t)ntohl((x) & 0xFFFFFFFFUL)) << 32) | ntohl((uint32_t)((x) >> 32)))
#endif

using namespace std;
using namespace zmq;
using namespace chrono;

#define DBG 0	//print extra verbosity apart from -v switch
  
void   Usage()
{
    char usage_str[] =
        "\nUsage: \n\
        -h help  \n\
        -b seconds thread latency per GB input \n\
        -i destination address (string)  \n\
        -m thread memory footprint in GB  \n\
        -n out going NIC speed in Gbps  \n\
        -o output size in GB  \n\
        -p destination port (default = 8888)  \n\
        -r receive port (default = 8888)  \n\
        -s sleep versus burn cpu = 0/1 (default = false = 0)  \n\
        -t num threads (default = 10)  \n\
        -v verbose = 0/1 (default = false = 0)  \n\
        -x run in sim mode = 0/1 (default = false = 0)  \n\
        -y yaml config file  \n\
        -z act as terminal node = 0/1 (default = false = 0)  \n\n";

    cout << "[cpu_emu]: " << usage_str;
}

// Computational Function to emulate/stimulate processimng load/latency, etc. 
void func(size_t nmrd, size_t cmpLt_GB, double memGB, bool psdS, uint16_t tag, bool vrbs=false) 
{ 
    const float ts(cmpLt_GB*nmrd*1e-9); //reqd timespan in seconds
    const float tsms(cmpLt_GB*nmrd*1e-6); //reqd timespan in milliseconds
    const float tsus(cmpLt_GB*nmrd*1e-3); //reqd timespan in microseconds
    const float tsns(cmpLt_GB*nmrd);    //reqd timespan in nanoseconds
    size_t memSz = memGB*1024*1024*1024; //memory footprint in bytes
    if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Allocating " << memSz << " bytes ..." << endl;
    if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Allocating " << float(memSz/(1024*1024*1024)) << " Gbytes ..." << endl;

    double* x;
    try {
        x = new double[memSz];
        if(vrbs) std::cout << "Memory allocation for " << memSz << " succeeded.\n";
    } catch (const std::bad_alloc& e) {
        if(vrbs) std::cout << "Memory allocation for " << memSz << " failed: " << e.what() << '\n';
        exit(1);
    }    
    //usefull work emulation 
    if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Threading for " << ts   << " secs ..."  << " size " << nmrd << endl;
    if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Threading for " << tsms << " msecs ..." << " size " << nmrd << endl;
    if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Threading for " << tsus << " usecs ..." << " size " << nmrd << endl;
    if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Threading for " << tsns << " nsecs ..." << " size " << nmrd << endl;
    if(psdS) {
        auto cms = chrono::nanoseconds(size_t(round(tsns)));
        if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Sleep_Threaded for " << ts           << " secs ..."  << " size " << nmrd << endl;
        if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Sleep_Threaded for " << tsms         << " msecs ..." << " size " << nmrd << endl;
        if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Sleep_Threaded for " << tsus         << " usecs ..." << " size " << nmrd << endl;
        if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Sleep_Threaded for " << tsns         << " nsecs ..." << " size " << nmrd << endl;
        if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Sleeping for "       << float(cms.count())/float(1e6)  << " msecs ..." << " size " << nmrd << endl;
        this_thread::sleep_for(cms);
    }else{
        auto ts = (cmpLt_GB*nmrd*1e-9);
        //high_resolution_clock::time_point start_time = std::chrono::high_resolution_clock::now();
        auto start_time = std::chrono::high_resolution_clock::now();
        if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Burning ...";
        
        double fracsecs, secs;
        fracsecs = modf (ts , &secs);
        if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " secs = " << secs << " fracsecs = " << fracsecs << endl;
        size_t sz1k = 1024;
        size_t strtMem = 0;
        auto end_time = std::chrono::high_resolution_clock::now();
        duration<double> time_span = duration_cast<duration<double>>(end_time - start_time);
        while (time_span.count() < ts) { 
            for (size_t i = strtMem; i<min(strtMem + sz1k, memSz); i++) { x[i] = tanh(i); }
            strtMem += sz1k;
            if(strtMem > memSz - sz1k) strtMem = 0;
            end_time = std::chrono::high_resolution_clock::now();
            time_span = duration_cast<duration<double>>(end_time - start_time);
            if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Checking " << time_span.count() << " against "<< ts  << endl;
        }
        auto tsc = time_span.count();
        if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Threaded for " << tsc     << " secs "  << " size " << nmrd << endl;
        if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Threaded for " << tsc*1e3 << " msecs " << " size " << nmrd << endl;
        if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Threaded for " << tsc*1e6 << " usecs " << " size " << nmrd << endl;
        if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Threaded for " << tsc*1e9 << " nsecs " << " size " << nmrd << endl;
    }
    delete x;
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
    lbls.push_back("sleep"); lbls.push_back("threads"); lbls.push_back("latency");
    lbls.push_back("mem_footprint"); lbls.push_back("output_size"); lbls.push_back("verbose");
    lbls.push_back("terminal"); lbls.push_back("sim_mode"); lbls.push_back("out_nic");
    
    auto it = lbls.begin(); //a hack to get the type

    while (yaml_parser_parse(&parser, &event)) {
        switch (event.type) {
        case YAML_NO_EVENT:
            break;
        case YAML_STREAM_START_EVENT:
            if(DBG) printf("[cpu_emu]: Stream started\n");
            break;
        case YAML_STREAM_END_EVENT:
            if(DBG) printf("[cpu_emu]: Stream ended\n");
            break;
        case YAML_DOCUMENT_START_EVENT:
            if(DBG) printf("[cpu_emu]: Document started\n");
            break;
        case YAML_DOCUMENT_END_EVENT:
            if(DBG) printf("[cpu_emu]: Document ended\n");
            break;
        case YAML_MAPPING_START_EVENT:
            if(DBG) printf("[cpu_emu]: Mapping started\n");
            break;
        case YAML_MAPPING_END_EVENT:
            if(DBG) printf("[cpu_emu]: Mapping ended\n");
            break;
        case YAML_SEQUENCE_START_EVENT:
            if(DBG) printf("[cpu_emu]: Sequence started\n");
            break;
        case YAML_SEQUENCE_END_EVENT:
            if(DBG) printf("[cpu_emu]: Sequence ended\n");
            break;
        case YAML_SCALAR_EVENT:
            s = (const char*)event.data.scalar.value;
            it = find(lbls.begin(), lbls.end(), s);
            if (it != lbls.end()) {
                if(DBG) cout << "[cpu_emu " << tag << " ]: " << " Label: " << s << '\n';
                lbl_stk.push(s);
            } else {
                s1 = lbl_stk.top();
                if(DBG) cout << "[cpu_emu " << tag << " ]: " << " Label: " << s1 << " Datum: " << s << '\n';
                mymap[s1] = s;
                lbl_stk.pop();
            }
            break;
        default:
            if(DBG) cout << "[cpu_emu " << tag << " ]: " << " (Default)" << endl;
            break;
        }

        if(event.type == YAML_STREAM_END_EVENT) break;
        yaml_event_delete(&event);
    }
    if(DBG) cout << "[cpu_emu " << tag << " ]: " << " All done parsing, got this:" << endl;
    if(DBG) for (map<string,string>::iterator it=mymap.begin(); it!=mymap.end(); ++it)
        cout << it->first << " => " << it->second << '\n';
    
    yaml_parser_delete(&parser);
    fclose(file);
}
  
int main (int argc, char *argv[])
{ 
    int optc;

    bool     psdB=false, psdI=false, psdM=false, psdO=false, psdY=false;
    bool     psdP=false, psdR=false, psdS=false, psdT=false, psdV=false;
    bool     psdZ=false, psdX=false, psdN=false;
    string   yfn = "cpu_emu.yaml";
    char     dst_ip[INET6_ADDRSTRLEN] = "127.0.0.1";	// target ip
    uint16_t rcv_prt = 8888;  // receive port default
    uint16_t dst_prt = 8888;  // target port default
    auto     nmThrds = 5;     // default
    bool     vrbs    = false; // verbose ?
    double   cmpLt_GB  = 100;   // seconds/(input GB) computational latency
    double   memGB   = 10;    // thread memory footprint in GB
    double   otmemGB = 0.01;  // program output in GB
    double   outNicSpd = 10;  // outgoing NIC speed in Gbps

    std::cout << std::fixed << std::setprecision(7);  // 6 decimal places            

    while ((optc = getopt(argc, argv, "hb:i:m:n:o:p:r:st:v:xy:z")) != -1)
    {
        switch (optc)
        {
        case 'h':
            Usage();
            exit(1);
        case 'b':
            cmpLt_GB = (double) atof((const char *) optarg) ;
            psdB = true;
            if(DBG) cout << " -b " << cmpLt_GB;
            break;
        case 'i':
            strcpy(dst_ip, (const char *) optarg) ;
            psdI = true;
            if(DBG) cout << " -i " << dst_ip;
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
        case 's':
            psdS = true;
            if(DBG) cout << " -s ";
            break;
        case 't':
            nmThrds = (uint16_t) atoi((const char *) optarg) ;
            psdT = true;
            if(DBG) cout << " -t " << nmThrds;
            break;
        case 'v':
            vrbs = (bool) atoi((const char *) optarg) ;
            psdV = true;
            if(DBG) cout << " -v " << vrbs;
            break;
        case 'x':
            psdX = true;
            if(DBG) cout << " -x ";
            break;
        case 'y':
            yfn = (const char *) optarg ;
            psdY = true;
            if(DBG) cout << " -y " << yfn;
            break;
        case 'z':
            psdZ = true;
            if(DBG) cout << " -z ";
            break;
        case '?':
            cout << "[cpu_emu " << rcv_prt << "]: " << " Unrecognised option: " << optopt;
            Usage();
            exit(1);
        }
    }

    if(DBG) cout << endl;

    if (psdY) {//parse the yaml file if given        
        parse_yaml(yfn.c_str(), rcv_prt, vrbs);
        //cmd line parms overide yaml file settings (which are otherwise in the map)
        if(!psdB) cmpLt_GB = stof(mymap["latency"]);
        if(!psdI) strcpy(dst_ip, mymap["destination"].c_str());
        if(!psdM) memGB    = stof(mymap["mem_footprint"]);
        if(!psdN) outNicSpd= stof(mymap["out_nic"]);
        if(!psdO) otmemGB  = stof(mymap["output_size"]);
        if(!psdP) dst_prt  = stoi(mymap["dst_port"]);
        if(!psdR) rcv_prt  = stoi(mymap["rcv_port"]);
        if(!psdS) psdS     = stoi(mymap["sleep"]) == 1;
        if(!psdT) nmThrds  = stoi(mymap["threads"]);
        if(!psdV) vrbs     = stoi(mymap["verbose"]);
        if(!psdX) psdX     = stoi(mymap["sim_mode"]) == 1;
        if(!psdZ) psdZ     = stoi(mymap["terminal"]) == 1;
    }    
    ////////
    if(vrbs) cout << "[cpu_emu "   << rcv_prt                     << " ]: "
                << " Operating with yaml = " << (psdY?yfn:"N/A")
                << "\tcmpLt_GB = " << cmpLt_GB
                << "\tdst_ip = "   << (psdZ?"N/A":string(dst_ip)) << "\tmemGB = "       << memGB
                << "\totmemGB = "  << otmemGB                     << "\tdst_prt = "     << (psdZ?"N/A":to_string(dst_prt))
                << "\trcv_prt = "  << rcv_prt                     << "\tsleep = "       << psdS << "\tsim_mode = " << psdX
                << "\toutNicSpd  = "  << outNicSpd
                << "\tnmThrds = "  << nmThrds                     << "\tverbose = "   << vrbs << "\tyfn = " << (psdY?yfn:"N/A")
                << "\tterminal = " << psdZ                        << '\n';

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
    if(vrbs) cout << "[cpu_emu " << rcv_prt << "]: " << " Connecting to receiver " + string("tcp://*:") + to_string(rcv_prt) << endl;
    
    if(!psdZ) {
        //  Prepare our destination socket
        if(vrbs) cout << "[cpu_emu " << rcv_prt << "]: " << " Connecting to destination " + string("tcp://") + dst_ip + ':' +  to_string(dst_prt) << endl;
        dst_sckt.connect (string("tcp://") + dst_ip + ':' +  to_string(dst_prt));
    }
    uint64_t request_nbr = 0;
    double mnBfSz = 0; //mean receive Size (bits)
    while (true) {
        //if(vrbs) cout << "[cpu_emu " << rcv_prt << "]: " << " Setting up request message ..." << endl;
        message_t request;

        //  Wait for next request from client
        if(vrbs) cout << "[cpu_emu " << rcv_prt << "]: " << " Waiting for source ..." << endl;
        
        recv_result_t rtcd;
        
        {//block
            auto start = high_resolution_clock::now();
            rtcd = rcv_sckt.recv (request, recv_flags::none);
            message_t reply (3+1);
            memcpy (reply.data (), "ACK", 3);
            if(vrbs>10) cout << "[cpu_emu " << rcv_prt << "]: Sending ACK  (" << request_nbr << ')' << endl;
            rcv_sckt.send (reply, send_flags::none);
        }

        uint64_t bufSiz = 0; //bits
        uint32_t stream_id = 0;
        uint64_t tsr = 0; // sim clock from sender
        uint64_t tsc = 0; // computational latency
        uint64_t tsn = 0; // outbound network latency
        if (psdX) { //parse recvd message to get simlated data size recvd
        
            BufferPacket pkt = BufferPacket::from_message(request);

            bufSiz = pkt.size;
            stream_id = pkt.stream_id;
            tsr = pkt.timestamp;
        } else {
            bufSiz = 8*rtcd.value();
        }
        if(DBG) cout << tsr  << " [cpu_emu " << rcv_prt << "]: " << " Received request " 
                      << request_nbr << " from port " + string("tcp://") + dst_ip + ':' +  to_string(rcv_prt)
                      << " rtcd = " << int(rtcd.value()) << " from client" << endl;
                      
        if(vrbs) cout << tsr  << " [cpu_emu " << rcv_prt << "]: " << " chunk size = " 
                      << (psdX?"(Spec'd) ":"(actual) ") << bufSiz << " bits " << bufSiz*1e-9 << " Gb "
                      << " from client " << "ts = " << (psdX?tsr:0) << endl;  // && request_nbr % 10 == 0
        //  Do some 'work'
        // load (or emulate load on) system with ensuing work
        if (psdX) {
            //reqd computational timespan in nanoseconds
            tsc = uint64_t(cmpLt_GB*1e-9*float(bufSiz)/8);            
        } else {//parse recvd message to get simlated data size recvd
            vector<thread> threads;

            for (int i=1; i<=nmThrds; ++i)  //start the threads
                threads.push_back(thread(func, bufSiz/8, cmpLt_GB, memGB, psdS, rcv_prt, vrbs));

            for (auto& th : threads) th.join();
            if(DBG) cout << "[cpu_emu " << rcv_prt << "]: " << " synchronized all threads..." << endl;
        }

        if(!psdZ) {
            if(DBG) cout << "[cpu_emu " << rcv_prt << "]: " << " Forwarding "
                          << " request " << request_nbr << " from port " + string("tcp://") + dst_ip + ':' +  to_string(rcv_prt)
                          << " to port " + string("tcp://") + dst_ip + ':' +  to_string(dst_prt)<< endl;
            //forward to next hop    
            // Send a message to the destination
            size_t outSz = 8*otmemGB*1.024*1.024*1.024*1e9; //output size in bits

            send_result_t sr;
            if(psdX) {
                BufferPacket pkt;
                //represents harvested data
                pkt.size = outSz;
                //reqd transmission timespan in nanoseconds
                tsn = uint64_t(float(1e9*8*otmemGB/outNicSpd));
                //advance the sim clock for comp + network latencies
                pkt.timestamp = tsr + tsn + tsc;
                pkt.stream_id = stream_id;
	            // Send "chunk" spec
                sr = dst_sckt.send(pkt.to_message(), zmq::send_flags::none);

                // Receive the reply from the destination
                //  Get the reply.
                message_t reply;
                if(vrbs) cout << "[cpu_emu " << rcv_prt << "]: Waiting for destination ACK (" << request_nbr << ')' << endl;
                recv_result_t rtcd = dst_sckt.recv (reply, recv_flags::none);
                if(vrbs) cout << "[cpu_emu " << rcv_prt << "]: Destination Actual reply (" << request_nbr << ") " << reply << " With rtcd = " << rtcd.value() << endl;

                if(DBG) cout << "[cpu_emu " << rcv_prt << "]: " << " output Num written  (" << request_nbr << ") " << sr.value()  << endl;
                if(sr.value() != pkt.PACKET_SIZE) cout << "Destination data incorrect size (" << request_nbr << ") " << endl;
            } else {
	        // Send  "chunk"
                message_t dst_msg(outSz/8);  //represents harvested data
                sr = dst_sckt.send(dst_msg, send_flags::none);
                zmq::message_t reply;
                dst_sckt.recv(reply, zmq::recv_flags::none);
                if(DBG) cout << "[cpu_emu " << rcv_prt << "]: " << " output Num written (" << request_nbr << ") "  << sr.value()  << endl;
                if(sr.value() != outSz/8) cout << "Destination data incorrect size(" << request_nbr << ") "  << endl;
            }
        }
        request_nbr++;
        mnBfSz = (request_nbr-1)*mnBfSz/request_nbr + bufSiz/request_nbr; //incrementally update mean receive size
        const uint64_t tsl = tsn + tsc;
        // Record end time
        if (request_nbr % 10 == 0) {
            if(vrbs) std::cout << tsr+tsl << " [cpu_emu " << rcv_prt << "]: " << " Measured chunk rate " << float(1)/(1e-9*float(tsl)) << " chunk Hz." << " for " << request_nbr << " chunks" << std::endl;
            if(vrbs) std::cout << tsr+tsl << " [cpu_emu " << rcv_prt << "]: " << " Measured bit rate " << 1e-6*float(1*mnBfSz)/(1e-9*float(tsl)) << " MHz mnBfSz " << mnBfSz << std::endl;
            if(vrbs) std::cout << tsr+tsl << " [cpu_emu " << rcv_prt << "]: " << " recd " << request_nbr << std::endl;
        }
    }
    return 0;
}
