cmake_minimum_required(VERSION 3.10)
project(pcap2stream VERSION 1.0)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# Find required packages
find_package(Threads REQUIRED)
find_library(PCAP_LIBRARY pcap)

if(NOT PCAP_LIBRARY)
    message(FATAL_ERROR "libpcap not found")
endif()

# Add executable
add_executable(pcap2stream pcap2stream.cc)

# Link libraries
target_link_libraries(pcap2stream
    PRIVATE
    ${PCAP_LIBRARY}
    Threads::Threads
)

# Install
install(TARGETS pcap2stream
    RUNTIME DESTINATION bin
) 