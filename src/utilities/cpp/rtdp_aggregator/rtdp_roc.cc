#include <zmq.hpp>
#include <string>
#include <iostream>
#include <thread>
#include <chrono>
#include <fstream>
#include <sstream>
#include <vector>
#include <cmath>
#include <fmt/core.h>

#include "EVIONetworkTransferHeader.h"

bool QUIT = false; // used to exit loop sending EVIO blocks
bool GO   = true; // used to delay start of sending EVIO blocks
std::chrono::system_clock::time_point GO_WHEN; // optionally specify extact time to start
uint64_t TOTAL_BLOCKS_SENT = 0; // This is used by the rates thread to calculate rates
uint64_t TOTAL_BYTES_SENT = 0;  // This is used by the rates thread to calculate rates

void command_subscriber_thread(const std::string &host_port);
void print_rates_thread(void);

#include <iostream>
#include <string>
#include <cstdlib>

struct CommandLineOptions {
    std::string rocFileDir = ".";
    uint32_t rocid = 1;
    std::string host = "localhost:5558";
    std::string cmdHost = "localhost:5559";
    bool loop = false;
    double rate = 0.0;
	bool printRates = true;
};

//---------------------------------------
// printHelp
//---------------------------------------
void printHelp() {
    std::cout << "Usage: [options]\n"
              << "-h, --help                       Print this help message\n"
              << "-r, --rocfiledir <directory>     Directory to look for input EVIO files\n"
              << "--rocid <id>                     rocid to send (used for file name and zmq identity)\n"
              << "--host <host[:port]>             Host (and port) to send EVIO data to (default: localhost:5558)\n"
              << "--cmdhost <host[:port]>          Host (and port) to get commands from (default: localhost:5559)\n"
              << "-l, --loop                       Loop over input file sending events indefinitely\n"
              << "-R, --rate <rateHz>              Rate to send EVIO blocks in Hz\n"
              << "-w,--wait                        Wait for cmdhost to tell when to start sending\n"
              << "-q,--quiet                       Operate in quiet mode (don't print rates)\n";
}

//---------------------------------------
// parseCommandLine
//---------------------------------------
CommandLineOptions parseCommandLine(int argc, char* argv[]) {
    CommandLineOptions options;
    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];
        if (arg == "-h" || arg == "--help") {
            printHelp();
            exit(0);
        } else if (arg == "-r" || arg == "--rocfiledir") {
            if (i + 1 < argc) {
                options.rocFileDir = argv[++i];
            } else {
                std::cerr << arg << " option requires one argument." << std::endl;
                exit(1);
            }
        } else if (arg == "--rocid") {
            if (i + 1 < argc) {
                options.rocid = std::stoi(argv[++i]);
            } else {
                std::cerr << arg << " option requires one argument." << std::endl;
                exit(1);
            }
        } else if (arg == "--host") {
            if (i + 1 < argc) {
                options.host = argv[++i];
            } else {
                std::cerr << arg << " option requires one argument." << std::endl;
                exit(1);
            }
        } else if (arg == "--cmdhost") {
            if (i + 1 < argc) {
                options.cmdHost = argv[++i];
            } else {
                std::cerr << arg << " option requires one argument." << std::endl;
                exit(1);
            }
        } else if (arg == "-l" || arg == "--loop") {
            options.loop = true;
        } else if (arg == "-R" || arg == "--rate") {
            if (i + 1 < argc) {
                options.rate = std::stod(argv[++i]);
            } else {
                std::cerr << arg << " option requires one argument." << std::endl;
                exit(1);
            }
        } else if (arg == "-w" || arg == "--wait") {
            GO = false;
        } else if (arg == "-q" || arg == "--quiet") {
            options.printRates = false;
        } else {
            std::cerr << "Unknown option: " << arg << std::endl;
            exit(1);
        }
    }
    return options;
}

