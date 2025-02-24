//---------------------------------------------------------------------------
// This program will:
//  1. Listen to the imcoming traffic from a TCP port via ZMQ.
//  2. Copy the incoming data to GPU and do some fake calculation on GPU.
//  3. Copy the results from GPU to CPU and send them out via ZMQ.
//
//---------------------------------------------------------------------------

#include <iostream>
#include <thread>
#include <string>
#include <chrono>
#include <vector>
#include <cmath>
#include <unistd.h> // For getpid()

#include <zmq.hpp>

#include <cuda_runtime.h>
#include <curand_kernel.h>
#include <cublas_v2.h>


// ZMQ settings
constexpr const int ZMQ_IN_PORT = 55555;
constexpr const int ZMQ_OUT_PORT = 55556;
constexpr const char* ZMQ_RECV_ADDR = "tcp://*:55555";
constexpr const char* ZMQ_SEND_ADDR = "tcp://*:55556";

/// TODO: @xmei, Design the SQL Rate Logger
// #include "SQLiteRateLogger.h"  // Check Podio2tcp

// Global SQlite Rate logger
/// NOTE: data schema for the receiver DB
// sqlite> .schema rate_logs
// CREATE TABLE rate_logs (
//     id INTEGER PRIMARY KEY AUTOINCREMENT,
//     timestamp_utc_ms INTEGER,
//     pid STRING,
//     rateHz_recv_period REAL,
//     rateMbps_recv_period REAL
// );
// SQLiteRateLogger rate_logger;
// std::string RATE_DB_COLUMNS = "timestamp_utc_ms, pid, "
//                             "rateHz_recv_period, "
//                             "rateMbps_recv_period";


//.........................................................................
// Matrix multiplication setup

constexpr int MATRIX_IN_COLUMN_WIDTH = 2048;
constexpr float MATRIX_OUT_REDUCE_RATE = 0.5; // Define reduction rate

// CUDA error check
#define CUDA_CALL(x) do { if((x) != cudaSuccess) { \
    std::cerr << "CUDA error at " << __FILE__ << ":" << __LINE__ << " - " << cudaGetErrorString(x) << std::endl; \
    exit(EXIT_FAILURE); }} while(0)

// GPU kernel function
__global__ void generateRandomMatrix(float* d_rand, int rows, int cols, unsigned long seed) {
    int idx = blockIdx.x * blockDim.x + threadIdx.x;
    int totalElements = rows * cols;
    if (idx < totalElements) {
        curandState state;
        curand_init(seed, idx, 0, &state);
        d_rand[idx] = curand_uniform(&state);
    }
}

void matrixProcess(float* d_A, float* d_B, int rows, int cols) {
    cublasHandle_t handle;
    cublasCreate(&handle);
    const float alpha = 1.0f, beta = 0.0f;
    int reducedCols = cols * MATRIX_OUT_REDUCE_RATE;
    /// TODO: add comments for cublasSgemm and make reduceCols from input param
    cublasSgemm(handle, CUBLAS_OP_N, CUBLAS_OP_N, reducedCols, rows, cols, &alpha, d_A, cols, d_B, cols, &beta, d_A, reducedCols);
    cublasDestroy(handle);
}

void processMatrixComputation(float* d_A, float* d_B, int rows, int cols) {
    /// TODO: check the option for selecting GPU TC vs FP
    matrixProcess(d_A, d_B, rows, cols);
}
//.........................................................................


//.........................................................................
// Based on the podio2tcp application.
class CommandLineOptions {
public:
    std::string inputFilename;
    std::string outfile;
    int recv_port = ZMQ_IN_PORT;
    int send_port = ZMQ_OUT_PORT;
    double rate = 0.0; // Unset
    std::string sqliteFilename;      // SQL file parameter
    std::string ipAddress = "localhost";

