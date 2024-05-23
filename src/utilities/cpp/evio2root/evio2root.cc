//
// This will convert the SRO formatted evio data (non-aggregated) into a ROOT
// file. This works for the May 2024 CLAS12 RTDP beam test data and converts
// both F250 and DCRB hits. (Any particular crate will only have one type
// though.)
//
//
// g++ -g evio2root.cc -o evio2root `root-config --cflags --libs`
//

#include <iostream>
#include <fstream>
#include <string>
#include <vector>
#include <filesystem>
#include <chrono>
#include <cstdint>
#include <TFile.h>
#include <TTree.h>
using namespace std;

typedef struct{
	uint32_t block_len;
	uint32_t block_num;
	uint32_t header_len;
	uint32_t event_count;
	uint32_t reserved1;
	uint32_t bitinfo_version;
	uint32_t reserved2;
	uint32_t magic_number;
	
	void dump(void){
		std::cout << "block_len : " << block_len << std::endl;
		std::cout << "block_num : " << block_num << std::endl;
		std::cout << "header_len : " << header_len << std::endl;
		std::cout << "event_count : " << event_count << std::endl;
		std::cout << "reserved1 : " << reserved1 << std::endl;
		std::cout << "bitinfo : 0x" << std::hex << (bitinfo_version>>8) << std::dec << "  version: " << (bitinfo_version&0xff) << std::endl;
		std::cout << "reserved2 : " << reserved2 << std::endl;
		std::cout << "magic_number : 0x" << std::hex << magic_number << std::dec << std::endl;
	}
	
}EVIONetworkTransferHeader;

typedef struct{
	uint32_t roc_bank_len;
	uint32_t rocid_type_ss;
	uint32_t sib_len;
	uint32_t sib_head;
	uint32_t tss_head;
	uint32_t frame_num;
	uint32_t timestamp1;
	uint32_t timestamp2;
	uint32_t ais_head;
	
	void dump(void){
		std::cout << "roc_bank_len : " << roc_bank_len << std::endl;
		std::cout << "       rocid : " << (rocid_type_ss>>16) << "  SS: " << std::hex << (rocid_type_ss&0x0f) << std::dec << std::endl;
		std::cout << "     sib_len : " << sib_len << std::endl;
		std::cout << "     sib tag : 0x" << std::hex << (sib_head>>16) << std::dec << "  SS: " << (sib_head&0x0f) << std::endl;
		std::cout << "     tss tag : 0x" << std::hex << (tss_head>>24) << std::dec << "  tss_len: " << (tss_head&0xff) << std::endl;
		std::cout << "   frame_num : " << frame_num << std::endl;
		std::cout << "  timestamp1 : " << timestamp1 << std::endl;
		std::cout << "  timestamp2 : " << timestamp2 << std::endl;
		std::cout << "     ais tag : 0x" << std::hex << (ais_head>>24) << std::dec << "  ais_len: " << (ais_head&0xff) << std::endl;
		
		uint32_t *b = (uint32_t*)this;
		for(size_t i=9; i<roc_bank_len+1; i++){
			std::cout << "payload : 0x" << std::hex << b[i] << std::dec << std::endl;
		}
	}

}ROCTimeSliceBankHeader;

typedef struct{
	uint32_t payload_len;
	uint32_t head;
	uint32_t *data;
	uint16_t ais_payload_word;
	uint8_t module_id; // copied from bits 11-8 of ais_payload_word
	
	void dump(void){
		std::cout << "payload_len : " << payload_len << std::endl;
		std::cout << "       head : 0x" << std::hex << (head>>16) << std::dec << "  SS: " << (head&0x0f) << std::endl;
	}	
}TimeSlicePortDataBank;

typedef struct{
	ROCTimeSliceBankHeader *head;
	std::vector<uint16_t> ais_payload;
	std::vector<TimeSlicePortDataBank> databank;
}ROCTimeSliceBank;

typedef struct{
	uint32_t frame_number;
	uint64_t frame_timestamp;
	uint32_t rocid;
	uint32_t slot;
	uint32_t chan;
	uint32_t q;
	uint32_t t; // in ns
}F250Hit;

typedef struct{
	uint32_t frame_number;
	uint64_t frame_timestamp;
	uint32_t rocid;
	uint32_t slot;
	uint32_t chan;
	uint32_t t; // in ns (time is actually measured in units of 32ns but converted to ns for here)
}DCRBHit;


//-------------------------------
// show_progress
//-------------------------------
void show_progress(size_t current, size_t total) {
    float progress = (float)current / total * 100;
    std::cout << "\rProgress: " << progress << "%        " << std::flush;
}

//---------------------------------------
// swap
//
// swap block of 32bit unsigned ints.
//---------------------------------------
void swap( uint32_t *buff, size_t Nwords){

	for(size_t i=0; i<Nwords; i++){
		auto &bout = buff[i];
		auto bin = bout;
		bout = ((bin&0x000000ff)<<24) + ((bin&0x0000ff00)<<8) + ((bin&0x00ff0000)>>8) + ((bin&0xff000000)>>24);
	}

}

