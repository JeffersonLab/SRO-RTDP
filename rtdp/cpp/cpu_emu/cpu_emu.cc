#include <stdio.h> 
#include <netdb.h> 
#include <netinet/in.h> 
#include <stdlib.h> 
#include <string.h> 
#include <sys/socket.h> 
#include <sys/types.h> 
#include <unistd.h>	// read(), write(), close()
#include <fstream>
#include <iostream>
#include <unistd.h>
#include <arpa/inet.h>
#include <thread>
#include <math.h>
#include <vector>
#include <chrono>
#include <csignal>
#include <sys/time.h>
#include <algorithm>    // std::min
#include <string>
#include <yaml.h>
#include <stack>
#include <map>


volatile bool timeoutExpired = false;

void alarmHandler(int signum) {
    timeoutExpired = true;
}

using namespace std;

#define MAX (64*1024)	// buffer size seems arbitrary - what's a good value ?
#define SA struct sockaddr
#define DBG 1	//print extra verbosity apart from -v switch
  
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
        -v verbose (= 0/1 - default = false (0))  \n\n";

    std::cout <<  usage_str;
}

// Computational Function to emulate/stimulate processimng load/latency, etc. 
void func(char* buff, ssize_t nmrd, ssize_t scs_GB, double memGB, bool psdS, bool vrbs=false) 
{ 
    if(vrbs) std::cout << "Threading ..." << endl;
    ssize_t memSz = memGB*1024*1024*1024; //memory footprint in bytes
    if(vrbs) std::cout << "Allocating " << memSz << " bytes ..." << endl;
    double* x = new double[memSz];
    //usefull work emeulation 
    if(psdS) {
        if(vrbs) std::cout << "Sleeping ..." << endl;
        std::this_thread::sleep_for (std::chrono::microseconds(uint32_t(scs_GB*nmrd*1e-3)));
    }else{
        if(vrbs) std::cout << "Burning ..." << endl;
        signal(SIGALRM, alarmHandler);
    
        /* Start a timer that expires after required latency */
        
        double musecs, fracsecs, secs;
        musecs = scs_GB*nmrd*1e-9; //raw microseconds
        fracsecs = modf (musecs , &secs);
        if(vrbs) std::cout << "secs = " << secs << " fracsecs = " << fracsecs << endl;
      
        struct itimerval timer;
        timer.it_value.tv_sec = secs;
        timer.it_value.tv_usec = uint32_t(fracsecs*1e6);
        timer.it_interval.tv_sec = 0;
        timer.it_interval.tv_usec = 0;
        setitimer (ITIMER_REAL, &timer, 0);
        ssize_t sz1k = 1024;
        ssize_t strtMem = 0;
        while (!timeoutExpired) { 
            for (ssize_t i = strtMem; i<std::min(strtMem + sz1k, memSz); i++) { x[i] = tanh(i); }
            strtMem += sz1k;
            if(strtMem > memSz - sz1k) strtMem = 0;
        }
    }

    if(vrbs) std::cout << "Threading Done" << endl;
}

map<string,string> mymap;

void parse_yaml(const char *filename) {
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
    auto it = lbls.begin(); //a hack to get the type

    while (yaml_parser_parse(&parser, &event)) {
        switch (event.type) {
        case YAML_NO_EVENT:
            break;
        case YAML_STREAM_START_EVENT:
            if(DBG) printf("Stream started\n");
            break;
        case YAML_STREAM_END_EVENT:
            if(DBG) printf("Stream ended\n");
            break;
        case YAML_DOCUMENT_START_EVENT:
            if(DBG) printf("Document started\n");
            break;
        case YAML_DOCUMENT_END_EVENT:
            if(DBG) printf("Document ended\n");
            break;
        case YAML_MAPPING_START_EVENT:
            if(DBG) printf("Mapping started\n");
            break;
        case YAML_MAPPING_END_EVENT:
            if(DBG) printf("Mapping ended\n");
            break;
        case YAML_SEQUENCE_START_EVENT:
            if(DBG) printf("Sequence started\n");
            break;
        case YAML_SEQUENCE_END_EVENT:
            if(DBG) printf("Sequence ended\n");
            break;
        case YAML_SCALAR_EVENT:
            s = (const char*)event.data.scalar.value;
            it = std::find(lbls.begin(), lbls.end(), s);
            if (it != lbls.end()) {
                if(DBG) cout << "Label: " << s << '\n';
                lbl_stk.push(s);
            } else {
                s1 = lbl_stk.top();
                if(DBG) cout << "Label: " << s1 << " Datum: " << s << '\n';
                mymap[s1] = s;
                lbl_stk.pop();
            }
            break;
        default:
            if(DBG) cout << "(Default)" << endl;
            break;
        }

        if(event.type == YAML_STREAM_END_EVENT) break;
        yaml_event_delete(&event);
    }
    if(DBG) cout << "All done parsing, got this:" << endl;
    if(DBG) for (std::map<string,string>::iterator it=mymap.begin(); it!=mymap.end(); ++it)
        std::cout << it->first << " => " << it->second << '\n';
    
    yaml_parser_delete(&parser);
    fclose(file);
}
  
