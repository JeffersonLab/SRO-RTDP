//
// This will scan an SRO evio file from a single ROC stream and verify
// that the format is correct. It does not do full format checking, but
// is intended to scan the frame numbers to verify that they are all
// there and that there are no repeats.
//
//
//  cmake -S evioverify -B evioverify.build
//  cmake --build evioverify.build
//
//  g++ -g -std=c++2a evioverify.cc -o evioverify
//

#include <iostream>
#include <fstream>
#include <string>
#include <vector>
#include <set>
#include <filesystem>
#include <chrono>
#include <cstdint>

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
// AttemptCorruptFileRecovery
//
// This attempts to adjust the input file pointer and re-read the
// nth. It does this by looking for the 0xc0da value in the current
// nth which is part of the CODA magic word. This is motivated by
// the May 2024 CLAS12 RTDP data where it was noticed a number of
// files were corrupted, but the magic word was 0xc0da and not
// 0xc0da100 (i.e. it was shifted by 16 bits).
//---------------------------------------
bool AttemptCorruptFileRecovery(std::ifstream &ifs, EVIONetworkTransferHeader &nth)
{
	static uint32_t Ncalls = 0;
	Ncalls++;

	uint16_t *b16 = (uint16_t*)&nth;
	for(int i=0; i<sizeof(nth)/2; i++ ){
		if( b16[i] == 0xc0da ){
			// Found the partial magic word!
			// This should normally be at i=15 on a little endian machine
			std::cout << "Found partial magic word at i=" << i << " !  Attempting recovery ... " << std::endl;
			std::streampos offset = sizeof(EVIONetworkTransferHeader) - (15 - i) * 2;
			ifs.seekg(-offset, std::ios::cur);
			return true; // return tryagain=true to have main loop try once more
		}
	}

	// Hmmm... didn't find it. Try swapping 
	swap((uint32_t*)&nth, sizeof(nth)/sizeof(uint32_t));
	for(int i=0; i<sizeof(nth)/2; i++ ){
		if( b16[i] == 0xc0da ){
			// Found the partial magic word!
			// This should normally be at i=15 on a little endian machine
			std::cout << "Found partial magic word* at i=" << i << " !  Attempting recovery ... " << std::endl;
			std::streampos offset = sizeof(EVIONetworkTransferHeader) - (15 - i) * 2;
			ifs.seekg(-offset, std::ios::cur);
			return true; // return tryagain=true to have main loop try once more
		}
	}

	std::cerr << Ncalls << " issues encountered (last one was fatal)" << std::endl;
	return false; // tryagain=false  tell main loop to give up and quit program	
}

//---------------------------------------
// main
//---------------------------------------
int main(int narg, char* argv[]){

	if( narg<2 ){ std::cout << "Usage:  evioverify file.evio" << std::endl;}
	
	std::string fname = argv[1];
	std::ifstream ifs(fname.c_str(), std::ifstream::binary);
	if(! ifs.is_open() ){
		std:cerr << "Unable to open file: " << fname << std::endl;
		return -1;
	}
	// Get the total size of the input file
    size_t total_size = std::filesystem::file_size(fname);
    size_t processed_size = 0;
	
	std::cout << " Opened input file: " << fname <<  "  (" << total_size/1024.0/1024.0/1024.0 << "GB)" << std::endl;

	uint64_t min_frame_number = 0xFFFFFFFFFFFFFFFF;
	uint64_t max_frame_number = 0;
	uint64_t last_frame_number = 0xFFFFFFFFFFFFFFFF;
	uint32_t Nskipped_frames = 0;
    auto last_update = std::chrono::steady_clock::now();
	std::set<uint32_t> rocids;
	uint32_t ppids = 0;
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
			std::cout << "===========================" << std::endl;

			// attempt to get file pointer realigned with magic word position
			bool tryagain = AttemptCorruptFileRecovery(ifs, nth);
			if( tryagain ) continue;
			return -1;
		}
		
		// Read in the ROC Time Slice Bank
		size_t buff_len = nth.block_len - (sizeof(nth)/sizeof(uint32_t));
		buff_len += 2; // Unknown extra 2 words at end of Network transfer block. Read them so we stay in alignment.
		uint32_t buff[buff_len];
		ifs.read( (char*)buff, buff_len*sizeof(uint32_t) );
		if( swap_needed ) swap(buff, buff_len);
		
		// This overlays the first part of the ROC Time Slice Bank (see slide in coda eventbuilding doc)
		// It gives access to the ROC header, Steam info header, Time Slice Segment vaues, and header
		// word of the Aggregation Info Segment (AIS)
		ROCTimeSliceBankHeader *rtsbh = (ROCTimeSliceBankHeader*)buff;

		rocids.insert(( rtsbh->rocid_type_ss>>16) );

		if( rtsbh->frame_num < min_frame_number) min_frame_number = rtsbh->frame_num;
		if( rtsbh->frame_num > max_frame_number) max_frame_number = rtsbh->frame_num;

		if(last_frame_number == 0xFFFFFFFFFFFFFFFF) last_frame_number = rtsbh->frame_num;
		uint64_t frame_num_diff = rtsbh->frame_num - last_frame_number;
		if( (frame_num_diff!=0) && (frame_num_diff!=1) ){
			std::cerr << "Frame skip detected: frame_num=" << rtsbh->frame_num << "  last_frame_num=" << last_frame_number << std::endl;
			Nskipped_frames++;
		}
		last_frame_number = rtsbh->frame_num;

		// Parse the Aggregation Info Segment (AIS) payloads
		// Each 16bit payload word corresponds to Payload Port data bank that comes later
		size_t Npayload_words = (rtsbh->ais_head&0xffff)*2;
		if( rtsbh->ais_head&0x00800000 ) Npayload_words--; // padding present so ignore last
		uint16_t *payload = (uint16_t*)&buff[9];
		for(size_t i=0; i<Npayload_words; i++){
			auto ppid = payload[i] & 0x1F;
			ppids |= (1 << ppid);
		}

		// Update screen with progress
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

	// Ensure final progress is 100%
    show_progress(total_size, total_size);
    std::cout << std::endl;
	std::cout << "File: " << fname << std::endl;
	std::cout << "Frame number range: " << min_frame_number << " - " << max_frame_number << std::endl;
	std::cout << "Num. skipped frames: " << Nskipped_frames << std::endl;
	std::cout << "rocids: "; for(auto rocid : rocids ) std::cout << rocid << ","; std::cout << endl;
	std::cout << " ppids: "; for(int i=0; i<32; i++ ) if((ppids>>i)&0x1) std::cout <<  i << ","; std::cout << endl;
    std::cout << std::endl;

	return 0;
}


