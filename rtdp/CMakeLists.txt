##
# Build and compile rtdp emulation components.
#
# Author: xmei@jlab.org, GPT
# Checked-in: Sep/23/2025
# Last updated: Sep/25/2025
##

cmake_minimum_required(VERSION 3.16)

cmake_policy(SET CMP0074 NEW)
cmake_policy(SET CMP0104 NEW)  # for setting CUDA_ARCHITECTURES

project(RTDP LANGUAGES CXX)

# Require modern C++.
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Access custom find-modules under cmake/
list(APPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake")
# (Optional) helps pkg-config respect CMAKE_PREFIX_PATH
set(PKG_CONFIG_USE_CMAKE_PREFIX_PATH ON)

# ============================================================
# A) C++ Dependencies: Threads, YAML, ZeroMQ
#    (YAML and ZeroMQ are provided by cmake/Findxxx.cmake)
# ============================================================
find_package(Threads REQUIRED)

find_package(YAML   REQUIRED)      # provides YAML::YAML (via our FindYAML.cmake)
message(STATUS "YAML version: ${YAML_VERSION}")
message(STATUS "YAML include dirs: ${YAML_INCLUDE_DIRS}")
message(STATUS "YAML libraries   : ${YAML_LIBRARIES}")

find_package(ZeroMQ REQUIRED)      # provides ZeroMQ::ZeroMQ ZeroMQ::cppzmq (via our FindZeroMQ.cmake)
message(STATUS "ZeroMQ version: ${ZeroMQ_VERSION}")
message(STATUS "ZeroMQ include: ${ZeroMQ_INCLUDE_DIRS}")
message(STATUS "ZeroMQ library: ${ZeroMQ_LIBRARIES}")
message(STATUS "zmq.hpp path: ${ZeroMQ_CPPZMQ_INCLUDE_DIR}")

# ============================================================
# B) CPU executables
# ============================================================
set(CPU_EMU_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/src/cpu_emu")

add_executable(cpu_emu
  "${CPU_EMU_SOURCE_DIR}/cpu_emu.cc"
)

target_include_directories(cpu_emu PRIVATE
  "${CPU_EMU_SOURCE_DIR}"
  "${CPPZMQ_INCLUDE_DIR}"
)

target_link_libraries(cpu_emu
  PRIVATE
    Threads::Threads
    YAML::YAML
    ZeroMQ::ZeroMQ
    ZeroMQ::cppzmq
)

set_target_properties(cpu_emu PROPERTIES
  RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/bin"
)

# Sender utility: zmq-event-emu-clnt
add_executable(zmq-event-emu-clnt
  "${CPU_EMU_SOURCE_DIR}/zmq-event-emu-clnt.cc"
)

target_include_directories(zmq-event-emu-clnt PRIVATE
  "${CPU_EMU_SOURCE_DIR}"
  "${CPPZMQ_INCLUDE_DIR}"
)

target_link_libraries(zmq-event-emu-clnt
  PRIVATE
    YAML::YAML
    Threads::Threads
    ZeroMQ::ZeroMQ
    ZeroMQ::cppzmq
)

set_target_properties(zmq-event-emu-clnt PROPERTIES
  RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/bin"
)

include(GNUInstallDirs)
install(TARGETS cpu_emu RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR})
install(TARGETS zmq-event-emu-clnt RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR})

# ============================================================
# C) Optional GPU compilation (gpu_emu)
# ============================================================
find_package(CUDAToolkit QUIET)
if(CUDAToolkit_FOUND)
  enable_language(CUDA)

  set(GPU_PROXY_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/src/gpu_proxy")

  # Allow user override: -DCMAKE_CUDA_ARCHITECTURES="75;80;86"
  if(NOT CMAKE_CUDA_ARCHITECTURES)
    set(CMAKE_CUDA_ARCHITECTURES "75;80;86")
  endif()

  add_executable(gpu_emu
    "${GPU_PROXY_SOURCE_DIR}/gpuEmu.cu"
  )

  target_include_directories(gpu_emu PRIVATE
    "${GPU_PROXY_SOURCE_DIR}"
    "${CPPZMQ_INCLUDE_DIR}"
    ${CUDAToolkit_INCLUDE_DIRS}
  )

  target_link_libraries(gpu_emu
    PRIVATE
      CUDA::cudart
      CUDA::cublas
      CUDA::curand
      ZeroMQ::ZeroMQ
      ZeroMQ::cppzmq
  )

  set_target_properties(gpu_emu PROPERTIES
    CUDA_SEPARABLE_COMPILATION ON
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/bin"
  )

  install(TARGETS gpu_emu DESTINATION bin)
else()
  message(STATUS "CUDA toolkit not found; gpu_proxy target will not be built.")
endif()

# ============================================================
# D) Install some helper scripts
# ============================================================
# TODO: check which other files can be installed.
include(GNUInstallDirs)

# 1) Python helpers (as executables, preserve +x)
file(GLOB PY_HELPERS "${CMAKE_CURRENT_SOURCE_DIR}/src/python_zmq_helper/*.py")
install(PROGRAMS ${PY_HELPERS} DESTINATION ${CMAKE_INSTALL_BINDIR})

# 2) Bash helpers (as executables, preserve +x)
file(GLOB BASH_HELPERS "${CMAKE_CURRENT_SOURCE_DIR}/src/*.bash")
install(PROGRAMS ${BASH_HELPERS} DESTINATION ${CMAKE_INSTALL_BINDIR})
