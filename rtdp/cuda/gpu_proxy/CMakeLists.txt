cmake_minimum_required(VERSION 3.16)
cmake_policy(SET CMP0074 NEW)
cmake_policy(SET CMP0104 NEW)  # for setting CUDA_ARCHITECTURES
project(GPUEmu LANGUAGES CXX CUDA)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# TODO: update this part for the future GPU-enabled eic-shell.
# find_package(cppzmq REQUIRED)   # holds only within eic-shell not native JLab ifarm env

# Find ZeroMQ library
# The header files are added manually by wget
find_library(ZMQ_LIBRARY NAMES zmq HINTS /lib64 /usr/lib64 /usr/local/lib64)
# If libzmq is not found, provide a manual path (Adjust if needed)
if (NOT ZMQ_LIBRARY)
    set(ZMQ_LIBRARY "/lib64/libzmq.so.5")   # match `ldconfig -p | grep zmq` results
endif()
# Include directories for ZeroMQ (manual header location)
include_directories(${CMAKE_SOURCE_DIR}/external/zmq)  # path to zmq.hpp, zmq.h

find_package(SQLite3 REQUIRED)

# Enable CUDA
find_package(CUDAToolkit REQUIRED)

# Set CUDA architecture (Turing/Ampere+ GPUs)
set(CMAKE_CUDA_ARCHITECTURES "75;80;86")  # 75 (Turing), 80 (A800/A100), 86 (RTX 30xx)

# Include CUDA and library paths
include_directories(${CUDAToolkit_INCLUDE_DIRS})
link_directories(${CUDAToolkit_LIBRARY_DIR})

# Add GPU executable
add_executable(gpu_emu gpuEmu.cu)

# Ensure CUDA compilation
set_target_properties(gpu_emu PROPERTIES CUDA_SEPARABLE_COMPILATION ON)

# Link necessary libraries (CUDA, ZeroMQ, SQLite)
target_link_libraries(gpu_emu CUDA::cudart CUDA::cublas CUDA::curand ${ZMQ_LIBRARY} ${SQLite3_LIBRARIES})

# Install 
install(TARGETS gpu_emu DESTINATION bin)
install(DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}/python_zmq_helper/" DESTINATION bin FILES_MATCHING PATTERN "*.py")
