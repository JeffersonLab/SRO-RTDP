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

const float  oneToU = 1e6;
const float  uToOne = 1/oneToU;
const float  KtoOne = 1e3;
const float  MtoOne = 1e6;
const float  GtoOne = 1e9;
const float  oneToK = 1/KtoOne;
const float  oneToM = 1/MtoOne;
const float  oneToG = 1/GtoOne;
const float  GtoK   = 1e6;
const float  KtoG   = 1/GtoK;
const float  GtoM   = 1e3;
const float  MtoG   = 1/GtoM;
const float  btoB   = 1e-1;
const float  Btob   = 1/btoB;
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
        -i subscription address (string)  \n\
        -m thread memory footprint in GB  \n\
        -o output size in GB  \n\
        -p subscription port (default = 8888)  \n\
        -r publish port (default = 8888)  \n\
        -s sleep versus burn cpu = 0/1 (default = false = 0)  \n\
        -t num threads (default = 10)  \n\
        -v verbose = 0/1 (default = false = 0)  \n\
        -y yaml config file  \n\
        -z act as terminal node = 0/1 (default = false = 0)  \n\n";

    cout << "[cpu_emu]: " << usage_str;
}

// Computational Function to emulate/stimulate processing load/latency, etc. 
void func(size_t nmrd_B, size_t cmpLt_sGB, double memGB, bool psdS, uint16_t tag, bool vrbs=false) 
{ 
    const float ts(cmpLt_sGB*nmrd_B/(GtoOne)); //reqd timespan in seconds
    const float tsms(ts/oneToK); //reqd timespan in milliseconds
    const float tsus(ts/oneToM); //reqd timespan in microseconds
    const float tsns(ts/oneToG);    //reqd timespan in nanoseconds
    size_t memSz = memGB*sz1K*sz1K*sz1K; //memory footprint in bytes
    if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Allocating " << memSz << " bytes ..." << endl;
    if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Allocating " << float(memSz/(sz1K*sz1K*sz1K)) << " Gbytes ..." << endl;

    double* x;
    try {
        x = new double[memSz];
        if(vrbs) std::cout << "Memory allocation for " << memSz << " succeeded.\n";
    } catch (const std::bad_alloc& e) {
        if(vrbs) std::cout << "Memory allocation for " << memSz << " failed: " << e.what() << '\n';
        exit(1);
    }    
    //usefull work emulation 
    if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Threading for " << ts   << " secs ..."  << " size " << nmrd_B << endl;
    if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Threading for " << tsms << " msecs ..." << " size " << nmrd_B << endl;
    if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Threading for " << tsus << " usecs ..." << " size " << nmrd_B << endl;
    if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Threading for " << tsns << " nsecs ..." << " size " << nmrd_B << endl;
    if(psdS) {
        auto cms_ns = chrono::nanoseconds(size_t(round(tsns)));
        if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Sleep_Threaded for " << ts           << " secs ..."  << " size " << nmrd_B << endl;
        if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Sleep_Threaded for " << tsms         << " msecs ..." << " size " << nmrd_B << endl;
        if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Sleep_Threaded for " << tsus         << " usecs ..." << " size " << nmrd_B << endl;
        if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Sleep_Threaded for " << tsns         << " nsecs ..." << " size " << nmrd_B << endl;
        if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Sleeping for "       << float(cms_ns.count())*oneToG/oneToK  << " msecs ..." << " size " << nmrd_B << endl;
        this_thread::sleep_for(cms_ns);
    }else{
        auto ts = (cmpLt_sGB*nmrd_B/GtoOne);
        //high_resolution_clock::time_point start_time = std::chrono::high_resolution_clock::now();
        auto start_time = std::chrono::high_resolution_clock::now();
        if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Burning ...";
        
        double fracsecs, secs;
        fracsecs = modf (ts , &secs);
        if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " secs = " << secs << " fracsecs = " << fracsecs << endl;
        size_t strtMem = 0;
        auto end_time = std::chrono::high_resolution_clock::now();
        duration<double> time_span = duration_cast<duration<double>>(end_time - start_time);
        while (time_span.count() < ts) { 
            for (size_t i = strtMem; i<min(strtMem + sz1K, memSz); i++) { x[i] = tanh(i); }
            strtMem += sz1K;
            if(strtMem > memSz - sz1K) strtMem = 0;
            end_time = std::chrono::high_resolution_clock::now();
            time_span = duration_cast<duration<double>>(end_time - start_time);
            if(DBG) cout << "[cpu_emu " << tag << " ]: " << " Checking " << time_span.count() << " against "<< ts  << endl;
        }
        auto tsc = time_span.count();
        if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Threaded for " << tsc        << " secs "  << " size " << nmrd_B << endl;
        if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Threaded for " << tsc*oneToK << " msecs " << " size " << nmrd_B << endl;
        if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Threaded for " << tsc*oneToM << " usecs " << " size " << nmrd_B << endl;
        if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Threaded for " << tsc*oneToG << " nsecs " << " size " << nmrd_B << endl;
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
    //lbls.push_back("sbscrptn_ip"); lbls.push_back("sub_prt"); lbls.push_back("pub_prt");
    lbls.push_back("sleep"); lbls.push_back("threads"); lbls.push_back("latency");
    lbls.push_back("mem_footprint"); lbls.push_back("output_size"); lbls.push_back("verbose");
    //lbls.push_back("terminal"); lbls.push_back("frame_cnt");
    
    auto it = lbls.begin(); //a hack to get the type

    while (yaml_parser_parse(&parser, &event)) {
        switch (event.type) {
        case YAML_NO_EVENT:
            break;
        case YAML_STREAM_START_EVENT:
            if(DBG) cout << 0 << " [cpu_emu]: Stream started " << endl;
            break;
        case YAML_STREAM_END_EVENT:
            if(DBG) cout << 0 << " [cpu_emu]: Stream ended " << endl;
            break;
        case YAML_DOCUMENT_START_EVENT:
            if(DBG) cout << 0 << " [cpu_emu]: Document started " << endl;
            break;
        case YAML_DOCUMENT_END_EVENT:
            if(DBG) cout << 0 << " [cpu_emu]: Document ended " << endl;
            break;
        case YAML_MAPPING_START_EVENT:
            if(DBG) cout << 0 << " [cpu_emu]: Mapping started " << endl;
            break;
        case YAML_MAPPING_END_EVENT:
            if(DBG) cout << 0 << " [cpu_emu]: Mapping ended " << endl;
            break;
        case YAML_SEQUENCE_START_EVENT:
            if(DBG) cout << 0 << " [cpu_emu]: Sequence started " << endl;
            break;
        case YAML_SEQUENCE_END_EVENT:
            if(DBG) cout << 0 << " [cpu_emu]: Sequence ended " << endl;
            break;
        case YAML_SCALAR_EVENT:
            s = (const char*)event.data.scalar.value;
            it = find(lbls.begin(), lbls.end(), s);
            if (it != lbls.end()) {
                if(DBG) cout << 0 << " [cpu_emu " << tag << " ]: " << " Label: " << s << endl;
                lbl_stk.push(s);
            } else {
                s1 = lbl_stk.top();
                if(DBG) cout << 0 << " [cpu_emu " << tag << " ]: " << " Label: " << s1 << " Datum: " << s << endl;
                mymap[s1] = s;
                lbl_stk.pop();
            }
            break;
        default:
            if(DBG) cout << 0 << " [cpu_emu " << tag << " ]: " << " (Default)" << endl;
            break;
        }

        if(event.type == YAML_STREAM_END_EVENT) break;
        yaml_event_delete(&event);
    }
    if(DBG) cout << 0 << " [cpu_emu " << tag << " ]: " << " All done parsing, got this:" << endl;
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
    char     sub_ip[INET6_ADDRSTRLEN];	// subscription ip
    uint16_t pub_prt = 8888;  // publication port default
    uint16_t sub_prt = 8888;  // subscription port default
    auto     nmThrds = 5;     // default
    bool     vrbs    = false; // verbose ?
    // 500 seconds/(input GB) computational latency for 60kB CLAS12
    // 0.5 microseconds/byte
    // 0.5 seconds per megabyte
    double   cmpLt_sGB  = 500;   // seconds/(input GB) computational latency
    double   cmpLt_usB  = 0.5;   // usec/(input B) computational latency
    double   cmpLt_sMB  = 0.5;   // seconds/(input MB) computational latency
    double   memGB      = 0.01;    // thread memory footprint in GB
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
            if(DBG) cout << "[cpu_emu " << sub_prt << "]: " << " -b " << cmpLt_sGB << endl;
            break;
        case 'i':
            strcpy(sub_ip, (const char *) optarg) ;
            psdI = true;
            if(DBG) cout << "[cpu_emu " << sub_prt << "]: " << " -i " << sub_ip << endl;
            break;
        case 'f':
            frame_cnt = (uint64_t) atoi((const char *) optarg) ;
            psdF = true;
            if(DBG) cout << "[cpu_emu " << sub_prt << "]: " << " -f " << frame_cnt << endl;
            break;
        case 'm':
            memGB = (double) atof((const char *) optarg) ;
            psdM = true;
            if(DBG) cout << "[cpu_emu " << sub_prt << "]: " << " -m " << memGB << endl;
            break;
        case 'o':
            otmemGB = (double) atof((const char *) optarg) ;
            psdO = true;
            if(DBG) cout << "[cpu_emu " << sub_prt << "]: " << " -o " << otmemGB << endl;
            break;
        case 'p':
            sub_prt = (uint16_t) atoi((const char *) optarg) ;
            psdP = true;
            if(DBG) cout << "[cpu_emu " << sub_prt << "]: " << " -p " << sub_prt << endl;
            break;
        case 'r':
            pub_prt = (uint16_t) atoi((const char *) optarg) ;
            psdR = true;
            if(DBG) cout << "[cpu_emu " << sub_prt << "]: " << " -r " << pub_prt << endl;
            break;
        case 's':
            psdS = true;
            if(DBG) cout << "[cpu_emu " << sub_prt << "]: " << " -s " << endl;
            break;
        case 't':
            nmThrds = (uint16_t) atoi((const char *) optarg) ;
            psdT = true;
            if(DBG) cout << "[cpu_emu " << sub_prt << "]: " << " -t " << nmThrds << endl;
            break;
        case 'v':
            vrbs = (bool) atoi((const char *) optarg) ;
            psdV = true;
            if(DBG) cout << "[cpu_emu " << sub_prt << "]: " << " -v " << vrbs << endl;
            break;
        case 'y':
            yfn = (const char *) optarg ;
            psdY = true;
            if(DBG) cout << "[cpu_emu " << sub_prt << "]: " << " -y " << yfn << endl;
            break;
        case 'z':
            psdZ = true;
            zed = (uint16_t) atoi((const char *) optarg) ;
            trmnl = zed==1?true:false;
            if(DBG) cout << "[cpu_emu " << sub_prt << "]: " << " -z " << zed << endl;
            break;
        case '?':
            cout << "[cpu_emu " << sub_prt << "]: " << " Unrecognised option: " << optopt << endl;
            Usage();
            exit(1);
        }
    }

    if(DBG) cout << endl;

    if(!(psdI && psdP && psdZ) || (!trmnl && !psdR)) {Usage(); exit(1);}

    if (psdY) {//parse the yaml file if given        
        parse_yaml(yfn.c_str(), pub_prt, vrbs);
        //cmd line parms overide yaml file settings (which are otherwise in the map)
        if(!psdB) cmpLt_sGB = stof(mymap["latency"]);
        //if(!psdI) strcpy(sub_ip, mymap["sbscrptn_ip"].c_str());
        if(!psdM) memGB    = stof(mymap["mem_footprint"]);
        if(!psdO) otmemGB  = stof(mymap["output_size"]);
        //if(!psdP) sub_prt  = stoi(mymap["sub_prt"]);
        //if(!(psdR && psdZ)) pub_prt  = stoi(mymap["pub_prt"]);
        if(!psdS) psdS     = stoi(mymap["sleep"]) == 1;
        if(!psdT) nmThrds  = stoi(mymap["threads"]);
        if(!psdV) vrbs     = stoi(mymap["verbose"]);
        //if(!trmnl) trmnl   = stoi(mymap["terminal"]) == 1;
        //if(!psdF) frame_cnt= stoi(mymap["frame_cnt"]) == 1;
    }    
    ////////
    if(vrbs) cout << "[cpu_emu "   << sub_prt << " ]: "
                << " Operating with yaml = " << (psdY?yfn:"N/A")
                << "\tcmpLt_sGB = " << cmpLt_sGB << "\tsub_ip = "  << sub_ip
                << "\tsub_prt = "   << sub_prt   << "\tpub_prt = " << pub_prt                               
                << "\tmemGB = "     << memGB     << "\totmemGB = " << otmemGB << "\tsleep = "   << psdS
                << "\tnmThrds = "   << nmThrds   << "\tverbose = " << vrbs << "\tyfn = " << (psdY?yfn:"N/A")
                << "\tterminal = "  << trmnl     << '\n';

    // RNG for latency variance generation using a Gaussian (normal) distribution to generate a scaling factor 
    // centered around 1.0, with a standard deviation chosen so that ~99.7% of values fall 
    // in the 70% to 130% range (i.e., ±3σ ≈ 30%):

    static std::random_device rd;
    static std::mt19937 gen(rd());

    // Mean = 1.0, Std Dev = 0.1 gives 99.7% of samples in [0.7, 1.3]
    static std::normal_distribution<> sd_10pcnt(1.0, 0.1);

    //  Prepare our subscription context and socket
    context_t sub_cntxt(1);
    if(vrbs) cout << "[cpu_emu " << sub_prt << "]: " << " Defining sub context" << endl;

    socket_t sub_sckt(sub_cntxt, socket_type::sub);
    if(vrbs) cout << "[cpu_emu " << sub_prt << "]: " << " Defining SUB protocol rcv socket" << endl;
    //sub_sckt.set(zmq::sockopt::rcvhwm, int(0)); // queue length /////////////////////////////////////////////////////

    sub_sckt.connect(string("tcp://") + sub_ip + ':' + to_string(sub_prt));
    if(vrbs) cout << "[cpu_emu " << sub_prt << "]: " << " Subscribing to " << sub_ip << ':' + to_string(sub_prt) << endl;
    // Subscribe to all messages (empty topic)
    sub_sckt.set(zmq::sockopt::subscribe, "");
    if(vrbs) cout << "[cpu_emu " << sub_prt << "]: " << " subscibing" << endl;

    //  Prepare our publication context and socket
    context_t pub_cntxt(1);
    socket_t pub_sckt(pub_cntxt, socket_type::pub);
    if(!trmnl) {
    	if(vrbs) cout << "[cpu_emu " << sub_prt << "]: " << " Publishing on port " << to_string(pub_prt) << endl;
    	pub_sckt.bind(string("tcp://*:") + to_string(pub_prt));
    	pub_sckt.set(zmq::sockopt::sndhwm, int(0)); // queue length
    }
    
    uint32_t request_nbr = 0;
    double   mnBfSz_B      = 0; //mean receive Size bytes
    uint64_t bufSiz_B      = 0; //bytes
    uint64_t cmBufSiz_B    = 0; //bytes - cummulative
    uint64_t tsr_us         = 0; // system hi-res clock in microseconds since epoch
    uint64_t tsr_base_us    = 0; // base system hi-res clock in microseconds since epoch
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
        //if(vrbs) cout << "[cpu_emu " << sub_prt << "]: " << " Setting up request message ..." << endl;
        message_t request;

        //  Wait for next request from client
        if(vrbs) cout << tsr_us << " [cpu_emu " << sub_prt << "]: " << " Waiting for source ..." << endl;
        
        recv_result_t rtcd;
        
        rtcd = sub_sckt.recv (request, recv_flags::none);
        request_nbr++;
        auto parsed = deserialize_packet(tsr_us, pub_prt, static_cast<uint8_t*>(request.data()), request.size());
        now = high_resolution_clock::now();
        us = duration_cast<microseconds>(now.time_since_epoch());
        if(request_nbr == 1) {
            tsr_base_us = parsed.timestamp_us; // establish zero offset clock from chain source
            if (DBG) cout << 0 << " [cpu_emu " << sub_prt << "]: " << " us.count = " << us.count() << " tsr_base_us = " << tsr_base_us << " tsr_us = " << tsr_us << endl;
        }
        tsr_us = us.count()-tsr_base_us;  //zero based clock usecs
        if(DBG) cout << tsr_us+1 << " [cpu_emu " << sub_prt << "]: " << "deserializing packet for request_nbr " << request_nbr << endl;
        if(DBG) cout << tsr_us+2 << " [cpu_emu " << sub_prt << "]: " << "deserializing success for frame_num " << parsed.frame_num << endl;
        bufSiz_B = 8*rtcd.value(); //bits


        if(DBG) cout << tsr_us+1 << " [cpu_emu " << sub_prt << "]: " << "deserializing packet ... request.size() " << request.size() << " HEADER_SIZE = " << HEADER_SIZE << endl;
        if(DBG) cout << tsr_us+3 << " [cpu_emu " << sub_prt << "]: " << "bufSiz_B = " << bufSiz_B << " parsed.size = " << parsed.size_B 
                    << " sizeof(struct DeserializedPacket) = " << sizeof(struct DeserializedPacket) << endl;

        if(vrbs) std::cout << tsr_us << " [cpu_emu " << sub_prt << "]: " << " recd " << parsed.frame_num << endl;
        if(vrbs)  cout << tsr_us << " [cpu_emu " << sub_prt << "]: " << " Received request "
                      << request_nbr << " from port " + string("tcp://") + sub_ip + ':' +  to_string(pub_prt)
                      << " rtcd = " << int(rtcd.value()) << " from client" << endl;
                      
        if(vrbs) cout << tsr_us+1  << " [cpu_emu " << sub_prt << "]: " << " frame size = "
                      << "(actual) " << bufSiz_B << " bytes " << bufSiz_B*oneToG << " GB "
                      << " from client " << "ts = " << tsr_us << " (" << request_nbr << ')' << endl;
        if (DBG) cout << tsr_us+4 << " [cpu_emu " << sub_prt << "]: " << " Recving us.count = " << us.count() << " tsr_base_us = " << tsr_base_us << " tsr_us = " << tsr_us << endl;
        //assert(bufSiz_B == parsed.size-sizeof(struct DeserializedPacket)); //bits
        stream_id =  parsed.stream_id ;
        frame_num =  parsed.frame_num ;
        if(frame_num > lst_frm_nm + 1) msdFrms += frame_num - (lst_frm_nm + 1);
        lst_frm_nm = frame_num;

        //  Do some 'work'
        // load (or emulate load on) system with ensuing work
        {

            tsc = cmpLt_sGB*(parsed.size_B)*oneToK/GtoOne; //reqd timespan in microseconds
            mxTsc = max(mxTsc,tsc);

            vector<thread> threads;

            for (int i=1; i<=nmThrds; ++i)  //start the threads
                threads.push_back(thread(func, parsed.size_B, cmpLt_sGB, memGB, psdS, pub_prt, vrbs));

            for (auto& th : threads) th.join();
            //reqd computational timespan in usec    
            now = high_resolution_clock::now();
            us = duration_cast<microseconds>(now.time_since_epoch());
            tsr_us = us.count()-tsr_base_us;  //zero based clock
            if (DBG) cout << tsr_us+1 << " [cpu_emu " << sub_prt << "]: " << " Syncrhonizing us.count = " << us.count() << " tsr_base_us = " << tsr_base_us << " tsr_us = " << tsr_us << endl;
            if(vrbs) cout << tsr_us  << " [cpu_emu " << sub_prt << "]: " << " synchronized all threads..." << endl;
        }

        if(!trmnl) {
            if(DBG) cout << tsr_us+2  << " [cpu_emu " << sub_prt << "]: " << " Forwarding "
                          << " request " << frame_num << " from port " + string("tcp://") + sub_ip + ':' +  to_string(pub_prt)
                          << " to port " + string("tcp://") + sub_ip + ':' +  to_string(sub_prt) << " (" << frame_num << ')' << endl;
            // Publish a message for subscribers
            size_t outSz = 8*otmemGB*1.024*1.024*1.024*1e9; //output size in bits

            send_result_t sr;
            {
	        // Send  "frame"
                //represents harvested data
                auto x = std::clamp(sd_10pcnt(gen), 0.7, 1.3);  //+/- 3 sd
                std::vector<uint8_t> payload(outSz*x);  //represents harvested data
                if(DBG) cout << tsr_us+1 << " [cpu_emu " << sub_prt << "]: " << "serializing packet for request_nbr " << request_nbr << endl;
                auto data = serialize_packet(tsr_us, pub_prt, payload.size(), parsed.timestamp_us, parsed.stream_id, parsed.frame_num, payload);
                if(DBG) cout << tsr_us+2 << " [cpu_emu " << sub_prt << "]: " << "serializing success for frame_num " << parsed.frame_num << endl;
                zmq::message_t message(data.size());
                std::memcpy(message.data(), data.data(), data.size());
                sr = pub_sckt.send(message, zmq::send_flags::none);
                if (!sr) std::cerr << tsr_us << " [cpu_emu " << sub_prt << "]:  Failed to send" << endl;
                if (vrbs && sr.has_value()) std::cout << tsr_us << " [cpu_emu " << sub_prt << "]: Bytes sent = " << sr.value() << endl;

                if(vrbs) cout << tsr_us+3 << " [cpu_emu " << sub_prt << "]:  Sending frame size = " << outSz << " (" 
                              << frame_num << ')' << " to " << pub_prt << " at " << tsr_us << " with code " << endl;
                if(DBG) cout << tsr_us+4 << "[cpu_emu " << sub_prt << "]: " << " output Num written (" << request_nbr << ") "  
                             << sr.value() << " (" << request_nbr << ')' << endl;
                if(sr.value() != HEADER_SIZE + payload.size()) cout << tsr_us+3 << "[cpu_emu " << sub_prt << "]: " << " sbscrptn_ip data incorrect size(" << request_nbr << ") "  << endl;
            }
        }
        if(vrbs) cout << tsr_us + 4 << " [cpu_emu " << sub_prt << "]:  done (" << frame_num << ')' << endl;
 
        mnBfSz_B = (request_nbr-1)*mnBfSz_B/request_nbr + bufSiz_B/request_nbr; //incrementally update mean receive size
        // Record end time
        //if(request_nbr < 10) continue; //warmup
        if(vrbs) std::cout << tsr_us + 5 << " [cpu_emu " << sub_prt << "]: " << " Measured latencies: tsc = " << tsc << " tsn = " << tsn 
                           << " (" << frame_num << ") mxTsc = " << mxTsc << endl;
        if(vrbs) std::cout << tsr_us + 6 << " [cpu_emu " << sub_prt << "]: " << " Measured frame rate " << float(request_nbr)/(float(tsr_us)*oneToM) 
                           << " frame Hz." << " for " << frame_num << " frames" << endl;
        if(vrbs) std::cout << tsr_us + 7 << " [cpu_emu " << sub_prt << "]: " << " Measured bit rate " << 1e-6*float(request_nbr*mnBfSz_B)/(float(tsr_us)*oneToM)
                           << " MHz mnBfSz_B " << mnBfSz_B << " (" << frame_num << ')' << endl;
        if(vrbs) cout << tsr_us + 8 << " [cpu_emu " << sub_prt << "]:  Missed frames: " << msdFrms << endl;
        if(vrbs) cout << tsr_us + 9 << " [cpu_emu " << sub_prt << "]:  Missed frame ratio: " << float(msdFrms)/float(frame_num) 
                      << " frame_num " << frame_num  << " request_nbr " << request_nbr << endl;
        cout  << tsr_us + 10 << " [cpu_emu " << sub_prt << "]:  stats computed ..." << endl;
    } //main loop
    cout  << tsr_us + 11 << " [cpu_sim " << pub_prt << "]:  " << (trmnl?"Terminal":"Non Terminal") << " exiting: mxTsc = " << mxTsc << endl;
    std::cout.flush();
    std::cerr.flush();
    return 0;
}
