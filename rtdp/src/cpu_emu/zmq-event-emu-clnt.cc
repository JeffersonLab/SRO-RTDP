//
//  Basic ZeoMQ client in C++
//  Sends frames to subscriber
//
#include <unistd.h>
#include <zmq.hpp>
#include <string>
#include <iostream>
#include <netinet/in.h>
#include "buffer_packet_emu.hh"
#include <random>
#include <cassert>
#include <thread>
#include <map>
#include <yaml.h>
#include <stack>

using namespace std;
using namespace zmq;
using namespace chrono;

// Power of ten scaling constants
const float  B_b   = 1e1;
const float  b_B   = 1/B_b;
const float  G_1   = 1e9;
const float  one_G = 1/G_1;
const float  G_K   = 1e6;
const float  K_G   = 1/G_K;
const float  G_M   = 1e3;
const float  M_G   = 1/G_M;
const float  K_1   = 1e3;
const float  one_K = 1/K_1;
const float  M_1   = 1e6;
const float  one_M = 1/M_1;
const float  m_1   = 1e-3;
const float  one_m = 1/m_1;
const float  m_u   = 1e3 ;
const float  u_m   = 1/m_u;
const float  u_1   = 1e-6;
const float  one_u = 1/u_1;
const float  n_1   = 1e-9;
const float  one_n = 1/n_1;
const float  n_m   = 1e-6;
const float  m_n   = 1/n_m;

const size_t sz1K   = 1024;
const size_t sz1M   = sz1K*sz1K;
const size_t sz1G   = sz1M*sz1K;
    
void   Usage()
{
    char usage_str[] =
        "\nUsage: \n\
        -h help  \n\
        -a stream/channel id (0) \n\
        -f event count (100) \n\
        -p publication port (8888) \n\
        -r bit rate to send (Gbps) (1)\n\
        -s event size (MB) (1) \n\
        -v verbose = 0/1 (1)  \n\
        -y yaml config file  \n\n";

    cout << " [emulate_stream]: " << usage_str;
}

map<string,string> mymap;

void parse_yaml0(const char *filename, uint8_t vrbs=0) {
    FILE *file = fopen(filename, "r");
    if (!file) {
        perror("Failed to open file");
        return;
    }

    yaml_parser_t parser;
    yaml_event_t event;

    if (!yaml_parser_initialize(&parser)) {
        cerr << "Failed to initialize parser! " << endl;
        fclose(file);
        return;
    }

    yaml_parser_set_input_file(&parser, file);
    stack<string> lbl_stk;
    string s, s1;

    vector<string> lbls;
    lbls.push_back("pub_port");
    lbls.push_back("stream_id"); lbls.push_back("frame_sz_MB"); lbls.push_back("frame_cnt");
    lbls.push_back("avg_bit_rt_Gbps"); lbls.push_back("verbosity"); 
    
    auto it = lbls.begin(); //a hack to get the type

    while (yaml_parser_parse(&parser, &event)) {
        switch (event.type) {
        case YAML_NO_EVENT:
            break;
        case YAML_STREAM_START_EVENT:
            if(DBG) cout << 0 << " [zmq-event-emu-clnt]: Stream started " << endl;
            break;
        case YAML_STREAM_END_EVENT:
            if(DBG) cout << 0 << " [zmq-event-emu-clnt]: Stream ended " << endl;
            break;
        case YAML_DOCUMENT_START_EVENT:
            if(DBG) cout << 0 << " [zmq-event-emu-clnt]: Document started " << endl;
            break;
        case YAML_DOCUMENT_END_EVENT:
            if(DBG) cout << 0 << " [zmq-event-emu-clnt]: Document ended " << endl;
            break;
        case YAML_MAPPING_START_EVENT:
            if(DBG) cout << 0 << " [zmq-event-emu-clnt]: Mapping started " << endl;
            break;
        case YAML_MAPPING_END_EVENT:
            if(DBG) cout << 0 << " [zmq-event-emu-clnt]: Mapping ended " << endl;
            break;
        case YAML_SEQUENCE_START_EVENT:
            if(DBG) cout << 0 << " [zmq-event-emu-clnt]: Sequence started " << endl;
            break;
        case YAML_SEQUENCE_END_EVENT:
            if(DBG) cout << 0 << " [zmq-event-emu-clnt]: Sequence ended " << endl;
            break;
        case YAML_SCALAR_EVENT:
            s = (const char*)event.data.scalar.value;
            it = find(lbls.begin(), lbls.end(), s);
            if (it != lbls.end()) {
                if(DBG) cout << 0 << " [zmq-event-emu-clnt]: " << " Label: " << s << endl;
                lbl_stk.push(s);
            } else {
                s1 = lbl_stk.top();
                if(DBG) cout << 0 << " [zmq-event-emu-clnt]: " << " Label: " << s1 << " Datum: " << s << endl;
                mymap[s1] = s;
                lbl_stk.pop();
            }
            break;
        default:
            if(DBG) cout << 0 << " [zmq-event-emu-clnt]: " << " (Default)" << endl;
            break;
        }

        if(event.type == YAML_STREAM_END_EVENT) break;
        yaml_event_delete(&event);
    }
    if(DBG) cout << 0 << " [zmq-event-emu-clnt]: " << " All done parsing, got this:" << endl;
    if(DBG) for (map<string,string>::iterator it=mymap.begin(); it!=mymap.end(); ++it)
        cout << it->first << " => " << it->second << endl;
    
    yaml_parser_delete(&parser);
    fclose(file);
}

