#define DBG 0	//print extra verbosity apart from -v switch
  
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
#include "buffer_packet_emu.hh"
#include <random>
#include <cassert>

#ifdef __linux__
    #define HTONLL(x) ((1==htonl(1)) ? (x) : (((uint64_t)htonl((x) & 0xFFFFFFFFUL)) << 32) | htonl((uint32_t)((x) >> 32)))
    #define NTOHLL(x) ((1==ntohl(1)) ? (x) : (((uint64_t)ntohl((x) & 0xFFFFFFFFUL)) << 32) | ntohl((uint32_t)((x) >> 32)))
#endif

using namespace std;
using namespace zmq;
using namespace chrono;

void   Usage()
{
    char usage_str[] =
        "\nUsage: \n\
        -h help  \n\
        -b seconds thread latency per GB input \n\
        -f total frames sender will send  \n\
        -i destination address (string)  \n\
        -m thread memory footprint in GB  \n\
        -o output size in GB  \n\
        -p destination port (default = 8888)  \n\
        -r receive port (default = 8888)  \n\
        -s sleep versus burn cpu = 0/1 (default = false = 0)  \n\
        -t num threads (default = 10)  \n\
        -v verbose = 0/1 (default = false = 0)  \n\
        -y yaml config file  \n\
        -z act as terminal node = 0/1 (default = false = 0)  \n\n";

    cout << "[cpu_emu]: " << usage_str;
}

// Computational Function to emulate/stimulate processing load/latency, etc. 
void func(size_t nmrd, size_t cmpLt_sGB, double memGB, bool psdS, uint16_t tag, bool vrbs=false) 
{ 
    const float ts(cmpLt_sGB*nmrd*1e-9); //reqd timespan in seconds
    const float tsms(cmpLt_sGB*nmrd*1e-6); //reqd timespan in milliseconds
    const float tsus(cmpLt_sGB*nmrd*1e-3); //reqd timespan in microseconds
    const float tsns(cmpLt_sGB*nmrd);    //reqd timespan in nanoseconds
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
        auto ts = (cmpLt_sGB*nmrd*1e-9);
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
            if(DBG) cout << "[cpu_emu " << tag << " ]: " << " Checking " << time_span.count() << " against "<< ts  << endl;
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
        cerr << "Failed to initialize parser! " << endl;
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
    lbls.push_back("terminal"); lbls.push_back("frame_cnt");
    
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
                if(DBG) cout << "[cpu_emu " << tag << " ]: " << " Label: " << s << endl;
                lbl_stk.push(s);
            } else {
                s1 = lbl_stk.top();
                if(DBG) cout << "[cpu_emu " << tag << " ]: " << " Label: " << s1 << " Datum: " << s << endl;
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
        cout << it->first << " => " << it->second << endl;
    
    yaml_parser_delete(&parser);
    fclose(file);
}
 
