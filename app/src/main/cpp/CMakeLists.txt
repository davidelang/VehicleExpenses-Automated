cmake_minimum_required(VERSION 3.22.1)

project("native_components")

# 16KB ELF max-page-size for Android 15+ (all ABIs; critical for armeabi-v7a on NDK r28).
add_link_options(-Wl,-z,max-page-size=16384)

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

# Explicitly link against the Paddle Lite library provided in jniLibs
add_library(paddle_lite_jni SHARED IMPORTED)
set_target_properties(paddle_lite_jni PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libpaddle_lite_jni.so)

# x86 split build: thin jni + fat light_api_shared (kernels live in light)
set(PADDLE_LIGHT_SO ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libpaddle_light_api_shared.so)
if(EXISTS ${PADDLE_LIGHT_SO})
    add_library(paddle_light_api_shared SHARED IMPORTED)
    set_target_properties(paddle_light_api_shared PROPERTIES IMPORTED_LOCATION ${PADDLE_LIGHT_SO})
    set(PADDLE_EXTRA_LIBS paddle_light_api_shared)
else()
    set(PADDLE_EXTRA_LIBS "")
endif()

# 1. buffer_set library (Isolated memory management)
add_library(buffer_set SHARED BufferSet.cpp)
target_link_libraries(buffer_set jnigraphics log opencv_java4)

# 2. native_ocr library (OpenCV dependent, uses c++_shared)
add_library(native_ocr SHARED NativeImageUtils.cpp)
target_link_libraries(native_ocr
    jnigraphics
    log
    opencv_java4
    paddle_lite_jni
    ${PADDLE_EXTRA_LIBS}
    libraw_static
)

# Include directories
include_directories(include)
include_directories(${LIBRAW_PATH})
