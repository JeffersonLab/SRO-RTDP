
#include <string>
#include <cstring>
#include <sstream>
#include <iostream>
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
    std::string host    = "localhost:5560";
    std::string port    = "5558";
    std::string cmdport = "5559";
    int nrocs = 1;
    double rate = 0;  // 0 means as fast as possible
    std::chrono::system_clock::time_point starttime = std::chrono::system_clock::now();  // Default to now
    int id = 0;
    bool no_output = false; // set to true to skip writing to output socket (for debugging)
};

//---------------------------------------
// printHelp
//---------------------------------------
void printHelp() {
    std::cout << "\nUsage: [options]\n\n"
              << "-h, --help                    Print help\n"
              << "--host <host[:port]>          Set the host (and port) to send the data to\n"
              << "-p, --port <port>             The TCP port number to listen to for data\n"
              << "--noout                       Do not send output to remote host (for debugging)\n"
              << "--cmdport <port>              Set the port to use for publishing commands to the rocs\n"
              << "-n, --nrocs <Nrocs>           The number of rocs to expect connections from\n"
              << "-r, --rate <rate>             The rate (in EVIO blocks/second) to send data out at\n"
              << "-t, --timestamp <starttime>   Time when rocs should start sending data\n"
              << "--id <id>                     Data Concentrator ID number\n";
    std::cout << "\n"
            "The value of starttime can be in one of several the forms including: \n\n"
            "\"YYYY-MM-DD HH:MM:SS\" to specify specific date/time\n\n"
            "\"HH:MM:SS\" to specify a specific date/time on current day\n\n"
            "\"+HH:MM:SS\" to specify an amount of time in the future relative to the current time.\n\n"
            "Note that when specifying the time part (HH:MM:SS) the hours and minutes\n"
            "can be omitted so that only a relative time in seconds is given. For example\n"
            "giving the argument \"-t +10\" will start processing 10 seconds from now.\n\n";
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
        } else if (arg == "--host") {
            if (i + 1 < argc) { options.host = argv[++i]; if(options.host.find(':') == std::string::npos) options.host += ":5560"; }
            else { throw std::runtime_error("--host requires an argument"); }
        } else if (arg == "-p" || arg == "--port") {
            if (i + 1 < argc) { options.port = argv[++i]; }
            else { throw std::runtime_error("--port requires an argument"); }
        }else if (arg == "--noout" ) {
            options.no_output = true;
        } else if (arg == "--cmdport") {
            if (i + 1 < argc) { options.cmdport = argv[++i]; }
            else { throw std::runtime_error("--cmdport requires an argument"); }
        } else if (arg == "-n" || arg == "--nrocs") {
            if (i + 1 < argc) { options.nrocs = std::stoi(argv[++i]); }
            else { throw std::runtime_error("--nrocs requires an argument"); }
        } else if (arg == "-r" || arg == "--rate") {
            if (i + 1 < argc) { options.rate = std::stod(argv[++i]); }
            else { throw std::runtime_error("--rate requires an argument"); }
        } else if (arg == "-t" || arg == "--timestamp") {
            if (i + 1 < argc) {
                std::string time_str = argv[++i];
                // Parse time logic here
                if (time_str.front() == '+') {
                    // Relative time parsing
                    time_str.erase(0, 1); // Remove '+'
                    std::istringstream ss(time_str);
                    char delim;
                    int hours = 0, minutes = 0, seconds;
                    if (time_str.find(':') != std::string::npos) {
                        if (!(ss >> hours >> delim >> minutes >> delim >> seconds)) {
                            throw std::runtime_error("Invalid time format");
                        }
                    } else {
                        seconds = std::stoi(time_str);
                    }
                    options.starttime = std::chrono::system_clock::now()
                        + std::chrono::hours(hours)
                        + std::chrono::minutes(minutes)
                        + std::chrono::seconds(seconds);
                } else {
                    // Absolute time parsing
                    std::tm tm = {};
                    std::istringstream ss(time_str);
                    if (time_str.find('-') != std::string::npos) {
                        ss >> std::get_time(&tm, "%Y-%m-%d %H:%M:%S");
                    } else {
                        ss >> std::get_time(&tm, "%H:%M:%S");
                    }
                    options.starttime = std::chrono::system_clock::from_time_t(std::mktime(&tm));
                }
            } else { throw std::runtime_error("--timestamp requires an argument"); }
        } else if (arg == "--id") {
            if (i + 1 < argc) { options.id = std::stoi(argv[++i]); }
            else { throw std::runtime_error("--id requires an argument"); }
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
        // Use options in your server setup
        std::cout << "Server starting with ID: " << options.id << std::endl;
    } catch (const std::exception& e) {
        std::cerr << "Error: " << e.what() << std::endl;
        return 1;
    }

    // Open publisher for sending commands to rocs
    zmq::context_t cmd_context(1);
    zmq::socket_t publisher(cmd_context, ZMQ_PUB);
    publisher.bind("tcp://*:" + options.cmdport);

    // Open server port for incoming data
    zmq::context_t out_context(1);
    zmq::socket_t socket_in(out_context, ZMQ_ROUTER);
    socket_in.bind("tcp://*:" + options.port);

    // Create socket for senting data to next stage
	// We set the identity (routing_id) to the rocid. This is just a 
	// convenient convention that ensures a unique identity.
    zmq::context_t in_context(1);
    zmq::socket_t socket_out(in_context, ZMQ_DEALER);
    if( ! options.no_output ) {
        socket_out.set(zmq::sockopt::routing_id, zmq::buffer(&options.id, sizeof(options.id)));
        int hwm = 1;  // (see comments in rtdp_roc.cc)
        socket_out.set(zmq::sockopt::sndhwm, hwm);
        socket_out.connect("tcp://" + options.host); // (see comments in rtdp_roc.cc)
    }

    // Print some setting info
    std::cout << "Listening for data on port: " << options.port << std::endl;
    std::cout << "Writing data to: " << options.host << std::endl;
    std::cout << "Publishing commands on port: " << options.cmdport << std::endl;
    if( options.rate >0.0 ){
        std::cout << "Limiting writing rate to " << options.rate << " EVIO blocks/sec" << std::endl;
    }else{
        std::cout << "No limitation set to output write rate" << std::endl;
    }
    std::cout << "Waiting for " << options.nrocs << " ROCs to connect" << std::endl;

    // Launch thread for gathering ROC data from streams
    std::thread receive_thread(receive_data_thread, std::ref(socket_in), options.nrocs);
    
    // Launch thread for combining ROC data into single buffers and sending
    std::thread send_thread(send_data_thread, std::ref(socket_out), options.nrocs, options.no_output);

    receive_thread.join();
    send_thread.join();

    return 0;
}


