#!/usr/bin/env bash
# Build arm64 (and optional x86_64) server_det_probe against product light_api.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
VE_ROOT=$(cd "$HERE/../../../.." && pwd)
OUT="${OUT:-$HERE/out}"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK:-}}"
if [[ -z "$NDK" || ! -d "$NDK" ]]; then
  NDK=$(ls -d "$HOME/Android/Sdk/ndk"/* 2>/dev/null | sort -V | tail -1 || true)
fi
[[ -n "$NDK" && -d "$NDK" ]] || { echo "ERROR: ANDROID_NDK_HOME required" >&2; exit 1; }
PRE="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
API="${PADDLE_PROBE_API:-26}"
SRC="$HERE/server_det_probe.cpp"
LOADER="$VE_ROOT/third_party/paddle/tests/ocr_functional/qemu/loader_main.c"
STUB="$VE_ROOT/third_party/paddle/tests/ocr_functional/qemu/liblog_stub.c"
INC="$VE_ROOT/app/src/main/cpp/include/paddle"
if [[ -d "$VE_ROOT/third_party/paddle/artifact/jni" ]]; then
  JNI_ROOT="$VE_ROOT/third_party/paddle/artifact/jni"
else
  JNI_ROOT="$VE_ROOT/app/src/main/jniLibs"
fi
mkdir -p "$OUT"

build_one() {
  local abi=$1 cxx=$2 cc=$3 triple=$4
  local so_dir="$JNI_ROOT/$abi"
  local light="$so_dir/libpaddle_light_api_shared.so"
  if [[ ! -f "$light" ]]; then
    # fall back to app jniLibs
    light="$VE_ROOT/app/src/main/jniLibs/$abi/libpaddle_light_api_shared.so"
    so_dir="$(dirname "$light")"
  fi
  if [[ ! -f "$light" ]]; then
    echo "SKIP $abi — missing light SO" >&2
    return 0
  fi
  local cxx_shared="$PRE/sysroot/usr/lib/$triple/libc++_shared.so"
  local stage="$OUT/$abi"
  mkdir -p "$stage"
  echo "Building $abi ..."
  "$cxx" -std=c++17 -O2 -fPIC -shared \
    -I"$INC" \
    -o "$stage/libpaddle_ocr_core.so" "$SRC" \
    -L"$so_dir" -lpaddle_light_api_shared \
    -lc++_shared -lc -lm -ldl \
    -Wl,-rpath,'$ORIGIN' -Wl,-Bdynamic \
    2>"$OUT/build.$abi.log" || {
      echo "FAIL core $abi" >&2
      tail -40 "$OUT/build.$abi.log" >&2
      return 1
    }
  "$cc" -O2 -fPIE -pie -o "$stage/server_det_probe" "$LOADER" -ldl \
    -Wl,-rpath,'$ORIGIN' 2>>"$OUT/build.$abi.log"
  # loader looks for libpaddle_ocr_core via fixed name used by ocr functional
  # Our loader_main expects paddle_ocr_functional name — check loader
  cp -a "$stage/server_det_probe" "$stage/paddle_ocr_functional" 2>/dev/null || true
  cp -a "$light" "$stage/libpaddle_light_api_shared.so"
  cp -a "$cxx_shared" "$stage/libc++_shared.so"
  ln -sfn libc++_shared.so "$stage/libc++.so"
  "$cc" -shared -fPIC -O2 -o "$stage/liblog.so" "$STUB"
  chmod +x "$stage/server_det_probe" "$stage/paddle_ocr_functional"
  file "$stage/server_det_probe" | sed 's/^/  /'
}

build_one arm64-v8a \
  "$PRE/bin/aarch64-linux-android${API}-clang++" \
  "$PRE/bin/aarch64-linux-android${API}-clang" \
  aarch64-linux-android
build_one x86_64 \
  "$PRE/bin/x86_64-linux-android${API}-clang++" \
  "$PRE/bin/x86_64-linux-android${API}-clang" \
  x86_64-linux-android

echo "OK under $OUT"
ls -la "$OUT"/*/server_det_probe 2>/dev/null || true
