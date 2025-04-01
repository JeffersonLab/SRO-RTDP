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
#include <atomic>
#include <unistd.h> // For getpid()

#include <zmq.hpp>

#include <cuda_runtime.h>
#include <curand_kernel.h>
#include <cublas_v2.h>


// ZMQ settings
constexpr const int ZMQ_IN_PORT = 55555;
constexpr const int ZMQ_OUT_PORT = 55556;

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

// CUBLAS error check
#define CUBLAS_CALL(call) \
do { \
    cublasStatus_t err = call; \
    if (err != CUBLAS_STATUS_SUCCESS) { \
        std::cerr << "CUBLAS error at " << __FILE__ << ":" << __LINE__ << std::endl; \
        exit(EXIT_FAILURE); \
    } \
} while (0)

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

// Excute on GPU
void matrixProcess(float* d_A, float* d_B, float* d_C, int rows, int in_cols, int out_cols) {
    cublasHandle_t handle;
    CUBLAS_CALL(cublasCreate(&handle));
    const float alpha = 1.0f, beta = 0.0f;
    
    // Perform matrix multiplication: C = A * B
    CUBLAS_CALL(cublasSgemm(handle, CUBLAS_OP_N, CUBLAS_OP_N, 
                            out_cols, rows, in_cols, 
                            &alpha, d_B, out_cols, 
                            d_A, in_cols, 
                            &beta, d_C, out_cols));
    
    CUBLAS_CALL(cublasDestroy(handle));
}

// Execute on CPU.
void cpuMatrixMultiply(
    const std::vector<float>& A, const std::vector<float>& B, std::vector<float>& C,
    int rows, int in_cols, int out_cols) {
        for (int i = 0; i < rows; ++i) {
            for (int j = 0; j < out_cols; ++j) {
                float sum = 0.0f;
                for (int k = 0; k < in_cols; ++k) {
                    sum += A[i * in_cols + k] * B[k * out_cols + j];
                }
                C[i * out_cols + j] = sum;
            }
        }
}
//.........................................................................


//.........................................................................
// Based on the podio2tcp application.
class CommandLineOptions {
public:
    int recv_port = ZMQ_IN_PORT;
    int send_port = ZMQ_OUT_PORT;
    double rate = MATRIX_OUT_REDUCE_RATE;
    int width = MATRIX_IN_COLUMN_WIDTH; // Default matrix column size

    std::string out_ip = "127.0.0.1";  // Default out IP is local
    bool useTensorCores = false;
    bool verbose = false;

    std::string sqliteFilename;      // SQL file parameter

    static CommandLineOptions Parse(int argc, char* argv[]) {
        CommandLineOptions options;
        for (int i = 1; i < argc; ++i) {
            std::string arg = argv[i];
            if (arg == "-h" || arg == "--help") {
                PrintUsage();
                exit(0);
            } else if (arg == "--in-port" && i + 1 < argc) {
                options.recv_port = std::stoi(argv[++i]);
            } else if (arg == "-a" || arg == "--out-ip") {
                if (i + 1 < argc) {
                    options.out_ip = argv[++i];
                }
            } else if (arg == "--out-port" && i + 1 < argc) {
                options.send_port = std::stoi(argv[++i]);
            } else if (arg == "-r" || arg == "--rate") {
                if (i + 1 < argc) {
                    options.rate = std::stod(argv[++i]);
                }
            } else if (arg == "-w" || arg == "--width") {
                if (i + 1 < argc) {
                    options.width = std::stoi(argv[++i]);
                }
            } else if (arg == "-s" || arg == "--sqlfile") {
                if (i + 1 < argc) {
                    options.sqliteFilename = argv[++i];
                }
            } else if (arg == "--tc") {
                options.useTensorCores = true;
            } else if (arg == "-v" || arg == "--verbose") {
                options.verbose = true;
            }
        }
        return options;
    }


