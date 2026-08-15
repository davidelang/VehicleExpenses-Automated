#!/usr/bin/env bash
# Re-opt product .nb models with the current pin's opt (must match host/device light SO pin).
#
# Usage:
#   OPT_TOOL=third_party/paddle/artifact/linux-x86_64/opt_linux_x86_current \
#     ./scripts/reopt-product-models.sh
#
# Writes into ABI flavor asset trees (app/src/{arm64,x86_64,armv7}/assets/paddle/...).
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
VE=$(cd "$HERE/.." && pwd)
OPT_TOOL="${OPT_TOOL:-$VE/third_party/paddle/artifact/linux-x86_64/opt_linux_x86_current}"
MODEL_ROOT="${MODEL_ROOT:-/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/research/models}"
SCRATCH="${SCRATCH:-/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/scratch/reopt-product-$(date +%Y%m%d-%H%M%S)}"
ASSETS_ARM64="$VE/app/src/arm64/assets/paddle"
ASSETS_X86="$VE/app/src/x86_64/assets/paddle"
ASSETS_ARMV7="$VE/app/src/armv7/assets/paddle"
HOST_NBS="$VE/third_party/paddle/prod_models_host"

DET_SHAPE="x:1,1,128,128:1,1,1280,1280:1,1,4000,4000"
REC_SHAPE="x:1,1,48,32:1,1,48,320:1,1,48,1280"

if [[ ! -x "$OPT_TOOL" ]]; then
  echo "ERROR: opt not executable: $OPT_TOOL" >&2
  exit 1
fi
for d in det_mono rec_v3_mono rec_numeric_mono; do
  if [[ ! -f "$MODEL_ROOT/$d/inference.pdmodel" ]]; then
    echo "ERROR: missing $MODEL_ROOT/$d/inference.pdmodel" >&2
    exit 1
  fi
done

mkdir -p "$SCRATCH"
echo "OPT_TOOL=$OPT_TOOL"
echo "SCRATCH=$SCRATCH"
"$OPT_TOOL" --help 2>&1 | head -3 || true
# show embedded pin hint if present
strings "$OPT_TOOL" | grep -E '^[0-9a-f]{7,12}$' | head -5 || true

opt_one() {
  local model_dir=$1 name=$2 target=$3 suffix=$4 fp16=$5 out_calib=$6
  local out_base="$SCRATCH/${name}_${suffix}"
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
  if [[ "$name" == det ]]; then
    export NNADAPTER_DYNAMIC_SHAPE_INFO="$DET_SHAPE"
  else
    export NNADAPTER_DYNAMIC_SHAPE_INFO="$REC_SHAPE"
  fi
  if [[ "$fp16" == "1" ]]; then
    args+=(--enable_fp16=true)
  else
    args+=(--enable_fp16=false)
  fi
  if [[ -n "$out_calib" ]]; then
    args+=(--output_calib_precision="$out_calib")
  fi
  echo "=== opt $name $suffix target=$target fp16=$fp16 calib=${out_calib:-none} ==="
  "$OPT_TOOL" "${args[@]}" 2>&1 | tee "$SCRATCH/log_${name}_${suffix}.txt"
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
  # Previous committed .nb is in git. Do not write .bak next to assets (aapt ships them).
  cp -a "$src" "$dest_dir/$dest_name"
  ls -lh "$dest_dir/$dest_name"
}

# --- prod_u8fp32_u8: armv7, armv8, x86_64 ---
for pair in "arm:armv7" "arm:armv8" "x86:x86_64"; do
  target="${pair%%:*}"
  suffix="${pair##*:}"
  opt_one det_mono det "$target" "fp32_${suffix}" 0 uint8
  opt_one rec_v3_mono rec_v3 "$target" "fp32_${suffix}" 0 ""
  opt_one rec_numeric_mono rec_numeric "$target" "fp32_${suffix}" 0 ""
  case "$suffix" in
    armv7) dest="$ASSETS_ARMV7/prod_u8fp32_u8" ;;
    x86_64) dest="$ASSETS_X86/prod_u8fp32_u8" ;;
    *) dest="$HOST_NBS" ;;
  esac
  if [[ "$dest" == "$HOST_NBS" ]]; then
    install_nb "$SCRATCH/det_fp32_${suffix}.nb" "$HOST_NBS" "prod_u8fp32_det_${suffix}.nb"
    install_nb "$SCRATCH/rec_v3_fp32_${suffix}.nb" "$HOST_NBS" "prod_u8fp32_rec_v3_${suffix}.nb"
    install_nb "$SCRATCH/rec_numeric_fp32_${suffix}.nb" "$HOST_NBS" "prod_u8fp32_rec_numeric_${suffix}.nb"
  else
    install_nb "$SCRATCH/det_fp32_${suffix}.nb" "$dest" "det_${suffix}.nb"
    install_nb "$SCRATCH/rec_v3_fp32_${suffix}.nb" "$dest" "rec_v3_${suffix}.nb"
    install_nb "$SCRATCH/rec_numeric_fp32_${suffix}.nb" "$dest" "rec_numeric_${suffix}.nb"
  fi
done

# --- prod_u8fp16: armv8 + x86_64 (ship path; no armv7 fp16 HW) ---
for pair in "arm:armv8" "x86:x86_64"; do
  target="${pair%%:*}"
  suffix="${pair##*:}"
  opt_one det_mono det "$target" "fp16_${suffix}" 1 uint8
  opt_one rec_v3_mono rec_v3 "$target" "fp16_${suffix}" 1 ""
  opt_one rec_numeric_mono rec_numeric "$target" "fp16_${suffix}" 1 ""
  if [[ "$suffix" == "armv8" ]]; then
    install_nb "$SCRATCH/det_fp16_${suffix}.nb" "$ASSETS_ARM64/prod_u8fp16" "det_${suffix}.nb"
    install_nb "$SCRATCH/rec_v3_fp16_${suffix}.nb" "$ASSETS_ARM64/prod_u8fp16" "rec_v3_${suffix}.nb"
    install_nb "$SCRATCH/rec_numeric_fp16_${suffix}.nb" "$ASSETS_ARM64/prod_u8fp16" "rec_numeric_${suffix}.nb"
  else
    install_nb "$SCRATCH/det_fp16_${suffix}.nb" "$HOST_NBS" "prod_u8fp16_det_${suffix}.nb"
    install_nb "$SCRATCH/rec_v3_fp16_${suffix}.nb" "$HOST_NBS" "prod_u8fp16_rec_v3_${suffix}.nb"
    install_nb "$SCRATCH/rec_numeric_fp16_${suffix}.nb" "$HOST_NBS" "prod_u8fp16_rec_numeric_${suffix}.nb"
  fi
done

echo "REOPT_DONE scratch=$SCRATCH"
ls -lh "$ASSETS_ARM64/prod_u8fp16"/*.nb "$ASSETS_X86/prod_u8fp32_u8"/*.nb "$ASSETS_ARMV7/prod_u8fp32_u8"/*.nb