    static CommandLineOptions Parse(int argc, char* argv[]) {
        CommandLineOptions options;
        /// TODO: add a mode for using FP or TC on GPUs
        for (int i = 1; i < argc; ++i) {
            std::string arg = argv[i];
            if (arg == "-h" || arg == "--help") {
                PrintUsage();
                exit(0);
            } else if (arg == "--in-port") {
                if (i + 1 < argc) {
                    options.recv_port = std::stoi(argv[++i]);
                }
            } else if (arg == "--out-port") {
                if (i + 1 < argc) {
                    options.send_port = std::stoi(argv[++i]);
                }
            } else if (arg == "-s" || arg == "--sqlfile") {
                if (i + 1 < argc) {
                    options.sqliteFilename = argv[++i];
                }
            }
        }

        return options;
    }

    static void PrintUsage() {
        std::cout << "\n" 
                  << "Usage: gpu_emu [--in-port port] [--out-port port]\n"
                  << "\n"
                  << "-h, --help   Print this help statement\n"
                  << "    --in-port  <port> Set ZMQ port to listen on (default is 55555)\n"
                  << "    --out-port <port> Set ZMQ port to push to (default is 55556)\n"
                  << "-s, --sqlfile <file> Specify the SQL rate logger file\n"
                  << "\n"
                  << "This is xxxx\n."
                  << "xxx\n"
                  << "xxx."
                  << "\n"
                  << "If --sqlfile is used, it specifies the SQLite database output.\n"
                  << "\n";
    }
};
//.........................................................................


//.........................................................................
// The monitoring thread
void monitorTraffic(size_t* inBytes, size_t* outBytes) {
    using namespace std::chrono;
    while (true) {
        size_t prevIn = *inBytes, prevOut = *outBytes;
        std::this_thread::sleep_for(seconds(1));
        size_t curIn = *inBytes, curOut = *outBytes;
        std::cout << "Incoming: " << ((curIn - prevIn) * 8.0 / 1e6) << " Mbps, "
                  << "Outgoing: " << ((curOut - prevOut) * 8.0 / 1e6) << " Mbps" << std::endl;
    }
}
//.........................................................................


