// buffer_packet.hpp
#pragma once
#include <cstdint>
#include <cstring>
#include <arpa/inet.h>
#include <zmq.hpp>

struct BufferPacket {
    size_t size;
    double timestamp;
    uint32_t stream_id;

    static constexpr size_t PACKET_SIZE = sizeof(size_t) + sizeof(double) + sizeof(uint32_t);

    void serialize(char* buffer) const {
        uint32_t stream_id_be = htonl(stream_id);
        std::memcpy(buffer, &size, sizeof(size));
        std::memcpy(buffer + sizeof(size), &timestamp, sizeof(timestamp));
        std::memcpy(buffer + sizeof(size)+sizeof(timestamp), &stream_id_be, sizeof(stream_id_be));
    }

    static BufferPacket deserialize(const char* buffer) {
        BufferPacket pkt;
        uint32_t stream_id_be;
        std::memcpy(&pkt.size, buffer, sizeof(size));
        std::memcpy(&pkt.timestamp, buffer + sizeof(size), sizeof(timestamp));
        std::memcpy(&stream_id_be, buffer + sizeof(size)+sizeof(timestamp), sizeof(stream_id_be));
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