void parse_yaml1(const char* filename, uint8_t vrbs = 0) {
    try {
        // ----- Open the file -----
        FILE* file = fopen(filename, "r");
        if (!file) {
            throw std::runtime_error(std::string("Failed to open file: ") + filename);
        }

        // RAII cleanup for FILE*
        auto file_closer = [&](FILE* f) { if (f) fclose(f); };
        std::unique_ptr<FILE, decltype(file_closer)> file_ptr(file, file_closer);

        // ----- Initialize parser -----
        yaml_parser_t parser;
        if (!yaml_parser_initialize(&parser)) {
            throw std::runtime_error("Failed to initialize libyaml parser");
        }

        // RAII cleanup for parser
        auto parser_deleter = [&](yaml_parser_t* p) { yaml_parser_delete(p); };
        std::unique_ptr<yaml_parser_t, decltype(parser_deleter)> parser_ptr(&parser, parser_deleter);

        yaml_parser_set_input_file(&parser, file);

        // ----- Setup label tracking -----
        std::vector<std::string> lbls = {
            "pub_port", "stream_id", "frame_sz_MB", "frame_cnt",
            "avg_bit_rt_Gbps", "verbosity"
        };

        std::stack<std::string> lbl_stack;
        yaml_event_t event;
        std::string s, s1;

        // ----- Parse loop -----
        while (true) {
            if (!yaml_parser_parse(&parser, &event)) {
                throw std::runtime_error("YAML parse error occurred");
            }

            switch (event.type) {
            case YAML_SCALAR_EVENT:
                s = reinterpret_cast<const char*>(event.data.scalar.value);

                if (std::find(lbls.begin(), lbls.end(), s) != lbls.end()) {
                    if (vrbs) std::cout << "Label: " << s << std::endl;
                    lbl_stack.push(s);
                } else {
                    if (lbl_stack.empty()) {
                        yaml_event_delete(&event);
                        throw std::runtime_error("Malformed YAML: scalar without preceding label");
                    }
                    s1 = lbl_stack.top();
                    lbl_stack.pop();
                    if (vrbs) std::cout << "Label: " << s1 << " Data: " << s << std::endl;
                    mymap[s1] = s;
                }
                break;

            case YAML_STREAM_END_EVENT:
                yaml_event_delete(&event);
                if (vrbs) std::cout << "Stream ended" << std::endl;
                return;   // clean exit, RAII will clean parser and file

            default:
                // Optional verbose printing
                break;
            }

            yaml_event_delete(&event);
        }
    }

    // ----- Catch & diagnose errors -----
    catch (const std::exception& ex) {
        std::cerr << "[parse_yaml] ERROR: " << ex.what() << std::endl;
    }
    catch (...) {
        std::cerr << "[parse_yaml] Unknown internal error" << std::endl;
    }
    if(DBG) cout << 0 << " [zmq-event-emu-clnt]: " << " All done parsing, got this:" << endl;
    if(DBG) for (map<string,string>::iterator it=mymap.begin(); it!=mymap.end(); ++it)
        cout << it->first << " => " << it->second << endl;    
}