//---------------------------------------
// main
//---------------------------------------
int main(int argc, char *argv[]) {

	// Parse the command line
    CommandLineOptions options = parseCommandLine(argc, argv);

    // Create socket for senting data to Data Concentrator
	// We set the identity (routing_id) to the rocid. This is just a 
	// convenient convention that ensures a unique identity.
    zmq::context_t context(1);
    zmq::socket_t socket(context, ZMQ_DEALER);
	socket.set(zmq::sockopt::routing_id, zmq::buffer(&options.rocid, sizeof(options.rocid)));

	// This sets the sending High Water Mark which tells zmq how many internal
	// buffers to keep before blocking send calls. Setting this to 1 gets us 
	// close to controlling the rate at which EVIO blocks are sent. Anything 
	// higher means zmqs internal optimizations kick in which may lead to bursty 
	// send rates.
	int hwm = 1;
	socket.set(zmq::sockopt::sndhwm, hwm);

	// Connect to server
	// (n.b. I don't think this actually establishes the connection. Just records
	// where it should eventually connect to when we try to send something.)
    socket.connect("tcp://" + options.host);

    // Open EVIO file (e.g. ROCfiles/roc001.evio)
    std::string fname = fmt::format("{}/roc{:03}.evio", options.rocFileDir, options.rocid);
	std::ifstream ifs(fname.c_str(), std::ifstream::binary);
	if(! ifs.is_open() ){
		std::cerr << "Unable to open file: " << fname << std::endl;
		return -1;
	}
	std::cout << " Opened EVIO file: " <<  fname << std::endl;

	// Start command subscriber thread to listen for out-of-band commands from server
	std::thread cmd_thread;
	if( ! options.cmdHost.empty() ){
    	cmd_thread = std::thread(command_subscriber_thread, options.cmdHost);
	} else {
		// No host:port specified to subscribe to. Set GO to true now since
		// we will never get a "GO" command otherwise.
		GO = true;
	}

	// (optionally) Start thread to periodically print the rate to the screen
	std::thread rates_thread;
	if( options.printRates ) rates_thread = std::thread(print_rates_thread);
    
	// Wait until we are told to go
	std::cout << "Waiting for GO ..." << std::endl;
	while( ! GO ) std::this_thread::sleep_for(std::chrono::milliseconds(100));

	// Wait until specific time (if one was set)
	std::chrono::system_clock::time_point defaultTimePoint;
	if(GO_WHEN != defaultTimePoint) {
		// Ugghh this is way too complex just to print the time_point!
		std::time_t time = std::chrono::system_clock::to_time_t(GO_WHEN);
		std::tm* ptm = std::localtime(&time);
		std::cout << "Waiting to start until: " << std::put_time(ptm, "%Y-%m-%d %H:%M:%S") << std::endl;
		std::this_thread::sleep_until(GO_WHEN);
	}
    
	// Prepare to govern rate (if rate>0.0)
	// (set loop_interval to 0 if rate is set to 0 which means go as fast as possible)
	auto loop_interval = std::chrono::duration<double>( options.rate > 0.0 ? (1.0/options.rate):0.0);
	auto last_time = std::chrono::steady_clock::now();

    // Loop over EVIO blocks in file
	std::cout << "Start event sending." << std::endl;
	uint64_t words_sent_total = 0;
	do{
		while( ! ifs.eof() ){
			
			// Read in network transfer header
			EVIONetworkTransferHeader nth;
			ifs.read( (char*)&nth, sizeof(nth) );
			if( ifs.eof() || ifs.gcount()!=sizeof(nth) ) break;
			
			// Check magic number and byteswap
			bool swap_needed = nth.magic_number != 0xc0da0100;
			if( swap_needed ) swap((uint32_t*)&nth, sizeof(nth)/sizeof(uint32_t));
			if( nth.magic_number != 0xc0da0100 ){
				std::cout << "==== Bad magic number! ====" << std::endl;
				nth.dump();
				return -1;
			}

			// Skip sending the NTH for now. This is how CODA groups many blocks
			// into a single send to make it more efficient.
			// socket.send( zmq::buffer((const char*)&nth, sizeof(nth)), zmq::send_flags::none);
			
			// Read in the ROC event block (this is actually many EVIO event blocks)
			uint32_t buff_len = nth.block_len - (sizeof(nth)/sizeof(uint32_t));
			if( buff_len > 0 ){ // end of file has empty block
				uint32_t buff[buff_len];
				ifs.read( (char*)buff, buff_len*sizeof(uint32_t) );
				if( swap_needed ) swap(buff, buff_len);

				// The DC will need to combine EVIO blocks from all ROCs. That is much
				// easier if we send a single block in each transfer.
				// Loop over EVIO blocks here, sending each.
				uint32_t words_sent = 0;
				while( words_sent < buff_len ){
					uint32_t *block_ptr = &buff[words_sent];
					uint32_t block_words = block_ptr[0]+1;
					socket.send( zmq::buffer((const char*)block_ptr, block_words * 4), zmq::send_flags::none);
					words_sent += block_words;
					TOTAL_BLOCKS_SENT++; // (for rates thread)
					TOTAL_BYTES_SENT = (words_sent_total + words_sent) * 4; // (for rates thread)

					// Optionally sleep enough to govern the rate
					// n.b. an empirical adjustment is applied based on a specific testing case. This is likely
					// not going to be correct for all use cases and should be revisted.
					auto now = std::chrono::steady_clock::now();
					auto elapsed = std::chrono::duration_cast<std::chrono::duration<double>>(now - last_time);  // Calculate elapsed time
					auto sleep_time = loop_interval - elapsed;  // Calculate the remaining time to sleep
					if( options.rate>0.0 ) sleep_time -= std::chrono::microseconds((int)(140-20*log10(options.rate))); // empirical adjustment (for running everything on ifarm9)
					if (sleep_time > std::chrono::duration<double>(0)) std::this_thread::sleep_for(sleep_time);
					last_time = std::chrono::steady_clock::now();

					if(QUIT) break;
				}
				words_sent_total += words_sent;
			}

			if(QUIT) break;
		}

		// Reset file pointer to start of file in case we are looping
		ifs.clear();
		ifs.seekg(0, std::ios::beg); 

	} while( options.loop ); // optionally loop over file indefinitely

	QUIT = true;

	// Sleep for 2 seconds to give the zmq and system TCP stack time to flush any
	// remaining buffers. Emprically, it was observed that sending a lot of
	// data and exiting right after resulted in some packets not being delivered.
	std::this_thread::sleep_for(std::chrono::seconds(2));

	std::cout << "\nFinished. Total sent: " << (double)words_sent_total*4.0/1000000.0 << " Mbytes " << std::endl;

	// Cleanup by closing socket.
	socket.close();
	context.close();

	// Cleanup command thread (if needed)
	std::cout << "Joining cmd thread " << std::endl;
	if (cmd_thread.joinable()) {
        cmd_thread.join();  // Wait for the thread to finish.
        std::cout << "Command thread has been joined." << std::endl;
	}

	// Cleanup print_rate thread (if needed)
	std::cout << "Joining rates thread " << std::endl;
	if (rates_thread.joinable()) {
        rates_thread.join();  // Wait for the thread to finish.
        std::cout << "Print Rate thread has been joined." << std::endl;
	}
	
    return 0;
}


