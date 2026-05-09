cmake_minimum_required(VERSION 3.22.1)

project("memory_bridge")

# Explicitly link against the OpenCV library provided in jniLibs
add_library(opencv_java4 SHARED IMPORTED)
set_target_properties(opencv_java4 PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libopencv_java4.so)

# Create the Memory Bridge library
add_library(memory_bridge SHARED MemoryBridge.cpp)

# Include OpenCV headers from the broken-dev-2 branch sync or standard locations
# Note: For now we assume they are in include/
include_directories(include)

# Link against Android NDK libraries and OpenCV
target_link_libraries(memory_bridge
    jnigraphics
    log
    opencv_java4
)
