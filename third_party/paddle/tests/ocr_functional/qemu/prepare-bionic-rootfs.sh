#!/usr/bin/env bash
# Cache a minimal Android Bionic rootfs (linker + libc/libm/libdl + libc++) per ABI
# so NDK-built dynamic ELFs can run under qemu-user.
#
# Sources (first hit wins):
#   1) Existing cache under this script's rootfs/<abi>/
#   2) adb device with matching primary ABI (Pixel arm64 also ships armv7 linker/lib)
#   3) emulator-5554 for x86_64
#
# Usage:
#   ./prepare-bionic-rootfs.sh              # all ABIs possible
#   ./prepare-bionic-rootfs.sh x86_64 arm64-v8a
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
CACHE="${PADDLE_OCR_ROOTFS_CACHE:-$HERE/rootfs}"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK:-}}"
if [[ -z "$NDK" || ! -d "$NDK" ]]; then
  NDK=$(ls -d "$HOME/Android/Sdk/ndk"/* 2>/dev/null | sort -V | tail -1 || true)
fi
PRE="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
ABIS=("${@:-arm64-v8a armeabi-v7a x86_64}")
# if no args, default three
if [[ $# -eq 0 ]]; then
  ABIS=(arm64-v8a armeabi-v7a x86_64)
fi

have_rootfs() {
  local abi=$1 r=$CACHE/$abi
  case "$abi" in
    arm64-v8a|x86_64) [[ -x $r/apex/com.android.runtime/bin/linker64 || -x $r/system/bin/linker64 ]] ;;
    armeabi-v7a) [[ -x $r/apex/com.android.runtime/bin/linker || -x $r/system/bin/linker ]] ;;
    *) return 1 ;;
  esac
}

pick_serial_for_abi() {
  local want=$1 s abi abilist
  # Prefer emulator-5554 for x86 when present
  if [[ "$want" == "x86_64" ]] && adb -s emulator-5554 get-state 2>/dev/null | grep -q device; then
    echo emulator-5554
    return 0
  fi
  while read -r s; do
    [[ -z "$s" ]] && continue
    abi=$(adb -s "$s" shell getprop ro.product.cpu.abi 2>/dev/null | tr -d '\r')
    abilist=$(adb -s "$s" shell getprop ro.product.cpu.abilist 2>/dev/null | tr -d '\r')
    case "$want" in
      x86_64)
        if [[ "$abi" == *x86* || "$abilist" == *x86_64* || "$s" == emulator-* ]]; then
          echo "$s"; return 0
        fi
        ;;
      arm64-v8a)
        [[ "$abi" == arm64-v8a || "$abi" == arm64* || "$abilist" == *arm64* ]] && { echo "$s"; return 0; }
        ;;
      armeabi-v7a)
        # 64-bit arm devices often still ship 32-bit linker + lib/
        [[ "$abi" == arm64-v8a || "$abi" == armeabi-v7a || "$abi" == arm64* || "$abilist" == *armeabi* ]] && { echo "$s"; return 0; }
        ;;
    esac
  done < <(adb devices 2>/dev/null | awk 'NR>1 && $2=="device"{print $1}')
  return 1
}

pull_bionic() {
  local abi=$1 serial=$2
  local r=$CACHE/$abi
  rm -rf "$r"
  mkdir -p "$r/system/bin" "$r/system/lib" "$r/system/lib64" \
    "$r/lib" "$r/lib64" \
    "$r/apex/com.android.runtime/bin" \
    "$r/apex/com.android.runtime/lib/bionic" \
    "$r/apex/com.android.runtime/lib64/bionic"

  if [[ "$abi" == "armeabi-v7a" ]]; then
    adb -s "$serial" pull /apex/com.android.runtime/bin/linker \
      "$r/apex/com.android.runtime/bin/linker"
    ln -sfn ../../apex/com.android.runtime/bin/linker "$r/system/bin/linker"
    for lib in libc.so libm.so libdl.so libdl_android.so; do
      adb -s "$serial" pull "/apex/com.android.runtime/lib/bionic/$lib" \
        "$r/apex/com.android.runtime/lib/bionic/$lib" 2>/dev/null || \
      adb -s "$serial" pull "/system/lib/$lib" "$r/system/lib/$lib"
    done
    cp -a "$r/apex/com.android.runtime/lib/bionic/"*.so "$r/system/lib/" 2>/dev/null || true
    cp -a "$r/system/lib/"*.so "$r/lib/" 2>/dev/null || true
    cp -a "$PRE/sysroot/usr/lib/arm-linux-androideabi/libc++_shared.so" "$r/system/lib/"
    cp -a "$PRE/sysroot/usr/lib/arm-linux-androideabi/libc++_shared.so" "$r/lib/"
    ln -sfn libc++_shared.so "$r/system/lib/libc++.so"
    ln -sfn libc++_shared.so "$r/lib/libc++.so"
    # liblog: NDK stub installed by build.sh into out/<abi>/ — do NOT use device liblog
    # (it pulls in device libc++ ABI and crashes under qemu).
  else
    adb -s "$serial" pull /apex/com.android.runtime/bin/linker64 \
      "$r/apex/com.android.runtime/bin/linker64"
    ln -sfn ../../apex/com.android.runtime/bin/linker64 "$r/system/bin/linker64"
    for lib in libc.so libm.so libdl.so libdl_android.so; do
      adb -s "$serial" pull "/apex/com.android.runtime/lib64/bionic/$lib" \
        "$r/apex/com.android.runtime/lib64/bionic/$lib"
    done
    cp -a "$r/apex/com.android.runtime/lib64/bionic/"*.so "$r/system/lib64/"
    cp -a "$r/apex/com.android.runtime/lib64/bionic/"*.so "$r/lib64/"
    local triple
    if [[ "$abi" == "x86_64" ]]; then triple=x86_64-linux-android
    else triple=aarch64-linux-android
    fi
    cp -a "$PRE/sysroot/usr/lib/$triple/libc++_shared.so" "$r/system/lib64/"
    cp -a "$PRE/sysroot/usr/lib/$triple/libc++_shared.so" "$r/lib64/"
    ln -sfn libc++_shared.so "$r/system/lib64/libc++.so"
    ln -sfn libc++_shared.so "$r/lib64/libc++.so"
  fi
  echo "prepared rootfs $r (from $serial)"
}

for abi in "${ABIS[@]}"; do
  if have_rootfs "$abi" && [[ "${PADDLE_OCR_ROOTFS_FORCE:-0}" != "1" ]]; then
    echo "OK cached rootfs $CACHE/$abi"
    continue
  fi
  ser=$(pick_serial_for_abi "$abi" || true)
  if [[ -z "${ser:-}" ]]; then
    echo "WARN: no adb device for $abi — cannot prepare rootfs (run with device once)" >&2
    continue
  fi
  pull_bionic "$abi" "$ser"
done

echo "rootfs cache: $CACHE"
ls -la "$CACHE" 2>/dev/null || true
