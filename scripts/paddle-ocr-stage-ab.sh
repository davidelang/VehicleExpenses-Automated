#!/usr/bin/env bash
# Stage-wise A/B of two light SOs under QEMU ocr_functional.
# Does not reimplement the full app pump tree — isolates:
#   det-heat  → heatmap mass/CRC + rotation
#   det-boxes → red-box-like AABBs
#   rec       → CTC text + per-char probs + logit CRC
#
# Usage:
#   scripts/paddle-ocr-stage-ab.sh \
#     --so-a pin/libpaddle_light_api_shared.so \
#     --so-b cand/libpaddle_light_api_shared.so \
#     --image path/to.pgm \
#     [--abi x86_64] [--sides "224 608 1024"] [--threads 1] \
#     [--resize topleft|letterbox] [--rec-image crop.pgm]
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
VE=$(cd "$HERE/.." && pwd)
STAGE_ROOT="$VE/third_party/paddle/tests/ocr_functional/qemu/out"
ROOTFS_ROOT="$VE/third_party/paddle/tests/ocr_functional/qemu/rootfs"
FIX="$VE/third_party/paddle/tests/ocr_functional/fixtures"
ASSETS_ARM64="$VE/app/src/arm64/assets/paddle"
HOST_NBS="$VE/third_party/paddle/prod_models_host"
ABI=x86_64
SOA=""; SOB=""
SIDES="224 608 1024"
THREADS=1
RESIZE=topleft
IMG=""
REC_IMG=""
DICT="$FIX/en_dict.txt"

while [[ $# -gt 0 ]]; do
  case $1 in
    --abi) ABI=$2; shift 2;;
    --so-a) SOA=$2; shift 2;;
    --so-b) SOB=$2; shift 2;;
    --image) IMG=$2; shift 2;;
    --rec-image) REC_IMG=$2; shift 2;;
    --sides) SIDES=$2; shift 2;;
    --threads) THREADS=$2; shift 2;;
    --resize) RESIZE=$2; shift 2;;
    --dict) DICT=$2; shift 2;;
    *) echo "bad arg $1" >&2; exit 2;;
  esac
done
[[ -f "$SOA" && -f "$SOB" ]] || { echo "need --so-a and --so-b" >&2; exit 2; }
[[ -n "$IMG" && -f "$IMG" ]] || { echo "need --image PGM" >&2; exit 2; }

case $ABI in
  x86_64)
    DET=$HOST_NBS/prod_u8fp16_det_x86_64.nb
    REC=$HOST_NBS/prod_u8fp16_rec_v3_x86_64.nb
    Q=qemu-x86_64; LP=/lib64:/system/lib64
    ;;
  arm64-v8a)
    DET=$ASSETS_ARM64/prod_u8fp16/det_armv8.nb
    REC=$ASSETS_ARM64/prod_u8fp16/rec_v3_armv8.nb
    Q=qemu-aarch64; LP=/lib64:/system/lib64
    ;;
  *) echo "unsupported abi $ABI" >&2; exit 2;;
esac

STAGE=$STAGE_ROOT/$ABI
ROOTFS=$ROOTFS_ROOT/$ABI
BIN=$STAGE/paddle_ocr_functional
[[ -x $BIN ]] || { echo "build qemu harness first: third_party/paddle/tests/ocr_functional/qemu/build.sh" >&2; exit 2; }

echo "A $(sha256sum "$SOA" | awk '{print $1}')"
echo "B $(sha256sum "$SOB" | awk '{print $1}')"
echo "image=$IMG resize=$RESIZE threads=$THREADS abi=$ABI"

run_stage() {
  local so=$1 lab=$2 stage=$3 side=$4 extra_img=$5
  cp -f "$so" "$STAGE/libpaddle_light_api_shared.so"
  local img=$IMG
  [[ -n "$extra_img" ]] && img=$extra_img
  local log; log=$(mktemp)
  local args=(--abi "$ABI" --stage "$stage" --det "$DET" --rec "$REC" --dict "$DICT"
              --image "$img" --det-side "$side" --resize "$RESIZE" --threads "$THREADS"
              --no-strict --max-edit 99 --angle-tol 90)
  if [[ "$stage" == rec ]]; then
    args+=(--expect-text "")
  fi
  set +e
  $Q -L "$ROOTFS" -E "LD_LIBRARY_PATH=$STAGE:$LP" \
    $BIN "${args[@]}" >"$log" 2>&1
  local rc=$?
  set -e
  # extract RESULT line
  local res; res=$(grep -E '^RESULT ' "$log" | head -1 || true)
  local heat; heat=$(grep -E '^HEAT ' "$log" | head -1 || true)
  local probs; probs=$(grep -E '^PROBS ' "$log" | head -1 || true)
  printf '%s stage=%s side=%s rc=%s\n  %s\n' "$lab" "$stage" "$side" "$rc" "$res"
  [[ -n "$heat" ]] && printf '  %s\n' "$heat"
  [[ -n "$probs" ]] && printf '  %s\n' "$probs"
  # machine line for diff
  echo "$lab|$stage|$side|$rc|$res|$heat|$probs"
  rm -f "$log"
}

diff_count=0
echo "=== det-heat (heatmap mass/CRC + rotation) ==="
for side in $SIDES; do
  ra=$(run_stage "$SOA" A det-heat "$side" "" | tail -1)
  rb=$(run_stage "$SOB" B det-heat "$side" "" | tail -1)
  # strip lab prefix for compare of metrics
  sa=${ra#A|}; sb=${rb#B|}
  if [[ "$sa" == "$sb" ]]; then
    echo "  side=$side IDENTICAL"
  else
    echo "  side=$side DIFF"
    echo "    A $ra"
    echo "    B $rb"
    diff_count=$((diff_count + 1))
  fi
done

echo "=== det-boxes (red-box-like AABBs, thr=0 min_area=10) ==="
for side in $SIDES; do
  ra=$(run_stage "$SOA" A det-boxes "$side" "" | tail -1)
  rb=$(run_stage "$SOB" B det-boxes "$side" "" | tail -1)
  sa=${ra#A|}; sb=${rb#B|}
  # compare RESULT portion (nboxes/mass/crc)
  if [[ "$sa" == "$sb" ]]; then
    echo "  side=$side IDENTICAL"
  else
    echo "  side=$side DIFF"
    echo "    A $ra"
    echo "    B $rb"
    diff_count=$((diff_count + 1))
  fi
done

if [[ -n "$REC_IMG" && -f "$REC_IMG" ]]; then
  echo "=== rec (text + probs + logit CRC) on $REC_IMG ==="
  ra=$(run_stage "$SOA" A rec 0 "$REC_IMG" | tail -1)
  rb=$(run_stage "$SOB" B rec 0 "$REC_IMG" | tail -1)
  sa=${ra#A|}; sb=${rb#B|}
  if [[ "$sa" == "$sb" ]]; then
    echo "  rec IDENTICAL"
  else
    echo "  rec DIFF"
    echo "    A $ra"
    echo "    B $rb"
    diff_count=$((diff_count + 1))
  fi
else
  echo "=== rec skipped (pass --rec-image crop.pgm for rec A/B) ==="
fi

if [[ $diff_count -eq 0 ]]; then
  echo "RESULT:ALL_STAGES_IDENTICAL_ON_FIXTURE"
  exit 0
fi
echo "RESULT:STAGE_DIFF count=$diff_count"
exit 2
