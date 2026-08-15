#!/usr/bin/env bash
# Build per-ABI:
#   out/<abi>/paddle_ocr_functional     — tiny loader (dlopen core)
#   out/<abi>/libpaddle_ocr_core.so     — pipeline + links light_api
#   out/<abi>/libpaddle_light_api_shared.so, libc++_shared.so, liblog.so
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
VE_ROOT=$(cd "$HERE/../../../../.." && pwd)
OUT="${OUT:-$HERE/out}"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK:-}}"
if [[ -z "$NDK" || ! -d "$NDK" ]]; then
  NDK=$(ls -d "$HOME/Android/Sdk/ndk"/* 2>/dev/null | sort -V | tail -1 || true)
fi
[[ -n "$NDK" && -d "$NDK" ]] || { echo "ERROR: ANDROID_NDK_HOME required" >&2; exit 1; }
PRE="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
API="${PADDLE_OCR_API:-26}"
SRC="$HERE/functional_main.cpp"
LOADER="$HERE/loader_main.c"
STUB="$HERE/liblog_stub.c"
INC="$VE_ROOT/app/src/main/cpp/include"
if [[ -d "$VE_ROOT/third_party/paddle/artifact/jni" ]]; then
  JNI_ROOT="$VE_ROOT/third_party/paddle/artifact/jni"
else
  JNI_ROOT="$VE_ROOT/app/src/main/jniLibs"
fi
mkdir -p "$OUT"

# Ensure C export exists
if ! grep -q 'paddle_ocr_functional_run' "$SRC"; then
  cat >> "$SRC" << 'EOF'

extern "C" int paddle_ocr_functional_run(int argc, char** argv) {
  return main(argc, argv);
}
EOF
fi

build_one() {
  local abi=$1 cxx=$2 cc=$3 triple=$4
  local so_dir="$JNI_ROOT/$abi"
  local light="$so_dir/libpaddle_light_api_shared.so"
  if [[ ! -f "$light" ]]; then
    echo "SKIP $abi — missing $light" >&2
    return 0
  fi
  local cxx_shared="$PRE/sysroot/usr/lib/$triple/libc++_shared.so"
  local stage="$OUT/$abi"
  mkdir -p "$stage"
  echo "Building $abi ..."

  # Core shared library (links paddle)
  "$cxx" -std=c++17 -O2 -fPIC -shared \
    -I"$INC" \
    -o "$stage/libpaddle_ocr_core.so" "$SRC" \
    -L"$so_dir" -lpaddle_light_api_shared \
    -lc++_shared -lc -lm -ldl \
    -Wl,-rpath,'$ORIGIN' -Wl,-Bdynamic \
    2>"$OUT/build.$abi.log" || {
      echo "FAIL core $abi" >&2
      tail -30 "$OUT/build.$abi.log" >&2
      return 1
    }

  # Loader executable (no paddle NEEDED)
  "$cc" -O2 -fPIE -pie -o "$stage/paddle_ocr_functional" "$LOADER" -ldl \
    -Wl,-rpath,'$ORIGIN' 2>>"$OUT/build.$abi.log"

  # Deps + stub liblog
  cp -a "$light" "$stage/libpaddle_light_api_shared.so"
  cp -a "$cxx_shared" "$stage/libc++_shared.so"
  ln -sfn libc++_shared.so "$stage/libc++.so"
  "$cc" -shared -fPIC -O2 -o "$stage/liblog.so" "$STUB"

  # Convenience top-level copy
  cp -a "$stage/paddle_ocr_functional" "$OUT/paddle_ocr_functional.$abi"
  chmod +x "$stage/paddle_ocr_functional"
  file "$stage/paddle_ocr_functional" | sed 's/^/  /'
  file "$stage/libpaddle_ocr_core.so" | sed 's/^/  /'
}

build_one arm64-v8a \
  "$PRE/bin/aarch64-linux-android${API}-clang++" \
  "$PRE/bin/aarch64-linux-android${API}-clang" \
  aarch64-linux-android
build_one armeabi-v7a \
  "$PRE/bin/armv7a-linux-androideabi${API}-clang++" \
  "$PRE/bin/armv7a-linux-androideabi${API}-clang" \
  arm-linux-androideabi
build_one x86_64 \
  "$PRE/bin/x86_64-linux-android${API}-clang++" \
  "$PRE/bin/x86_64-linux-android${API}-clang" \
  x86_64-linux-android

echo "OK built under $OUT"
ls -la "$OUT"/*/paddle_ocr_functional 2>/dev/null || true