int main (int argc, char *argv[])
{ 
    int optc;

    bool     psdB=false, psdI=false, psdF=false, psdM=false, psdO=false, psdY=false;
    bool     psdP=false, psdR=false, psdS=false, psdT=false, psdV=false;
    bool     psdZ=false, trmnl=false;
    string   yfn = "cpu_emu.yaml";
    char     dst_ip[INET6_ADDRSTRLEN] = "127.0.0.1";	// target ip
    uint16_t rcv_prt = 8888;  // receive port default
    uint16_t dst_prt = 8888;  // target port default
    auto     nmThrds = 5;     // default
    bool     vrbs    = false; // verbose ?
    // 500 seconds/(input GB) computational latency for 60kB CLAS12
    // 0.5 microseconds/byte
    // 0.5 seconds per megabyte
    double   cmpLt_sGB  = 500;   // seconds/(input GB) computational latency
    double   cmpLt_usB  = 0.5;   // usec/(input B) computational latency
    double   cmpLt_sMB  = 0.5;   // seconds/(input MB) computational latency
    double   memGB      = 10;    // thread memory footprint in GB
    double   otmemGB    = 0.01;  // program output in GB
    uint16_t zed        = 0;     //terminal node ?
    uint64_t frame_cnt  = 0;     //total frames sender will send

    std::cout << std::fixed << std::setprecision(7);  // 6 decimal places            

    while ((optc = getopt(argc, argv, "hb:f:i:m:o:p:r:st:v:y:z:")) != -1)
    {
        switch (optc)
        {
        case 'h':
            Usage();
            exit(1);
        case 'b':
            cmpLt_sGB = (double) atof((const char *) optarg) ;
            psdB = true;
            if(DBG) cout << " -b " << cmpLt_sGB << endl;
            break;
        case 'i':
            strcpy(dst_ip, (const char *) optarg) ;
            psdI = true;
            if(DBG) cout << " -i " << dst_ip << endl;
            break;
        case 'f':
            frame_cnt = (uint64_t) atoi((const char *) optarg) ;
            psdF = true;
            if(DBG) cout << " -f " << frame_cnt << endl;
            break;
        case 'm':
            memGB = (double) atof((const char *) optarg) ;
            psdM = true;
            if(DBG) cout << " -m " << memGB << endl;
            break;
        case 'o':
            otmemGB = (double) atof((const char *) optarg) ;
            psdO = true;
            if(DBG) cout << " -o " << otmemGB << endl;
            break;
        case 'p':
            dst_prt = (uint16_t) atoi((const char *) optarg) ;
            psdP = true;
            if(DBG) cout << " -p " << dst_prt << endl;
            break;
        case 'r':
            rcv_prt = (uint16_t) atoi((const char *) optarg) ;
            psdR = true;
            if(DBG) cout << " -r " << rcv_prt << endl;
            break;
        case 's':
            psdS = true;
            if(DBG) cout << " -s " << endl;
            break;
        case 't':
            nmThrds = (uint16_t) atoi((const char *) optarg) ;
            psdT = true;
            if(DBG) cout << " -t " << nmThrds << endl;
            break;
        case 'v':
            vrbs = (bool) atoi((const char *) optarg) ;
            psdV = true;
            if(DBG) cout << " -v " << vrbs << endl;
            break;
        case 'y':
            yfn = (const char *) optarg ;
            psdY = true;
            if(DBG) cout << " -y " << yfn << endl;
            break;
        case 'z':
            psdZ = true;
            zed = (uint16_t) atoi((const char *) optarg) ;
            trmnl = zed==1?true:false;
            if(DBG) cout << " -z " << zed << endl;
            break;
        case '?':
            cout << "[cpu_emu " << rcv_prt << "]: " << " Unrecognised option: " << optopt << endl;
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
        if(!psdO) otmemGB  = stof(mymap["output_size"]);
        if(!psdP) dst_prt  = stoi(mymap["dst_port"]);
        if(!psdR) rcv_prt  = stoi(mymap["rcv_port"]);
        if(!psdS) psdS     = stoi(mymap["sleep"]) == 1;
        if(!psdT) nmThrds  = stoi(mymap["threads"]);
        if(!psdV) vrbs     = stoi(mymap["verbose"]);
        if(!trmnl) trmnl   = stoi(mymap["terminal"]) == 1;
        if(!psdF) frame_cnt= stoi(mymap["frame_cnt"]) == 1;
    }    
    ////////
    if(vrbs) cout << "[cpu_emu "   << rcv_prt                     << " ]: "
                << " Operating with yaml = " << (psdY?yfn:"N/A")
                << "\tcmpLt_sGB = " << cmpLt_sGB
                << "\tdst_ip = "   << (trmnl?"N/A":string(dst_ip)) << "\tmemGB = "       << memGB
                << "\totmemGB = "  << otmemGB                     << "\tdst_prt = "     << (trmnl?"N/A":to_string(dst_prt))
                << "\trcv_prt = "  << rcv_prt                     << "\tsleep = "       << psdS
                << "\tnmThrds = "  << nmThrds                     << "\tverbose = "   << vrbs << "\tyfn = " << (psdY?yfn:"N/A")
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
    if(vrbs) cout << "[cpu_emu " << rcv_prt << "]: " << " Defining rcv context" << endl;

    socket_t rcv_sckt(rcv_cntxt, socket_type::sub);
    if(vrbs) cout << "[cpu_emu " << rcv_prt << "]: " << " Defining SUB protocol rcv socket" << endl;
    //rcv_sckt.set(zmq::sockopt::rcvhwm, int(0)); // queue length

    //rcv_sckt.connect(string("tcp://*:") + to_string(rcv_prt));
    //rcv_sckt.connect("tcp://localhost:5555");
    rcv_sckt.connect(string("tcp://localhost:") + to_string(rcv_prt));
    if(vrbs) cout << "[cpu_emu " << rcv_prt << "]: " << " Connecting to receiver " + string("tcp://localhost:") + to_string(rcv_prt) << endl;
    // Subscribe to all messages (empty topic)
    rcv_sckt.set(zmq::sockopt::subscribe, "");
    if(vrbs) cout << "[cpu_emu " << rcv_prt << "]: " << " rcv subscibing" << endl;

    context_t dst_cntxt(1);
    socket_t dst_sckt(dst_cntxt, socket_type::pub);
    dst_sckt.bind(string("tcp://*:") + to_string(dst_prt));
    dst_sckt.set(zmq::sockopt::sndhwm, int(0)); // queue length
    
    if(!trmnl) {
        //  Prepare our destination socket
        if(vrbs) cout << "[cpu_emu " << rcv_prt << "]: " << " Connecting to destination " + string("tcp://") + dst_ip + ':' +  to_string(dst_prt) << endl;
        //dst_sckt.connect (string("tcp://") + dst_ip + ':' +  to_string(dst_prt));
    }
    uint32_t request_nbr = 0;
    double   mnBfSz      = 0; //mean receive Size (bits)
    uint64_t bufSiz      = 0; //bits
    uint64_t cmBufSiz    = 0; //bits - cummulative
    uint64_t tsr         = 0; // system hi-res clock in microseconds since epoch
    uint64_t tsr_base    = 0; // base system hi-res clock in microseconds since epoch
    uint32_t stream_id   = 0;
    uint32_t frame_num   = 0;
    uint32_t lst_frm_nm  = 0; //last frame number
    uint64_t tsc         = 0; // computational latency
    uint64_t tsn         = 0; // outbound network latency
    uint64_t mxTsc       = 0; // computational latency
    uint64_t mxTsn       = 0; // computational latency
    uint64_t msdFrms     = 0; // missed frames count
    auto now = high_resolution_clock::now();
    auto us = duration_cast<microseconds>(now.time_since_epoch());
    while (frame_num < frame_cnt) {
        //if(vrbs) cout << "[cpu_emu " << rcv_prt << "]: " << " Setting up request message ..." << endl;
        message_t request;

        //  Wait for next request from client
        if(vrbs) cout << tsr << " [cpu_emu " << rcv_prt << "]: " << " Waiting for source ..." << endl;
        
        recv_result_t rtcd;
        
        rtcd = rcv_sckt.recv (request, recv_flags::none);
        request_nbr++;
        if(vrbs) cout << tsr+1 << " [cpu_emu " << rcv_prt << "]: " << "deserializing packet for request_nbr " << request_nbr << endl;
        auto parsed = deserialize_packet(static_cast<uint8_t*>(request.data()), request.size());
        if(vrbs) cout << tsr+2 << " [cpu_emu " << rcv_prt << "]: " << "deserializing success for frame_num " << parsed.frame_num << endl;
        bufSiz = 8*rtcd.value(); //bits


        if(vrbs) cout << tsr+1 << " [cpu_emu " << rcv_prt << "]: " << "deserializing packet ... request.size() " << request.size() << " HEADER_SIZE = " << HEADER_SIZE << endl;
        if(vrbs) cout << tsr+3 << " [cpu_emu " << rcv_prt << "]: " << "bufSiz = " << bufSiz << " parsed.size = " << parsed.size 
                    << " sizeof(struct DeserializedPacket) = " << sizeof(struct DeserializedPacket) << endl;

        if(vrbs)  cout << tsr << " [cpu_emu " << rcv_prt << "]: " << " Received request "
                      << request_nbr << " from port " + string("tcp://") + dst_ip + ':' +  to_string(rcv_prt)
                      << " rtcd = " << int(rtcd.value()) << " from client" << endl;
                      
        if(vrbs) cout << tsr+1  << " [cpu_emu " << rcv_prt << "]: " << " frame size = "
                      << "(actual) " << bufSiz << " bits " << bufSiz*1e-9 << " Gb "
                      << " from client " << "ts = " << tsr << " (" << request_nbr << ')' << endl;

        now = high_resolution_clock::now();
        us = duration_cast<microseconds>(now.time_since_epoch());
        if(request_nbr == 1) {
            tsr_base = parsed.timestamp; // establish zero offset clock from chain source
            if (DBG) cout << 0 << " [cpu_emu " << rcv_prt << "]: " << " us.count = " << us.count() << " tsr_base = " << tsr_base << " tsr = " << tsr << endl;
        }
        tsr = us.count()-tsr_base;  //zero based clock usecs
        if (DBG) cout << tsr+4 << " [cpu_emu " << rcv_prt << "]: " << " Recving us.count = " << us.count() << " tsr_base = " << tsr_base << " tsr = " << tsr << endl;
        //assert(bufSiz == parsed.size-sizeof(struct DeserializedPacket)); //bits
        stream_id =  parsed.stream_id ;
        frame_num =  parsed.frame_num ;
        if(frame_num > lst_frm_nm + 1) msdFrms += frame_num - (lst_frm_nm + 1);
        lst_frm_nm = frame_num;

        //  Do some 'work'
        // load (or emulate load on) system with ensuing work
        {

            tsc = cmpLt_sGB*(parsed.size/8)*1e-3; //reqd timespan in microseconds
            mxTsc = max(mxTsc,tsc);

            vector<thread> threads;

            for (int i=1; i<=nmThrds; ++i)  //start the threads
                threads.push_back(thread(func, parsed.size/8, cmpLt_sGB, memGB, psdS, rcv_prt, vrbs));

            for (auto& th : threads) th.join();
            //reqd computational timespan in usec    
            now = high_resolution_clock::now();
            us = duration_cast<microseconds>(now.time_since_epoch());
            tsr = us.count()-tsr_base;  //zero based clock
            if (DBG) cout << tsr+1 << " [cpu_emu " << rcv_prt << "]: " << " Syncrhonizing us.count = " << us.count() << " tsr_base = " << tsr_base << " tsr = " << tsr << endl;
            if(vrbs) cout << tsr  << " [cpu_emu " << rcv_prt << "]: " << " synchronized all threads..." << endl;
        }

        if(!trmnl) {
            if(DBG) cout << tsr+2  << " [cpu_emu " << rcv_prt << "]: " << " Forwarding "
                          << " request " << frame_num << " from port " + string("tcp://") + dst_ip + ':' +  to_string(rcv_prt)
                          << " to port " + string("tcp://") + dst_ip + ':' +  to_string(dst_prt) << " (" << frame_num << ')' << endl;
            //forward to next hop
            // Send a message to the destination
            size_t outSz = 8*otmemGB*1.024*1.024*1.024*1e9; //output size in bits

            send_result_t sr;
            {
	        // Send  "frame"
                //represents harvested data
                auto x = std::clamp(sd_10pcnt(gen), 0.7, 1.3);  //+/- 3 sd
                std::vector<uint8_t> payload(outSz*x/8);  //represents harvested data
                if(vrbs) cout << tsr+1 << " [cpu_emu " << rcv_prt << "]: " << "serializing packet for request_nbr " << request_nbr << endl;
                auto data = serialize_packet(8*payload.size(), us.count(), parsed.stream_id, parsed.frame_num, payload);
                if(vrbs) cout << tsr+2 << " [cpu_emu " << rcv_prt << "]: " << "serializing success for frame_num " << parsed.frame_num << endl;
                zmq::message_t message(data.size());
                std::memcpy(message.data(), data.data(), data.size());
                sr = dst_sckt.send(message, zmq::send_flags::none);
                if (!sr) std::cerr << tsr << " [cpu_emu " << rcv_prt << "]:  Failed to send" << endl;
                if (sr.has_value()) std::cout << tsr << " [cpu_emu " << rcv_prt << "]: Bytes sent = " << sr.value() << endl;

                if(vrbs) cout << tsr+3 << " [cpu_emu " << rcv_prt << "]:  Sending frame size = " << outSz << " (" 
                              << frame_num << ')' << " to " << dst_prt << " at " << tsr << " with code " << endl;
                if(DBG) cout << tsr+4 << "[cpu_emu " << rcv_prt << "]: " << " output Num written (" << request_nbr << ") "  
                             << sr.value() << " (" << request_nbr << ')' << endl;
                if(sr.value() != HEADER_SIZE + payload.size()) cout << tsr+3 << "[cpu_emu " << rcv_prt << "]: " << " Destination data incorrect size(" << request_nbr << ") "  << endl;
            }
        }
        if(vrbs) cout << tsr + 4 << " [cpu_emu " << rcv_prt << "]:  done (" << frame_num << ')' << endl;
 
        mnBfSz = (request_nbr-1)*mnBfSz/request_nbr + bufSiz/request_nbr; //incrementally update mean receive size
        // Record end time
        if(request_nbr < 10) continue; //warmup
        if(vrbs) std::cout << tsr + 5 << " [cpu_emu " << rcv_prt << "]: " << " Measured latencies: tsc = " << tsc << " tsn = " << tsn 
                           << " (" << frame_num << ") mxTsc = " << mxTsc << endl;
        if(vrbs) std::cout << tsr + 6 << " [cpu_emu " << rcv_prt << "]: " << " Measured frame rate " << float(request_nbr)/(1e-6*float(tsr)) 
                           << " frame Hz." << " for " << frame_num << " frames" << endl;
        if(vrbs) std::cout << tsr + 7 << " [cpu_emu " << rcv_prt << "]: " << " Measured bit rate " << 1e-6*float(request_nbr*mnBfSz)/(1e-6*float(tsr)) 
                           << " MHz mnBfSz " << mnBfSz << " (" << frame_num << ')' << endl;
        if(vrbs) cout << tsr + 8 << " [cpu_emu " << rcv_prt << "]:  Missed frames: " << msdFrms << endl;
        if(vrbs) cout << tsr + 9 << " [cpu_emu " << rcv_prt << "]:  Missed frame ratio: " << float(msdFrms)/float(frame_num) 
                      << " frame_num " << frame_num  << " request_nbr " << request_nbr << endl;
        cout  << tsr + 10 << " [cpu_emu " << rcv_prt << "]:  stats computed ..." << endl;
    } //main loop
    cout  << tsr + 11 << " [cpu_sim " << rcv_prt << "]:  " << (trmnl?"Terminal":"Non Terminal") << " exiting: mxTsc = " << mxTsc << endl;
    std::cout.flush();
    std::cerr.flush();
    return 0;
}
