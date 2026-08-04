#!/usr/bin/env bash
# OpenCV Android fat libopencv_java4.so (core+imgproc+imgcodecs), 16KB pages.
# Expects: third_party/opencv/src = git tree at lock pin (patches already applied).
# Does NOT apply patches. Creates writable src/build and src/bin only.
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
TP_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)
SRC=${OPENCV_SRC:-"$TP_ROOT/src"}
EXPECTED_SHA=${OPENCV_EXPECTED_SHA:-71d3237a093b60a27601c20e9ee6c3e52154e8b1}

[[ -d "$SRC/.git" || -f "$SRC/.git" ]] || {
  echo "ERROR: no git tree at $SRC — run: ./third_party/fetch-deps ro opencv" >&2
  exit 1
}
HEAD=$(git -C "$SRC" rev-parse HEAD)
if [[ "$HEAD" != "$EXPECTED_SHA" && "$HEAD" != "$EXPECTED_SHA"* ]]; then
  # allow short prefix match
  if [[ "${HEAD:0:12}" != "${EXPECTED_SHA:0:12}" ]]; then
    echo "WARNING: src HEAD=$HEAD expected $EXPECTED_SHA" >&2
  fi
fi

export ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk}
if ! command -v cmake >/dev/null 2>&1; then
  SDK_CMAKE=$(ls -d "$ANDROID_HOME/cmake"/*/bin 2>/dev/null | sort -V | tail -1 || true)
  [[ -n "${SDK_CMAKE:-}" ]] && export PATH="$SDK_CMAKE:$PATH"
fi
command -v cmake >/dev/null || { echo "ERROR: cmake not found" >&2; exit 1; }

NDK=${ANDROID_NDK_HOME:-}
if [[ -z "$NDK" || ! -d "$NDK" ]]; then
  NDK=$(ls -d "$ANDROID_HOME/ndk"/* 2>/dev/null | sort -V | tail -1 || true)
fi
[[ -d "$NDK" ]] || { echo "ERROR: NDK not found" >&2; exit 1; }
export ANDROID_NDK_HOME="$NDK"
export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}

# Portable ant if needed
ANT_BIN=$(command -v ant || true)
if [[ -z "$ANT_BIN" ]]; then
  ANT_CACHE="$TP_ROOT/.tools/apache-ant-1.10.14"
  if [[ ! -x "$ANT_CACHE/bin/ant" ]]; then
    mkdir -p "$TP_ROOT/.tools"
    curl -fsSL -o "$TP_ROOT/.tools/ant.tgz" \
      https://archive.apache.org/dist/ant/binaries/apache-ant-1.10.14-bin.tar.gz
    tar -xzf "$TP_ROOT/.tools/ant.tgz" -C "$TP_ROOT/.tools"
    mv "$TP_ROOT/.tools/apache-ant-1.10.14" "$ANT_CACHE"
  fi
  ANT_BIN=$ANT_CACHE/bin/ant
  export PATH="$(dirname "$ANT_BIN"):$PATH"
fi

# Writable build/bin under src (RO pin may need parent briefly writable)
ensure_dir_rw() {
  local d="$1"
  if mkdir -p "$d" 2>/dev/null && [[ -w "$d" ]]; then return 0; fi
  chmod u+w "$SRC" 2>/dev/null || true
  mkdir -p "$d"
  chmod u+w "$d"
}

ensure_dir_rw "$SRC/build"
ensure_dir_rw "$SRC/bin"

OPENCV_MODULES=${OPENCV_MODULES:-core,imgproc,imgcodecs,java,java_bindings_generator}
API_LEVEL=${ANDROID_API_LEVEL:-24}
PAGE_FLAGS="-Wl,-z,max-page-size=16384"
COMMON_C_FLAGS="-fdata-sections -ffunction-sections -fvisibility=hidden"
COMMON_LD_FLAGS="${PAGE_FLAGS} -Wl,--gc-sections -Wl,--as-needed"

if [[ -n "${OPENCV_ABIS:-}" ]]; then
  # shellcheck disable=SC2206
  ABIS=($OPENCV_ABIS)
else
  ABIS=(arm64-v8a x86_64)
fi

echo "OpenCV src=$SRC head=$(git -C "$SRC" rev-parse --short HEAD)"
echo "NDK=$NDK modules=$OPENCV_MODULES abis=${ABIS[*]}"

# Minimal patch: skip android_sdk subdir when BUILD_ANDROID_PROJECTS=OFF
# Applied only to a copy of CMakeLists in the build tree? Better: cmake patch via sed on a build-only file.
# We inject a one-line override by setting ANDROID_BUILD_BASE_DIR absolute and using a
# cmake -C or patch file in patches/ if needed. Prefer env CMAKE args first.

for ABI in "${ABIS[@]}"; do
  # OpenCV-style out-of-source binary dir under src/build
  BUILD_DIR="$SRC/build/$ABI"
  DEST_BIN="$SRC/bin/$ABI"
  ensure_dir_rw "$BUILD_DIR"
  ensure_dir_rw "$DEST_BIN"
  rm -rf "$BUILD_DIR"
  mkdir -p "$BUILD_DIR" "$DEST_BIN"

  case "$ABI" in
    arm64-v8a) EXTRA_C="$COMMON_C_FLAGS -O3" ;;
    x86_64)    EXTRA_C="$COMMON_C_FLAGS -O2 -mno-avx512f" ;;
    *)         EXTRA_C="$COMMON_C_FLAGS -O2" ;;
  esac

  ANDROID_BASE="$BUILD_DIR/opencv_android"
  mkdir -p "$ANDROID_BASE"

  echo "=== Configure $ABI ==="
  cmake -S "$SRC" -B "$BUILD_DIR" \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$ABI" \
    -DANDROID_PLATFORM="android-$API_LEVEL" \
    -DANDROID_STL=c++_shared \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_C_FLAGS_RELEASE="$EXTRA_C" \
    -DCMAKE_CXX_FLAGS_RELEASE="$EXTRA_C" \
    -DCMAKE_SHARED_LINKER_FLAGS_RELEASE="$COMMON_LD_FLAGS" \
    -DCMAKE_MODULE_LINKER_FLAGS_RELEASE="$COMMON_LD_FLAGS" \
    -DCMAKE_EXE_LINKER_FLAGS_RELEASE="$COMMON_LD_FLAGS" \
    -DBUILD_SHARED_LIBS=OFF \
    -DBUILD_FAT_JAVA_LIB=ON \
    -DBUILD_opencv_world=OFF \
    -DBUILD_opencv_java=ON \
    -DBUILD_ANDROID_PROJECTS=OFF \
    -DBUILD_ANDROID_EXAMPLES=OFF \
    -DBUILD_TESTS=OFF \
    -DBUILD_PERF_TESTS=OFF \
    -DBUILD_EXAMPLES=OFF \
    -DBUILD_DOCS=OFF \
    -DBUILD_opencv_apps=OFF \
    -DBUILD_LIST="$OPENCV_MODULES" \
    -DWITH_OPENCL=OFF \
    -DWITH_IPP=OFF \
    -DWITH_CUDA=OFF \
    -DWITH_ITT=OFF \
    -DWITH_PROTOBUF=OFF \
    -DWITH_QUIRC=OFF \
    -DWITH_TIFF=OFF \
    -DWITH_OPENEXR=OFF \
    -DWITH_IMGCODEC_HDR=OFF \
    -DWITH_IMGCODEC_SUNRASTER=OFF \
    -DWITH_IMGCODEC_PXM=OFF \
    -DWITH_IMGCODEC_PFM=OFF \
    -DANT_EXECUTABLE="$ANT_BIN" \
    -DJava_JAVA_EXECUTABLE="$JAVA_HOME/bin/java" \
    -DJava_JAVAC_EXECUTABLE="$JAVA_HOME/bin/javac" \
    -DANDROID_BUILD_BASE_DIR="$ANDROID_BASE" \
    -DINSTALL_CREATE_DISTRIB=ON

  echo "=== Build $ABI (opencv_java) ==="
  cmake --build "$BUILD_DIR" -j"$(nproc 2>/dev/null || echo 4)" --target opencv_java

  found=$(find "$BUILD_DIR" -name 'libopencv_java4.so' 2>/dev/null | head -1 || true)
  [[ -n "$found" ]] || found=$(find "$BUILD_DIR" -name 'libopencv_java*.so' 2>/dev/null | head -1 || true)
  [[ -n "$found" ]] || { echo "ERROR: no libopencv_java*.so for $ABI" >&2; exit 1; }

  cp -f "$found" "$DEST_BIN/libopencv_java4.so"
  STRIP="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
  [[ -x "$STRIP" ]] && "$STRIP" --strip-unneeded "$DEST_BIN/libopencv_java4.so" || true
  echo "OK $DEST_BIN/libopencv_java4.so ($(stat -c%s "$DEST_BIN/libopencv_java4.so") bytes)"
done

cat > "$SRC/bin/BUILD_INFO.txt" <<EOF
opencv_expected_sha=$EXPECTED_SHA
opencv_src_head=$(git -C "$SRC" rev-parse HEAD)
modules=$OPENCV_MODULES
abis=${ABIS[*]}
ndk=$NDK
page_flags=$PAGE_FLAGS
built_at=$(date -Iseconds)
EOF
echo "Done — products under $SRC/bin/<abi>/; run get-artifacts to fill artifact/"
