#!/usr/bin/env bash
# Historical VE full-file patch set (build-system + external + code).
# Mounted at /patches in the Docker build. Restores the Jul-2026 working recipe.
set -euo pipefail
: "${PATCHES:=/patches}"

echo "Applying Build System Patches from $PATCHES ..."
cp "$PATCHES/build-system/android.cmake" cmake/os/android.cmake
cp "$PATCHES/build-system/common_os.cmake" cmake/os/common.cmake
cp "$PATCHES/build-system/mkldnn.cmake" cmake/external/mkldnn.cmake
cp "$PATCHES/build-system/cblas.cmake" cmake/cblas.cmake
cp "$PATCHES/build-system/build_linux.sh" lite/tools/build_linux.sh
cp "$PATCHES/build-system/copy_libs.cmake" lite/tools/copy_libs.cmake
cp "$PATCHES/build-system/common.cmake" cmake/backends/common.cmake
cp "$PATCHES/build-system/configure.cmake" cmake/configure.cmake
cp "$PATCHES/build-system/CMakeLists.txt" CMakeLists.txt
cp "$PATCHES/build-system/TryRunResults.cmake" TryRunResults.cmake
cp "$PATCHES/build-system/simd.cmake" cmake/simd.cmake
cp "$PATCHES/build-system/postproject.cmake" cmake/postproject.cmake
cp "$PATCHES/build-system/lite_CMakeLists.txt" lite/CMakeLists.txt
cp "$PATCHES/build-system/utils_CMakeLists.txt" lite/utils/CMakeLists.txt
cp "$PATCHES/build-system/build_android.sh" lite/tools/build_android.sh
cp "$PATCHES/build-system/api_CMakeLists.txt" lite/api/CMakeLists.txt
cp "$PATCHES/build-system/android_api_CMakeLists.txt" lite/api/android/CMakeLists.txt
cp "$PATCHES/build-system/x86_CMakeLists.txt" lite/backends/x86/CMakeLists.txt
cp "$PATCHES/build-system/model_parser_CMakeLists.txt" lite/model_parser/CMakeLists.txt
cp "$PATCHES/build-system/jni_CMakeLists.txt" lite/api/android/jni/CMakeLists.txt

echo "Applying External Dependency Patches..."
cp "$PATCHES/external/openblas.cmake" cmake/external/openblas.cmake
cp "$PATCHES/external/glog.cmake" cmake/external/glog.cmake
cp "$PATCHES/external/gflags.cmake" cmake/external/gflags.cmake

echo "Applying Source Code Patches..."
cp -r "$PATCHES/code/lite/." lite/

echo "All full-file patches applied successfully."