    static void PrintUsage() {
        std::cout << "\n" 
                  << "Usage: gpu_emu [--in-port] [-a|--out-ip] [--out-port]\n"
                  << "\n"
                  << "-h, --help     Print this help statement\n"
                  << "    --in-port  <port> Set ZMQ port to listen on (default is 55555)\n"
                  << "-a, --out-ip   <IP_Address> The IP address ZMQ push to (default is localhost)\n"
                  << "    --out-port <port> Set ZMQ port to push to (default is 55556)\n"
                  << "-r, --rate     Control the ratio of output/input volume (default is 0.5)\n"
                  << "-w, --width    Set the GPU input matrix column size (default is 2048)\n"
                //   << "-s, --sqlfile  <file> Specify the SQL rate logger file\n"
                //   << "    --tc       Use GPU Tensor Cores instead of FP units\n"
                  << "-v, --verbose    Enable the verbose mode (default is false)\n"

                  << "\n"
                  << "This is a GPU Proxy\n"
                  << "It takes input from a ZMQ PULL port and builds a matrix, sends to GPU and do matrix\n"
                  << "multiplication on the GPU. After that, it copies the result back to CPU and PUSH to\n"
                  << "another ZMQ IP & port.\n"
                  << "\n"
                //   << "If --sqlfile is used, it specifies a SQLite rate logger.\n"
                  << "\n";
    }
};
//.........................................................................


//.........................................................................
// The monitoring thread
std::atomic<size_t> totalInBytes{0};
std::atomic<size_t> totalOutBytes{0};

void monitorTraffic(std::atomic<size_t>* inBytes, std::atomic<size_t>* outBytes) {
    using namespace std::chrono;
    while (true) {
        size_t prevIn = inBytes->load();
        size_t prevOut = outBytes->load();
        std::this_thread::sleep_for(seconds(2));
        size_t curIn = inBytes->load();
        size_t curOut = outBytes->load();

        double inRate_MBps = (curIn - prevIn) / (1000.0 * 1000.0) / 2.0;
        double outRate_MBps = (curOut - prevOut) / (1000.0 * 1000.0) / 2.0;

        std::cout << "[Monitor] Incoming: [" << inRate_MBps << " MB/s], "
                  << "Outgoing: [" << outRate_MBps << " MB/s]" << std::endl;
    }
}
//.........................................................................