#include <yaml.h>
#include <iostream>
#include <string>
#include <map>
#include <stdexcept>

using namespace std;

map<string, string> parse_yaml(const char *filename)
{
    map<string, string> result;
    FILE *file = fopen(filename, "r");

    if (!file)
        throw runtime_error("Could not open YAML file");

    yaml_parser_t parser;
    yaml_event_t event;

    if (!yaml_parser_initialize(&parser)) {
        fclose(file);
        throw runtime_error("Failed to initialize YAML parser");
    }

    yaml_parser_set_input_file(&parser, file);

    string pending_key;       // holds the last key seen
    bool expect_value = false;

    while (true) {
        if (!yaml_parser_parse(&parser, &event)) {
            yaml_parser_delete(&parser);
            fclose(file);
            throw runtime_error("YAML parsing error");
        }

        switch (event.type) {

        case YAML_SCALAR_EVENT: {
            string value = (const char*)event.data.scalar.value;

            if (!expect_value) {
                // SCALAR interpreted as a key
                pending_key = value;
                expect_value = true;
            } else {
                // SCALAR interpreted as a value for the pending key
                result[pending_key] = value;
                expect_value = false;
            }
            break;
        }

        case YAML_MAPPING_END_EVENT:
        case YAML_STREAM_END_EVENT:
            yaml_event_delete(&event);
            goto done;
            break;

        default:
            break;
        }

        yaml_event_delete(&event);
    }

done:
    yaml_parser_delete(&parser);
    fclose(file);

    if (expect_value) {
        throw runtime_error("Malformed YAML: key '" + pending_key +
                            "' has no value");
    }

    return result;
}

