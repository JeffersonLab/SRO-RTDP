//
// g++ evio2csv.cc -o evio2csv
//

#include <iostream>
#include <fstream>
#include <string>
#include <vector>

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
	
	std::string ofname = fname + ".csv";
	std::ofstream ofs( ofname.c_str() );
	ofs << "frame_number,frame_timestamp,rocid,slot,chan,q,t\n";
	
	std::cout << " In file: " <<  fname << std::endl;
	std::cout << "Out file: " << ofname << std::endl;

	uint32_t Nhits = 0;
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
		
		ROCTimeSliceBankHeader *rtsbh = (ROCTimeSliceBankHeader*)buff;
		//rtsbh->dump();

		// Parse the Aggregation Info Segment (AIS) payloads
		ROCTimeSliceBank rtsb;
		rtsb.head = rtsbh;
		size_t Npayload_words = (rtsb.head->ais_head&0xff);
		uint32_t *ptr = (uint32_t*)&buff[9];
		for(size_t i=0; i<Npayload_words; i++){
			rtsb.ais_payload.push_back((*ptr)&0xffff);
			rtsb.ais_payload.push_back((*ptr)>>16);
			ptr++;
		}
		if( rtsb.head->ais_head&0x00800000 ) rtsb.ais_payload.pop_back(); // remove padding if needed
		
		// Index the Payload banks for each port
		for(auto ainfo : rtsb.ais_payload){
			//std::cout << "buff: 0x" << std::hex << buff << "  ptr: 0x" << ptr << std::dec << "  payload_len: " << *ptr << std::endl;
			TimeSlicePortDataBank payload_port_bank;
			payload_port_bank.payload_len = *ptr++;
			payload_port_bank.head = *ptr++;
			payload_port_bank.data = ptr;
			rtsb.databank.push_back(payload_port_bank);

			ptr += payload_port_bank.payload_len - 1;			
		}
		
		// Loop over payload data
		std::vector<F250Hit> hits;
		for(auto ppb : rtsb.databank){
		
			for( size_t i=0; i<ppb.payload_len-1; i++){
				
				auto w = ppb.data[i];
				
				// FADC250 SRO format is not documented. Dave A. pointed me to Vardan 
				// who pointed me to this code on Github in ersap-actor repository:
				// src/main/java/org/jlab/ersap/actor/coda/source/file/CodaOutputFileReader.java
				F250Hit hit;
				hit.frame_number = rtsbh->frame_num;
				hit.frame_timestamp = (uint64_t)rtsbh->timestamp1 + (((uint64_t)rtsbh->timestamp2)<<32);
				hit.rocid = rtsbh->rocid_type_ss >> 16;
				hit.slot = ppb.head >> 16;
				hit.chan = (w>>13) & 0x000F;
				hit.q    = (w>>0 ) & 0x1FFF;
				hit.t    = ((w>>17) & 0x3FFF)*4;
			
				hits.push_back(hit);
			}
		}
		
		// Loop over hits, writing to CSV file
		//std::cout << "Writing " << hits.size() << " hits to CSV file ..." << std::endl;
		Nhits += hits.size();
		for( auto &hit : hits ){
			ofs << hit.frame_number << ","
				<< hit.frame_timestamp << ","
				<< hit.rocid << ","
				<< hit.slot << ","
				<< hit.chan << ","
				<< hit.q << ","
				<< hit.t << "\n";
		}
		
		size_t Nwords = (ptr - buff);
		//std::cout << "Nwords: parsed="<< Nwords << "  NTH=" << nth.block_len-8 << std::endl;
	}
	
	
	// Close input and output files
	ifs.close();
	ofs.close();
	
	std::cout << "Wrote " << Nhits << " hits to CSV file." << std::endl;

	return 0;
}


