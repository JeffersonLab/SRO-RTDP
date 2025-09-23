
#include <iostream>


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

//---------------------------------------
// swap
//
// swap block of 32bit unsigned ints.
//---------------------------------------
static void swap( uint32_t *buff, size_t Nwords){

	for(size_t i=0; i<Nwords; i++){
		auto &bout = buff[i];
		auto bin = bout;
		bout = ((bin&0x000000ff)<<24) + ((bin&0x0000ff00)<<8) + ((bin&0x00ff0000)>>8) + ((bin&0xff000000)>>24);
	}

}