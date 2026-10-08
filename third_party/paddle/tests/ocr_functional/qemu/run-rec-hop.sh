#!/usr/bin/env bash
# Rec hop: one rec_v3 predictor, Resize 1,1,48,160 Run then 1,1,48,128 Run.
# Pre-patch x86 light SO is allowed to SIGSEGV (document). Patched SO must PASS both.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
VE_ROOT=$(cd "$HERE/../../../../.." && pwd)
OUT="${PADDLE_OCR_OUT:-$HERE/out}"
ROOTFS="${PADDLE_OCR_ROOTFS_CACHE:-$HERE/rootfs}"
ABI="${PADDLE_OCR_HOP_ABI:-x86_64}"
# Product x86 rec (tablet/emu), not the host leftover u8fp16 copy.
REC="${PADDLE_OCR_REC:-$VE_ROOT/app/src/x86_64/assets/paddle/prod_u8fp32_u8/rec_v3_x86_64.nb}"
JNI_ROOT="${PADDLE_JNI_ROOT:-}"
if [[ -z "$JNI_ROOT" ]]; then
  if [[ -d "$VE_ROOT/third_party/paddle/artifact/jni" ]]; then
    JNI_ROOT="$VE_ROOT/third_party/paddle/artifact/jni"
  else
    JNI_ROOT="$VE_ROOT/app/src/main/jniLibs"
  fi
fi

if [[ "${PADDLE_OCR_REBUILD:-1}" == "1" ]] || [[ ! -x $OUT/$ABI/paddle_ocr_functional ]]; then
  "$HERE/build.sh"
fi
"$HERE/prepare-bionic-rootfs.sh" "$ABI"

bin="$OUT/$ABI/paddle_ocr_functional"
[[ -x "$bin" ]] || { echo "FAIL missing $bin" >&2; exit 2; }
[[ -f "$REC" ]] || { echo "FAIL missing rec $REC" >&2; exit 2; }
[[ -d "$ROOTFS/$ABI" ]] || { echo "FAIL missing rootfs $ROOTFS/$ABI" >&2; exit 2; }

# Prefer the SO the harness was linked against (copied into OUT by build.sh).
# Override: copy a candidate light SO into OUT/$ABI before running.
light="$OUT/$ABI/libpaddle_light_api_shared.so"
if [[ ! -f "$light" ]]; then
  light="$JNI_ROOT/$ABI/libpaddle_light_api_shared.so"
fi
echo "rec-hop: abi=$ABI rec=$REC light=$light"

find_qemu() {
  local base=$1
  for c in "${base}-static" "$base" "/usr/bin/${base}-static" "/usr/bin/$base"; do
    if command -v "$c" >/dev/null 2>&1; then echo "$c"; return 0; fi
    if [[ -x "$c" ]]; then echo "$c"; return 0; fi
  done
  return 1
}

q=$(find_qemu qemu-x86_64) || { echo "need qemu-x86_64" >&2; exit 127; }
set +e
"$q" -L "$ROOTFS/$ABI" -E "LD_LIBRARY_PATH=$(dirname "$bin"):/lib64:/system/lib64" \
  "$bin" --abi "$ABI" --stage rec-hop --rec "$REC" --threads 1
ec=$?
set -e
if [[ $ec -eq 0 ]]; then
  echo "rec-hop: PASS"
  exit 0
fi
echo "rec-hop: FAIL exit=$ec (pre-patch x86 DirectConv hop is expected SIGSEGV)" >&2
exit "$ec"
