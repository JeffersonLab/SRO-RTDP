// buffer_packet_emu.hpp

#define DBG 0	//print extra verbosity apart from -v switch
  

#ifdef __linux__
    #define HTONLL(x) ((1==htonl(1)) ? (x) : (((uint64_t)htonl((x) & 0xFFFFFFFFUL)) << 32) | htonl((uint32_t)((x) >> 32)))
    #define NTOHLL(x) ((1==ntohl(1)) ? (x) : (((uint64_t)ntohl((x) & 0xFFFFFFFFUL)) << 32) | ntohl((uint32_t)((x) >> 32)))
#endif

#include <cstdint>
#include <cstring>
#include <vector>
#include <stdexcept>
#include <iostream>
#include <arpa/inet.h> // For htonl, ntohl

#pragma pack(push, 1)
struct PacketHeader {
    uint32_t size_B;        // Payload size_B
    uint64_t timestamp_uS;  // Timestamp uS since epoch
    uint32_t stream_id;     // Stream ID
    uint32_t frame_num;     // Frame number
};
#pragma pack(pop)

const size_t HEADER_SIZE = sizeof(PacketHeader);

// Serialize header and payload into a byte vector
//auto data = serialize_packet(8*payload.size(), us.count(), parsed.frame_num, parsed.stream_id, payload);
std::vector<uint8_t> serialize_packet(uint64_t tsr_us, uint16_t rcv_prt, uint32_t size_B, uint64_t timestamp_uS,
                                      uint32_t stream_id, uint32_t frame_num,
                                      const std::vector<uint8_t>& payload) {
    if(DBG) std::cout << timestamp_uS << " [cpu_emu " << rcv_prt << "]: serialize_packet: size_B = " << size_B << " stream_id = " << stream_id << " timestamp_uS = " << timestamp_uS << " frame_num = " 
                    << frame_num << " payload size_B = " << payload.size() << std::endl;
    if (size_B != payload.size()) {
        std::cout.flush();
        std::cerr.flush();
        throw std::invalid_argument("Size does not match payload length");
    }

    std::vector<uint8_t> buffer(HEADER_SIZE + payload.size());
    PacketHeader header;
    header.size_B = htonl(size_B);
    header.timestamp_uS = htobe64(timestamp_uS); // Ensure big-endian
    header.stream_id = htonl(stream_id);
    header.frame_num = htonl(frame_num);
    if(DBG) std::cout << "serialize_packet: into size_B = " << header.size_B << " stream_id = " << header.stream_id << " timestamp_uS = " << header.timestamp_uS << " frame_num = " 
                    << header.frame_num << " payload size_B = " << payload.size() << std::endl;

    std::memcpy(buffer.data(), &header, HEADER_SIZE);
    std::memcpy(buffer.data() + HEADER_SIZE, payload.data(), payload.size());

    return buffer;
}

// Deserialize packet from raw data
struct DeserializedPacket {
    uint32_t size_B;
    uint64_t timestamp_uS;
    uint32_t stream_id;
    uint32_t frame_num;
    std::vector<uint8_t> payload;
};

//auto parsed = deserialize_packet(static_cast<uint8_t*>(request.data()), request.size_B());
DeserializedPacket deserialize_packet(uint64_t tsr_us, uint16_t rcv_prt, const uint8_t* data, size_t length) {
    if (length < HEADER_SIZE) {
        throw std::runtime_error("Data too short");
    }

    PacketHeader header;
    std::memcpy(&header, data, HEADER_SIZE);

    uint32_t size_B = ntohl(header.size_B); //bits to bytes
    if(DBG) std::cout << tsr_us << " [cpu_emu " << rcv_prt << "]: deserialize_packet: header.size_B = " << size_B << " size_B = " << uint32_t(size_B) << " length = " << length << std::endl;
    if (length != HEADER_SIZE + size_B) {
        std::cout.flush();
        std::cerr.flush();
        throw std::runtime_error("Packet length mismatch");
    }

    DeserializedPacket packet;
    packet.size_B = size_B;
    packet.timestamp_uS = be64toh(header.timestamp_uS); //be64toh(header.timestamp_uS); ?
    packet.stream_id = ntohl(header.stream_id);
    packet.frame_num = ntohl(header.frame_num);
    packet.payload.assign(data + HEADER_SIZE, data + HEADER_SIZE + size_B);
    if(DBG) std::cout << tsr_us << " [cpu_emu " << rcv_prt << "]: deserialized_packet: size_B = " << packet.size_B << " timestamp_uS " << packet.timestamp_uS << " stream_id " << packet.stream_id << " frame_num " << packet.frame_num << std::endl;

    return packet;
}

