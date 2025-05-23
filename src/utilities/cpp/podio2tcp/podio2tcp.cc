
#include <iostream>
#include <fstream>
#include <thread>
#include <chrono>
#include <vector>
#include <stdexcept>
#include <iomanip>

#include <TFile.h>
#include <TMemFile.h>
#include <TTree.h>
#include <TMessage.h>
#include <TBufferFile.h>
#include <TError.h>

#include <unistd.h> // For getpid()

#include <zmq.hpp>

#include "SQLiteRateLogger.h"

// Global pointers to the trees in the input file.
TFile* ginputfile = nullptr;
TTree* gevents_tree = nullptr;
TTree* gruns_tree = nullptr;
TTree* gmetadata_tree = nullptr;
TTree* gpodio_metadata_tree = nullptr;

// Global SQlite Rate logger
/// NOTE: data format for the sender DB
// sqlite>.schema rate_logs
// CREATE TABLE rate_logs (
//     id INTEGER PRIMARY KEY AUTOINCREMENT,
//     timestamp_utc_ms INTEGER,
//     pid STRING,
//     rateHz_read_period REAL,
//     rateHz_sent_period REAL,
//     rateMbps_read_period REAL,
//     rateMbps_sent_period REAL,
//     rateHz_read_total REAL,
//     rateHz_sent_total REAL,
//     rateMbps_read_total REAL,
//     rateMbps_sent_total REAL
// );
SQLiteRateLogger rate_logger;
std::string RATE_DB_COLUMNS = "timestamp_utc_ms, pid, rateHz_read_period, rateHz_sent_period, "
                          "rateMbps_read_period, rateMbps_sent_period, rateHz_read_total, "
                          "rateHz_sent_total, rateMbps_read_total, rateMbps_sent_total";

// zmq socket to stream events through
zmq::socket_t *gventilator = nullptr;
int DEFAULT_ZMQ_PORT = 55577;

// Number of seconds to ignore read/send stats in order to allow zmq buffers to fill
double warm_up_time = 5.0;


//=======================
class CommandLineOptions {
public:
    std::string inputFilename;
    std::string outfile;
    std::string remoteIP = "localhost";
    std::string sqliteFilename; // SQLite DB name
    int port = DEFAULT_ZMQ_PORT; // Default
    int groupevents = 50;
    bool loop = false;
    double rate = 0.0; // Unset

    static CommandLineOptions Parse(int argc, char* argv[]) {
        CommandLineOptions options;
        for (int i = 1; i < argc; ++i) {
            std::string arg = argv[i];
            if (arg == "-h" || arg == "--help") {
                PrintUsage();
                exit(0);
            } else if (arg == "-p" || arg == "--port") {
                if (i + 1 < argc) {
                    options.port = std::stoi(argv[++i]);
                }
            } else if (arg == "-i" || arg == "--ip-address") {
                if (i + 1 < argc) {
                    options.remoteIP = argv[++i];
                }
            } else if (arg == "-g" || arg == "--groupevents") {
                if (i + 1 < argc) {
                    options.groupevents = std::stoi(argv[++i]);
                }
            } else if (arg == "-o" || arg == "--outfile") {
                if (i + 1 < argc) {
                    options.outfile = argv[++i];
                }
            } else if (arg == "-l" || arg == "--loop") {
                options.loop = true;
            } else if (arg == "-r" || arg == "--rate") {
                if (i + 1 < argc) {
                    options.rate = std::stod(argv[++i]);
                }
            } else if (arg == "-s" || arg == "--sqlfile") { // SQLite3 file option
                if (i + 1 < argc) {
                    options.sqliteFilename = argv[++i];
                }
            } else {
                options.inputFilename = arg;
            }
        }

        // Validate required inputs
        if (options.inputFilename.empty()) {
            std::cerr << "Error: inputfilename is required.\n";
            PrintUsage();
            exit(1);
        }

        return options;
    }

