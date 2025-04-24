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
        -o output size in GB  \n\
        -p destination port (default = 8888)  \n\
        -r receive port (default = 8888)  \n\
        -s sleep versus burn cpu  \n\
        -t num threads (default = 10)  \n\
        -y yaml config file  \n\
        -z act as terminal node  \n\
        -v verbose (= 0/1 - default = false (0))  \n\n";

    cout << "[cpu_emu]: " << usage_str;
}

// Computational Function to emulate/stimulate processimng load/latency, etc. 
void func(size_t nmrd, size_t scs_GB, double memGB, bool psdS, bool vrbs=false) 
{ 
    const size_t ts(scs_GB*nmrd*1e-9); //reqd timespan in seconds
    const size_t tsns(scs_GB*nmrd);    //reqd timespan in nanoseconds
    size_t memSz = memGB*1024*1024*1024; //memory footprint in bytes
    if(vrbs) cout << "[cpu_emu]: Allocating " << memSz << " bytes ..." << endl;
    double* x = new double[memSz];
    //usefull work emulation 
    if(vrbs) cout << "[cpu_emu]: Threading for " << ts << " secs ..." << endl;
    if(psdS) {
        auto cms = chrono::nanoseconds(tsns);
        if(vrbs) cout << "[cpu_emu]: Sleeping for " << tsns << " nsecs" << endl;
        this_thread::sleep_for(cms);
    }else{
        //high_resolution_clock::time_point start_time = std::chrono::high_resolution_clock::now();
        auto start_time = std::chrono::high_resolution_clock::now();
        if(vrbs) cout << "[cpu_emu]: Burning ...";
        
        double fracsecs, secs;
        fracsecs = modf (ts , &secs);
        if(vrbs) cout << "[cpu_emu]: secs = " << secs << " fracsecs = " << fracsecs << endl;
        size_t sz1k = 1024;
        size_t strtMem = 0;
        auto end_time = std::chrono::high_resolution_clock::now();
        duration<double> time_span = duration_cast<duration<double>>(end_time - start_time);
        while (time_span.count() < ts) { 
            //if(vrbs) cout << "[cpu_emu]: Checking " << time_span.count() << " against "<< ts  << endl;
            for (size_t i = strtMem; i<min(strtMem + sz1k, memSz); i++) { x[i] = tanh(i); }
            strtMem += sz1k;
            if(strtMem > memSz - sz1k) strtMem = 0;
            end_time = std::chrono::high_resolution_clock::now();
            time_span = duration_cast<duration<double>>(end_time - start_time);
        }
        if(vrbs) cout << "[cpu_emu]: Threaded for " << time_span.count() << " secs Done" << endl;
    }
}

map<string,string> mymap;

