#!/usr/bin/env bash
# Build paddle_so_smoke for arm64-v8a, armeabi-v7a, x86_64 (NDK, preferably static).
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
OUT="${OUT:-$HERE/out}"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK:-}}"
if [[ -z "$NDK" || ! -d "$NDK" ]]; then
  NDK=$(ls -d "$HOME/Android/Sdk/ndk"/* 2>/dev/null | sort -V | tail -1 || true)
fi
if [[ -z "$NDK" || ! -d "$NDK" ]]; then
  echo "ERROR: set ANDROID_NDK_HOME to NDK r26+ (have app link-gate NDK28)" >&2
  exit 1
fi
PRE="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
if [[ ! -d "$PRE" ]]; then
  echo "ERROR: NDK prebuilt missing at $PRE" >&2
  exit 1
fi
API="${PADDLE_SMOKE_API:-26}"
SRC="$HERE/smoke_main.cpp"
mkdir -p "$OUT"

build_one() {
  local abi=$1 cxx=$2
  local out="$OUT/paddle_so_smoke.$abi"
  echo "Building $out (static) with $cxx ..."
  # Static: runs under qemu-user without Android /system/bin/linker.
  if ! "$cxx" -std=c++17 -O2 -static -o "$out" "$SRC" 2>"$OUT/build.$abi.log"; then
    echo "static link failed for $abi — trying dynamic (needs Android linker to run)" >&2
    "$cxx" -std=c++17 -O2 -o "$out" "$SRC" -ldl 2>>"$OUT/build.$abi.log"
  fi
  chmod +x "$out"
  file "$out" | sed 's/^/  /'
}

build_one arm64-v8a "$PRE/bin/aarch64-linux-android${API}-clang++"
build_one armeabi-v7a "$PRE/bin/armv7a-linux-androideabi${API}-clang++"
build_one x86_64 "$PRE/bin/x86_64-linux-android${API}-clang++"

echo "OK built:"
ls -lh "$OUT"/paddle_so_smoke.*
