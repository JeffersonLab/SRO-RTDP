cmake_minimum_required(VERSION 3.16)
project(EvioSplitROCProject)

# Optional: Set C++ standard
set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

find_package(evio 4.3 REQUIRED)

# Define the executable
add_executable(evioSplitRoc evioSplitROC.cc)
add_executable(sendEvio_v2 sendEvio_v2.cc)

# Link against the evio and evio++ libraries
# target_link_libraries(evioSplitRoc PRIVATE evio::evio evio::evio++)

target_link_libraries(evioSplitRoc evio evio++)
target_link_libraries(sendEvio_v2 evio evio++)