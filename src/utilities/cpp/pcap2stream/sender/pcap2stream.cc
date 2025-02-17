#include <pcap.h>
#include <iostream>
#include <string>
#include <unordered_map>
#include <vector>
#include <filesystem>
#include <fstream>
#include <netinet/ip.h>
#include <netinet/tcp.h>
#include <netinet/if_ether.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <thread>
#include <mutex>
#include <queue>
#include <condition_variable>
#include <atomic>

// Structure to hold packet data and metadata
struct PacketData {
    std::vector<uint8_t> payload;
    uint32_t ts_sec;
    uint32_t ts_usec;
};

// Thread-safe queue for packet data
class PacketQueue {
private:
    std::queue<PacketData> queue;
    std::mutex mutex;
    std::condition_variable cond;
    bool closed = false;

public:
    void push(PacketData data) {
        std::unique_lock<std::mutex> lock(mutex);
        queue.push(std::move(data));
        cond.notify_one();
    }

    bool pop(PacketData& data) {
        std::unique_lock<std::mutex> lock(mutex);
        cond.wait(lock, [this] { return !queue.empty() || closed; });
        if (closed && queue.empty()) return false;
        data = std::move(queue.front());
        queue.pop();
        return true;
    }

    void close() {
        std::unique_lock<std::mutex> lock(mutex);
        closed = true;
        cond.notify_all();
    }
};

// Class to manage TCP connection and streaming for a source IP
class StreamClient {
private:
    std::string source_ip;
    std::string server_ip;
    int server_port;
    int sock_fd;
    PacketQueue packet_queue;
    std::thread worker_thread;
    std::atomic<bool> running{true};

    void worker_function() {
        PacketData packet;
        while (running && packet_queue.pop(packet)) {
            if (send(sock_fd, packet.payload.data(), packet.payload.size(), 0) < 0) {
                std::cerr << "Failed to send data for IP " << source_ip << std::endl;
                break;
            }
        }
    }

public:
    StreamClient(const std::string& src_ip, const std::string& srv_ip, int port)
        : source_ip(src_ip), server_ip(srv_ip), server_port(port), sock_fd(-1) {
    }

    bool connect() {
        sock_fd = socket(AF_INET, SOCK_STREAM, 0);
        if (sock_fd < 0) {
            std::cerr << "Failed to create socket for " << source_ip << std::endl;
            return false;
        }

        struct sockaddr_in server_addr;
        server_addr.sin_family = AF_INET;
        server_addr.sin_port = htons(server_port);
        if (inet_pton(AF_INET, server_ip.c_str(), &server_addr.sin_addr) <= 0) {
            std::cerr << "Invalid server IP address: " << server_ip << std::endl;
            close(sock_fd);
            return false;
        }

        if (::connect(sock_fd, (struct sockaddr*)&server_addr, sizeof(server_addr)) < 0) {
            std::cerr << "Connection failed for " << source_ip << std::endl;
            close(sock_fd);
            return false;
        }

        worker_thread = std::thread(&StreamClient::worker_function, this);
        return true;
    }

    void queue_packet(const PacketData& packet) {
        packet_queue.push(packet);
    }

    void stop() {
        running = false;
        packet_queue.close();
        if (worker_thread.joinable()) {
            worker_thread.join();
        }
        if (sock_fd >= 0) {
            close(sock_fd);
        }
    }

    ~StreamClient() {
        stop();
    }
};

class PcapProcessor {
private:
    std::unordered_map<std::string, std::unique_ptr<StreamClient>> clients;
    std::string server_ip;
    int base_port;

public:
    PcapProcessor(const std::string& srv_ip, int port) 
        : server_ip(srv_ip), base_port(port) {}

    void process_pcap(const std::string& filename) {
        char errbuf[PCAP_ERRBUF_SIZE];
        pcap_t* handle = pcap_open_offline(filename.c_str(), errbuf);
        if (!handle) {
            std::cerr << "Could not open file: " << filename << " - " << errbuf << std::endl;
            return;
        }

        struct pcap_pkthdr* header;
        const u_char* packet;
        int res;

        while ((res = pcap_next_ex(handle, &header, &packet)) >= 0) {
            if (res == 0) continue;

            struct ethhdr* eth = (struct ethhdr*)packet;
            if (ntohs(eth->h_proto) != ETH_P_IP) continue;

            struct iphdr* ip = (struct iphdr*)(packet + sizeof(struct ethhdr));
            if (ip->protocol != IPPROTO_TCP) continue;

            struct tcphdr* tcp = (struct tcphdr*)(packet + sizeof(struct ethhdr) + ip->ihl * 4);
            
            // Get source IP address
            char src_ip[INET_ADDRSTRLEN];
            inet_ntop(AF_INET, &(ip->saddr), src_ip, INET_ADDRSTRLEN);
            std::string source_ip(src_ip);

            // Calculate payload size and offset
            int payload_offset = sizeof(struct ethhdr) + ip->ihl * 4 + tcp->doff * 4;
            int payload_size = ntohs(ip->tot_len) - (ip->ihl * 4 + tcp->doff * 4);
            
            if (payload_size <= 0) continue;

            // Create new client if this is a new source IP
            if (clients.find(source_ip) == clients.end()) {
                int client_port = base_port + clients.size();
                auto client = std::make_unique<StreamClient>(source_ip, server_ip, client_port);
                if (client->connect()) {
                    std::cout << "New connection established for " << source_ip 
                              << " to " << server_ip << ":" << client_port << std::endl;
                    clients[source_ip] = std::move(client);
                }
            }

            // Queue the packet for transmission
            if (clients.find(source_ip) != clients.end()) {
                PacketData pdata;
                pdata.payload.assign(packet + payload_offset, packet + payload_offset + payload_size);
                pdata.ts_sec = header->ts.tv_sec;
                pdata.ts_usec = header->ts.tv_usec;
                clients[source_ip]->queue_packet(pdata);
            }
        }

        if (res == -1) {
            std::cerr << "Error reading packets: " << pcap_geterr(handle) << std::endl;
        }

        pcap_close(handle);
        
        // Clean up clients
        for (auto& client : clients) {
            client.second->stop();
        }
        clients.clear();
    }
};

int main(int argc, char* argv[]) {
    if (argc != 4) {
        std::cerr << "Usage: " << argv[0] << " <pcap_file> <server_ip> <base_port>" << std::endl;
        return 1;
    }

    std::string filename = argv[1];
    std::string server_ip = argv[2];
    int base_port = std::stoi(argv[3]);

    PcapProcessor processor(server_ip, base_port);
    processor.process_pcap(filename);

    return 0;
} 