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

# Prebuilts under jniLibs/. Always set IMPORTED_SONAME so consumers get a
# basename DT_NEEDED. Thin x86 libpaddle_lite_jni.so historically had *no*
# ELF SONAME; without this, lld embeds the host absolute IMPORTED_LOCATION
# and the APK fails dlopen on device:
#   UnsatisfiedLinkError: library "/home/.../libpaddle_lite_jni.so" not found
# → NativeImageUtils class load fails → every multi-scale cell is ingest_fail
# in ~1 minute (looks like an impossibly fast "done" run).
set(JNI_LIBS_DIR ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

# IMPORTED_NO_SONAME + -L: link with -lname so DT_NEEDED is the basename even
# when the prebuilt ELF has no SONAME (x86 thin jni). IMPORTED_SONAME alone is
# not enough for lld when the input .so lacks DT_SONAME.
add_library(opencv_java4 SHARED IMPORTED)
set_target_properties(opencv_java4 PROPERTIES
    IMPORTED_LOCATION ${JNI_LIBS_DIR}/libopencv_java4.so
    IMPORTED_SONAME "libopencv_java4.so"
    IMPORTED_NO_SONAME TRUE
)

add_library(paddle_lite_jni SHARED IMPORTED)
set_target_properties(paddle_lite_jni PROPERTIES
    IMPORTED_LOCATION ${JNI_LIBS_DIR}/libpaddle_lite_jni.so
    IMPORTED_SONAME "libpaddle_lite_jni.so"
    IMPORTED_NO_SONAME TRUE
)

# x86 split: thin jni + fat light_api_shared. Arm fat jni is monolib.
set(PADDLE_LIGHT_SO ${JNI_LIBS_DIR}/libpaddle_light_api_shared.so)
if(EXISTS ${PADDLE_LIGHT_SO})
    add_library(paddle_light_api_shared SHARED IMPORTED)
    set_target_properties(paddle_light_api_shared PROPERTIES
        IMPORTED_LOCATION ${PADDLE_LIGHT_SO}
        IMPORTED_SONAME "libpaddle_light_api_shared.so"
        IMPORTED_NO_SONAME TRUE
    )
    set(PADDLE_EXTRA_LIBS paddle_light_api_shared)
else()
    set(PADDLE_EXTRA_LIBS "")
endif()

# 1. buffer_set library (Isolated memory management)
add_library(buffer_set SHARED BufferSet.cpp)
target_link_directories(buffer_set PRIVATE ${JNI_LIBS_DIR})
target_link_libraries(buffer_set jnigraphics log opencv_java4)

# 2. native_ocr library (OpenCV dependent, uses c++_shared)
add_library(native_ocr SHARED NativeImageUtils.cpp ContentExpandNative.cpp)
target_link_directories(native_ocr PRIVATE ${JNI_LIBS_DIR})
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