//.........................................................................
// Main
int main(int narg, char *argv[]){

    // Parse command options (will print help and exit if help is asked for)
    CommandLineOptions options = CommandLineOptions::Parse(narg, argv);

    //............................................
    // Setup network communication via zmq
    zmq::context_t context(1);

    // Receiving socket
    zmq::socket_t receiver(context, ZMQ_PULL);
    // Taken from podio2tcp: set High Water Mark for maximum number of messages to queue before stalling
    receiver.set(zmq::sockopt::rcvhwm, 10);
    try {
        receiver.bind(ZMQ_RECV_ADDR);
        std::cout << "ZeroMQ listening at: " << ZMQ_RECV_ADDR << "\n";
    }  catch (const zmq::error_t& e) {
        std::cout << "Error: Failed to bind to the listening address [" << ZMQ_RECV_ADDR << "]:" << e.what() << "\n";
        return 1;
    }

    // Sending socket
    zmq::socket_t sender(context, ZMQ_PUSH);
    sender.set(zmq::sockopt::rcvhwm, 10);
    try {
        sender.bind(ZMQ_SEND_ADDR);
        std::cout << "ZeroMQ listening at: " << ZMQ_SEND_ADDR << "\n";
    }  catch (const zmq::error_t& e) {
        std::cout << "Error: Failed to bind to the sending address [" << ZMQ_SEND_ADDR << "]:" << e.what() << "\n";
        return 1;
    }
    //............................................

    //............................................
    // Monitoring thread
    // size_t inBytes = 0, outBytes = 0;
    // std::thread monitor(monitorTraffic, &inBytes, &outBytes);
    // monitor.detach();
    //............................................

    //............................................
    // SQL logger setup
    // if (!options.sqliteFilename.empty() && !rate_logger.openDB(options.sqliteFilename)) {
    //     std::cerr << "Failed to open database: " << options.sqliteFilename << std::endl;
    //     return 1;
    // }
    //............................................


    std::cout << "\nWaiting for data ..." << std::endl;


    auto last_time = std::chrono::high_resolution_clock::now();
    while (true) {
        zmq::message_t recv_buffer;
        auto res = receiver.recv(recv_buffer, zmq::recv_flags::none);
        if (!res) {
            std::cerr << "Error: ZeroMQ receive failed!" << std::endl;
        } else {
            std::cout << "Received [" << res.value() << "] bytes from ZeroMQ socket." << std::endl;
        }
        
        size_t curr_inBytes = recv_buffer.size();
        if( curr_inBytes == 0 ) { 
            std::cout << "  (skipping empty buffer)" << std::endl;
            continue;
        }

        float *d_in, *d_rand;
        // Setup the input matrix A [rows * in_columns (default as 2048)] on the CPU side
        int totalElements = curr_inBytes / sizeof(float);
        int rows = (totalElements + MATRIX_IN_COLUMN_WIDTH - 1) / MATRIX_IN_COLUMN_WIDTH;
        int in_cols = MATRIX_IN_COLUMN_WIDTH;

        std::vector<float> h_in(rows * in_cols, 0);
        memcpy(h_in.data(), recv_buffer.data(), curr_inBytes);

        // Copy input matrix to GPU
        CUDA_CALL(cudaMalloc(&d_in, rows * in_cols * sizeof(float)));
        CUDA_CALL(cudaMemcpy(d_in, h_in.data(), rows * in_cols * sizeof(float), cudaMemcpyHostToDevice));

        // Set the random matrix d_rand on the GPU. d_rand has @var MATRIX_IN_COLUMN_WIDTH rows.
        int out_cols = std::ceil(MATRIX_IN_COLUMN_WIDTH * MATRIX_OUT_REDUCE_RATE);
        int rand_elements = MATRIX_IN_COLUMN_WIDTH * out_cols;
        CUDA_CALL(cudaMalloc(&d_rand, rand_elements * sizeof(float)));

        int threadsPerBlock = 256;
        int numBlocks = (rand_elements + threadsPerBlock - 1) / threadsPerBlock;
        generateRandomMatrix<<<numBlocks, threadsPerBlock>>>(d_rand, MATRIX_IN_COLUMN_WIDTH, out_cols, time(NULL));
        CUDA_CALL(cudaDeviceSynchronize());

        // Do the matrixMul with cublas function call
        processMatrixComputation(d_in, d_rand, rows, in_cols);

        // Copy the result matrix back to host
        std::vector<float> h_out(rows * out_cols);
        CUDA_CALL(cudaMemcpy(h_out.data(), d_in, rows * out_cols * sizeof(float), cudaMemcpyDeviceToHost));

        CUDA_CALL(cudaFree(d_in));
        CUDA_CALL(cudaFree(d_rand));

        zmq::message_t reply(rows * out_cols * sizeof(float));
        memcpy(reply.data(), h_out.data(), rows * out_cols * sizeof(float));
        res = sender.send(reply, zmq::send_flags::none);
        if (!res) {
            std::cerr << "Error: ZeroMQ send failed!" << std::endl;
        } else {
            std::cout << "Sent [" << res.value() << "] bytes via ZeroMQ socket." << std::endl;
        }
        // outBytes += reply.size();

        // Print statistic
        /// TODO: move to a monitoring thread instead.
        auto now = std::chrono::high_resolution_clock::now();
        // auto duration = std::chrono::duration<double>(now - last_time).count();
        // auto rateMbps = curr_inBytes / duration * 8.0 / 1.0E6;
        // auto savePrecision = std::cout.precision();
        // std::cout << "  INCOMING data rate: " << std::fixed << std::setprecision(3)  << rateMbps << " (Mbps)" << std::endl;
        // std::cout.precision(savePrecision);

        // Log to SQLite DB
        // auto utc_timestamp_in_ms =
        //     std::chrono::duration_cast<std::chrono::milliseconds>(now.time_since_epoch()).count();
        // std::string pid_str = std::to_string(getpid());
        // std::ostringstream values;
        // values << std::to_string(utc_timestamp_in_ms) << ", "
        //     << pid_str << ", "
        //     << std::fixed << std::setprecision(3)  // Ensure consistent floating-point precision
        //     << rateHz << ", "
        //     << rateMbps;
        // if (!rate_logger.insertRateLog(RATE_DB_COLUMNS, values.str())) {
        //     std::cerr << "Failed to insert record into the database." << std::endl;
        // }

        last_time = now;

        // std::this_thread::sleep_for(std::chrono::seconds(1));
    }
    // Close SQLite3 DB
    // rate_logger.closeDB();

    return 0;
}