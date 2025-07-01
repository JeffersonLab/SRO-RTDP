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
    uint32_t size;         // Payload size
    uint64_t timestamp;    // Timestamp
    uint32_t stream_id;    // Stream ID
    uint32_t frame_num;    // Frame number
};
#pragma pack(pop)

const size_t HEADER_SIZE = sizeof(PacketHeader);

// Serialize header and payload into a byte vector
//auto data = serialize_packet(8*payload.size(), us.count(), parsed.frame_num, parsed.stream_id, payload);
std::vector<uint8_t> serialize_packet(uint64_t tsr, uint16_t rcv_prt, uint32_t size, uint64_t timestamp,
                                      uint32_t stream_id, uint32_t frame_num,
                                      const std::vector<uint8_t>& payload) {
    if(DBG) std::cout << timestamp << " [cpu_emu " << rcv_prt << "]: serialize_packet: size = " << size << " stream_id = " << stream_id << " timestamp = " << timestamp << " frame_num = " 
                    << frame_num << " payload size = " << payload.size() << std::endl;
    if (size/8 != payload.size()) {
        std::cout.flush();
        std::cerr.flush();
        throw std::invalid_argument("Size does not match payload length");
    }

    std::vector<uint8_t> buffer(HEADER_SIZE + payload.size());
    PacketHeader header;
    header.size = htonl(size);
    header.timestamp = htobe64(timestamp); // Ensure big-endian
    header.stream_id = htonl(stream_id);
    header.frame_num = htonl(frame_num);
    if(DBG) std::cout << "serialize_packet: into size = " << header.size << " stream_id = " << header.stream_id << " timestamp = " << header.timestamp << " frame_num = " 
                    << header.frame_num << " payload size = " << payload.size() << std::endl;

    std::memcpy(buffer.data(), &header, HEADER_SIZE);
    std::memcpy(buffer.data() + HEADER_SIZE, payload.data(), payload.size());

    return buffer;
}

// Deserialize packet from raw data
struct DeserializedPacket {
    uint32_t size;
    uint64_t timestamp;
    uint32_t stream_id;
    uint32_t frame_num;
    std::vector<uint8_t> payload;
};

//auto parsed = deserialize_packet(static_cast<uint8_t*>(request.data()), request.size());
DeserializedPacket deserialize_packet(uint64_t tsr, uint16_t rcv_prt, const uint8_t* data, size_t length) {
    if (length < HEADER_SIZE) {
        throw std::runtime_error("Data too short");
    }

    PacketHeader header;
    std::memcpy(&header, data, HEADER_SIZE);

    uint32_t size = ntohl(header.size)/8; //bits to bytes
    if(DBG) std::cout << tsr << " [cpu_emu " << rcv_prt << "]: deserialize_packet: header.size = " << size << " size/8 = " << uint32_t(size) << " length = " << length << std::endl;
    if (length != HEADER_SIZE + size) {
        std::cout.flush();
        std::cerr.flush();
        throw std::runtime_error("Packet length mismatch");
    }

    DeserializedPacket packet;
    packet.size = size;
    packet.timestamp = be64toh(header.timestamp); //be64toh(header.timestamp); ?
    packet.stream_id = ntohl(header.stream_id);
    packet.frame_num = ntohl(header.frame_num);
    packet.payload.assign(data + HEADER_SIZE, data + HEADER_SIZE + size);
    if(DBG) std::cout << tsr << " [cpu_emu " << rcv_prt << "]: deserialized_packet: size = " << packet.size << " timestamp " << packet.timestamp << " stream_id " << packet.stream_id << " frame_num " << packet.frame_num << std::endl;

    return packet;
}