//---------------------------------------
// main
//---------------------------------------
int main(int narg, char* argv[]){

	if( narg<2 ){ std::cout << "Usage:  evio2csv file.evio" << std::endl;}
	
	std::string fname = argv[1];
	std::ifstream ifs(fname.c_str(), std::ifstream::binary);
	if(! ifs.is_open() ){
		std:cerr << "Unable to open file: " << fname << std::endl;
		return -1;
	}
	// Get the total size of the input file
    size_t total_size = std::filesystem::file_size(fname);
    size_t processed_size = 0;
	
	// Open output file
	std::string ofname = fname + ".root";
	TFile file( ofname.c_str(), "RECREATE");

	// Define F250 tree
    TTree tree_f250("f250", "F250 Hits Data");
	F250Hit thit_f250;
	tree_f250.Branch("frame_number", &thit_f250.frame_number, "frame_number/i");
    tree_f250.Branch("frame_timestamp", &thit_f250.frame_timestamp, "frame_timestamp/l");
    tree_f250.Branch("rocid", &thit_f250.rocid, "rocid/i");
    tree_f250.Branch("slot", &thit_f250.slot, "slot/i");
    tree_f250.Branch("chan", &thit_f250.chan, "chan/i");
    tree_f250.Branch("q", &thit_f250.q, "q/i");
    tree_f250.Branch("t", &thit_f250.t, "t/i");

	// Define DCRB tree
    TTree tree_dcrb("dcrb", "DCRB Hits Data");
	DCRBHit thit_dcrb;
	tree_dcrb.Branch("frame_number", &thit_dcrb.frame_number, "frame_number/i");
    tree_dcrb.Branch("frame_timestamp", &thit_dcrb.frame_timestamp, "frame_timestamp/l");
    tree_dcrb.Branch("rocid", &thit_dcrb.rocid, "rocid/i");
    tree_dcrb.Branch("slot", &thit_dcrb.slot, "slot/i");
    tree_dcrb.Branch("chan", &thit_dcrb.chan, "chan/i");
    tree_dcrb.Branch("t", &thit_dcrb.t, "t/i");
	
	std::cout << " In file: " <<  fname      << std::endl;
	std::cout << "Out file: " << ofname << std::endl;

	uint32_t Nhits_f250 = 0;
	uint32_t Nhits_dcrb = 0;
    auto last_update = std::chrono::steady_clock::now();
	while( ! ifs.eof() ){
		
		// Read in network transfer header
		EVIONetworkTransferHeader nth;
		ifs.read( (char*)&nth, sizeof(nth) );
		if( ifs.eof() || ifs.gcount()!=sizeof(nth) ) break;
		
		// Check magic number and byteswap
		bool swap_needed = nth.magic_number != 0xc0da0100;
		if( swap_needed ) swap((uint32_t*)&nth, sizeof(nth)/sizeof(uint32_t));
		if( nth.magic_number != 0xc0da0100 ){
			std::cout << "==== Bad magic number! ====" << std::endl;
			nth.dump();
			return -1;
		}
		
		// Read in the ROC Time Slice Bank
		size_t buff_len = nth.block_len - (sizeof(nth)/sizeof(uint32_t));
		buff_len += 2; // Unknown extra 2 words at end of Network transfer block. Read them so we stay in alignment.
		uint32_t buff[buff_len];
		ifs.read( (char*)buff, buff_len*sizeof(uint32_t) );
		//std::cout << "Read " << ifs.gcount() << " payload bytes" << std::endl;
		if( swap_needed ) swap(buff, buff_len);
		
		// This overlays the first part of the ROC Time Slice Bank (see slide in coda eventbuilding doc)
		// It gives access to the ROC header, Steam info header, Time Slice Segment vaues, and header
		// word of the Aggregation Info Segment (AIS)
		ROCTimeSliceBankHeader *rtsbh = (ROCTimeSliceBankHeader*)buff;

		// Parse the Aggregation Info Segment (AIS) payloads
		// Each 16bit payload word corresponds to Payload Port data bank that comes later
		ROCTimeSliceBank rtsb;
		rtsb.head = rtsbh;
		size_t Npayload_words = (rtsb.head->ais_head&0xffff);
		uint32_t *ptr = (uint32_t*)&buff[9];
		for(size_t i=0; i<Npayload_words; i++){
			rtsb.ais_payload.push_back((*ptr)&0xffff);
			rtsb.ais_payload.push_back((*ptr)>>16);
			ptr++;
		}
		if( rtsb.head->ais_head&0x00800000 ) rtsb.ais_payload.pop_back(); // remove padding if needed
		
		// Index the Payload Port banks for each port.
		// Each bank here corresponds to a 16bit AIS payload word from above
		for(auto ainfo : rtsb.ais_payload){
			//std::cout << "buff: 0x" << std::hex << buff << "  ptr: 0x" << ptr << std::dec << "  payload_len: " << *ptr << std::endl;
			TimeSlicePortDataBank payload_port_bank;
			payload_port_bank.payload_len = *ptr++;
			payload_port_bank.head = *ptr++;
			payload_port_bank.data = ptr;
			payload_port_bank.ais_payload_word = ainfo; // convenient to copy here
			payload_port_bank.module_id = (payload_port_bank.ais_payload_word >> 8) & 0x0F;  // also convenient
			rtsb.databank.push_back(payload_port_bank);

			// Do a consistensy check that the Payload Port # from the AIS word is
			// the same as the PP ID in this bank. They should follow the same order
			// but in principle, they could differ. More likely, this would indicate
			// corrupted data.
			uint8_t ppid_ais = ainfo & 0x1F;
			uint8_t ppid_bank_head = (payload_port_bank.head >> 16) & 0xFF;
			if( ppid_ais != ppid_bank_head ){
				std::cerr << "ERROR: The AIS payload word port # differs from the corresponding PP ID in the data bank!" << std::endl;
				std::cerr << "AIS=0x" << std::hex << ppid_ais << "  databank=0x" << ppid_bank_head << std::dec << std::endl;
				return -1;
			}

			ptr += payload_port_bank.payload_len - 1;			
		}
		
		// Loop over payload data (both F250 and DCRB)
		for(auto ppb : rtsb.databank){
		
			for( size_t i=0; i<ppb.payload_len-1; i++){
				
				auto w = ppb.data[i];

				if( ppb.module_id == 0 ) { // F250
					// FADC250 SRO format is not documented. 
					// Dave A. pointed me to Vardan  who pointed me to this code on Github in ersap-actor repository:
					// src/main/java/org/jlab/ersap/actor/coda/source/file/CodaOutputFileReader.java
					F250Hit &hit = thit_f250;
					hit.frame_number = rtsbh->frame_num;
					hit.frame_timestamp = (uint64_t)rtsbh->timestamp1 + (((uint64_t)rtsbh->timestamp2)<<32);
					hit.rocid = rtsbh->rocid_type_ss >> 16;
					hit.slot = ppb.head >> 16;
					hit.chan = (w>>13) & 0x000F;
					hit.q    = (w>>0 ) & 0x1FFF;
					hit.t    = ((w>>17) & 0x3FFF)*4;
					tree_f250.Fill();
					Nhits_f250++;
				}else if(ppb.module_id == 1) { // DCRB
					// DCRB uses two 32bit words to encode hit pattern of 48 (of the 96) inputs to the module
					auto w2 = ppb.data[++i];

					// Each wire is represented by one bit in pattern. The pattern
					// is 48 bits spread over two 32bit words. The high 3bits of the
					// first word indicate whether it is the first(0) or second(1)
					// 48 inputs of the module.
					auto ch = (w>>29) & 0x07;  // 0=inputs 0-47   1=inputs 48-95
					uint64_t pattern_28_00 = w  & 0x1FFFFFFF;
					uint64_t pattern_47_29 = w2 & 0x7FFFF;
					uint64_t pattern = (pattern_47_29<<29) | pattern_28_00;

					// Loop over all possible 48 bits and create hits for any that are set
					for( int i=0; i<48; i++ ){
						if( ! ((pattern>>i) & 0x1) ) continue;  // skip wires that weren't hit

						DCRBHit &hit = thit_dcrb;
						hit.frame_number = rtsbh->frame_num;
						hit.frame_timestamp = (uint64_t)rtsbh->timestamp1 + (((uint64_t)rtsbh->timestamp2)<<32);
						hit.rocid = rtsbh->rocid_type_ss >> 16;
						hit.slot = ppb.head >> 16;
						hit.chan = i + (ch*48); // chan will be 0-95
						hit.t = ((w2>>19) & 0x7FF) * 32; // multiply by 32 to convert to ns
						tree_dcrb.Fill();
						Nhits_dcrb++;
					}
				}else{
					std::cerr << "Unknown module type id (" << ppb.module_id << "). Should be 0 or 1!" << std::endl;
					return -2;
				}
			}
		}

		auto now = std::chrono::steady_clock::now();
        std::chrono::duration<double> elapsed_seconds = now - last_update;
        if (elapsed_seconds.count() >= 1.0) {
			processed_size = ifs.tellg();
            show_progress(processed_size, total_size);
            last_update = now;
        }

	}
	
	// Close input and output files
	ifs.close();
	file.Write();
    file.Close();

	// Ensure final progress is 100%
    show_progress(total_size, total_size);
    std::cout << std::endl;
	std::cout << "Wrote " << Nhits_f250 << " F250 hits to CSV file." << std::endl;
	std::cout << "Wrote " << Nhits_dcrb << " DCRB hits to CSV file." << std::endl;
    std::cout << std::endl;

	return 0;
}