int main (int argc, char *argv[])
{
    int optc;

    bool     psdA=false, psdF=false, psdP=false, psdR=false, psdS=false;
    bool     psdV=false, psdY=false;

    string   yfn = "zmq-event-emu-clnt.yaml";

    uint16_t stream_id          = 0;    // stream or channel id
    uint16_t pub_port            = 8888; // target port
    uint64_t frame_cnt          = 1e2;  // event count
    float    frame_sz_MB        = 1;    // event size (MB)
    float    avg_bit_rt_Gbps    = 1;    // sending bit rate in Gbps
    uint8_t  vrbs               = 1; // verbosity; 0 -> nothing to stdout

    cout << std::fixed << std::setprecision(7);

    while ((optc = getopt(argc, argv, "a:f:hp:r:s:v:y:")) != -1)
    {
        switch (optc)
        {
        case 'h':
            Usage();
            exit(1);
        case 'a':
            stream_id = (uint16_t) atoi((const char *) optarg) ;
            psdA = true;
            if(DBG) cout << " -a " << stream_id;
            break;
        case 'f':
            frame_cnt = (uint16_t) atoi((const char *) optarg) ;
            psdF = true;
            if(DBG) cout << " -c " << frame_cnt;
            break;
        case 'p':
            pub_port = (uint16_t) atoi((const char *) optarg) ;
            psdP = true;
            if(DBG) cout << " -p " << pub_port;
            break;
        case 'r':
            avg_bit_rt_Gbps = (float) atof((const char *) optarg) ;
            psdR = true;
            if(DBG) cout << " -r " << avg_bit_rt_Gbps;
            break;
        case 's':
            frame_sz_MB = (float) atof((const char *) optarg) ;
            psdS = true;
            if(DBG) cout << " -s " << frame_sz_MB;
            break;
        case 'v':
            vrbs = (uint8_t) atoi((const char *) optarg) ;
            psdV = true;
            if(DBG) cout << " -v " << vrbs << endl;
            break;
        case 'y':
            yfn  = (const char *) optarg ;
            psdY = true;
            if(DBG) cout << " -y " << yfn << endl;
            break;
         case '?':
            cout << " [zmq-event-emu-clnt]: Unrecognised option: " << optopt;
            Usage();
            exit(1);
        }
    }
    
    try {
        auto config = parse_yaml(yfn.c_str());

        for (auto &kv : config) {
            cout << kv.first << " = " << kv.second << endl;
        }
/***************************
        int stream_id        = stoi(config["stream_id"]);
        double frame_sz_MB   = stod(config["frame_sz_MB"]);
        int frame_cnt        = stoi(config["frame_cnt"]);
        double avg_bit_rt    = stod(config["avg_bit_rt_Gbps"]);
        int verbosity        = stoi(config["verbosity"]);
        int pub_port         = stoi(config["pub_port"]);
*****************************/
        stream_id       = stoi(config["stream_id"]);
        frame_sz_MB     = stod(config["frame_sz_MB"]);
        frame_cnt       = stoi(config["frame_cnt"]);
        avg_bit_rt_Gbps = stod(config["avg_bit_rt_Gbps"]);
        vrbs            = stoi(config["verbosity"]);
        pub_port        = stoi(config["pub_port"]);

        cout << "\nParsed OK.\n";

    } catch (const exception &e) {
        cerr << "ERROR: " << e.what() << endl;
    }
/***********************
    if (psdY) {//parse the yaml file if given        
        parse_yaml(yfn.c_str(), vrbs);
        //if not passed as arg, retrieve from yaml map)
        if(!psdA) stream_id         = stof(mymap["stream_id"]);
        if(!psdF) frame_cnt         = stof(mymap["frame_cnt"]);
        if(!psdP) pub_port           = stof(mymap["pub_port"]);
        if(!psdR) avg_bit_rt_Gbps   = stoi(mymap["avg_bit_rt_Gbps"]);
        if(!psdS) frame_sz_MB       = stoi(mymap["frame_sz_MB"]);
        if(!psdV) vrbs              = (bool) stoi(mymap["verbosity"]) == 1;
    }
***********************/    
    if(vrbs>0) cout << 0 << " [emulate_stream:] yaml parsed" << endl;

    // RNG for latency variance generation using a Gaussian (normal) distribution to generate a scaling factor 
    // centered around 1.0, with a standard deviation chosen so that ~99.7% of values fall 
    // in the 70% to 130% range (i.e., ±3σ ≈ 30%):

    static random_device rd;
    static mt19937 gen(rd());

    // Mean = 1.0, Std Dev = 0.1 gives 99.7% of samples in [0.7, 1.3]
    static normal_distribution<> nd_10pcnt(1.0, 0.1);

    //  Prepare our publication context and socket
    if(DBG) cout << "[zmq-event-emu-clnt " << pub_port << "]" << endl;
    if(DBG) cout << "[emulate_sender-zmq " << pub_port << "]: Publishing on port " << to_string(pub_port) << endl;
    context_t pub_cntxt(1);
    socket_t pub_sckt(pub_cntxt, socket_type::pub);
    pub_sckt.bind(string("tcp://*:") + to_string(pub_port));
    pub_sckt.set(zmq::sockopt::sndhwm, int(0)); // queue length

    this_thread::sleep_for(chrono::seconds(1)); //  # Give receiver time to bind

    auto now_hrc   = high_resolution_clock::now();
    auto clk_uSd   = duration_cast<microseconds>(now_hrc.time_since_epoch());
    uint64_t start_uS  = clk_uSd.count();
    uint64_t now_uS = start_uS;
    if(DBG) cout << "[emulate_sender-zmq " << pub_port << "]: start_uS " << start_uS << endl;

    double   mnBfSz_B    = 0; //mean receive Size bytes

    //  Do frame_cnt requests
    for (uint64_t frame_num = 1; frame_num <= frame_cnt; frame_num++) {
        //cout << " [emulate_stream]: Sending  " << frame_num << "..." << endl;

        send_result_t sr;

        // Send  "frame"
        auto x = 1; // No frame size variation for DAQ system   // clamp(nd_10pcnt(gen), 0.7, 1.3);  //+/- 3 sd
        vector<uint8_t> payload(size_t(M_1*frame_sz_MB*x));
        
        if(DBG) cout << now_uS+1 << " [emulate_stream:] serializing packet for frame_num " << frame_num << endl;
        auto data = serialize_packet(now_uS, pub_port, payload.size(), now_uS, stream_id, frame_num, payload);
        if(DBG) cout << now_uS+2 << " [emulate_stream:] serializing success for frame_num " << frame_num << endl;
        zmq::message_t message(data.size());
        memcpy(message.data(), data.data(), data.size());
        sr = pub_sckt.send(message, zmq::send_flags::none);
        if (!sr) {cerr << now_uS << " [emulate_stream:] Failed to send" << endl; exit(1);}
        
        float bufSiz_B = sr.value();
        //if (vrbs && sr.has_value()) cout << now_uS << " [emulate_stream:] Sending frame size = " << bufSiz_B << " frame_num = " << frame_num << endl;

        if(vrbs>0) cout << now_uS+3 << " [emulate_stream:] Sending frame size = " << payload.size() << " (" 
                      << frame_num << ')' << " to " << pub_port << " at " << now_uS << " with code " << endl;
        if(DBG) cout << now_uS+4 << " [emulate_stream:] output Num written (" << frame_num << ") "  
                     << bufSiz_B << " (" << frame_num << ')' << endl;
        if(bufSiz_B != HEADER_SIZE + payload.size()) cout << now_uS+3 << " [emulate_stream:] data incorrect size(" << frame_num << ") "  << endl;


        if(DBG) cout << now_uS+5 << " [emulate_stream:] sent: size=" 
                  << HEADER_SIZE + payload.size() << endl;

        float rate_sleep_S = one_G*payload.size()*B_b / avg_bit_rt_Gbps;  // in seconds
        auto cms = chrono::microseconds(size_t(round(one_u*rate_sleep_S))); //reqd timespan in microseconds
        if(DBG) cout << now_uS+3 << " [emulate_stream:] Rate sleep for " << rate_sleep_S << " S"  
                      << " Payload size = " << payload.size() << " bit rate Mbps " << G_M*avg_bit_rt_Gbps << endl;

        this_thread::sleep_for(cms);

        now_hrc = high_resolution_clock::now();
        auto clk_uSd        = duration_cast<microseconds>(now_hrc.time_since_epoch());
        //if(DBG) cout << now_uS << " [emulate_stream:] Updating clock from " << now_uS << " to ";
        now_uS = clk_uSd.count();
        if(DBG) cout << now_uS << " [emulate_stream:]  " << now_uS << endl;
        
        if(DBG) cout << now_uS + 6 << " [emulate_stream:] Estimated frame rate (Hz): "
                           << float(frame_num)/(float(now_uS-start_uS)*u_1) 
                           << " frame_num " << frame_num << " elpsd_tm_uS " << now_uS-start_uS << endl;

        mnBfSz_B = (frame_num-1)*mnBfSz_B/frame_num + bufSiz_B/frame_num; //incrementally update mean receive size
        if(DBG) cout << now_uS + 7 << " [emulate_stream:] Estimated bit rate (Gbps): " 
                           << float(frame_num*mnBfSz_B*B_b*one_G)/(float(now_uS-start_uS)*u_1)
                           << " frame_num " << frame_num << " elpsd_tm_uS " << now_uS-start_uS << endl;
        if(DBG) cout << now_uS + 7 << " [emulate_stream:] Estimated bit rate (bps): " 
                           << float(frame_num*mnBfSz_B*B_b)/(float(now_uS-start_uS)*u_1)
                           << " frame_num " << frame_num << " elpsd_tm_uS " << now_uS-start_uS << endl;

    }
    return 0;
}
