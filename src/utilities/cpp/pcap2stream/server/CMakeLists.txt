cmake_minimum_required(VERSION 3.10)
project(stream_server VERSION 1.0)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# Find required packages
find_package(Threads REQUIRED)

# Add executable
add_executable(stream_server stream_server.cc)

# Link libraries
target_link_libraries(stream_server
    PRIVATE
    Threads::Threads
)

# Install
install(TARGETS stream_server
    RUNTIME DESTINATION bin
)