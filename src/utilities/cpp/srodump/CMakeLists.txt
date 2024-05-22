cmake_minimum_required(VERSION 3.16)
cmake_policy(SET CMP0074 NEW)

project(srodump)

set(CMAKE_CXX_STANDARD 20)

find_package(Threads REQUIRED)
find_package(evio REQUIRED)

# Find all .c and .cc files in the current directory
file(GLOB SOURCES "*.c" "*.cc")

# Create the executable
add_executable(srodump ${SOURCES})

include_directories($ENV{evio_ROOT}/include)
target_link_libraries(srodump PRIVATE evio evio++ Threads::Threads)