//.........................................................................
// Main
int main(int narg, char *argv[]){

    // Parse command options (will print help and exit if help is asked for)
    CommandLineOptions options = CommandLineOptions::Parse(narg, argv);

    // Enable the verbose mode if the cmd flag is provided
    bool verbose_mode = options.verbose;

    //............................................
    // Setup network communication via zmq
    zmq::context_t context(1);

    // Receiving socket
    zmq::socket_t receiver(context, ZMQ_PULL);
    // Taken from podio2tcp: set High Water Mark for maximum number of messages to queue before stalling
    receiver.set(zmq::sockopt::rcvhwm, 10000);
    std::string recv_addr = "tcp://*:" + std::to_string(options.recv_port);
    try {
        // Listen to localhost only
        receiver.bind(recv_addr.c_str());
        std::cout << "RECV - ZeroMQ pulling from: " << recv_addr << "\n";
    }  catch (const zmq::error_t& e) {
        std::cout << "Error: Failed to bind to the receiving address [" << recv_addr << "]:" << e.what() << "\n";
        return 1;
    }

    // Sending socket
    zmq::socket_t sender(context, ZMQ_PUSH);
    // Set High Water Mark for maximum number of messages to queue before stalling
    sender.set(zmq::sockopt::sndhwm, 10000);
    std::string send_addr = "tcp://" + options.out_ip + ":" + std::to_string(options.send_port);
    try {
        sender.connect(send_addr.c_str());  // NOTE: connect() not bind()!!!!
        std::cout << "SEND - ZeroMQ pushing to: " << send_addr << "\n";
    }  catch (const zmq::error_t& e) {
        std::cout << "Error: Failed to connect to the sending address [" << send_addr << "]:" << e.what() << "\n";
        return 1;
    }
    //............................................

    std::cout << "\nWaiting for data ...\n" << std::endl;

    std::thread monitor_thread(monitorTraffic, &totalInBytes, &totalOutBytes);
    monitor_thread.detach();   // Start the rate monitoring thread

    while (true) {
        zmq::message_t recv_buffer;
        auto res = receiver.recv(recv_buffer, zmq::recv_flags::none);
        if (!res) {
            std::cerr << "Error: ZeroMQ receive failed!" << std::endl;
        }

        if (verbose_mode) {
            std::cout << "Received [" << res.value() << "] bytes from ZeroMQ socket." << std::endl;
        }
        
        size_t curr_inBytes = recv_buffer.size();
        totalInBytes += curr_inBytes;
        if( curr_inBytes == 0 ) { 
            if (verbose_mode) {
                std::cout << "  (skipping empty buffer)" << std::endl;
            }
            continue;
        }

        float *d_in, *d_rand, *d_out;
        // Setup the input matrix A [rows * in_columns (default as 2048)] on the CPU side
        int totalElements = curr_inBytes / sizeof(float);
        int rows = (totalElements + options.width - 1) / options.width;
        int in_cols = options.width;

        std::vector<float> h_in(rows * in_cols, 0);
        memcpy(h_in.data(), recv_buffer.data(), curr_inBytes);

        if (verbose_mode) {
            std::cout << "First 10 elements of h_in:" << std::endl;
            for (size_t i = 0; i < std::min(h_in.size(), static_cast<size_t>(10)); ++i) {
                std::cout << h_in[i] << " ";
            }
            std::cout << std::endl << std::endl;
        }

        // Copy input matrix to GPU
        CUDA_CALL(cudaMalloc(&d_in, rows * in_cols * sizeof(float)));
        if (verbose_mode) {
            std::cout << "\t Input matrix dimension, (#columns)x(#rows): " << in_cols << "x" << rows << std::endl;
        }
        CUDA_CALL(cudaMemcpy(d_in, h_in.data(), rows * in_cols * sizeof(float), cudaMemcpyHostToDevice));

        // Set the random matrix d_rand on the GPU. d_rand has @var options.width rows.
        int out_cols = std::ceil(options.width * options.rate);
        int rand_elements = in_cols * out_cols;
        CUDA_CALL(cudaMalloc(&d_rand, rand_elements * sizeof(float)));

        int threadsPerBlock = 256;
        int numBlocks = (rand_elements + threadsPerBlock - 1) / threadsPerBlock;
        if (verbose_mode) {
            std::cout << "\t Random matrix dimension, (#columns)x(#rows): " << out_cols << "x" << options.width << std::endl;
        }
        generateRandomMatrix<<<numBlocks, threadsPerBlock>>>(d_rand, options.width, out_cols, time(NULL));
        CUDA_CALL(cudaDeviceSynchronize());

        // Process matrix multiplication
        CUDA_CALL(cudaMalloc(&d_out, rows * out_cols * sizeof(float)));
        matrixProcess(d_in, d_rand, d_out, rows, in_cols, out_cols);

        CUDA_CALL(cudaDeviceSynchronize());

        // Copy the result matrix back to host
        std::vector<float> h_out(rows * out_cols, 0);
        CUDA_CALL(cudaMemcpy(h_out.data(), d_out, rows * out_cols * sizeof(float), cudaMemcpyDeviceToHost));

        if (verbose_mode) {
            std::vector<float> h_rand(rand_elements, 0);
            CUDA_CALL(cudaMemcpy(h_rand.data(), d_rand, rand_elements * sizeof(float), cudaMemcpyDeviceToHost));
    
            std::cout << "First 10 elements of h_out:" << std::endl;
            for (size_t i = 0; i < std::min(h_out.size(), static_cast<size_t>(10)); ++i) {
                std::cout << h_out[i] << " ";
            }
            std::cout << std::endl << std::endl;

            std::vector<float> h_out_ref(rows * out_cols, 0);
            cpuMatrixMultiply(h_in, h_rand, h_out_ref, rows, in_cols, out_cols);
            std::cout << "\nFirst 10 elements of CPU computed matrix multiplication result:" << std::endl;
            for (size_t i = 0; i < std::min(h_out_ref.size(), static_cast<size_t>(10)); ++i) {
                std::cout << h_out_ref[i] << " ";
            }
            std::cout << std::endl << std::endl;
        }

        zmq::message_t message(h_out.data(), h_out.size() * sizeof(float));   // remember to * sizeof(float)!!!
        if (verbose_mode) {
            std::cout <<"\t Output matrix dimension, (#columns)x(#rows): " << out_cols << "x" << rows << std::endl;
        }

        res = sender.send(message, zmq::send_flags::dontwait);   // zmq::send_flags::dontwait is non-blocking mode
        if (!res) {
            std::cerr << "Error: ZeroMQ send failed!" << std::endl;
        }
        totalOutBytes += res.value();
        if (verbose_mode) {
            std::cout << "Sent [" << res.value() << "] bytes via ZeroMQ socket.\n" << std::endl;
        }

        CUDA_CALL(cudaFree(d_in));
        CUDA_CALL(cudaFree(d_rand));
        CUDA_CALL(cudaFree(d_out));

    }

    return 0;
}