    static void PrintUsage() {
        std::cout << "\n"
                  << "Usage: podio2tcp [options] inputfilename\n"
                  << "\n"
                  << "-h, --help   Print this help statement\n"
                  << "-i, --ip-address <ip> Specify the remote IP address to connect to (default is 'localhost')\n"
                  << "-p, --port <port> Set ZMQ port to listen on (default is 55577)\n"
                  << "-o, --outfile <filename> Convert input podio root file to podio stream file with this filename\n"
                  << "-g, --groupevents <num> Group this many events together in buffer when serializing\n"
                  << "-l, --loop When sending, loop over the input file forever\n"
                  << "-r, --rate <rate> Limit rate to this in Hz (not yet implemented)\n"
                  << "-s, --sqlfile <dbname> Specify SQLite database as input\n"
                  << "\n"
                  << "inputfilename represents either a podio root file (e.g. simout.edm4hep.root)\n"
                  << "or a podio stream file (e.g. simout.edm4hep.root.podiostr).\n"
                  << "\n"
                  << "If --sqlfile is used, it specifies the SQLite database output.\n"
                  << "\n";
    }
};
//.........................................................................



//============================================================================================
// EventsToBuffer
/**
 * @brief Serialize the podio trees for the specified event range into a provided buffer.
 * 
 * The number of events serialized is returned. If the indicated range exceeds the number
 * of events in the tree, then only the events available are serialized, and that number is
 * returned. A value of zero is returned if the range does not overlap at all with the
 * number of events in the tree or if the tree pointers are not valid. The value of
 * firstevent starts with 0.
 * 
 * @param firstevent The index of the first event to serialize, starting from 0.
 * @param numevents The number of events to serialize starting from the firstevent.
 * @param buff A reference to a vector of uint8_t where the serialized events will be stored.
 * @return The number of events actually serialized into the buffer. Returns 0 if the range
 * does not overlap with the events in the tree or if the tree pointers are not valid.
 */
int EventsToBuffer( Long64_t firstevent, Long64_t numevents, std::vector<uint8_t> &buff)
{
    auto savedir = gDirectory;  // save current root directory

    // Create a memory resident file for the copied tree(s) to be placed in
    TMemFile memfile("tmpdir", "recreate");

    // Copy trees into memory resident file.
    // The events tree only copies a range of events while the metadata trees
    // (which have single entires) are copied completely.
    // These copies are automatically destroyed when memfile goes out of scope.

    int Nevents_copied = 0;
    if( gruns_tree           ) gruns_tree->CopyTree("");
    if( gmetadata_tree       ) gmetadata_tree->CopyTree("");
    if( gpodio_metadata_tree ) gpodio_metadata_tree->CopyTree("");
    if( gevents_tree         ){
        // The following line is *very* slow
        auto t = gevents_tree->CopyTree("", "", numevents, firstevent);  // CopyTree(selection, options, nentries, firstentry)
        Nevents_copied = t->GetEntries();
    }
 
    savedir->cd();  // restore saved directory

    // Create a TMessage object and serialize the TMemfile into it
    // TMessage *tm = new TMessage(kMESS_ANY);
    TMessage tm(kMESS_ANY);
    memfile.Write();
    tm.WriteLong64(memfile.GetEND()); // see treeClient.C ROOT tutorial
    memfile.CopyTo(tm);

    // Copy serialized message into provided buffer
    buff.resize(tm.Length());
    std::memcpy(buff.data(), tm.Buffer(), tm.Length());
    return Nevents_copied;
}

//-------------------------------------------------------------
// WriteBufferToStreamFile
//-------------------------------------------------------------
void WriteBufferToStreamFile(std::ofstream &ofstream, Long64_t Nevents, std::vector<uint8_t> &buff)
{
    ofstream << Nevents 
             << " " << buff.size();
    ofstream.write( (const char*)buff.data(), buff.size() );
}

//-------------------------------------------------------------
// ReadBufferFromStreamFile
//-------------------------------------------------------------
Long64_t ReadBufferFromStreamFile(std::ifstream &ifstream, std::vector<uint8_t> &buff)
{
    Long64_t Nevents = 0;
    size_t bufflen = 0;
    ifstream >> Nevents;
    ifstream >> bufflen;
    buff.resize(bufflen);
    ifstream.read( (char*)buff.data(), bufflen );

    return Nevents;
}

//============================================================================================
// OpenPODIOFile
/**
 * @brief Open the specified PODIO ROOT file and capture pointers to the PODIO TTrees.
 * 
 * The captured pointers are stored in global variables for use by other routines. This
 * function is a prerequisite for other operations that interact with the PODIO data
 * structures.
 * 
 * @param podiofilename A reference to the string containing the name of the PODIO ROOT file
 * to open.
 * 
 * @note This function updates global variables with pointers to the TTrees contained within
 * the opened PODIO ROOT file. It's important to call this before attempting to access or
 * serialize data from the TTrees.
 */
