#!/usr/bin/env bash
# A/B two light SOs under QEMU ocr_functional stage (same models/fixture).
# See docs in script header of prior version + HEATMAP investigation.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
VE=$(cd "$HERE/.." && pwd)
STAGE_ROOT="$VE/third_party/paddle/tests/ocr_functional/qemu/out"
ROOTFS_ROOT="$VE/third_party/paddle/tests/ocr_functional/qemu/rootfs"
FIX="$VE/third_party/paddle/tests/ocr_functional/fixtures"
ASSETS_ARM64="$VE/app/src/arm64/assets/paddle"
HOST_NBS="$VE/third_party/paddle/prod_models_host"
ABI=x86_64; SOA=""; SOB=""; SIDES="224 608 1024"
while [[ $# -gt 0 ]]; do
  case $1 in
    --abi) ABI=$2; shift 2;;
    --so-a) SOA=$2; shift 2;;
    --so-b) SOB=$2; shift 2;;
    --det-sides) SIDES=$2; shift 2;;
    *) echo "bad $1"; exit 2;;
  esac
done
[[ -f "$SOA" && -f "$SOB" ]] || { echo need sos; exit 2; }
case $ABI in
  x86_64) DET=$HOST_NBS/prod_u8fp16_det_x86_64.nb; REC=$HOST_NBS/prod_u8fp16_rec_v3_x86_64.nb; Q=qemu-x86_64; LP=/lib64:/system/lib64;;
  arm64-v8a) DET=$ASSETS_ARM64/prod_u8fp16/det_armv8.nb; REC=$ASSETS_ARM64/prod_u8fp16/rec_v3_armv8.nb; Q=qemu-aarch64; LP=/lib64:/system/lib64;;
  *) echo abi; exit 2;;
esac
STAGE=$STAGE_ROOT/$ABI
ROOTFS=$ROOTFS_ROOT/$ABI
[[ -x $STAGE/paddle_ocr_functional ]] || { echo "build qemu harness first"; exit 2; }

run() {
  local so=$1 lab=$2 side=$3
  cp -f "$so" "$STAGE/libpaddle_light_api_shared.so"
  local log; log=$(mktemp)
  set +e
  $Q -L "$ROOTFS" -E "LD_LIBRARY_PATH=$STAGE:$LP" \
    $STAGE/paddle_ocr_functional --abi $ABI --det $DET --rec $REC \
    --dict $FIX/en_dict.txt --image $FIX/skewed_hello.pgm \
    --expect-text ABCD12345 --expect-angle 15 --det-side $side >$log 2>&1
  local rc=$?; set -e
  local heat=$(grep -oE 'heat max=[0-9.]+' $log | head -1 | cut -d= -f2 || echo NA)
  local boxes=$(grep 'pass1 angle=' $log | head -1 | grep -oE 'boxes=[0-9]+' | cut -d= -f2 || echo NA)
  local angle=$(grep 'RESULT abi=' $log | head -1 | grep -oE 'angle=[-0-9.]+' | cut -d= -f2 || echo NA)
  local ocr=$(grep 'RESULT abi=' $log | head -1 | grep -oE "ocr='[^']*'" | sed "s/ocr='//;s/'$//" || echo NA)
  local pass=FAIL; grep -q 'PASS paddle_ocr_functional' $log && pass=PASS
  printf '%s side=%s rc=%s pass=%s heat=%s boxes=%s angle=%s ocr=%s\n' "$lab" "$side" "$rc" "$pass" "$heat" "$boxes" "$angle" "$ocr"
  echo "$pass|$heat|$boxes|$angle|$ocr"
}
echo A $(sha256sum "$SOA"|awk '{print $1}')
echo B $(sha256sum "$SOB"|awk '{print $1}')
diff_heat=0; hard=0
for side in $SIDES; do
  echo "=== $side ==="
  ra=$(run "$SOA" A $side | tail -1)
  rb=$(run "$SOB" B $side | tail -1)
  echo "  A $ra"; echo "  B $rb"
  IFS='|' read -r pa ha ba aa oa <<<"$ra"
  IFS='|' read -r pb hb bb ab ob <<<"$rb"
  [[ $pa == PASS && $pb == PASS ]] || hard=1
  [[ $ha == $hb && $ba == $bb && $oa == $ob ]] || diff_heat=1
done
if [[ $hard -eq 1 ]]; then echo RESULT:HARD_OR_OCR_DIFF; exit 1; fi
if [[ $diff_heat -eq 1 ]]; then echo RESULT:INVISIBLE_TO_HARNESS_OR_PARTIAL; exit 2; fi
echo RESULT:IDENTICAL_ON_FIXTURE; exit 0