//----------------------------------------------------------------------
// receive_data_thread
//
// This is run inside a thread whose job is to read data messages sent
// by ROCs and store them in queues. If it detects that the number of 
// queues is equal to the number of expected ROCs AND that each queue
// has at least one entry (i.e. EVIO block) then it will notify the
// process_data_thread so it can pop an EVIO block off of each queue
// and combine them into an aggregated event to send on to the next stage.
//----------------------------------------------------------------------
void receive_data_thread(zmq::socket_t &socket, const int nrocs){

    while (!QUIT) {

        // Read one message.
        // (DEALER-ROUTER always send messages in pairs with identity first)
        zmq::message_t identity_msg;
        zmq::message_t data_msg;
        if (!socket.recv(identity_msg, zmq::recv_flags::none) || !socket.recv(data_msg, zmq::recv_flags::none)) {
            // Error occurred. Terminate program.
            exit(-1);
        }

        uint32_t identity = *reinterpret_cast<uint32_t*>(identity_msg.data());
        std::vector<uint8_t> data(static_cast<uint8_t*>(data_msg.data()), static_cast<uint8_t*>(data_msg.data()) + data_msg.size());
        {
            // Use contained scope and lock_guard to automatically release mutex
            std::lock_guard<std::mutex> lock(RECEIVED_DATA_MUTEX);
            RECEIVED_DATA[identity].push(std::move(data));

            if( RECEIVED_DATA.size() == nrocs ){
                // Check that all queues have at least 1 entry
                bool all_queues_ready = std::all_of(RECEIVED_DATA.begin(), RECEIVED_DATA.end(), [](const auto& pair) { return !pair.second.empty(); });
                if( all_queues_ready ) ALL_QUEUES_READY_COND.notify_one();
            }
        }
    }
}

//----------------------------------------------------------------------
// send_data_thread
//
// This is run inside a thread whose job is to send data messages that
// it builds by combining buffers from each of the ROC queues. 
//
// If the no_output flag is true, then the buffers are created, but the
// actual send call is skipped. (This allows rates to be tested for
// debugging.)
//----------------------------------------------------------------------
void send_data_thread(zmq::socket_t &socket, const int nrocs, bool no_output){

    while (!QUIT) {

        // Block until notified all queues have at least one EVI block
        std::unique_lock<std::mutex> lock(RECEIVED_DATA_MUTEX);
        ALL_QUEUES_READY_COND.wait(lock, [] {
            // Verify that no queue is empty
            for (const auto& pair : RECEIVED_DATA) {
                if (!pair.second.empty()) return true;
            }
            return false;
        });

        // Pop the first EVIO block off of each queue into local vector
        // (this let's us quickly release mutex before processing below)
        std::map< uint32_t, std::vector<uint8_t> > data;
        for (auto& pair : RECEIVED_DATA) {
            auto& rocid = pair.first;
            auto& queue = pair.second;
            if (!queue.empty()) {
                data[rocid] = std::move(queue.front());
                queue.pop();
            }
        }

        // Count number of bytes in all EVIO blocks
        size_t data_bytes = std::accumulate(data.begin(), data.end(), size_t(0), [](size_t sum, const auto& pair) { return sum + pair.second.size();});

        // Copy all data into an EVIO bank of banks
        // Total words includes 2 words for EVIO bank of banks header used
        // to encapsulate the ROC data
        uint32_t buff_words = data_bytes/4 + 2;
        uint32_t buff[buff_words];
        uint32_t idx = 0;
        buff[idx++] = buff_words -1; // exclusive length of bank of banks buffer
        buff[idx++] = 0x0; // FIXME: set header vals
        for( auto& pair : data ){
            memcpy((char*)&buff[idx], pair.second.data(), pair.second.size());
            idx += pair.second.size()/4;
        }

        // Send data to next stage
        // std::cout << "SENDING buffer with " << idx << " words" << std::endl;
        // for(auto &pair: RECEIVED_DATA) std::cout << " queue size for " << pair.first <<" : " << pair.second.size() << std::endl;
        if( ! no_output ) socket.send( zmq::buffer((const char*)buff, idx * 4), zmq::send_flags::none);
    }
}