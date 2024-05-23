
//
//  g++ pcap2evio.cc -o pcap2evio -lpcap
//
//
#include <iostream>
#include <fstream>
#include <pcap.h>
#include <netinet/ip.h>
#include <netinet/tcp.h>
#include <netinet/in.h>
#include <arpa/inet.h>

uint64_t Nbytes_written = 0;

//-----------------------------
// packet_handler
//-----------------------------
void packet_handler(u_char *user_data, const struct pcap_pkthdr *pkthdr, const u_char *packet) {

	// This is called by pcap_loop() for every packet in the file.

    std::ofstream *outfile = reinterpret_cast<std::ofstream*>(user_data);
    const struct ip *ip_header;
    const struct tcphdr *tcp_header;
    const char *payload;
    int ethernet_header_length = 14; // Standard size of Ethernet header
    int ip_header_length, tcp_header_length, payload_length;

    // Skip the Ethernet header
    ip_header = (struct ip*)(packet + ethernet_header_length);
    ip_header_length = ip_header->ip_hl * 4; // IP header length

    // Check for TCP protocol
    if (ip_header->ip_p == IPPROTO_TCP) {
        tcp_header = (struct tcphdr*)(packet + ethernet_header_length + ip_header_length);
        tcp_header_length = tcp_header->th_off * 4; // TCP header length

        // Calculate the payload size
        int total_headers_size = ethernet_header_length + ip_header_length + tcp_header_length;
        payload_length = pkthdr->len - total_headers_size;

        if (payload_length > 0) {
            payload = (char *)(packet + total_headers_size);
			uint64_t Nbytes_to_write = payload_length;
			
			// The first 14 bytes have an unknown format so we drop them.
            const uint64_t bytes_to_skip = 14;
			if( Nbytes_written<bytes_to_skip ){
				uint64_t Nskip = bytes_to_skip - Nbytes_written; // bytes left to skip
				if( Nskip > Nbytes_to_write) Nskip = Nbytes_to_write;
				payload += Nskip;
				Nbytes_to_write -= Nskip;
			}
			
            outfile->write(payload, Nbytes_to_write);
			Nbytes_written += payload_length; // This also counts the 32 bytes we skip at front of file
        }
    }
}

//-----------------------------
// main
//-----------------------------
int main(int argc, char *argv[]) {

	// Parse command line
    if (argc != 2) {
        std::cerr << "Usage: " << argv[0] << " <pcap file>\n";
        return 1;
    }
    
    std::string infile = argv[1];
    std::string outfile = infile;

    // Find the position of the ".pcap" extension
    size_t pos = outfile.rfind(".pcap");

    if (pos != std::string::npos) {
        // Replace ".pcap" with ".evio"
        outfile.replace(pos, std::string(".pcap").length(), ".evio");
    } else {
        std::cerr << "Error: The input filename does not end with '.pcap'" << std::endl;
        return 1;
    }
	
    std::cout << " Input file: " << infile  << std::endl;
    std::cout << "Output file: " << outfile << std::endl;

	// Open input file
    char errbuf[PCAP_ERRBUF_SIZE];
    pcap_t *handle;
    handle = pcap_open_offline(infile.c_str(), errbuf);
    if (handle == nullptr) {
        std::cerr << "pcap_open_offline() failed: " << errbuf << "\n";
        return 2;
    }
    std::cout << "Opened file: " << infile << "  (snaplen=" << pcap_snapshot(handle) << ")" << std::endl;

	// Open output file
    std::ofstream ofile(outfile.c_str(), std::ios::out | std::ios::binary);
    if (!ofile.is_open()) {
        std::cerr << "Failed to open file " << outfile << " for writing.\n";
        return 3;
    }

	// Process all packets in input file
    if (pcap_loop(handle, 0, packet_handler, reinterpret_cast<u_char*>(&ofile)) < 0) {
        std::cerr << "pcap_loop() failed: " << pcap_geterr(handle) << std::endl;
        ofile.close();
        pcap_close(handle);
        return 4;
    }

	// Close input and output files
    ofile.close();
    pcap_close(handle);
	
	std::cout << "Wrote " << Nbytes_written << " bytes (" << Nbytes_written/1.0E9 << " GB)" << std::endl;
	
    return 0;
}
