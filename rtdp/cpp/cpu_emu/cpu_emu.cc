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

unsigned int sleep(unsigned int seconds);

using namespace std;

#define MAX (64*1024)
#define SA struct sockaddr
#define DBG 0
  
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
        -v verbose (= 0/1 - default = false (0))  \n\n";

    std::cout <<  usage_str;
    std::cout <<  "Required: -b -i -m -o -t \n\n";
}

// Computational Function to emulate/stimulate processimng load/latency, etc. 
void func(char* buff, ssize_t nmrd, ssize_t scs_GB, double memGB, bool psdS, bool vrbs=false) 
{ 

    if(vrbs) std::cout << "Threading ..." << endl;
    uint64_t memSz = memGB*1024*1024*1024; //memory footprint in bytes
    if(vrbs) std::cout << "Allocating " << memSz << " bytes ..." << endl;
    double* x = new double[memSz];
    //usefull work emeulation 
    if(psdS) {
        if(vrbs) std::cout << "Sleeping ..." << endl;
        std::this_thread::sleep_for (std::chrono::microseconds(uint32_t(scs_GB*nmrd*1e-3)));
    }else{
        if(vrbs) std::cout << "Burning ..." << endl;
        for (ssize_t i = 0; i<memSz; i++) x[i] = tanh(i);
    }
    if(vrbs) std::cout << "Threading Done" << endl;

} 
  
// Driver function 
int main (int argc, char *argv[])
{ 
    int optc;

    bool     psdB=false, psdI=false, psdM=false, psdO=false;
    bool     psdP=false, psdR=false, psdS=false, psdT=false, psdV=false;
    char     dst_ip[INET6_ADDRSTRLEN];	// target ip
    uint16_t rcv_prt = 8888; // receive port default
    uint16_t dst_prt = 8888; // target port default
    auto     nmThrds = 10;   // default
    bool     vrbs = false;   // verbode ?
    double   scs_GB  = 0;    // seconds/(input GB) thread latency
    double   memGB   = 0;    // thread memory footprint in GB
    double   otmemGB = 0;    // program putput in GB

    while ((optc = getopt(argc, argv, "hb:i:m:o:p:r:st:v:")) != -1)
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
        case '?':
            std::cout << "Unrecognised option: " << optopt;
            Usage();
            exit(1);
        }
    }
    if(DBG) std::cout << endl;
    if(!(psdB && psdI && psdM && psdO && psdT)) { Usage(); exit(1); }

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
    if ((listen(sockfd, 5)) != 0) { 
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
  
    // Function for chatting between client and server 
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
	
	// if(otmemGB*(1024*1024*1024) > nmrd) { cerr << "Output cannot exceed input size\n"; exit(EXIT_FAILURE); }
    
    //load (or emulate load on) system with ensuing work

	std::vector<std::thread> threads;

	for (int i=1; i<=nmThrds; ++i)
		threads.push_back(std::thread(func, buff, nmrd, scs_GB, memGB, psdS, vrbs));
    //std::thread second func(buff, nmrd); 
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
        write(sockfd, x, outSz);
	    // close the socket 
	    close(sockfd);
	} 
}