void parse_yaml(const char *filename, bool vrbs=false) {
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
    lbls.push_back("terminal");
    
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
                if(DBG) cout << "[cpu_emu]: Label: " << s << '\n';
                lbl_stk.push(s);
            } else {
                s1 = lbl_stk.top();
                if(DBG) cout << "[cpu_emu]: Label: " << s1 << " Datum: " << s << '\n';
                mymap[s1] = s;
                lbl_stk.pop();
            }
            break;
        default:
            if(DBG) cout << "[cpu_emu]: (Default)" << endl;
            break;
        }

        if(event.type == YAML_STREAM_END_EVENT) break;
        yaml_event_delete(&event);
    }
    if(DBG) cout << "[cpu_emu]: All done parsing, got this:" << endl;
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
    bool     psdZ=false;
    string   yfn = "cpu_emu.yaml";
    char     dst_ip[INET6_ADDRSTRLEN] = "127.0.0.1";	// target ip
    uint16_t rcv_prt = 8888; // receive port default
    uint16_t dst_prt = 8888; // target port default
    auto     nmThrds = 5;   // default
    bool     vrbs = false;   // verbose ?
    double   scs_GB  = 100;    // seconds/(input GB) thread latency
    double   memGB   = 10;    // thread memory footprint in GB
    double   otmemGB = 0.01;    // program putput in GB

    while ((optc = getopt(argc, argv, "hb:i:m:o:p:r:st:v:y:z")) != -1)
    {
        switch (optc)
        {
        case 'h':
            Usage();
            exit(1);
        case 'b':
            scs_GB = (double) atof((const char *) optarg) ;
            psdB = true;
            if(DBG) cout << " -b " << scs_GB;
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
            cout << "[cpu_emu]: Unrecognised option: " << optopt;
            Usage();
            exit(1);
        }
    }

    if(DBG) cout << endl;

    if (psdY) {//parse the yaml file if given        
        parse_yaml(yfn.c_str(), vrbs);
        //cmd line parms overide yaml file settings (which are otherwise in the map)
        if(!psdB) scs_GB = stof(mymap["latency"]);
        if(!psdI) strcpy(dst_ip, mymap["destination"].c_str());
        if(!psdM) memGB = stof(mymap["mem_footprint"]);
        if(!psdO) otmemGB = stof(mymap["output_size"]);
        if(!psdP) dst_prt = stoi(mymap["dst_port"]);
        if(!psdR) rcv_prt = stoi(mymap["rcv_port"]);
        if(!psdS) psdS = stoi(mymap["sleep"]) == 1;
        if(!psdT) nmThrds = stoi(mymap["threads"]);
        if(!psdV) vrbs = stoi(mymap["verbose"]);
        if(!psdZ) psdZ = stoi(mymap["terminal"]) == 1;
    }    
    ////////
    if(vrbs) cout << "[cpu_emu]: Operating with scs_GB = " << scs_GB << "\tdst_ip = "
                << (psdZ?"N/A":string(dst_ip)) << "\tmemGB = " << memGB << "\totmemGB = "
                << otmemGB << "\tdst_prt = " << (psdZ?"N/A":to_string(dst_prt)) << "\trcv_prt = "
                << rcv_prt << "\tsleep = " << psdS << "\tnmThrds = "
                << nmThrds << "\tverbose = " << vrbs << "\tyfn = " << (psdY?yfn:"N/A") 
                << "\tterminal = " << psdZ << '\n';

    //  Prepare our receiving rcv_cntxt and socket
    context_t rcv_cntxt(1);
    context_t dst_cntxt(1);
    socket_t rcv_sckt(rcv_cntxt, socket_type::pull);
    socket_t dst_sckt(dst_cntxt, socket_type::push);
    rcv_sckt.bind(string("tcp://*:") + to_string(rcv_prt));
    if(vrbs) cout << "[cpu_emu]: Connecting to receiver " + string("tcp://*:") + to_string(rcv_prt) << endl;
    
    if(!psdZ) {
        //  Prepare our destination socket
        if(vrbs) cout << "[cpu_emu]: Connecting to destination " + string("tcp://") + dst_ip + ':' +  to_string(dst_prt) << endl;
        dst_sckt.connect (string("tcp://") + dst_ip + ':' +  to_string(dst_prt));
    }
    uint16_t request_nbr = 0;
    while (true) {
        //if(vrbs) cout << "[cpu_emu]: Setting up request message ..." << endl;
        message_t request;

        //  Wait for next request from client
        if(vrbs) cout << "[cpu_emu]: Waiting for source ..." << endl;
        recv_result_t rtcd = rcv_sckt.recv (request, recv_flags::none);
        if(vrbs) cout << "[cpu_emu]: Received request " << request_nbr++ << ": rtcd = " << rtcd.value() << " from client " << endl;

        //  Do some 'work'
        //load (or emulate load on) system with ensuing work

        vector<thread> threads;

        for (int i=1; i<=nmThrds; ++i)  //start the threads
            threads.push_back(thread(func, rtcd.value(), scs_GB, memGB, psdS, vrbs));

        for (auto& th : threads) th.join();
        if(vrbs) cout << "[cpu_emu]: synchronized all threads..." << endl;

        if(!psdZ) {
            if(vrbs) cout << "[cpu_emu]: Forwarding to destination " + string("tcp://") + dst_ip + ':' +  to_string(dst_prt) << endl;
            //forward to next hop    
            // Send a message to the destination
            size_t outSz = otmemGB*1.024*1.024*1.024*1e9; //output size in bytes
            message_t dst_msg(outSz); //harvested data
            send_result_t sr = dst_sckt.send(dst_msg, send_flags::none);
            if(vrbs) cout << "[cpu_emu]: output Num written " << sr.value()  << endl;
            if(sr.value() != outSz) cerr << "Destination data incorrect size" << endl;
        }
    }
    return 0;
}
