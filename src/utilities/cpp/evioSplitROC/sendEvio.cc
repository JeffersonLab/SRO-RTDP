#include <iostream>
#include <fstream>
#include <string>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include<unistd.h>

using namespace std;



/*********  Default parameters. However, host has to be provided as an input from the commandline  **********/

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

bool sendEvioData(Parameters& param) {
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

    // Read and send the file in chunks
    const int BUFFER_SIZE = 8192;
    ssize_t totalBytesRead = 0;
    ssize_t bytesSent = 0;
    char buffer[BUFFER_SIZE];
    while (!file.eof()) {
        file.read(buffer, BUFFER_SIZE);
        ssize_t bytesRead = file.gcount();
        totalBytesRead += bytesRead;
        if (bytesRead > 0) {
            bytesSent = send(sockfd, buffer, bytesRead, 0);
            if (bytesSent == -1) {
                std::cerr << "Error sending data\n";
                file.close();
                close(sockfd);
                return false;
            }
        }
    } 
    std::cout << "Total bytes read: " << totalBytesRead << endl;
    std::cout << "File sent successfully\n";
    close(sockfd);
    return true;
}

int main(int argc, char* argv[]) {
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
#include <iostream>
#include <fstream>
#include <string>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include<unistd.h>

using namespace std;



/*********  Default parameters. However, host has to be provided as an input from the commandline  **********/

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

bool sendEvioData(Parameters& param) {
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

    // Read and send the file in chunks
    const int BUFFER_SIZE = 8192;
    ssize_t totalBytesRead = 0;
    ssize_t bytesSent = 0;
    char buffer[BUFFER_SIZE];
    while (!file.eof()) {
        file.read(buffer, BUFFER_SIZE);
        ssize_t bytesRead = file.gcount();
        totalBytesRead += bytesRead;
        if (bytesRead > 0) {
            bytesSent = send(sockfd, buffer, bytesRead, 0);
            if (bytesSent == -1) {
                std::cerr << "Error sending data\n";
                file.close();
                close(sockfd);
                return false;
            }
        }
    } 
    std::cout << "Total bytes read: " << totalBytesRead << endl;
    std::cout << "File sent successfully\n";
    close(sockfd);
    return true;
}

int main(int argc, char* argv[]) {
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
