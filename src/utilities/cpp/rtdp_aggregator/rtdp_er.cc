
#include <string>
#include <cstring>
#include <sstream>
#include <iostream>
#include <fstream>
#include <unordered_map>
#include <map>
#include <chrono>
#include <condition_variable>
#include <iomanip>
#include <algorithm>
#include <functional>
#include <numeric>
#include <vector>
#include <queue>
#include <zmq.hpp>

#include "EVIONetworkTransferHeader.h"


bool QUIT = false;

std::map<uint32_t, std::queue<std::vector<uint8_t>>> RECEIVED_DATA;
std::mutex RECEIVED_DATA_MUTEX;
std::condition_variable ALL_QUEUES_READY_COND;

void receive_data_thread(zmq::socket_t &socket, const int nrocs);
void send_data_thread(zmq::socket_t &socket, const int nrocs, bool no_output);


struct ServerOptions {
    std::string port    = "5560";
    double rate = 0;  // 0 means as fast as possible
    std::chrono::system_clock::time_point starttime = std::chrono::system_clock::now();  // Default to now
    std::string filename = "rtdp_out.evio";
};

//---------------------------------------
// printHelp
//---------------------------------------
void printHelp() {
    std::cout << "\nUsage: [options]\n\n"
              << "-h, --help                    Print help\n"
              << "-p, --port <port>             The TCP port number to listen to for data\n"
              << "-r, --rate <rate>             The rate (in EVIO blocks/second) to send data out at"
              << "-f,--filename filename        Output file name to write data to\n";
}

//---------------------------------------
// parseCommandLine
//---------------------------------------
ServerOptions parseCommandLine(int argc, char* argv[]) {
    ServerOptions options;
    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];
        if (arg == "-h" || arg == "--help") {
            printHelp();
            exit(0);
        } else if (arg == "-p" || arg == "--port") {
            if (i + 1 < argc) { options.port = argv[++i]; }
            else { throw std::runtime_error("--port requires an argument"); }
        } else if (arg == "-r" || arg == "--rate") {
            if (i + 1 < argc) { options.rate = std::stod(argv[++i]); }
            else { throw std::runtime_error("--rate requires an argument"); }
        } else if (arg == "-f" || arg == "--filename") {
            if (i + 1 < argc) { options.filename = argv[++i]; }
            else { throw std::runtime_error("--filename requires an argument"); }
        } else {
            throw std::runtime_error("Unknown option: " + arg);
        }
    }
    return options;
}

//---------------------------------------
// main
//---------------------------------------
int main(int argc, char* argv[]) {

    // Parse command line
    ServerOptions options;
    try {
        options = parseCommandLine(argc, argv);
    } catch (const std::exception& e) {
        std::cerr << "Error: " << e.what() << std::endl;
        return 1;
    }

    // Open server port for incoming data
    zmq::context_t out_context(1);
    zmq::socket_t socket_in(out_context, ZMQ_ROUTER);
    socket_in.bind("tcp://*:" + options.port);

    // Open output file
    std::ofstream ofs(options.filename);
    if( ! ofs.is_open() ){
        std::cerr << "Unable to open file: " << options.filename << std::endl;
        exit(-1);
    }
    std::cout << "Writing data to file: " << options.filename <<std::endl;

    // Print some setting info
    std::cout << "Listening for data on port: " << options.port << std::endl;
     if( options.rate >0.0 ){
        std::cout << "Limiting writing rate to " << options.rate << " EVIO blocks/sec" << std::endl;
    }else{
        std::cout << "No limitation set to output write rate" << std::endl;
    }

    while (!QUIT) {

        // Read one message.
        // (DEALER-ROUTER always send messages in pairs with identity first)
        zmq::message_t identity_msg;
        zmq::message_t data_msg;
        if (!socket_in.recv(identity_msg, zmq::recv_flags::none) || !socket_in.recv(data_msg, zmq::recv_flags::none)) {
            // Error occurred. Terminate program.
            exit(-1);
        }

        uint32_t identity = *reinterpret_cast<uint32_t*>(identity_msg.data());
        std::vector<char> data(static_cast<char*>(data_msg.data()), static_cast<char*>(data_msg.data()) + data_msg.size());
        ofs.write(data.data(), data.size());

        // TODO: Check if this is the end of the file and close if so.
    }

 
    // // Launch thread for gathering ROC data from streams
    // std::thread receive_thread(receive_data_thread, std::ref(socket_in), options.nrocs);
    
    // // Launch thread for combining ROC data into single buffers and sending
    // std::thread send_thread(send_data_thread, std::ref(socket_out), options.nrocs, options.no_output);

    // receive_thread.join();
    // send_thread.join();

    return 0;
}

