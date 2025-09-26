##
# - Find ZeroMQ (libzmq) and optional cppzmq (header-only C++ bindings).
#
# This module locates the ZeroMQ C library (libzmq) and, if available,
# the C++ header-only binding (zmq.hpp, known as cppzmq). If the system
# does not provide cppzmq, it falls back to a vendored copy in
#   ${CMAKE_CURRENT_LIST_DIR}/../external/zmq
#
# Imported Targets:
#   ZeroMQ::ZeroMQ   - The C library target for libzmq (link this)
#   ZeroMQ::cppzmq   - INTERFACE target for cppzmq headers (use if you #include <zmq.hpp>)
#
# Result Variables:
#   ZeroMQ_FOUND              - TRUE if libzmq was found
#   ZeroMQ_VERSION            - Version string (from pkg-config if available)
#   ZeroMQ_INCLUDE_DIR        - Directory containing zmq.h
#   ZeroMQ_LIBRARY            - Path to libzmq.so / libzmq.a
#   ZeroMQ_LIBRARIES          - Same as ZeroMQ_LIBRARY
#   ZeroMQ_INCLUDE_DIRS       - Same as ZeroMQ_INCLUDE_DIR
#   ZeroMQ_CPPZMQ_INCLUDE_DIR - Directory containing zmq.hpp (system or vendored)
#
# Example Usage:
#   find_package(ZeroMQ REQUIRED)
#   add_executable(my_app main.cc)
#   target_link_libraries(my_app PRIVATE ZeroMQ::ZeroMQ ZeroMQ::cppzmq)
#
# Notes:
#   - Use ZeroMQ::ZeroMQ whenever you link against libzmq.
#   - Use ZeroMQ::cppzmq if your code includes zmq.hpp (C++ binding).
#   - If you only use the C API (#include <zmq.h>), you don’t need ZeroMQ::cppzmq.
#
# Author: GPT
# Checked-in: Sep/23/2025
# Updated: Sep/23/2025
##

# --- libzmq (C library) ---
find_package(PkgConfig QUIET)
if(PkgConfig_FOUND)
  pkg_check_modules(PC_ZMQ libzmq QUIET)
endif()

find_path(ZeroMQ_INCLUDE_DIR
  NAMES zmq.h
  HINTS ${PC_ZMQ_INCLUDE_DIRS}
)

find_library(ZeroMQ_LIBRARY
  NAMES zmq libzmq
  HINTS ${PC_ZMQ_LIBRARY_DIRS}
)

set(ZeroMQ_VERSION "${PC_ZMQ_VERSION}")

include(FindPackageHandleStandardArgs)
find_package_handle_standard_args(ZeroMQ
  REQUIRED_VARS ZeroMQ_LIBRARY ZeroMQ_INCLUDE_DIR
  VERSION_VAR ZeroMQ_VERSION
)

if(ZeroMQ_FOUND)
  set(ZeroMQ_LIBRARIES ${ZeroMQ_LIBRARY})
  set(ZeroMQ_INCLUDE_DIRS ${ZeroMQ_INCLUDE_DIR})

  if(NOT TARGET ZeroMQ::ZeroMQ)
    add_library(ZeroMQ::ZeroMQ UNKNOWN IMPORTED)
    set_target_properties(ZeroMQ::ZeroMQ PROPERTIES
      IMPORTED_LOCATION "${ZeroMQ_LIBRARY}"
      INTERFACE_INCLUDE_DIRECTORIES "${ZeroMQ_INCLUDE_DIR}"
    )
  endif()
endif()

# --- cppzmq (header-only) ---
set(_vendored_cppzmq "${CMAKE_CURRENT_LIST_DIR}/../external/zmq")

find_path(CPPZMQ_INCLUDE_DIR
  NAMES zmq.hpp
  HINTS
    "${_vendored_cppzmq}"
    /usr/include /usr/local/include
    /usr/include/cppzmq /usr/local/include/cppzmq
    /usr/include/zmq     /usr/local/include/zmq
)

if(NOT CPPZMQ_INCLUDE_DIR OR NOT EXISTS "${CPPZMQ_INCLUDE_DIR}/zmq.hpp")
  message(STATUS "cppzmq (zmq.hpp) not found in system paths; using vendored: ${_vendored_cppzmq}")
  set(CPPZMQ_INCLUDE_DIR "${_vendored_cppzmq}")
endif()

# Export variable so projects can add it
set(ZeroMQ_CPPZMQ_INCLUDE_DIR "${CPPZMQ_INCLUDE_DIR}" CACHE PATH "cppzmq include dir")

# Convenience target
if(NOT TARGET ZeroMQ::cppzmq)
  add_library(ZeroMQ::cppzmq INTERFACE IMPORTED)
  set_target_properties(ZeroMQ::cppzmq PROPERTIES
    INTERFACE_INCLUDE_DIRECTORIES "${ZeroMQ_CPPZMQ_INCLUDE_DIR}"
  )
endif()
