##
# cmake/FindYAML.cmake
# Find the C libyaml (NOT yaml-cpp). Provides imported target: YAML::YAML
# Variables:
#   YAML_FOUND
#   YAML_INCLUDE_DIRS
#   YAML_LIBRARIES
#   YAML_VERSION (if available)
#
# Author: xmei@jlab.org, GPT
# Checked-in: Sep/23/2025
# Last updated: Sep/23/2025
##

find_package(PkgConfig QUIET)
if(PkgConfig_FOUND)
  pkg_check_modules(PC_YAML yaml-0.1 QUIET)
endif()

set(YAML_INCLUDE_DIRS "")
set(YAML_LIBRARIES   "")
set(YAML_VERSION     "")

if(PC_YAML_FOUND)
  # Some distros put yaml.h in /usr/include and pkg-config emits no -I
  set(YAML_INCLUDE_DIRS ${PC_YAML_INCLUDE_DIRS})
  set(YAML_LIBRARIES    ${PC_YAML_LIBRARIES})
  if(PC_YAML_VERSION)
    set(YAML_VERSION ${PC_YAML_VERSION})
  endif()

  # If include dirs are empty, locate yaml.h explicitly
  if(NOT YAML_INCLUDE_DIRS)
    find_path(YAML_INCLUDE_DIR
      NAMES yaml.h
      PATHS /usr/include /usr/local/include
    )
    if(YAML_INCLUDE_DIR)
      set(YAML_INCLUDE_DIRS "${YAML_INCLUDE_DIR}")
    endif()
  endif()

  # If libraries are empty (rare), resolve to actual lib path
  if(NOT YAML_LIBRARIES)
    find_library(YAML_LIBRARY NAMES yaml libyaml)
    if(YAML_LIBRARY)
      set(YAML_LIBRARIES "${YAML_LIBRARY}")
    endif()
  endif()
else()
  # Fallback search without pkg-config
  find_path(YAML_INCLUDE_DIR NAMES yaml.h)
  find_library(YAML_LIBRARY   NAMES yaml libyaml)
  set(YAML_INCLUDE_DIRS ${YAML_INCLUDE_DIR})
  set(YAML_LIBRARIES    ${YAML_LIBRARY})
endif()

include(FindPackageHandleStandardArgs)
find_package_handle_standard_args(YAML
  REQUIRED_VARS YAML_INCLUDE_DIRS YAML_LIBRARIES
  VERSION_VAR  YAML_VERSION
)

if(YAML_FOUND AND NOT TARGET YAML::YAML)
  if(PC_YAML_FOUND AND YAML_LIBRARIES AND NOT YAML_LIBRARY)
    # pkg-config case: expose flags via INTERFACE
    add_library(YAML::YAML INTERFACE IMPORTED)
    set_target_properties(YAML::YAML PROPERTIES
      INTERFACE_INCLUDE_DIRECTORIES "${YAML_INCLUDE_DIRS}"
      INTERFACE_LINK_LIBRARIES      "${YAML_LIBRARIES}"
    )
  else()
    # path-resolved library
    add_library(YAML::YAML UNKNOWN IMPORTED)
    set_target_properties(YAML::YAML PROPERTIES
      IMPORTED_LOCATION             "${YAML_LIBRARY}"
      INTERFACE_INCLUDE_DIRECTORIES "${YAML_INCLUDE_DIRS}"
    )
  endif()
endif()
# ============================================================
# End of FindYAML.cmake