void OpenPODIOFile(std::string &podiofilename)
{
    // This suppresses those annoying warnings about no dictionary when
    // the ROOT file is opened. (It probably suppresses others too.)
    gErrorIgnoreLevel = kError;

    if( ginputfile ) delete ginputfile;
    ginputfile = new TFile(podiofilename.c_str());
    if( ! ginputfile->IsOpen() ) throw std::runtime_error("Failed to open file: " + podiofilename);

    // Get pointers to the metadata trees.
    // Set pointers to nullptr first in case any fails 
    gevents_tree = nullptr;
    gruns_tree = nullptr;
    gmetadata_tree = nullptr;
    gpodio_metadata_tree = nullptr;
    ginputfile->GetObject("events", gevents_tree);
    ginputfile->GetObject("runs", gruns_tree);
    ginputfile->GetObject("metadata", gmetadata_tree);
    ginputfile->GetObject("podio_metadata", gpodio_metadata_tree);

    if(gevents_tree == nullptr  ) throw std::runtime_error("Failed to find events tree in: " + podiofilename);
    
    std::cout << "Opened PODIO ROOT file: \"" << podiofilename << "\"  with " << gevents_tree->GetEntries() << " events" << std::endl;
}

//============================================================================================
// ConvertPODIOtoStreamFile
/**
 * @brief Convert the specified podio root file into a podio stream file.
 * 
 * The stream file is a simple format that saves the serialized TMemFile objects
 * for chunks of Nevents_per_group events. It is very slow to generate the serialized
 * form so it is worth it to do the serialization once and then reuse the serialized
 * form many times.
 * 
 * @param podiofilename The name of the input podio root file.
 * @param streamfilename The name of the output stream file. If empty, a default name is used.
 * @param Nevents_per_group The number of events per group to serialize together. Defaults to 50.
 */
void ConvertPODIOtoStreamFile(std::string &podiofilename, std::string streamfilename="", Long64_t Nevents_per_group=50)
{
    if( streamfilename.empty() ) streamfilename = podiofilename + ".podiostr";
    std::ofstream streamfile(streamfilename);

    OpenPODIOFile(podiofilename); // will throw exception if unsuccessful

    Long64_t Nevents_read = 0;
    size_t Nbytes_written = 0;
    auto Nevents = gevents_tree->GetEntries();
    std::vector<uint8_t> buff;
    while( Nevents_read < Nevents ){
        auto N = EventsToBuffer( Nevents_read, Nevents_per_group, buff);
        Nevents_read += N;

        auto Nbytes_before = streamfile.tellp();
        WriteBufferToStreamFile( streamfile, N, buff );
        auto Nbytes_after = streamfile.tellp();

        // n.b. the number of events and buffer size are written as strings so
        // we use the stream position pointer to know exactly how many bytes 
        // were written.
        Nbytes_written += Nbytes_after - Nbytes_before; 

        std::cout << "  " << Nevents_read << " events written (" << Nbytes_written << " bytes)    \r";
        std::cout.flush();
    }
    streamfile.close();
    std::cout << std::endl << "Done." << std::endl;
    std::cout << "  Total events written: " << Nevents_read << std::endl;
    std::cout << "  Total bytes  written: " << Nbytes_written << std::endl;;
    std::cout << std::endl;
}

