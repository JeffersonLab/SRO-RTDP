//  Splits raw data file into one file per ROC

//  Data is stored in big endian, which is needed on the ROC since VME delivers
//   data in big endian

//  Currently only does blocklevel=1

//  ejw, 5-Dec-2013

// g++ -std=c++11 -o evioSplitRoc evioSplitRoc.cc -I/group/da/ejw/coda/include -L/group/da/ejw/coda/Linux-x86_64/lib -levioxx -levio -lexpat -lrt


#include <memory>
#include <string>
#include <sstream>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <iomanip>
#include <fstream>
#include <set>
#include <vector>

#include <sys/stat.h>

#include "evioBankIndex.hxx"
#include "evioUtil.hxx"
#include "evioFileChannel.hxx"

extern "C" {
#include "evio.h"
#ifndef swap_int32_t
//uint32_t* swap_int32_t(uint32_t*,int,uint32_t*);
#endif
}


using namespace evio;


static string dname = "ROCfiles";
static int maxRoc = 18; // make this 18 if using EVIO <4.1 !
static int maxEvt = 0;


#define _DBG_ cerr<<__FILE__<<":"<<__LINE__<<" "
#define _DBG__ cerr<<__FILE__<<":"<<__LINE__<<endl;


struct Parameters {
    string fileName = "";
    int port = 8080; 
    int rate = 0;
    std::string host;
    int period = 0; // period will be 0 if the rate is <= 0
};    

/****** The default parameters will be overridden if the data is obtained from the commandline ********/


void printCommand(const char* programName) {
    std::cerr << "Usage: " << programName << " -host <host_name> -f <file_name> [-p <port>] [-rate <rate_value>]\n";
}

bool parseCommandLine(int argc, char* argv[], Parameters& param) {
    if (argc < 5) {
        cout << "Here: " << argc << ends; 
        return false;
    }

    for (int i = 1; i < argc; i += 2) {
        if (std::string(argv[i]) == "-host") {
            param.host = argv[i + 1];
            
/*      This condition is an extra guard to make sure that the host is not skipped or is provided as a correct input    */
            if (!param.host.empty() && param.host[0] == '-') {
                cout << " Wrong input. Check the name of the host provided... " << ends;
                return false; 
            }
        } else if (std::string(argv[i]) == "-p") {
            param.port = std::atoi(argv[i + 1]);
        } else if (std::string(argv[i]) == "-rate") {
            param.rate = std::atoi(argv[i + 1]);
        } else if (std::string(argv[i]) == "-f"){
            param.fileName = argv[i+1];
        } else {
            return false; // Invalid option
        }
    }
    if (param.host.empty() || param.fileName.empty()) {
        std::cerr << "Error: Mandatory parameters -host or/and -f are missing.\n";
        return false;
    }
    return !param.host.empty();
}

void calculatePeriod(Parameters& param) {
    if (param.rate > 0){
        param.rate = 1/param.rate ;
    }
}

bool sendEvioData(Parameters& param)
{
    int sockfd = socket(AF_INET, SOCK_STREAM, 0);
    if (sockfd == -1) {
        std::cerr << "Error creating socket\n";
        return false;
    }

    // Set up the server address
    struct sockaddr_in serverAddr;
    serverAddr.sin_family = AF_INET;
    // serverAddr.sin_port = htons(static_cast<uint16_t>(param.port));
    serverAddr.sin_addr.s_addr = inet_addr(param.host.c_str());
    serverAddr.sin_port = htons(param.port); 
    // serverAddr.sin_addr.s_addr = inet_addr("127.0.0.1");
    if (connect(sockfd, (struct sockaddr*)&serverAddr, sizeof(serverAddr)) == -1) {
        std::cerr << "Error connecting to server\n";
        close(sockfd);
        return false;
    }

    // Open the file to send
    std::ifstream file(param.fileName, std::ios::binary);
    if (!file.is_open()) {
        std::cerr << "Error opening file\n";
        close(sockfd);
        return false;
    }
    int bufferSize = 100000;
	uint32_t buffer[bufferSize];

	try {

		// open input file channel
		evioFileChannel *chan = new evioFileChannel(param.fileName.c_str(),"r");
		chan->open();
        int nev=0;
		while(chan->readNoCopy()) {
			nev++;          
            int buffSize = (*(uint32_t*)chan->getNoCopyBuffer() + 1 )* 4;
            int bytesSent = send(sockfd, chan->getNoCopyBuffer(), buffSize, 0);
			if (bytesSent == -1) {
                    std::cerr << "Error sending data\n";
                    file.close();
                    close(sockfd);
                    return false;
                }   
            
             std::cout << "Send Data" << std::endl;
        }
		cout << endl;
		cout << "Closing files" << endl;
		chan->close();
        std::cout << "File sent successfully\n";
		cout << "Wrote " << nev << " events" << endl; 
    }catch (evioException e) {
		cerr << e.what() << endl;
		exit(EXIT_FAILURE);
}
    
    return true;
}
//-------------------------------------------------------------------------------

//------------------
// main
//------------------
int main(int argc, char **argv) {
  
  	Parameters param;

    if (!parseCommandLine(argc, argv, param)) {
        printCommand(argv[0]);
        return 1;
    }

    calculatePeriod(param);
    if (!sendEvioData(param)) {
        cout << "Error while sending the data... " << ends ;
        return 1;
    }

        return 0;
}

//-------------------------------------------
// Sending the evio data from an evio file
//-------------------------------------------


//-------------------------------------------------------------------------------
