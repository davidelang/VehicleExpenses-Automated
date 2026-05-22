cmake_minimum_required(VERSION 3.22.1)

project("memory_bridge")

# LibRaw Configuration
set(LIBRAW_PATH ${CMAKE_SOURCE_DIR}/libraw)
add_definitions(-DLIBRAW_NOTHREADS -DLIBRAW_NODISPLAY)

# Collect LibRaw sources
file(GLOB_RECURSE LIBRAW_SOURCES "${LIBRAW_PATH}/src/*.cpp")

# Build LibRaw as a static library
add_library(libraw_static STATIC ${LIBRAW_SOURCES})
target_include_directories(libraw_static PUBLIC ${LIBRAW_PATH})

# Explicitly link against the OpenCV library provided in jniLibs
add_library(opencv_java4 SHARED IMPORTED)
set_target_properties(opencv_java4 PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libopencv_java4.so)

# Create the Memory Bridge library
add_library(memory_bridge SHARED 
    NativeImageUtils.cpp
    BufferSet.cpp
)

# Include directories
include_directories(include)
include_directories(${LIBRAW_PATH})

# Link against Android NDK libraries, OpenCV, and LibRaw
target_link_libraries(memory_bridge
    jnigraphics
    log
    opencv_java4
    libraw_static
)
