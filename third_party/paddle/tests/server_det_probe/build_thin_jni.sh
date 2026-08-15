#!/usr/bin/env bash
# Rebuild ONLY the JNI wrapper (paddle_lite_jni + tensor_jni) against the existing
# product libpaddle_light_api_shared.so. Avoids multi-hour full Lite Docker builds.
#
# Output: out/thin_jni/<abi>/libpaddle_lite_jni.so  (DT_NEEDED light_api)
# Install: cp into app/src/main/jniLibs/<abi>/ (keep light SO next to it).
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
VE_ROOT=$(cd "$HERE/../../../.." && pwd)
OUT="${OUT:-$HERE/out/thin_jni}"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK:-}}"
if [[ -z "$NDK" || ! -d "$NDK" ]]; then
  NDK=$(ls -d "$HOME/Android/Sdk/ndk"/* 2>/dev/null | sort -V | tail -1 || true)
fi
[[ -n "$NDK" && -d "$NDK" ]] || { echo "ERROR: ANDROID_NDK_HOME" >&2; exit 1; }
PRE="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
API="${PADDLE_PROBE_API:-26}"

PADDLE_SRC="$VE_ROOT/third_party/paddle/src"
JNI_NATIVE="$PADDLE_SRC/lite/api/android/jni/native"
# Public API headers used by convert_util / light_api includes
INC_PUB="$VE_ROOT/app/src/main/cpp/include/paddle"
# Full tree for "lite/api/..." includes in convert_util
INC_SRC="$PADDLE_SRC"

if [[ -d "$VE_ROOT/third_party/paddle/artifact/jni" ]]; then
  LIGHT_ROOT="$VE_ROOT/third_party/paddle/artifact/jni"
else
  LIGHT_ROOT="$VE_ROOT/app/src/main/jniLibs"
fi

# Define LITE_ON_TINY_PUBLISH so CxxConfig path is stubbed (product is tiny publish)
CXXFLAGS=(
  -std=c++17 -O2 -fPIC -fvisibility=hidden
  -DLITE_ON_TINY_PUBLISH
  -DLITE_WITH_ARM
  -DLITE_WITH_JAVA
  -I"$JNI_NATIVE"
  -I"$INC_PUB"
  -I"$INC_SRC"
  -I"$PADDLE_SRC/lite/api/android/jni"
  -I"$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include"
)

build_abi() {
  local abi=$1 cxx=$2 triple=$3
  local light="$LIGHT_ROOT/$abi/libpaddle_light_api_shared.so"
  [[ -f "$light" ]] || light="$VE_ROOT/app/src/main/jniLibs/$abi/libpaddle_light_api_shared.so"
  [[ -f "$light" ]] || { echo "SKIP $abi no light"; return 0; }
  local stage="$OUT/$abi"
  mkdir -p "$stage"
  echo "thin jni $abi ..."
  # Preprocess includes: convert_util uses "lite/api/..." — INC_SRC is paddle/src
  "$cxx" "${CXXFLAGS[@]}" \
    -shared -o "$stage/libpaddle_lite_jni.so" \
    "$JNI_NATIVE/paddle_lite_jni.cc" \
    "$JNI_NATIVE/tensor_jni.cc" \
    -L"$(dirname "$light")" -lpaddle_light_api_shared \
    -llog -lc -lm -ldl -lc++_shared \
    -Wl,-rpath,'$ORIGIN' \
    2>"$OUT/build.$abi.log" || {
      echo "FAIL $abi" >&2
      tail -50 "$OUT/build.$abi.log" >&2
      return 1
    }
  cp -a "$light" "$stage/libpaddle_light_api_shared.so"
  ls -la "$stage/libpaddle_lite_jni.so"
  readelf -d "$stage/libpaddle_lite_jni.so" | grep -E 'NEEDED|SONAME' || true
  nm -D "$stage/libpaddle_lite_jni.so" | grep 'PaddlePredictor_run' || true
}

build_abi arm64-v8a \
  "$PRE/bin/aarch64-linux-android${API}-clang++" \
  aarch64-linux-android
build_abi x86_64 \
  "$PRE/bin/x86_64-linux-android${API}-clang++" \
  x86_64-linux-android

echo "OK $OUT"
