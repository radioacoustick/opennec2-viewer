# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (c) 2026 Valery Kustarev (https://github.com/radioacoustick)
#
# This file is part of nec2core engine for android app.
#
# Nec2core is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# Nec2core is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with Nec2core. If not, see <https://www.gnu.org/licenses/>.


cmake_minimum_required(VERSION 3.22.1)

project("nec2core")

# Setting flags to enable debugging
set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG} -O0 -g")
# Flags for Release Build (Max Speed + LTO)
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O3 -flto")

# Prevents the crashing if some paths are temporarily empty.
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# =========================================================================
# Building the nec2++ library
# =========================================================================

# Explicitly listing the required nec2++ source files
set(NECPP_SOURCES
        "../../../../external/necpp/src/c_geometry.cpp"
        "../../../../external/necpp/src/c_plot_card.cpp"
        "../../../../external/necpp/src/nec_context.cpp"
        "../../../../external/necpp/src/nec_exception.cpp"
        "../../../../external/necpp/src/nec_ground.cpp"
        "../../../../external/necpp/src/nec_output.cpp"
        "../../../../external/necpp/src/nec_radiation_pattern.cpp"
        "../../../../external/necpp/src/nec_results.cpp"
        "../../../../external/necpp/src/nec_structure_currents.cpp"
        "../../../../external/necpp/src/nec_wire.h"
        "../../../../external/necpp/src/misc.cpp"
        "../../../../external/necpp/src/electromag.cpp"
        "../../../../external/necpp/src/matrix_algebra.cpp"
        "../../../../external/necpp/src/c_evlcom.cpp"
        "../../../../external/necpp/src/c_ggrid.cpp"
)

# Declaring the build of the nec2++ static library
add_library(necpp STATIC ${NECPP_SOURCES})

# Specifying paths to header files for required source files
target_include_directories(necpp PUBLIC
        "${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/necpp/src"
        "${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/necpp/src/eigen"
        "${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/nlohmann"
        "${CMAKE_CURRENT_SOURCE_DIR}/include"
)

# =========================================================================
# Building a JNI interface for Android
# =========================================================================

# Declaring a SHARED library that Android will load via System.loadLibrary
add_library(nec2core SHARED
        "nec2core_jni.cpp"
        "nec_analyzers.cpp"
        "nec_parser.cpp"
        "nec2core_exception.cpp"
)

# Pointing the JNI library where to look for the header files for required source files
target_include_directories(nec2core PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/necpp/src"
        "${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/necpp/src/eigen"
        "${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/nlohmann"
)

# Linking everything together
# necpp — static nec2++ core
# log — Android system library for logging (__android_log_print)
target_link_libraries(nec2core
        necpp
        log
)

target_link_options(${PROJECT_NAME} PRIVATE "-Wl,-z,max-page-size=16384")