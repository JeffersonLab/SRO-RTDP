cmake_minimum_required(VERSION 3.12)

project(SRO-RTDP)

# Define the source files
set(SOURCES rtdp/python/rtdp.py)

# Set the installation directory for the script
install(PROGRAMS ${SOURCES} DESTINATION bin
        PERMISSIONS OWNER_EXECUTE OWNER_WRITE OWNER_READ
                    GROUP_EXECUTE GROUP_READ
                    WORLD_EXECUTE WORLD_READ)
