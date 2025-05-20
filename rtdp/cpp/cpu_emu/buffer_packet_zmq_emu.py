# buffer_packet_zmq_emu.py

# We'll define a structure that works in both C++ and Python by using a flat binary format with a 4-byte size header.
# This creates a binary buffer with the size encoded up front—C++ can use the same format with htonl() and memcpy().


import struct
import time
import ctypes

# Struct format: ! = network byte order, I = uint64, d = double
PACKET_FORMAT = "!IQI"  # uint32 (size), uint64 (timestamp), uint32 (stream_id)
PACKET_SIZE = struct.calcsize(PACKET_FORMAT)

def serialize_buffer(size: ctypes.c_uint32, timestamp: ctypes.c_uint64, stream_id: ctypes.c_uint32) -> bytes:
    return struct.pack(PACKET_FORMAT, size, timestamp, stream_id)

def deserialize_buffer(data: bytes):
    if len(data) != PACKET_SIZE:
        raise ValueError("Invalid packet size")
    size, timestamp, stream_id = struct.unpack(PACKET_FORMAT, data)
    return {
        "size": size,
        "timestamp": timestamp,
        "stream_id": stream_id
    }
