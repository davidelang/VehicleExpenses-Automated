#!/usr/bin/env bash
# Run paddle_ocr_functional under QEMU for each product ABI.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
# qemu → ocr_functional → tests → paddle → third_party → repo root
VE_ROOT=$(cd "$HERE/../../../../.." && pwd)
OUT="${PADDLE_OCR_OUT:-$HERE/out}"
FIX="$HERE/../fixtures"
ROOTFS="${PADDLE_OCR_ROOTFS_CACHE:-$HERE/rootfs}"
ABIS="${PADDLE_OCR_ABIS:-arm64-v8a armeabi-v7a x86_64}"
REPORT="${PADDLE_OCR_REPORT:-$VE_ROOT/dev-ai-interaction/scratch/paddle-ocr-qemu-$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$REPORT"

IMAGE="${PADDLE_OCR_IMAGE:-$FIX/skewed_hello.pgm}"
DICT="${PADDLE_OCR_DICT:-$FIX/en_dict.txt}"
EXPECT_TEXT="${PADDLE_OCR_EXPECT_TEXT:-ABCD12345}"
EXPECT_ANGLE="${PADDLE_OCR_EXPECT_ANGLE:-15}"
ANGLE_TOL="${PADDLE_OCR_ANGLE_TOL:-10}"
MAX_EDIT="${PADDLE_OCR_MAX_EDIT:-2}"

# Models
ASSETS="$VE_ROOT/app/src/main/assets/paddle"
model_for_abi() {
  local abi=$1 kind=$2  # kind=det|rec_v3
  case "$abi" in
    arm64-v8a)
      echo "$ASSETS/prod_u8fp16/${kind}_armv8.nb"
      ;;
    armeabi-v7a)
      echo "$ASSETS/prod_u8fp32_u8/${kind}_armv7.nb"
      ;;
    x86_64)
      echo "$ASSETS/prod_u8fp16/${kind}_x86_64.nb"
      ;;
  esac
}

find_qemu() {
  local base=$1
  for c in "${base}-static" "$base" "/usr/bin/${base}-static" "/usr/bin/$base"; do
    if command -v "$c" >/dev/null 2>&1; then echo "$c"; return 0; fi
    if [[ -x "$c" ]]; then echo "$c"; return 0; fi
  done
  return 1
}

run_qemu() {
  local abi=$1 bin=$2
  shift 2
  local q root=$ROOTFS/$abi
  case "$abi" in
    arm64-v8a)
      q=$(find_qemu qemu-aarch64) || { echo "need qemu-aarch64"; return 127; }
      # Prefer binary dir first so stub liblog + product paddle win over rootfs
      "$q" -L "$root" -E "LD_LIBRARY_PATH=$(dirname "$bin"):/lib64:/system/lib64" "$bin" "$@"
      ;;
    armeabi-v7a)
      q=$(find_qemu qemu-arm) || { echo "need qemu-arm"; return 127; }
      # Bionic 32-bit aborts if host PID > 65535
      if command -v bwrap >/dev/null 2>&1; then
        bwrap --unshare-pid --as-pid-1 --die-with-parent --dev-bind / / -- \
          "$q" -L "$root" -E "LD_LIBRARY_PATH=$(dirname "$bin"):/lib:/system/lib" "$bin" "$@"
      else
        "$q" -L "$root" -E "LD_LIBRARY_PATH=$(dirname "$bin"):/lib:/system/lib" "$bin" "$@"
      fi
      ;;
    x86_64)
      q=$(find_qemu qemu-x86_64) || { echo "need qemu-x86_64"; return 127; }
      "$q" -L "$root" -E "LD_LIBRARY_PATH=$(dirname "$bin"):/lib64:/system/lib64" "$bin" "$@"
      ;;
  esac
}

# Build if needed
if [[ "${PADDLE_OCR_REBUILD:-1}" == "1" ]] || [[ ! -x $OUT/paddle_ocr_functional.x86_64 ]]; then
  "$HERE/build.sh"
fi
"$HERE/prepare-bionic-rootfs.sh" $ABIS

FAIL=0
RAN=0
for abi in $ABIS; do
  bin="$OUT/$abi/paddle_ocr_functional"
  [[ -x "$bin" ]] || bin="$OUT/paddle_ocr_functional.$abi"
  if [[ ! -x "$bin" ]]; then
    echo "SKIP $abi — binary missing"
    FAIL=$((FAIL + 1))
    continue
  fi
  if [[ ! -d "$ROOTFS/$abi" ]]; then
    echo "SKIP $abi — rootfs missing ($ROOTFS/$abi)"
    FAIL=$((FAIL + 1))
    continue
  fi
  det=$(model_for_abi "$abi" det)
  rec=$(model_for_abi "$abi" rec_v3)
  if [[ ! -f "$det" || ! -f "$rec" ]]; then
    echo "SKIP $abi — models missing det=$det rec=$rec"
    FAIL=$((FAIL + 1))
    continue
  fi
  log="$REPORT/$abi.log"
  echo "==> $abi"
  set +e
  # Smaller det side is faster under QEMU; still enough for fixture
  det_side="${PADDLE_OCR_DET_SIDE:-224}"
  extra=()
  # armv7: always full path (same as other ABIs). Zero heatmap = FAIL (repro on device
  # and qemu; not papered over). Opt-out ABI with PADDLE_OCR_ABIS without armeabi-v7a.
  run_qemu "$abi" "$bin" \
    --abi "$abi" \
    --det "$det" \
    --rec "$rec" \
    --dict "$DICT" \
    --image "$IMAGE" \
    --expect-text "$EXPECT_TEXT" \
    --expect-angle "$EXPECT_ANGLE" \
    --angle-tol "$ANGLE_TOL" \
    --max-edit "$MAX_EDIT" \
    --det-side "$det_side" \
    "${extra[@]}" \
    >"$log" 2>&1
  ec=$?
  set -e
  RAN=$((RAN + 1))
  tail -30 "$log" | sed "s/^/  [$abi] /"
  if [[ $ec -eq 0 ]]; then
    echo "  PASS $abi"
  else
    echo "  FAIL $abi (exit $ec) — full log $log"
    FAIL=$((FAIL + 1))
  fi
done

echo "report: $REPORT ran=$RAN fail=$FAIL"
if [[ $RAN -eq 0 || $FAIL -ne 0 ]]; then
  echo "paddle-ocr-qemu: FAIL (ran=$RAN fail=$FAIL)" >&2
  exit 1
fi
echo "paddle-ocr-qemu: PASS"
exit 0