//----------------------------------------------------------
// command_subscriber_thread
//
// This is run in a separate thread to establish a separate
// connection to the DC so it can send commands outside
// of the primary data stream connection. 
//----------------------------------------------------------
void command_subscriber_thread(const std::string &host_port) {
    zmq::context_t context(1);
    zmq::socket_t subscriber(context, ZMQ_SUB);
    subscriber.connect("tcp://"+host_port);
    subscriber.set(zmq::sockopt::subscribe, "ROCcommands");

	// This is used for polling the socket to make sure there 
	// are both parts of a message there before attempting to
	// recv them. It allows us to exit cleanly when QUIT=true.
	zmq::pollitem_t items[] = {{subscriber, 0, ZMQ_POLLIN, 0}};

    while (! QUIT) {

		// Check that both parts of a multi-part message are available
		zmq::poll(&items[0], 1, std::chrono::milliseconds(100));
		if( ! (items[0].revents & ZMQ_POLLIN) ) continue;

        zmq::message_t topic;
        zmq::message_t message;
        
        auto res1 = subscriber.recv(topic, zmq::recv_flags::none);
        auto res2 = subscriber.recv(message, zmq::recv_flags::none);
		if (!res1 || !res2) break; // If either message is not here, that is a problem

        std::string topic_str(static_cast<char*>(topic.data()), topic.size());
        std::string msg_str(static_cast<char*>(message.data()), message.size());

        std::cout << "Received on topic " << topic_str << ": " << msg_str << std::endl;

		if( msg_str == "GO" ){
			GO = true;
		}else if( msg_str.find_first_of("GO AT ")==0 ){
			std::tm tm = {};
    		std::istringstream ss(msg_str.substr(6)); // assume command of form "GO AT YYYY-MM-DD HH:MM:SS"
    		ss >> std::get_time(&tm, "%Y-%m-%d %H:%M:%S"); // Parse datetime
			GO_WHEN = std::chrono::system_clock::from_time_t(std::mktime(&tm));
			GO = true;
		}else if(msg_str == "QUIT"){
			QUIT = true;
		}else{
			std::cerr << "unknown command: " + msg_str << std::endl;
		}
    }
}

//----------------------------------------------------------
// print_rates_thread
//
// This is run in a separate thread to periodically print
// the EVIO block rate to the screen. 
//----------------------------------------------------------
void print_rates_thread(void) {
	auto last_time = std::chrono::steady_clock::now();
	uint64_t last_words_sent = 0;
	uint64_t last_bytes_sent = 0;

	while( ! QUIT ){
		// Sleep until time to print another rate line
		std::this_thread::sleep_for(std::chrono::seconds(2));

		// Only print rate if in the GO state (no sense filling screen with zeros)
		if( GO ){
			auto now = std::chrono::steady_clock::now();
			auto tdiff = std::chrono::duration<double>( now - last_time ).count();
			auto Nblocks_diff = TOTAL_BLOCKS_SENT - last_words_sent;
			last_words_sent   = TOTAL_BLOCKS_SENT;
			auto Nbytes_diff  = TOTAL_BYTES_SENT - last_bytes_sent;
			last_bytes_sent   = TOTAL_BYTES_SENT;
			last_time         = now;
			double blockrateHz = (double)Nblocks_diff/(double)tdiff;
			double datarateMbps = (double)Nbytes_diff*8.0/1.0E6/(double)tdiff;
			auto savePrecision = std::cout.precision();
			std::cout << "  " << std::fixed << std::setprecision(1)
			          << "Send rate (blocks per second): " << blockrateHz << " Hz" << "  (" << datarateMbps << " Mbps)"<<std::endl;
			std::cout.precision(savePrecision);
		}
	}
}