#include <iostream>
#include <string>
#include <vector>
#include <thread>
#include <mutex>
#include <map>
#include <set>
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
#include <fcntl.h>
#include <errno.h>

class StreamServer {
private:
    std::string ip_address;
    int base_port;
    int num_ports;
    std::vector<std::thread> listener_threads;
    std::vector<int> server_sockets;
    std::map<int, std::ofstream> output_files;
    std::set<std::thread::id> active_client_threads;
    std::mutex files_mutex;
    std::mutex clients_mutex;
    std::atomic<bool> running{true};

    std::string get_timestamp() {
        auto now = std::chrono::system_clock::now();
        auto now_time = std::chrono::system_clock::to_time_t(now);
        char buffer[80];
        std::strftime(buffer, sizeof(buffer), "%Y%m%d_%H%M%S", std::localtime(&now_time));
        return std::string(buffer);
    }

    void handle_client(int client_socket, int port) {
        // Register client thread
        {
            std::lock_guard<std::mutex> lock(clients_mutex);
            active_client_threads.insert(std::this_thread::get_id());
        }

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

        // Set socket timeout to allow checking running flag
        struct timeval tv;
        tv.tv_sec = 1;
        tv.tv_usec = 0;
        setsockopt(client_socket, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));

        while (running) {
            bytes_received = recv(client_socket, buffer.data(), buffer_size, 0);
            if (bytes_received > 0) {
                std::lock_guard<std::mutex> lock(files_mutex);
                output_files[port].write(buffer.data(), bytes_received);
                output_files[port].flush();
            } else if (bytes_received == -1) {
                if (errno == EAGAIN || errno == EWOULDBLOCK) {
                    // Timeout occurred, continue to check running flag
                    continue;
                }
                // Other error occurred
                break;
            } else {
                // Connection closed by client
                break;
            }
        }

        close(client_socket);
        std::cout << "Client disconnected from port " << port << std::endl;

        // Unregister client thread
        {
            std::lock_guard<std::mutex> lock(clients_mutex);
            active_client_threads.erase(std::this_thread::get_id());
        }
    }

    void port_listener(int port) {
        int server_socket = socket(AF_INET, SOCK_STREAM, 0);
        if (server_socket < 0) {
            std::cerr << "Failed to create socket for port " << port << std::endl;
            return;
        }

        // Store server socket for cleanup
        server_sockets.push_back(server_socket);

        int opt = 1;
        if (setsockopt(server_socket, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt))) {
            std::cerr << "Failed to set socket options for port " << port << std::endl;
            close(server_socket);
            return;
        }

        // Set non-blocking mode
        int flags = fcntl(server_socket, F_GETFL, 0);
        fcntl(server_socket, F_SETFL, flags | O_NONBLOCK);

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
                if (errno == EAGAIN || errno == EWOULDBLOCK) {
                    // No pending connections, sleep briefly and continue
                    std::this_thread::sleep_for(std::chrono::milliseconds(100));
                    continue;
                }
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

        // Close all server sockets to unblock accept()
        for (int sock : server_sockets) {
            shutdown(sock, SHUT_RDWR);
            close(sock);
        }
        server_sockets.clear();

        // Wait for listener threads to finish
        for (auto& thread : listener_threads) {
            if (thread.joinable()) {
                thread.join();
            }
        }
        listener_threads.clear();

        // Wait for client threads to finish (with timeout)
        auto start = std::chrono::steady_clock::now();
        while (true) {
            {
                std::lock_guard<std::mutex> lock(clients_mutex);
                if (active_client_threads.empty()) {
                    break;
                }
            }

            auto now = std::chrono::steady_clock::now();
            if (std::chrono::duration_cast<std::chrono::seconds>(now - start).count() >= 5) {
                std::cout << "Timeout waiting for client threads to finish" << std::endl;
                break;
            }

            std::this_thread::sleep_for(std::chrono::milliseconds(100));
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

    void set_running(bool value) {
        running = value;
    }
};

StreamServer* g_server = nullptr;

void signal_handler(int signum) {
    std::cout << "\nReceived signal " << signum << ", initiating shutdown..." << std::endl;
    if (g_server) {
        g_server->set_running(false);
    }
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
    g_server = &server;
    server.start();

    std::cout << "Server running. Press Ctrl+C to stop." << std::endl;

    // Wait for shutdown signal
    while (g_server->running) {
        std::this_thread::sleep_for(std::chrono::seconds(1));
    }

    std::cout << "Shutting down server..." << std::endl;
    server.stop();
    std::cout << "Server stopped." << std::endl;

    return 0;
}