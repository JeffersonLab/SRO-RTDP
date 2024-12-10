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
#include <thread>	// std::thread
#include <math.h>	/* tanh, log */
#include <vector>	// std::vector

unsigned int sleep(unsigned int seconds);

using namespace std;

#define MAX (64*1024)
#define SA struct sockaddr
#define DBG 0
  
void   Usage()
{
    char usage_str[] =
        "\nUsage: \n\
        -i destination address (string)  \n\
        -p destination port (default = 8888)  \n\
        -t num threads (default = 10)  \n\
        -v verbose (= 0/1 - default = false (0))  \n\
        -h help \n\n";
        std::cout <<  usage_str;
        std::cout <<  "Required: -i -p\n";
}

// Computational Function to emulate/stimulate processimng load/latency, etc. 
void func(char* buff, ssize_t nmrd, bool vrbs=false) 
{ 

    //usefull work emeulation 
    if(vrbs) std::cout << "Threading ..." << endl;
    auto cmpScl = 1e3;
    double* x = new double[nmrd]; //mem allocation test
    for (ssize_t k = 0; k<cmpScl; k++) for (ssize_t i = 0; i<nmrd; i++) x[i] = tanh(0.1234);///////////////////////////////
    if(vrbs) std::cout << "Threading Done" << endl;

} 
  
// Driver function 
int main (int argc, char *argv[])
{ 
    int optc;

    bool passedI=false, passedP=false, passedT=false, passedV=false;
    char     dst_ip[INET6_ADDRSTRLEN];	// target ip
    uint16_t dst_prt = 8888;			// target port default
    auto nmThrds = 10;					// default
    bool vrbs = false;

    while ((optc = getopt(argc, argv, "v:t:i:p:h")) != -1)
    {
        switch (optc)
        {
        case 'h':
            Usage();
            exit(1);
        case 'i':
            strcpy(dst_ip, (const char *) optarg) ;
            passedI = true;
            if(DBG) std::cout << "-i " << dst_ip;
            break;
        case 'p':
            dst_prt = (uint16_t) atoi((const char *) optarg) ;
            passedP = true;
            if(DBG) std::cout << "-p " << dst_prt;
            break;
        case 't':
            nmThrds = (uint16_t) atoi((const char *) optarg) ;
            passedT = true;
            if(DBG) std::cout << "-t " << nmThrds;
            break;
        case 'v':
            vrbs = (bool) atoi((const char *) optarg) ;
            passedV = true;
            if(DBG) std::cout << "-v " << vrbs;
            break;
        case '?':
            std::cout << "Unrecognised option: " << optopt;
            Usage();
            exit(1);
        }
    }
    if(DBG) std::cout << endl;

    int sockfd, connfd, len; 
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
    servaddr.sin_port = htons(dst_prt); 
  
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
    
    //load (or emulate load on) system with ensuing work

	std::vector<std::thread> threads;

	for (int i=1; i<=nmThrds; ++i)
		threads.push_back(std::thread(func, buff, nmrd, vrbs));
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

		double* x = new double[nmrd/10]; //harvested data
	    write(sockfd, x, nmrd/10);
	    // close the socket 
	    close(sockfd);
	} 
}
