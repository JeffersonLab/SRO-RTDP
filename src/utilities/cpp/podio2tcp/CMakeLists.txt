cmake_minimum_required(VERSION 3.16)
cmake_policy(SET CMP0074 NEW)
project(podio2tcpProject)

# Optional: Set C++ standard
#set(CMAKE_CXX_STANDARD 17)
#set(CMAKE_CXX_STANDARD_REQUIRED ON)
#set(CMAKE_CXX_EXTENSIONS OFF)

find_package(ROOT REQUIRED)
find_package(cppzmq REQUIRED)
# Add this line to find SQLite. It should be included in the eic-shell.
#   (e.g.) -- Found SQLite3: /opt/local/include (found version "3.46.0")
find_package(SQLite3 REQUIRED)

# podio2tcp
add_executable(podio2tcp podio2tcp.cc)
target_include_directories(podio2tcp PRIVATE ${ROOT_INCLUDE_DIRS})
target_link_libraries(podio2tcp PRIVATE ${ROOT_LIBRARIES} cppzmq sqlite3)

# tcp2podio
add_executable(tcp2podio tcp2podio.cc)
target_include_directories(tcp2podio PRIVATE ${ROOT_INCLUDE_DIRS})
target_link_libraries(tcp2podio PRIVATE ${ROOT_LIBRARIES} cppzmq sqlite3)
