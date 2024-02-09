#include <pcap.h>
#include <iostream>
#include <unordered_map>
#include <algorithm>
#include <vector>
#include <netinet/ip.h>
#include <netinet/tcp.h>


// The code here is inefficient because it uses a std::vector to hold
// the sequence numbers for every packet. This is costly in part because
// of the constant reallocations of memory and also because the entire
// vector is searched for every packet as new packets are read in. There
// should be ~4M-10M packets in a file.

void packet_handler(u_char *userData, const struct pcap_pkthdr* pkthdr, const u_char* packet) {
    const struct ip* ipHeader;
    const struct tcphdr* tcpHeader;

    ipHeader = (struct ip*)(packet + 14); // Offset to skip Ethernet header
    tcpHeader = (struct tcphdr*)(packet + 14 + ipHeader->ip_hl * 4); // Offset to skip IP header

    if (ipHeader->ip_p == IPPROTO_TCP) {
        u_short dst_port = ntohs(tcpHeader->dest);
        u_int32_t seq = ntohl(tcpHeader->seq);  // Explicitly cast to u_int32_t

        auto &seq_nums = *reinterpret_cast<std::unordered_map<u_short, std::vector<u_int32_t>>*>(userData);

        if (seq_nums.find(dst_port) != seq_nums.end() && std::find(seq_nums[dst_port].begin(), seq_nums[dst_port].end(), seq) != seq_nums[dst_port].end()) {
            std::cout << "Potential retransmission detected on Destination Port: " << dst_port << std::endl;
        }

        seq_nums[dst_port].push_back(seq);
    }
}


int main() {
    pcap_t *handle;
    char errbuf[PCAP_ERRBUF_SIZE];
    std::unordered_map<u_short, std::vector<u_int32_t>> seq_numbers;

    handle = pcap_open_offline("your_file.pcap", errbuf);
    if (handle == nullptr) {
        std::cerr << "Couldn't open pcap file: " << errbuf << std::endl;
        return 2;
    }

    if (pcap_loop(handle, 0, packet_handler, reinterpret_cast<u_char*>(&seq_numbers)) < 0) {
        std::cerr << "pcap_loop() failed: " << pcap_geterr(handle) << std::endl;
        pcap_close(handle);
        return 3;
    }

    pcap_close(handle);
    return 0;
}
