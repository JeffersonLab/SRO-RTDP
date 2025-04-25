// buffer_packet.hpp
#pragma once
#include <cstdint>
#include <cstring>
#include <arpa/inet.h>
#include <zmq.hpp>

struct BufferPacket {
    uint32_t size;
    double   timestamp;
    uint32_t stream_id;

    static constexpr size_t PACKET_SIZE = sizeof(uint32_t) + sizeof(double) + sizeof(uint32_t);

    void serialize(char* buffer) const {
        uint32_t size_be      = htonl(size);
        double timestamp_be   = timestamp; // htonl(timestamp); ????
        uint32_t stream_id_be = htonl(stream_id);
        std::memcpy(buffer,                                        &size_be,      sizeof(size_be));
        std::memcpy(buffer + sizeof(size_be),                      &timestamp_be, sizeof(timestamp_be));
        std::memcpy(buffer + sizeof(size_be)+sizeof(timestamp_be), &stream_id_be, sizeof(stream_id_be));
    }

    static BufferPacket deserialize(const char* buffer) {
        BufferPacket pkt;
        uint32_t size_be;
        double timestamp_be;
        uint32_t stream_id_be;
        std::memcpy(&size_be,      buffer,                                        sizeof(size_be));
        std::memcpy(&timestamp_be, buffer + sizeof(size_be),                      sizeof(timestamp_be));
        std::memcpy(&stream_id_be, buffer + sizeof(size_be)+sizeof(timestamp_be), sizeof(stream_id_be));        
        
        pkt.size      = ntohl(size_be);
        pkt.timestamp = timestamp_be; //ntohl(timestamp_be); ???
        pkt.stream_id = ntohl(stream_id_be);
        return pkt;
    }

    zmq::message_t to_message() const {
        zmq::message_t msg(PACKET_SIZE);
        serialize(static_cast<char*>(msg.data()));
        return msg;
    }

    static BufferPacket from_message(const zmq::message_t& msg) {
        return deserialize(static_cast<const char*>(msg.data()));
    }
};

