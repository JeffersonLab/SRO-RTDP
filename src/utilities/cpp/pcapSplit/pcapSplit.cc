//
// This program will take a .pcap file as input and split it into
// individual files corresponding to the destination port of the
// packet. A directory is automatically created to hold the split
// files and those are automatically created as new ports are
// encountered in the file.
//
//
// g++ -g -std=c++2a -o pcapSplit pcapSplit.cc -lpcap
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

uint32_t g_snaplen = 65535; // this will get overridden with snaplen of input pcap file

void write_packet_to_file(std::unordered_map<uint16_t, std::ofstream>& port_files, const std::string& base_filename, const struct pcap_pkthdr* header, const u_char* packet, uint16_t port);

// Define the on-disk packet header struct with 32-bit values
struct pcap_pkthdr_disk {
    uint32_t ts_sec;   // time stamp seconds
    uint32_t ts_usec;  // time stamp microseconds
    uint32_t caplen;   // length of portion present
    uint32_t len;      // length this packet (off wire)
};

//-------------------------------
// show_progress
//-------------------------------
void show_progress(size_t current, size_t total) {
    float progress = (float)current / total * 100;
    std::cout << "\rProgress: " << progress << "%        " << std::flush;
}

//-------------------------------
// write_pcap_file_header
//
// Function to write pcap file header to a new file
//-------------------------------
void write_pcap_file_header(std::ofstream& file) {
    pcap_file_header file_header;
    file_header.magic = 0xa1b2c3d4;
    file_header.version_major = PCAP_VERSION_MAJOR;
    file_header.version_minor = PCAP_VERSION_MINOR;
    file_header.thiszone = 0;
    file_header.sigfigs = 0;
    file_header.snaplen = g_snaplen;
    file_header.linktype = DLT_EN10MB;

    file.write(reinterpret_cast<const char*>(&file_header), sizeof(file_header));
}

//-------------------------------
// process_pcap
//-------------------------------
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

    // Retrieve snaplen from the input pcap file header
    g_snaplen = pcap_snapshot(handle);

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

    uint32_t max_caplen = 0;
    while ((res = pcap_next_ex(handle, &header, &packet)) >= 0) {
        if (res == 0) continue;

        processed_size += header->caplen;
        if( header->caplen > max_caplen ) max_caplen = header->caplen;

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

    std::cout << "Max caplen: " << max_caplen << std::endl;
}

//-------------------------------
// write_packet_to_file
//-------------------------------
void write_packet_to_file(std::unordered_map<uint16_t, std::ofstream>& port_files, const std::string& directory, const struct pcap_pkthdr* header, const u_char* packet, uint16_t port) {
    if (port_files.find(port) == port_files.end()) {
        std::string file_path = directory + "/" + "port" + std::to_string(port) + ".pcap";
        port_files[port].open(file_path, std::ios::binary | std::ios::app);
        if (!port_files[port].is_open()) {
            std::cerr << "Could not open file: " << file_path << std::endl;
            return;
        }
        std::cout << "opened file: " << file_path << " for writing  (snaplen=" << g_snaplen << ")" << std::endl;

        // Write the pcap file header to the new file
        write_pcap_file_header(port_files[port]);
    }

    // Convert pcap_pkthdr to pcap_pkthdr_disk for on-disk format
    pcap_pkthdr_disk disk_header;
    disk_header.ts_sec = header->ts.tv_sec;
    disk_header.ts_usec = header->ts.tv_usec;
    disk_header.caplen = header->caplen;
    disk_header.len = header->len;

    std::ofstream& file = port_files[port];
    file.write(reinterpret_cast<const char*>(&disk_header), sizeof(disk_header));
    file.write(reinterpret_cast<const char*>(packet), header->caplen);
}

//-------------------------------
// main
//-------------------------------
int main(int argc, char* argv[]) {
    if (argc != 2) {
        std::cerr << "Usage: " << argv[0] << " <pcap_file>" << std::endl;
        return 1;
    }
    std::string filename = argv[1];
    process_pcap(filename);
    return 0;
}
