//
// This program will take a .pcap file as input and split it into
// individual files corresponding to the destination port of the
// packet. A directory is automatically created to hold the split
// files and those are automatically created as new ports are
// encountered in the file.
//
//
// g++ -g -std=c++20 -o pcapSplit pcapSplit.cc -lpcap
//
//
// This was mostly written by Grimoire on ChatGPT

#include <pcap.h>
#include <iostream>
#include <string>
#include <unordered_map>
#include <filesystem>
#include <fstream>
#include <netinet/ip.h>
#include <netinet/tcp.h>
#include <netinet/if_ether.h>
#include <sys/stat.h>
#include <chrono>

void write_packet_to_file(std::unordered_map<uint16_t, std::ofstream>& port_files, const std::string& base_filename, const struct pcap_pkthdr* header, const u_char* packet, uint16_t port);

void show_progress(size_t current, size_t total) {
    float progress = (float)current / total * 100;
    std::cout << "\rProgress: " << progress << "%        " << std::flush;
}

void process_pcap(const std::string& filename) {
    char errbuf[PCAP_ERRBUF_SIZE];
    pcap_t* handle = pcap_open_offline(filename.c_str(), errbuf);
    if (!handle) {
        std::cerr << "Could not open file: " << filename << " - " << errbuf << std::endl;
        return;
    }
    std::cout << "opened file: " << filename << " for reading" << std::endl;

    // Get the total size of the file
    struct stat st;
    if (stat(filename.c_str(), &st) != 0) {
        std::cerr << "Could not get file size: " << filename << std::endl;
        return;
    }
    size_t total_size = st.st_size;
    size_t processed_size = 0;

    struct pcap_pkthdr* header;
    const u_char* packet;
    int res;
    std::unordered_map<uint16_t, std::ofstream> port_files;
    std::string directory = filename + "_split";
    
    if (!std::filesystem::exists(directory)) {
        std::cout << "creating directory: " << directory << std::endl;
        std::filesystem::create_directory(directory);
    }

    auto last_update = std::chrono::steady_clock::now();

    while ((res = pcap_next_ex(handle, &header, &packet)) >= 0) {
        if (res == 0) continue;

        processed_size += header->caplen;

        struct ethhdr* eth = (struct ethhdr*) packet;
        if (ntohs(eth->h_proto) == ETH_P_IP) {
            struct iphdr* ip = (struct iphdr*) (packet + sizeof(struct ethhdr));
            if (ip->protocol == IPPROTO_TCP) {
                struct tcphdr* tcp = (struct tcphdr*) (packet + sizeof(struct ethhdr) + ip->ihl * 4);
                uint16_t dest_port = ntohs(tcp->dest);
                write_packet_to_file(port_files, directory, header, packet, dest_port);
            }
        }

        auto now = std::chrono::steady_clock::now();
        std::chrono::duration<double> elapsed_seconds = now - last_update;
        if (elapsed_seconds.count() >= 1.0) {
            show_progress(processed_size, total_size);
            last_update = now;
        }
    }

    if (res == -1) {
        std::cerr << "Error reading the packets: " << pcap_geterr(handle) << std::endl;
    }

    pcap_close(handle);

    // Close all the files
    for (auto& file_pair : port_files) {
        file_pair.second.close();
    }

    // Ensure final progress is 100%
    show_progress(total_size, total_size);
    std::cout << std::endl;
}

void write_packet_to_file(std::unordered_map<uint16_t, std::ofstream>& port_files, const std::string& directory, const struct pcap_pkthdr* header, const u_char* packet, uint16_t port) {
    if (port_files.find(port) == port_files.end()) {
        std::string file_path = directory + "/" + "port" + std::to_string(port) + ".pcap";
        port_files[port].open(file_path, std::ios::binary | std::ios::app);
        if (!port_files[port].is_open()) {
            std::cerr << "Could not open file: " << file_path << std::endl;
            return;
        }
        std::cout << "opened file: " << file_path << " for writing" << std::endl;
    }

    std::ofstream& file = port_files[port];
    file.write(reinterpret_cast<const char*>(header), sizeof(struct pcap_pkthdr));
    file.write(reinterpret_cast<const char*>(packet), header->caplen);
}

int main(int argc, char* argv[]) {
    if (argc != 2) {
        std::cerr << "Usage: " << argv[0] << " <pcap_file>" << std::endl;
        return 1;
    }
    std::string filename = argv[1];
    process_pcap(filename);
    return 0;
}
