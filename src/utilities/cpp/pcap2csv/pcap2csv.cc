//
//  g++ pcap2csv.cc -o pcap2csv -lpcap
//
//
#include <iostream>
#include <fstream>
#include <string>
#include <iomanip>
#include <limits>
#include <pcap.h>
#include <netinet/ip.h>
#include <netinet/tcp.h>
#include <netinet/udp.h>
#include <arpa/inet.h>

void write_packet_info_to_csv(std::ofstream &file, const u_char *packet, struct pcap_pkthdr packet_header) {
    // Convert the timestamp to a single floating-point value
    double timestamp = packet_header.ts.tv_sec + packet_header.ts.tv_usec / 1000000.0;

    struct ip *ip_header = (struct ip *)(packet + 14); // Skip Ethernet header

    file << timestamp << ",";
    file << packet_header.caplen << "," << packet_header.len << ",";
    //file << (ip_header->ip_hl * 4) << ",";
    //file << inet_ntoa(ip_header->ip_src) << ",";
    //file << inet_ntoa(ip_header->ip_dst) << ",";

    if (ip_header->ip_p == IPPROTO_TCP) {
        struct tcphdr *tcp_header = (struct tcphdr *)(packet + 14 + ip_header->ip_hl * 4);
        uint32_t seq_num = ntohl(tcp_header->th_seq);
        uint32_t ack_num = ntohl(tcp_header->th_ack);

        file << ntohs(tcp_header->th_dport) << ",";
        file << seq_num << ",";  // Sequence number
        file << ack_num << "\n";  // Acknowledgment number
    } else if (ip_header->ip_p == IPPROTO_UDP) {
        struct udphdr *udp_header = (struct udphdr *)(packet + 14 + ip_header->ip_hl * 4);
        file << ntohs(udp_header->uh_dport) << "\n";
    }
}

int main(int argc, char *argv[]) {
    if (argc != 2) {
        std::cerr << "Usage: " << argv[0] << " <pcap file>\n";
        return 1;
    }
    
    std::string infile = argv[1];
    std::string outfile = infile;

    // Find the position of the ".pcap" extension
    size_t pos = outfile.rfind(".pcap");

    if (pos != std::string::npos) {
        // Replace ".pcap" with ".csv"
        outfile.replace(pos, std::string(".pcap").length(), ".csv");
    } else {
        std::cerr << "Error: The input filename does not end with '.pcap'" << std::endl;
        return 1;
    }

    char errbuf[PCAP_ERRBUF_SIZE];
    pcap_t *handle = pcap_open_offline(infile.c_str(), errbuf);

    if (handle == nullptr) {
        std::cerr << "pcap_open_offline() failed: " << errbuf << "\n";
        return 2;
    }

    std::ofstream csv_file(outfile.c_str());
    if (!csv_file.is_open()) {
        std::cerr << "Failed to open file " << outfile << " for writing.\n";
        return 4;
    }
    
    // Make sure we write float values at max precision (mainly for timestamp)
    csv_file << std::setprecision(std::numeric_limits<double>::max_digits10);
    
    std::cout << " Input file: " << infile  << std::endl;
    std::cout << "Output file: " << outfile << std::endl;

    // Write the CSV header
    //csv_file << "Timestamp,CaptureLength,TotalLength,IPHeaderLength,SourceIP,DestinationIP,SourcePort,DestinationPort,Protocol\n";
    csv_file << "Timestamp,CaptureLength,TotalLength,DestinationPort,seq,ack\n";

    const u_char *packet;
    struct pcap_pkthdr packet_header;

    while ((packet = pcap_next(handle, &packet_header)) != nullptr) {
        write_packet_info_to_csv(csv_file, packet, packet_header);
    }

    csv_file.close();
    pcap_close(handle);
    return 0;
}
