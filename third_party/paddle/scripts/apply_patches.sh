#!/usr/bin/env bash
# Full-file overlays that differ from pin c6a9b9ada00e3b37aa80c21407e7d8e240595ebb.
# Mounted at /patches in the Docker build. Files identical to that pin are not copied.
set -euo pipefail
: "${PATCHES:=/patches}"

echo "Applying Build System Patches from $PATCHES ..."
cp "$PATCHES/build-system/android.cmake" cmake/os/android.cmake
cp "$PATCHES/build-system/build_linux.sh" lite/tools/build_linux.sh
cp "$PATCHES/build-system/configure.cmake" cmake/configure.cmake
cp "$PATCHES/build-system/CMakeLists.txt" CMakeLists.txt
cp "$PATCHES/build-system/postproject.cmake" cmake/postproject.cmake
cp "$PATCHES/build-system/lite_CMakeLists.txt" lite/CMakeLists.txt
cp "$PATCHES/build-system/api_CMakeLists.txt" lite/api/CMakeLists.txt

echo "Applying External Dependency Patches..."
cp "$PATCHES/external/openblas.cmake" cmake/external/openblas.cmake
cp "$PATCHES/external/glog.cmake" cmake/external/glog.cmake
cp "$PATCHES/external/gflags.cmake" cmake/external/gflags.cmake

echo "Applying Source Code Patches..."
cp -r "$PATCHES/code/lite/." lite/

echo "All full-file patches applied successfully."