//============================================================================================
// UpdateStats
void UpdateStats(size_t Nevents_read, size_t Nbytes_read, size_t Nbytes_sent,
                    const std::string& pid_str, bool log2db=false) {
    static size_t Nevents_read_period = 0;
    static size_t Nevents_read_total  = 0;
    static size_t Nevents_sent_period = 0;
    static size_t Nevents_sent_total  = 0;
    static size_t Nbytes_read_period  = 0;
    static size_t Nbytes_read_total   = 0;
    static size_t Nbytes_sent_period  = 0;
    static size_t Nbytes_sent_total   = 0;
    
    auto Nevents_sent = Nbytes_sent>0 ? Nevents_read:0;

    static double duration_period = 0.0;
    static double duration_total = 0.0;
    static auto first_time = std::chrono::high_resolution_clock::now();
    static auto last_time = std::chrono::high_resolution_clock::now();
    auto now = std::chrono::high_resolution_clock::now();

    auto time_since_start = std::chrono::duration<double>(now - first_time).count();
    if( time_since_start < warm_up_time ){
        static bool printed_message = false;
        if(!printed_message){
            std::cout << "Allowing zmq buffers to fill for " << warm_up_time << " seconds ... " << std::endl;
            printed_message = true;
        }
        last_time = now;
        return;
    }

    auto duration = std::chrono::duration<double>(now - last_time).count();
    last_time = now;

    Nevents_read_period += Nevents_read;
    Nevents_sent_period += Nevents_sent;
    Nbytes_read_period  += Nbytes_read;
    Nbytes_sent_period  += Nbytes_sent;
    duration_period     += duration;

    // Only update totals and print to screen at most once per second
    if( duration_period < 1.0 ) return;

    Nevents_read_total += Nevents_read_period;
    Nevents_sent_total += Nevents_sent_period;
    Nbytes_read_total  += Nbytes_read_period;
    Nbytes_sent_total  += Nbytes_sent_period;
    duration_total     += duration_period;

    auto rateMbps_read_period = Nbytes_read_period/duration_period*8.0/1.0E6;
    auto rateMbps_sent_period = Nbytes_sent_period/duration_period*8.0/1.0E6;
    auto rateMbps_read_total  = Nbytes_read_total/duration_total*8.0/1.0E6;
    auto rateMbps_sent_total  = Nbytes_sent_total/duration_total*8.0/1.0E6;

    auto rateHz_read_period = Nevents_read_period/duration_period;
    auto rateHz_sent_period = Nevents_sent_period/duration_period;
    auto rateHz_read_total = Nevents_read_total/duration_total;
    auto rateHz_sent_total = Nevents_sent_total/duration_total;
    
    auto savePrecision = std::cout.precision();
    std::cout << "  " << std::fixed << std::setprecision(1)
    << " read(sent): " << rateHz_read_period << " (" << rateHz_sent_period << ") Hz  " << rateMbps_read_period << " (" << rateMbps_sent_period << " Mbps)"
    << " [AVG read/sent: " << rateHz_read_total << " (" << rateHz_sent_total << ") Hz  " << rateMbps_read_total << " (" << rateMbps_sent_total << " Mbps)]"
    << std::endl;
    std::cout.precision(savePrecision);
    
    // Log to SQLite DB
    if (log2db) {
        auto utc_timestamp_in_ms = 
            std::chrono::duration_cast<std::chrono::milliseconds>(now.time_since_epoch()).count();

        std::ostringstream values;
        values << std::to_string(utc_timestamp_in_ms) << ", "
            << pid_str << ", "
            << std::fixed << std::setprecision(1)  // Ensure consistent floating-point precision
            << rateHz_read_period << ", "
            << rateHz_sent_period << ", "
            << rateMbps_read_period << ", "
            << rateMbps_sent_period << ", "
            << rateHz_read_total << ", "
            << rateHz_sent_total << ", "
            << rateMbps_read_total << ", "
            << rateMbps_sent_total;

            // Insert into the database
        if (!rate_logger.insertRateLog(RATE_DB_COLUMNS, values.str())) {
            std::cerr << "Failed to insert record into the database." << std::endl;
        }
    }

    Nevents_read_period = 0;
    Nevents_sent_period = 0 ;
    Nbytes_read_period  = 0;
    Nbytes_sent_period  = 0;
    duration_period     = 0.0;
}

//============================================================================================
// SendFromPodioRootFile
void SendFromPodioRootFile(CommandLineOptions &options, Long64_t Nevents_per_group=50)
{
    std::string podiofilename = options.inputFilename;
    OpenPODIOFile(podiofilename); // will throw exception if unsuccessful

    // auto last_time = std::chrono::high_resolution_clock::now();
    Long64_t Nevents_sent = 0;
    auto Nevents = gevents_tree->GetEntries();
    std::vector<uint8_t> buff;
    while( Nevents_sent < Nevents ){
        size_t Nbytes_sent = 0;
        auto Nevents_in_buffer = EventsToBuffer( Nevents_sent, Nevents_per_group, buff);
        zmq::message_t message(buff.data(), buff.size());
        bool sent = gventilator->send(message, zmq::send_flags::dontwait).has_value();
        if( sent ){
            Nevents_sent += Nevents_in_buffer;
            Nbytes_sent += buff.size();
        }else{
            // std::cout << "Unable to send (is receiver running?). Waiting 2 seconds..." << std::endl;
            std::this_thread::sleep_for(std::chrono::seconds(1));
        }

        // Print ticker. Also write to SQLite3 DB.
        UpdateStats(Nevents_in_buffer, buff.size(), Nbytes_sent, std::to_string(getpid()),
                    !options.sqliteFilename.empty());
    }
}

