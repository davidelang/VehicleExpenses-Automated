cmake_minimum_required(VERSION 3.22.1)

project("native_components")

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

# 1. buffer_set library (Isolated memory management)
add_library(buffer_set SHARED BufferSet.cpp)
target_link_libraries(buffer_set jnigraphics log)

# 2. native_ocr library (OpenCV dependent, uses c++_shared)
add_library(native_ocr SHARED NativeImageUtils.cpp)
target_link_libraries(native_ocr
    jnigraphics
    log
    opencv_java4
    libraw_static
)

# Include directories
include_directories(include)
include_directories(${LIBRAW_PATH})
