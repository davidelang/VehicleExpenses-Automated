#!/usr/bin/env bash
# Opt experiment rec nbs for OCR-geom canvas H=56 and H=64.
# Does NOT rewrite production prod_u8fp32_u8 / prod_u8fp16 rec files.
#
# Usage:
#   OPT_TOOL=... ./scripts/reopt-ocr-geom-rec-h.sh
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
VE=$(cd "$HERE/.." && pwd)
PIN_OPT="$VE/third_party/paddle/artifact/linux-x86_64/opt_linux_x86_current"
INT8_OPT="/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/paddle-build/output/int8_linux/opt_linux_x86_int8"
SANDBOX_OPT="/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/opt_linux_x86"
if [[ -x "$PIN_OPT" ]]; then
  DEFAULT_OPT="$PIN_OPT"
elif [[ -x "$INT8_OPT" ]]; then
  DEFAULT_OPT="$INT8_OPT"
elif [[ -x "$SANDBOX_OPT" ]]; then
  DEFAULT_OPT="$SANDBOX_OPT"
else
  DEFAULT_OPT="$PIN_OPT"
fi
OPT_TOOL="${OPT_TOOL:-$DEFAULT_OPT}"
MODEL_ROOT="${MODEL_ROOT:-/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/research/models}"
SCRATCH="${SCRATCH:-/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/scratch/reopt-ocr-geom-rec-$(date +%Y%m%d-%H%M%S)}"
ASSETS_ARM64="$VE/app/src/arm64/assets/paddle"
ASSETS_X86="$VE/app/src/x86_64/assets/paddle"

if [[ ! -x "$OPT_TOOL" ]]; then
  echo "ERROR: opt not executable: $OPT_TOOL" >&2
  exit 1
fi
for d in rec_v3_mono rec_numeric_mono; do
  if [[ ! -f "$MODEL_ROOT/$d/inference.pdmodel" ]]; then
    echo "ERROR: missing $MODEL_ROOT/$d/inference.pdmodel" >&2
    exit 1
  fi
done

mkdir -p "$SCRATCH"
echo "OPT_TOOL=$OPT_TOOL"
echo "SCRATCH=$SCRATCH"
"$OPT_TOOL" --help 2>&1 | head -3 || true

opt_one() {
  local model_dir=$1 name=$2 target=$3 suffix=$4 fp16=$5 rec_h=$6
  local out_base="$SCRATCH/${name}_h${rec_h}_${suffix}"
  local rec_shape="x:1,1,${rec_h},32:1,1,${rec_h},320:1,1,${rec_h},1280"
  local -a args=(
    --model_file="$MODEL_ROOT/$model_dir/inference.pdmodel"
    --param_file="$MODEL_ROOT/$model_dir/inference.pdiparams"
    --optimize_out="$out_base"
    --valid_targets="$target"
    --optimize_out_type=naive_buffer
    --analytic_input_quant=true
    --analytic_input_dtype=uint8
    --record_tailoring_info=true
  )
  export NNADAPTER_DYNAMIC_SHAPE_INFO="$rec_shape"
  echo "NNADAPTER_DYNAMIC_SHAPE_INFO=$NNADAPTER_DYNAMIC_SHAPE_INFO"
  if [[ "$fp16" == "1" ]]; then
    args+=(--enable_fp16=true)
  else
    args+=(--enable_fp16=false)
  fi
  echo "=== opt $name H=$rec_h $suffix target=$target fp16=$fp16 shape=$rec_shape ==="
  "$OPT_TOOL" "${args[@]}" 2>&1 | tee "$SCRATCH/log_${name}_h${rec_h}_${suffix}.txt"
  if [[ ! -f "${out_base}.nb" ]]; then
    echo "FAIL missing ${out_base}.nb" >&2
    ls -la "$SCRATCH" | head -20
    return 1
  fi
  ls -lh "${out_base}.nb"
}

install_nb() {
  local src=$1 dest_dir=$2 dest_name=$3
  mkdir -p "$dest_dir"
  cp -a "$src" "$dest_dir/$dest_name"
  ls -lh "$dest_dir/$dest_name"
}

for rec_h in 56 64; do
  opt_one rec_v3_mono rec_v3 x86 "fp32_x86_64" 0 "$rec_h"
  opt_one rec_numeric_mono rec_numeric x86 "fp32_x86_64" 0 "$rec_h"
  install_nb "$SCRATCH/rec_v3_h${rec_h}_fp32_x86_64.nb" \
    "$ASSETS_X86/exp_rec_h${rec_h}" "rec_v3_x86_64.nb"
  install_nb "$SCRATCH/rec_numeric_h${rec_h}_fp32_x86_64.nb" \
    "$ASSETS_X86/exp_rec_h${rec_h}" "rec_numeric_x86_64.nb"

  opt_one rec_v3_mono rec_v3 arm "fp16_armv8" 1 "$rec_h"
  opt_one rec_numeric_mono rec_numeric arm "fp16_armv8" 1 "$rec_h"
  install_nb "$SCRATCH/rec_v3_h${rec_h}_fp16_armv8.nb" \
    "$ASSETS_ARM64/exp_rec_h${rec_h}" "rec_v3_armv8.nb"
  install_nb "$SCRATCH/rec_numeric_h${rec_h}_fp16_armv8.nb" \
    "$ASSETS_ARM64/exp_rec_h${rec_h}" "rec_numeric_armv8.nb"
done

echo "REOPT_OCR_GEOM_REC_DONE scratch=$SCRATCH"
ls -lh "$ASSETS_X86"/exp_rec_h{56,64}/*.nb "$ASSETS_ARM64"/exp_rec_h{56,64}/*.nb