//============================================================================================
// SendFromPodioStreamFile
void SendFromPodioStreamFile(CommandLineOptions &options) {
    std::string streamfilename = options.inputFilename;
    std::ifstream streamfile(streamfilename);
    if( !streamfile.is_open() ) throw std::runtime_error("Failed to open file: " + streamfilename);

    auto last_time = std::chrono::high_resolution_clock::now();
    std::vector<uint8_t> buff;
    while( !streamfile.eof() ){
        
        size_t Nbytes_sent = 0;
        auto Nevents_in_buffer  = ReadBufferFromStreamFile( streamfile, buff);
        if(buff.empty()) break;
        zmq::message_t message(buff.data(), buff.size());
        bool sent = gventilator->send(message, zmq::send_flags::dontwait).has_value();
        if( sent ){
            Nbytes_sent += buff.size();
        }else{
            // std::cout << "Unable to send (is receiver running?). Waiting 2 seconds..." << std::endl;
            std::this_thread::sleep_for(std::chrono::seconds(1));
        }

        // Print ticker. Also write to SQLite3 DB.
        UpdateStats(Nevents_in_buffer, buff.size(), Nbytes_sent, std::to_string(getpid()),
                    !options.sqliteFilename.empty());
     }
}


void printOptionsSummary (CommandLineOptions &options) {
    std::cout << "Input Filename: " << options.inputFilename << "\n";
    if (!options.outfile.empty()) std::cout << "Output Filename: " << options.outfile << "\n";
    if (options.port != DEFAULT_ZMQ_PORT) std::cout << "Port: " << options.port << "\n";
    if (options.loop) std::cout << "Loop: enabled\n";
    if (options.rate > 0.0) std::cout << "Rate: " << options.rate << " Hz\n";
    if (!options.sqliteFilename.empty()) {
        std::cout << "Rates logging into SQLite3 DB: " << options.sqliteFilename << "\n\n";
    }
}


//-------------------------------------------------------------
// main
//-------------------------------------------------------------
int main(int argc, char *argv[]){

    // Parse command options (will print help and exit if help is asked for)
    CommandLineOptions options = CommandLineOptions::Parse(argc, argv);

    // Print summary
    printOptionsSummary(options);

    // User specified output file so convert to podio stream file
    if (!options.outfile.empty()){
        std::cout << "--- Converting podio ROOT file to podio stream file ---" << std::endl;
        std::cout << "   Input Filename: " << options.inputFilename << std::endl;
        std::cout << "  Output Filename: " << options.outfile << std::endl;
        std::cout << "     Group Events: " << options.groupevents << std::endl;
        std::cout << std::endl;
        ConvertPODIOtoStreamFile(options.inputFilename, options.outfile, options.groupevents);
        return 0;
    }

    // Setup zmq communication
    zmq::context_t context(1);
    zmq::socket_t ventilator(context, ZMQ_PUSH);
    gventilator = &ventilator; // (yes, I know, this is a bad way to do this)
    ventilator.set(zmq::sockopt::sndhwm, 100); // Set High Water Mark for maximum number of messages to queue before stalling
    std::string connectAddress = "tcp://" + options.remoteIP + ":" + std::to_string(options.port);
    try {
        ventilator.connect(connectAddress.c_str());
        std::cout << "ZeroMQ CLIENT connected to: " << connectAddress << "\n";
    } catch (const zmq::error_t& e) {
        std::cout << "Error: Failed to connect to the address [" << connectAddress << "]:" << e.what() << "\n";
        return 1;
    }

    // Setup SQLite3 database connection
    if (!options.sqliteFilename.empty() && !rate_logger.openDB(options.sqliteFilename)) {
        std::cerr << "Failed to open database: " << options.sqliteFilename << std::endl;
        return 1;
    }

    // Determine if input file is root or stream form. (Just check the suffix.)
    bool input_is_root = false;
    std::string suffix = ".root";
    if (options.inputFilename.length() >= suffix.length()) {
        input_is_root = options.inputFilename.compare(options.inputFilename.length() - suffix.length(), suffix.length(), suffix) == 0;
    }

    // Stream events from podio ROOT or stream file
    do {
        if( input_is_root ){
            SendFromPodioRootFile(options, options.groupevents);
        }else{
            SendFromPodioStreamFile(options);
        }
    } while( options.loop ); // loop runs once if loop is false or infinitely if true.

    // Close SQLite3 DB
    rate_logger.closeDB();

    return 0;
}
