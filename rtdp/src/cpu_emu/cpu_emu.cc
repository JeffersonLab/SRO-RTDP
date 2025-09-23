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
#include <new> // for bad_alloc
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

const size_t swtchLtn_uS = 1; //switch latency

void   Usage()
{
    char usage_str[] =
        "\nUsage: \n\
        -h help  \n\
        -b Processing latency in nsec/byte frame size \n\
        -f total frames sender will send  \n\
        -i subscription address (string)  \n\
        -m thread memory footprint in GB  \n\
        -o output size in GB  \n\
        -p subscription port (default = 8888)  \n\
        -r publish port (default = 8889)  \n\
        -s sleep versus burn cpu = 0/1 (default = false = 0)  \n\
        -t num threads (default = 10)  \n\
        -v verbose = 0/1 (default = false = 0)  \n\
        -y yaml config file  \n\
        -z act as terminal node = 0/1 (default = false = 0)  \n\n";

    cout << "[cpu_emu]: " << usage_str;
    cout << "Either -i required or -y" << "\n\n";

}

#include <iostream>
#include <random>
#include <algorithm>  // for std::max

double sample_gamma(double mean, double stdev, std::mt19937 &gen) {
    // Calculate shape (k) and scale (theta)
    double shape = (mean * mean) / (stdev * stdev);
    double scale = (stdev * stdev) / mean;

    // Gamma distribution
    std::gamma_distribution<double> gamma(shape, scale);

    // Draw sample and clip at lower bound = mean
    double value = gamma(gen);
    return value; //std::max(value, mean);
}

#if 0 //==============================
int main() {
    // Parameters
    double mean = 10.0;
    double stdev = 3.0;

    // Random generator
    std::random_device rd;
    std::mt19937 gen(rd());

    // Generate clipped samples
    for (int i = 0; i < 10; i++) {
        double x = sample_gamma(mean, stdev, gen);
        std::cout << x << "\n";
    }

    std::cout << "============" << '\n';
    // Sample until > mean
    for (int i = 0; i < 10; i++) {
        double value;
        do {
            value = sample_gamma(mean, stdev, gen);
        } while (value < mean);
        std::cout << value << '\n';
    }
    return 0;
}

#endif //=========================

// RNG for latency variance generation using a Gamma distribution with lower bound equal to mean 

static random_device rd;
static mt19937 gen(rd());

