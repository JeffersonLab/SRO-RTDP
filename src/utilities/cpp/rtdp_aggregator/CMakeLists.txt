cmake_minimum_required(VERSION 3.16)

# Set the project name and version
project(RTDP VERSION 1.0)

# C++ standard
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED True)

# Find the ZeroMQ library
find_package(cppzmq)
find_package(fmt)

# Add ROC
add_executable(rtdp_roc rtdp_roc.cc)
target_link_libraries(rtdp_roc cppzmq fmt::fmt)

# Add data concentrator
add_executable(rtdp_dc rtdp_dc.cc)
target_link_libraries(rtdp_dc cppzmq fmt::fmt)

# Add event recorder
add_executable(rtdp_er rtdp_er.cc)
target_link_libraries(rtdp_er cppzmq fmt::fmt)