int main (int argc, char *argv[])
{ 
    int optc;

    bool     psdB=false, psdI=false, psdM=false, psdO=false, psdY=false;
    bool     psdP=false, psdR=false, psdS=false, psdT=false, psdV=false;
    string   yfn = "cpu_emu.yaml";
    char     dst_ip[INET6_ADDRSTRLEN] = "127.0.0.1";	// target ip
    uint16_t rcv_prt = 8888; // receive port default
    uint16_t dst_prt = 8888; // target port default
    auto     nmThrds = 5;   // default
    bool     vrbs = false;   // verbose ?
    double   scs_GB  = 100;    // seconds/(input GB) thread latency
    double   memGB   = 10;    // thread memory footprint in GB
    double   otmemGB = 0.01;    // program putput in GB

    while ((optc = getopt(argc, argv, "hb:i:m:o:p:r:st:v:y:")) != -1)
    {
        switch (optc)
        {
        case 'h':
            Usage();
            exit(1);
        case 'b':
            scs_GB = (double) atof((const char *) optarg) ;
            psdB = true;
            if(DBG) std::cout << " -b " << scs_GB;
            break;
        case 'i':
            strcpy(dst_ip, (const char *) optarg) ;
            psdI = true;
            if(DBG) std::cout << " -i " << dst_ip;
            break;
        case 'm':
            memGB = (double) atof((const char *) optarg) ;
            psdM = true;
            if(DBG) std::cout << " -m " << memGB;
            break;
        case 'o':
            otmemGB = (double) atof((const char *) optarg) ;
            psdO = true;
            if(DBG) std::cout << " -o " << otmemGB;
            break;
        case 'p':
            dst_prt = (uint16_t) atoi((const char *) optarg) ;
            psdP = true;
            if(DBG) std::cout << " -p " << dst_prt;
            break;
        case 'r':
            rcv_prt = (uint16_t) atoi((const char *) optarg) ;
            psdR = true;
            if(DBG) std::cout << " -r " << rcv_prt;
            break;
        case 's':
            psdS = true;
            if(DBG) std::cout << " -s ";
            break;
        case 't':
            nmThrds = (uint16_t) atoi((const char *) optarg) ;
            psdT = true;
            if(DBG) std::cout << " -t " << nmThrds;
            break;
        case 'v':
            vrbs = (bool) atoi((const char *) optarg) ;
            psdV = true;
            if(DBG) std::cout << " -v " << vrbs;
            break;
        case 'y':
            yfn = (const char *) optarg ;
            psdY = true;
            if(DBG) std::cout << " -y " << yfn;
            break;
        case '?':
            std::cout << "Unrecognised option: " << optopt;
            Usage();
            exit(1);
        }
    }

    if(DBG) std::cout << endl;

    /////////parse the yaml file if given
    /////////cmd line parms overide yaml file settings
    if (psdY) {
        parse_yaml(yfn.c_str());
        if(!psdB) scs_GB = stof(mymap["latency"]);
        if(!psdI) strcpy(dst_ip, mymap["destination"].c_str());
        if(!psdM) memGB = stof(mymap["mem_footprint"]);
        if(!psdO) otmemGB = stof(mymap["output_size"]);
        if(!psdP) dst_prt = stoi(mymap["dst_port"]);
        if(!psdR) rcv_prt = stoi(mymap["rcv_port"]);
        if(!psdS) psdS = stoi(mymap["sleep"]) == 1;
        if(!psdT) nmThrds = stoi(mymap["threads"]);
        if(!psdV) vrbs = stoi(mymap["verbose"]);
    }    
    ////////
    if(vrbs) cout << "Operating with scs_GB = " << scs_GB << "\tdst_ip = "
                << dst_ip << "\tmemGB = " << memGB << "\totmemGB = "
                << otmemGB << "\tdst_prt = " << dst_prt << "\trcv_prt = "
                << rcv_prt << "\tsleep = " << psdS << "\tnmThrds = "
                << nmThrds << "\tverbose = " << vrbs << "\tyfn = " << yfn << '\n';

    int sockfd, connfd;
    socklen_t len;
    struct sockaddr_in servaddr, cli; 
  
    // socket create and verification 
    sockfd = socket(AF_INET, SOCK_STREAM, 0); 
    if (sockfd == -1) { 
        std::cout << "socket creation failed..." << endl; 
        exit(0); 
    } 
    else
        if(vrbs) std::cout << "Socket successfully created.." << endl;

    bzero(&servaddr, sizeof(servaddr)); 
  
    // assign IP, PORT 
    servaddr.sin_family = AF_INET; 
    servaddr.sin_addr.s_addr = htonl(INADDR_ANY); 
    servaddr.sin_port = htons(rcv_prt); 
  
    // Binding newly created socket to given IP and verification 
    if ((bind(sockfd, (SA*)&servaddr, sizeof(servaddr))) != 0) { 
        std::cout << "socket bind failed...\n"; 
        exit(0); 
    } 
    else
        if(vrbs) std::cout << "Socket successfully binded.." << endl; 
  
    // Now server is ready to listen and verification 
    if ((listen(sockfd, 5)) != 0) {  // backlog = 5 is arbitrary - what's a good value ?
        std::cout << "Listen failed..." << endl; 
        exit(0); 
    } 
    else
        if(vrbs) std::cout << "Server listening.." << endl;

    len = sizeof(cli); 
  
    // Accept the data packet from client and verification 
    connfd = accept(sockfd, (SA*)&cli, &len); 
    if (connfd < 0) { 
        std::cout << "server accept failed..." << endl; 
        exit(0); 
    } 
    else
        if(vrbs) std::cout << "server accept the client..." << endl; 
  
    // Read in event data 
    char buff[MAX];
    ssize_t nmrd = 0;
    ssize_t nmrd0 = 0;
    // loop for input event 
    do { 
  
        // read the message from client and copy it in buffer 
        nmrd += nmrd0 = read(connfd, buff, sizeof(buff));

    } while(nmrd0>0);

    close(sockfd); 
    if(vrbs) std::cout << "Num read " << nmrd  << endl;

    // if output size should not exceed input size
    // if(otmemGB*(1024*1024*1024) > nmrd) { cerr << "Output cannot exceed input size\n"; exit(EXIT_FAILURE); }
    
    //load (or emulate load on) system with ensuing work

    std::vector<std::thread> threads;

    for (int i=1; i<=nmThrds; ++i)  //start the threads
        threads.push_back(std::thread(func, buff, nmrd, scs_GB, memGB, psdS, vrbs));

    if(vrbs) std::cout << "synchronizing all threads..." << endl;

    for (auto& th : threads) th.join();
   
    //forward to next hop    
    { 

        int sockfd, connfd; 
        struct sockaddr_in servaddr, cli;  

        // socket create and verification 

        sockfd = socket(AF_INET, SOCK_STREAM, 0); 

        if (sockfd == -1) { 
            std::cout << "socket creation failed...\n"; 
            exit(0); 
        } 
        else 
            if(vrbs) std::cout << "Socket successfully created.." << endl;
	        
        bzero(&servaddr, sizeof(servaddr));   

        // assign IP, PORT 

        servaddr.sin_family = AF_INET; 
        servaddr.sin_addr.s_addr = inet_addr(dst_ip);
        servaddr.sin_port = htons(dst_prt);  

        // connect the client socket to server socket 

        if (connect(sockfd, (SA*)&servaddr, sizeof(servaddr)) != 0) { 
            std::cout << "connection with the server failed...\n"; 
            exit(0); 
        } 
        else 
            if(vrbs) std::cout << "connected to the server.." << endl;   

        uint64_t outSz = otmemGB*1.024*1.024*1.024*1e9; //output size in bytes
        double* x = new double[outSz]; //harvested data
        ssize_t nr = write(sockfd, x, outSz);
        close(sockfd);
    } 
}