// Computational Function to emulate/stimulate processing load/latency, etc. 
void func(size_t nmrd_B, size_t cmpLt_S_GB, double mem_GB, bool wlSlp, uint16_t tag, bool vrbs=false) 
{ 
    const float ts_S(cmpLt_S_GB*nmrd_B/(G_1)); //reqd timespan in seconds   ////// fix this for proper units
    size_t memSz = mem_GB*sz1G; //memory footprint in bytes
    if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Allocating " << memSz << " bytes ..." << endl;
    if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Allocating " << float(memSz/(sz1K*sz1K*sz1K)) << " Gbytes ..." << endl;

    double* x;
    try {
        x = new double[memSz];
        if(vrbs) cout << "Memory allocation for " << memSz << " succeeded.\n";
    } catch (const bad_alloc& e) {
        if(vrbs) cout << "Memory allocation for " << memSz << " failed: " << e.what() << '\n';
        exit(1);
    }    
    //usefull work emulation 
    if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Threading for " << ts_S   << " secs ..."  << " size " << nmrd_B << endl;
    if(wlSlp) {
        auto cms_us = chrono::nanoseconds(size_t(round(ts_S*one_n)));
        if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Sleep_Threaded for " << ts_S  << " secs ..."  << " size " << nmrd_B << endl;
        if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Sleeping for " << float(cms_us.count())*u_m  << " msecs ..." << " size " << nmrd_B << endl;
        this_thread::sleep_for(cms_us);
    }else{
        auto ts_S = (cmpLt_S_GB*nmrd_B/G_1);
        //high_resolution_clock::time_point start_time = chrono::high_resolution_clock::now();
        auto start_time_hrc = chrono::high_resolution_clock::now();
        if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Burning CPU ...";
        
        double fracsecs_S, secs;
        fracsecs_S = modf (ts_S , &secs);
        if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " secs = " << secs << " fracsecs_S = " << fracsecs_S << endl;
        size_t strtMem = 0;
        auto end_time_hrc = chrono::high_resolution_clock::now();
        duration<double> time_span_Sd = duration_cast<duration<double>>(end_time_hrc - start_time_hrc);
        while (time_span_Sd.count() < ts_S) { 
            for (size_t i = strtMem; i<min(strtMem + sz1K, memSz); i++) { x[i] = tanh(i); } //touch all allocated memory
            strtMem += sz1K;
            if(strtMem > memSz - sz1K) strtMem = 0;
            end_time_hrc = chrono::high_resolution_clock::now();
            time_span_Sd = duration_cast<duration<double>>(end_time_hrc - start_time_hrc);
            if(DBG) cout << "[cpu_emu " << tag << " ]: " << " Checking " << time_span_Sd.count() << " against "<< ts_S  << endl;
        }
        if(vrbs) cout << "[cpu_emu " << tag << " ]: " << " Threaded for " << time_span_Sd.count() << " secs "  << " size " << nmrd_B << endl;
    }
    delete[] x;
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
    lbls.push_back("latency"); lbls.push_back("mem_footprint"); lbls.push_back("output_size");    
    lbls.push_back("sbscrptn_ip"); lbls.push_back("sub_prt"); lbls.push_back("pub_prt");
    lbls.push_back("sleep"); lbls.push_back("threads"); lbls.push_back("verbose");
    lbls.push_back("terminal"); lbls.push_back("frame_cnt");
    
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

    bool     psdB=false, psdI=false, psdM=false, psdO=false, psdY=false;
    bool     psdP=false, psdR=false, psdS=false, psdT=false, psdV=false;
    bool     psdZ=false, psdF=false;
    string   yfn = "cpu_emu.yaml";
    char     sub_ip[INET6_ADDRSTRLEN] = "127.0.0.1";	// subscription ip
    uint16_t sub_prt = 8888;  // subscription port default
    uint16_t pub_prt = 8889;  // publication port default
    auto     nmThrds = 1;     // default
    bool     vrbs    = true; // verbose ?
    bool     wlSlp   = false; // will sleep versus burn cpu ?
    bool     trmnl   = false; // am I a terminal node or a pass-thru ?
    // 500 seconds/(input GB) computational latency for 60kB CLAS12
    // 0.5 microseconds/byte
    // 0.5 seconds per megabyte
    double   cmpLt_S_GB = 500;      // seconds/(input GB) computational latency
    double   mem_GB     = 0.01;     // thread memory footprint in GB
    double   otmem_GB   = 0.000057; // program output in GB
    uint64_t frame_cnt  = 100;      //total frames sender will send

    cout << fixed << setprecision(7);  // 6 decimal places            

    while ((optc = getopt(argc, argv, "hb:f:i:m:o:p:r:s:t:v:y:z:")) != -1)
    {
        switch (optc)
        {
        case 'h':
            Usage();
            exit(1);
        case 'b':
            cmpLt_S_GB = (double) atof((const char *) optarg) ; ////// fix this for proper units
            psdB = true;
            if(DBG) cout << "[cpu_emu " << sub_prt << "]: " << " -b " << cmpLt_S_GB << endl;
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
            mem_GB = (double) atof((const char *) optarg) ;
            psdM = true;
            if(DBG) cout << "[cpu_emu " << sub_prt << "]: " << " -m " << mem_GB << endl;
            break;
        case 'o':
            otmem_GB = (double) atof((const char *) optarg) ;
            psdO = true;
            if(DBG) cout << "[cpu_emu " << sub_prt << "]: " << " -o " << otmem_GB << endl;
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
            psdS  = true;
            wlSlp = bool(atoi((const char *) optarg));
            if(DBG) cout << "[cpu_emu " << sub_prt << "]: " << " -s " << wlSlp << endl;
            break;
        case 't':
            nmThrds = (uint16_t) atoi((const char *) optarg) ;
            psdT    = true;
            if(DBG) cout << "[cpu_emu " << sub_prt << "]: " << " -t " << nmThrds << endl;
            break;
        case 'v':
            vrbs = atoi((const char *) optarg) == 1;
            psdV = true;
            if(DBG) cout << "[cpu_emu " << sub_prt << "]: " << " -v " << vrbs << endl;
            break;
        case 'y':
            yfn  = (const char *) optarg ;
            psdY = true;
            if(DBG) cout << "[cpu_emu " << sub_prt << "]: " << " -y " << yfn << endl;
            break;
        case 'z':
            psdZ = true;
            trmnl = atoi((const char *) optarg) == 1;
            if(DBG) cout << "[cpu_emu " << sub_prt << "]: " << " -z " << trmnl << endl;
            break;
        case '?':
            cout << "[cpu_emu " << sub_prt << "]: " << " Unrecognised option: " << optopt << endl;
            Usage();
            exit(1);
        }
    }

    if(DBG) cout << endl;

    if(!(psdI  || psdY)) {Usage(); exit(1);}

    if (psdY) {//parse the yaml file if given        
        parse_yaml(yfn.c_str(), pub_prt, vrbs);
        //if not passed as arg, retrieve from yaml map)
        if(!psdB) cmpLt_S_GB = stof(mymap["latency"]);
        if(!psdM) mem_GB     = stof(mymap["mem_footprint"]);
        if(!psdO) otmem_GB   = stof(mymap["output_size"]);
        if(!psdI) strcpy(sub_ip, mymap["sbscrptn_ip"].c_str());
        if(!psdP) sub_prt  = stoi(mymap["sub_prt"]);
        if(!(psdR && psdZ)) pub_prt  = stoi(mymap["pub_prt"]);
        if(!psdS) wlSlp    = (bool) stoi(mymap["sleep"]) == 1;
        if(!psdT) nmThrds  = stoi(mymap["threads"]);
        if(!psdV) vrbs     = (bool) stoi(mymap["verbose"]) == 1;
        if(!psdZ) trmnl    = (bool) stoi(mymap["terminal"]) == 1;
        if(!psdF) frame_cnt= stoi(mymap["frame_cnt"]);
    }    
    ////////
    if(vrbs) cout << "[cpu_emu "   << sub_prt << " ]: "
                  << " Operating with yaml = "      << (psdY?yfn:"N/A")
                  << "\tcmpLt_sGB = " << cmpLt_S_GB << "\tsub_ip = "  << sub_ip
                  << "\tsub_prt = "   << sub_prt    << "\tpub_prt = " << pub_prt                               
                  << "\tmem_GB = "     << mem_GB      << "\totmem_GB = " << otmem_GB << "\tsleep = " << wlSlp
                  << "\tnmThrds = "   << nmThrds    << "\tverbose = " << vrbs << "\tyfn = " << (psdY?yfn:"N/A")
                  << "\tterminal = "  << trmnl      << '\n';


    //  Prepare our subscription context and socket
    context_t sub_cntxt(1);
    if(vrbs) cout << "[cpu_emu " << sub_prt << "]: " << " Defining sub context" << endl;

    socket_t sub_sckt(sub_cntxt, socket_type::sub);
    if(vrbs) cout << "[cpu_emu " << sub_prt << "]: " << " Defining SUB protocol rcv socket" << endl;
    sub_sckt.set(zmq::sockopt::rcvhwm, int(0)); // queue length: 1 = drop unread, 0 = unlimited

    sub_sckt.connect(string("tcp://") + sub_ip + ':' + to_string(sub_prt));
    if(vrbs) cout << "[cpu_emu " << sub_prt << "]: " << " Subscribing to " << sub_ip << ':' + to_string(sub_prt) << endl;
    // Subscribe to all messages (empty topic)
    sub_sckt.set(zmq::sockopt::subscribe, "");
    if(vrbs) cout << "[cpu_emu " << sub_prt << "]: " << " subscribing" << endl;

    //  Prepare our publication context and socket
    context_t pub_cntxt(1);
    socket_t pub_sckt(pub_cntxt, socket_type::pub);
    if(!trmnl) {
    	if(vrbs) cout << "[cpu_emu " << sub_prt << "]: " << " Publishing on port " << to_string(pub_prt) << endl;
    	pub_sckt.bind(string("tcp://*:") + to_string(pub_prt));
    	pub_sckt.set(zmq::sockopt::sndhwm, int(0)); // queue length
    }
    
    uint32_t request_nbr = 1;
    double   mnBfSz_B    = 0; //mean receive Size bytes
    uint64_t bufSiz_B    = 0; //bytes
    //uint64_t cmBufSiz_B  = 0; //bytes - cummulative
    uint64_t last_timestamp_uS = 0; //previous frame timestamp usec since epoch
    uint32_t stream_id   = 0;
    uint32_t frame_num   = 0;
    uint32_t lst_frm_nm  = frame_num; //last frame number
    uint64_t msdFrms     = 0; // missed frames count
    uint64_t last_cmp_lat_uS = 0; //last experienced computational latency
    //last experienced network latency defaul to to 60KB over 100 Gbps
    uint64_t last_nw_lat_uS  = one_u*60e3*B_b/(100*G_1);
    
    auto now_hrc      = high_resolution_clock::now();
    auto clk0_uSd     = duration_cast<microseconds>(now_hrc.time_since_epoch());
    auto clk_uSd      = clk0_uSd;
    uint64_t start_uS = clk0_uSd.count();    //start of elapsed time measure
    uint64_t now_uS   = clk_uSd.count();
    
    while (frame_num < frame_cnt) {
        //if(vrbs) cout << "[cpu_emu " << sub_prt << "]: " << " Setting up request message ..." << endl;
        message_t request;

        //  Wait for next request from client
        if(vrbs) cout << now_uS << " [cpu_emu " << sub_prt << "]: " << " Waiting for source ..." << endl;
        
        recv_result_t rtcd;
        
        rtcd = sub_sckt.recv (request, recv_flags::none);   ///////////  need a timeout here ///////////////////////////
        
        now_hrc = high_resolution_clock::now();
        clk_uSd  = duration_cast<microseconds>(now_hrc.time_since_epoch());
        now_uS  = clk_uSd.count();
        
        auto parsed  = deserialize_packet(now_uS, pub_prt, static_cast<uint8_t*>(request.data()), request.size());
        stream_id    = parsed.stream_id ;
        frame_num    = parsed.frame_num ;
        
        if(frame_num == 1) {
            //now_hrc  = high_resolution_clock::now();
            clk0_uSd  = clk_uSd;
            start_uS  = clk0_uSd.count();
        }

        if(frame_num > lst_frm_nm + 1) msdFrms += frame_num - (lst_frm_nm + 1); //might have missed more than one
        lst_frm_nm = frame_num;

        if(DBG) cout << now_uS+1 << " [cpu_emu " << sub_prt << "]: " << "deserializing packet for request_nbr " << request_nbr << endl;
        if(DBG) cout << now_uS+2 << " [cpu_emu " << sub_prt << "]: " << "deserializing success for frame_num " << parsed.frame_num << endl;
        bufSiz_B = rtcd.value(); //bytes


        if(DBG) cout << now_uS+1 << " [cpu_emu " << sub_prt << "]: " << "deserializing packet ... request.size() " << request.size() 
                     << " HEADER_SIZE = " << HEADER_SIZE << endl;
        if(DBG) cout << now_uS+3 << " [cpu_emu " << sub_prt << "]: " << "bufSiz_B = " << bufSiz_B << " parsed.size = " << parsed.size_B 
                     << " sizeof(struct DeserializedPacket) = " << sizeof(struct DeserializedPacket) << endl;

        if(vrbs) cout << now_uS << " [cpu_emu " << sub_prt << "]: " << " recd " << parsed.frame_num << endl;
        if(vrbs) cout << now_uS << " [cpu_emu " << sub_prt << "]: " << " Received request "
                      << request_nbr << " from port " + string("tcp://") + sub_ip + ':' +  to_string(pub_prt)
                      << " rtcd = " << int(rtcd.value()) << " from client" << endl;
                      
        if(vrbs) cout << now_uS+1  << " [cpu_emu " << sub_prt << "]: " << " frame size = "
                      << "(actual) " << bufSiz_B << " bytes " << bufSiz_B*one_G << " GB "
                      << " from client " << "ts = " << now_uS << " (" << request_nbr << ')' << endl;
                      
        auto last_rdy_uS = last_timestamp_uS + last_cmp_lat_uS + last_nw_lat_uS;
        last_nw_lat_uS = one_u*request.size()*B_b/(100*G_1);
        if(vrbs) cout << now_uS+2 << " [cpu_emu " << sub_prt << "]: comparing last_rdy_uS " 
             << last_rdy_uS << " to recd_uS " << now_uS << " frame " << frame_num << endl;
        if(now_uS < last_rdy_uS) {
            if(vrbs) {
                cout << now_uS+2  << " [cpu_emu " << sub_prt << "]:  dropped (" << frame_num << ')' << " request_nbr " << request_nbr 
                     << "(last_rdy_uS,recd_uS) (" << last_rdy_uS << ',' << now_uS << ')' << endl;
                    }
            if(frame_num != 0) {
                if(vrbs) cout << now_uS+2 << " [cpu_emu " << sub_prt << "]: " << " going to wait_for_frame " << endl; 
                continue;
            }
        }
        last_timestamp_uS = parsed.timestamp_uS;
        
        //  Do some 'work'
        // load (or emulate load on) system with ensuing work
        {
            vector<thread> threads;
            // variable latency
            auto vrblCmpLt_S_GB = 0;
            do {
                vrblCmpLt_S_GB = sample_gamma(cmpLt_S_GB, cmpLt_S_GB/10, gen);
            } while (vrblCmpLt_S_GB < cmpLt_S_GB);

            for (int i=1; i<=nmThrds; ++i)  //start the threads
                threads.push_back(thread(func, parsed.size_B, vrblCmpLt_S_GB, mem_GB, wlSlp, sub_prt, vrbs));

            for (auto& th : threads) th.join();
            //reqd computational timespan in usec    
            auto now1_hrc   = high_resolution_clock::now();
            auto uS1d       = duration_cast<microseconds>(now1_hrc.time_since_epoch());
            last_cmp_lat_uS = uS1d.count()-now_uS;  //zero based clock
            now_uS          = uS1d.count();         //update clock for computational latency
            
            if(vrbs) cout << now_uS  << " [cpu_emu " << sub_prt << "]: " << " synchronized all threads..." << endl;
        }

        if(!trmnl) {
            if(DBG) cout << now_uS+2  << " [cpu_emu " << sub_prt << "]: " << " Forwarding "
                         << " request " << frame_num << " from port " + string("tcp://") + sub_ip + ':' +  to_string(pub_prt)
                         << " to port " + string("tcp://") + sub_ip + ':' +  to_string(sub_prt) << " (" << frame_num << ')' << endl;
            // Publish a message for subscribers
            const size_t outSz_B = otmem_GB*sz1G; //output size in bytes

            send_result_t sr;
            {
    	        // Send  output "frame"
                //represents harvested data
                vector<uint8_t> payload(outSz_B);  //represents harvested data
                if(DBG) cout << now_uS+1 << " [cpu_emu " << sub_prt << "]: " << "serializing packet for request_nbr " << request_nbr << endl;///////////frame_num ???
                auto data = serialize_packet(now_uS, pub_prt, payload.size(), parsed.timestamp_uS, parsed.stream_id, parsed.frame_num, payload);
                if(DBG) cout << now_uS+2 << " [cpu_emu " << sub_prt << "]: " << "serializing success for frame_num " << parsed.frame_num << endl;
                zmq::message_t message(data.size());
                memcpy(message.data(), data.data(), data.size());
                sr = pub_sckt.send(message, zmq::send_flags::none);
                if (!sr) cerr << now_uS << " [cpu_emu " << sub_prt << "]:  Failed to send" << endl;
                if (vrbs && sr.has_value()) cout << now_uS << " [cpu_emu " << sub_prt << "]: Bytes sent = " << sr.value() << endl;

                if(vrbs) cout << now_uS+3 << " [cpu_emu " << sub_prt << "]:  Sending frame size = " << payload.size() << " (" 
                              << frame_num << ')' << " to " << pub_prt << " at " << now_uS << " with code " << endl;
                if(vrbs) cout << now_uS+4 << "[cpu_emu " << sub_prt << "]: " << " output Num written (" << request_nbr << ") "  
                             << sr.value() << " (" << request_nbr << ')' << endl;
                if(sr.value() != HEADER_SIZE + payload.size()) cout << now_uS+3 << "[cpu_emu " << sub_prt << "]: " 
                                                                    << " sbscrptn_ip data incorrect size(" << request_nbr << ") "  << endl;
            }
        }
        if(vrbs) cout << now_uS + 4 << " [cpu_emu " << sub_prt << "]:  done (" << frame_num << ')' << endl;
 
        mnBfSz_B = (request_nbr-1)*mnBfSz_B/request_nbr + bufSiz_B/request_nbr; //incrementally update mean receive size
        // Record end time
        //if(request_nbr < 10) continue; //warmup
        if(vrbs) cout << now_uS + 5 << " [cpu_emu " << sub_prt << "]: " << " Measured latencies: last_cmp_lat_uS = " << last_cmp_lat_uS 
                           << " last_nw_lat_uS = " << last_nw_lat_uS  << " (" << frame_num << ")" << endl;
        if(vrbs) cout << now_uS + 6 << " [cpu_emu " << sub_prt << "]: " << " Measured frame rate " 
                           << float(request_nbr)/(float(now_uS-start_uS)*one_M) 
                           << " frame Hz." << " for " << frame_num << " frames" << endl;
        if(vrbs) cout << now_uS + 7 << " [cpu_emu " << sub_prt << "]: " << " Measured bit rate " 
                           << float(request_nbr*mnBfSz_B*B_b)/(float(now_uS-start_uS)*one_M)
                           << " bps mnBfSz_B " << mnBfSz_B << " (" << frame_num << ')' << endl;
        if(vrbs) cout << now_uS + 8 << " [cpu_emu " << sub_prt << "]:  Missed frames: " << msdFrms << endl;
        if(vrbs) cout << now_uS + 9 << " [cpu_emu " << sub_prt << "]:  Missed frame ratio: " << float(msdFrms)/float(frame_num) 
                      << " frame_num " << frame_num  << " request_nbr " << request_nbr << endl;
        if(vrbs) cout  << now_uS + 10 << " [cpu_emu " << sub_prt << "]:  stats computed ..." << endl;
        request_nbr++;
    } //main loop
    cout  << now_uS + 11 << " [cpu_emu " << sub_prt << "]:  " << (trmnl?"Terminal":"Non Terminal") 
          << " exiting, elasped time S " << float(now_uS-start_uS)*one_M << endl;
    cout.flush();
    cerr.flush();
    return 0;
}
