cmake_minimum_required(VERSION 3.22.1)

project("opencv_bridge")

# Look for OpenCV package (this usually works if the OpenCV Android SDK is correctly integrated)
# But since we have the .so files in the build artifacts, we might need a different approach 
# if find_package fails. However, standard OpenCV Android implementation provides a config.

# We'll use the headers from the Paddle-Lite build as a fallback if needed, 
# but let's try to link against the OpenCV provided by the implementation.
# The `opencv` implementation in build.gradle usually includes native libs.

add_library(opencv_bridge SHARED OpenCvBridge.cpp)

# Link against Android NDK libraries
target_link_libraries(opencv_bridge
    jnigraphics
    log
    opencv_java4 # This links against the .so provided by the opencv implementation
)
