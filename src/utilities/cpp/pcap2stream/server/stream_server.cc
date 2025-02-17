#include <iostream>
#include <string>
#include <vector>
#include <thread>
#include <mutex>
#include <map>
#include <filesystem>
#include <fstream>
#include <atomic>
#include <chrono>
#include <ctime>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <signal.h>

class StreamServer {
private:
    std::string ip_address;
    int base_port;
    int num_ports;
    std::vector<std::thread> listener_threads;
    std::map<int, std::ofstream> output_files;
    std::mutex files_mutex;
    std::atomic<bool> running{true};

    std::string get_timestamp() {
        auto now = std::chrono::system_clock::now();
        auto now_time = std::chrono::system_clock::to_time_t(now);
        char buffer[80];
        std::strftime(buffer, sizeof(buffer), "%Y%m%d_%H%M%S", std::localtime(&now_time));
        return std::string(buffer);
    }

    void handle_client(int client_socket, int port) {
        std::string filename;
        {
            std::lock_guard<std::mutex> lock(files_mutex);
            if (output_files.find(port) == output_files.end()) {
                std::filesystem::create_directories("output");
                filename = "output/stream_" + std::to_string(port) + "_" + get_timestamp() + ".bin";
                output_files[port].open(filename, std::ios::binary);
                std::cout << "Created output file: " << filename << std::endl;
            }
        }

        const size_t buffer_size = 8192;
        std::vector<char> buffer(buffer_size);
        ssize_t bytes_received;

        while (running && (bytes_received = recv(client_socket, buffer.data(), buffer_size, 0)) > 0) {
            std::lock_guard<std::mutex> lock(files_mutex);
            output_files[port].write(buffer.data(), bytes_received);
            output_files[port].flush();
        }

        close(client_socket);
        std::cout << "Client disconnected from port " << port << std::endl;
    }

    void port_listener(int port) {
        int server_socket = socket(AF_INET, SOCK_STREAM, 0);
        if (server_socket < 0) {
            std::cerr << "Failed to create socket for port " << port << std::endl;
            return;
        }

        int opt = 1;
        if (setsockopt(server_socket, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt))) {
            std::cerr << "Failed to set socket options for port " << port << std::endl;
            close(server_socket);
            return;
        }

        struct sockaddr_in server_addr;
        server_addr.sin_family = AF_INET;
        server_addr.sin_port = htons(port);
        inet_pton(AF_INET, ip_address.c_str(), &server_addr.sin_addr);

        if (bind(server_socket, (struct sockaddr*)&server_addr, sizeof(server_addr)) < 0) {
            std::cerr << "Failed to bind to port " << port << std::endl;
            close(server_socket);
            return;
        }

        if (listen(server_socket, 5) < 0) {
            std::cerr << "Failed to listen on port " << port << std::endl;
            close(server_socket);
            return;
        }

        std::cout << "Listening on " << ip_address << ":" << port << std::endl;

        while (running) {
            struct sockaddr_in client_addr;
            socklen_t client_len = sizeof(client_addr);
            int client_socket = accept(server_socket, (struct sockaddr*)&client_addr, &client_len);

            if (!running) break;

            if (client_socket < 0) {
                std::cerr << "Failed to accept connection on port " << port << std::endl;
                continue;
            }

            char client_ip[INET_ADDRSTRLEN];
            inet_ntop(AF_INET, &client_addr.sin_addr, client_ip, INET_ADDRSTRLEN);
            std::cout << "New connection from " << client_ip << " on port " << port << std::endl;

            std::thread client_thread(&StreamServer::handle_client, this, client_socket, port);
            client_thread.detach();
        }

        close(server_socket);
    }

public:
    StreamServer(const std::string& ip, int base_port_num, int num_ports_to_use)
        : ip_address(ip), base_port(base_port_num), num_ports(num_ports_to_use) {}

    void start() {
        std::cout << "Starting server with " << num_ports << " ports starting from " << base_port << std::endl;

        for (int i = 0; i < num_ports; ++i) {
            int port = base_port + i;
            listener_threads.emplace_back(&StreamServer::port_listener, this, port);
        }
    }

    void stop() {
        running = false;

        // Close all listener threads
        for (auto& thread : listener_threads) {
            if (thread.joinable()) {
                thread.join();
            }
        }

        // Close all output files
        std::lock_guard<std::mutex> lock(files_mutex);
        for (auto& [port, file] : output_files) {
            if (file.is_open()) {
                file.close();
            }
        }
        output_files.clear();
    }

    ~StreamServer() {
        stop();
    }
};

std::atomic<bool> g_running{true};

void signal_handler(int signum) {
    g_running = false;
}

int main(int argc, char* argv[]) {
    if (argc != 4) {
        std::cerr << "Usage: " << argv[0] << " <ip_address> <base_port> <num_ports>" << std::endl;
        return 1;
    }

    std::string ip_address = argv[1];
    int base_port = std::stoi(argv[2]);
    int num_ports = std::stoi(argv[3]);

    // Set up signal handling
    signal(SIGINT, signal_handler);
    signal(SIGTERM, signal_handler);

    StreamServer server(ip_address, base_port, num_ports);
    server.start();

    std::cout << "Server running. Press Ctrl+C to stop." << std::endl;

    while (g_running) {
        std::this_thread::sleep_for(std::chrono::seconds(1));
    }

    std::cout << "\nShutting down server..." << std::endl;
    server.stop();
    std::cout << "Server stopped." << std::endl;

    return 0;
}