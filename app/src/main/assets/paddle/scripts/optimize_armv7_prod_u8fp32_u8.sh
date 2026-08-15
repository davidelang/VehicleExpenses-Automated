#!/usr/bin/env bash
# Produce true ARMv7 product models: uint8 feed → fp32 mid-graph.
#
# CRITICAL (2026-08-04): do NOT pass --quant_model=true / QUANT_INT8 for armv7.
# Weight int8 post-training quant produces all-zero det heatmaps with light API
# (repro: Pixel + QEMU, product SO and slim). Use analytic input calib only.
#
#   det:     uint8 in → fp32 → uint8 heatmap out  (--output_calib_precision=uint8)
#   rec_*:   uint8 in → fp32 CTC logits out       (no output calib — CTC needs float)
#
# No --enable_fp16 (head-unit class SoCs; no ARM82_FP16).
#
# Requires INT8-capable host opt (analytic_input_quant_pass + output_calib_precision).
# Default OPT_TOOL: sandbox paddle-build int8 opt.
#
# Outputs (default):
#   app/src/main/assets/paddle/prod_u8fp32_u8/{det,rec_v3,rec_numeric}_armv7.nb
#   + optional record_tailoring_info under OUTPUT_ROOT/*_armv7/
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Prefer worktree root assets layout; MODEL_ROOT points at mono pdmodel sources.
REPO_ROOT="$(cd "$SCRIPT_DIR/../../../../.." && pwd)"
OPT_TOOL="${OPT_TOOL:-/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/paddle-build/output/int8_linux/opt_linux_x86_int8}"
MODEL_ROOT="${MODEL_ROOT:-/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/research/models}"
OUT_ASSETS="${OUT_ASSETS:-$REPO_ROOT/app/src/main/assets/paddle/prod_u8fp32_u8}"
OUTPUT_ROOT="${OUTPUT_ROOT:-/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/scratch/armv7-prod-u8fp32-u8}"

if [[ ! -x "$OPT_TOOL" ]]; then
  echo "ERROR: opt tool not executable: $OPT_TOOL" >&2
  exit 1
fi
mkdir -p "$OUT_ASSETS" "$OUTPUT_ROOT"

# $1=model_dir $2=name $3=shape $4=output_calib|none
optimize_one() {
  local m_dir=$1 name=$2 shape=$3 out_calib=${4:-none}
  echo "=== armv7 u8→fp32 $name (output_calib=$out_calib) ==="
  export NNADAPTER_DYNAMIC_SHAPE_INFO="$shape"
  local -a extra=()
  if [[ "$out_calib" != "none" ]]; then
    extra+=(--output_calib_precision="$out_calib")
  fi
  "$OPT_TOOL" \
    --model_file="${MODEL_ROOT}/${m_dir}/inference.pdmodel" \
    --param_file="${MODEL_ROOT}/${m_dir}/inference.pdiparams" \
    --optimize_out="${OUTPUT_ROOT}/${name}_armv7" \
    --valid_targets=arm \
    --enable_fp16=false \
    --analytic_input_quant=true \
    --analytic_input_dtype=uint8 \
    "${extra[@]}" \
    --record_tailoring_info=true
  # Explicitly do not pass --quant_model / --quant_type (breaks armv7 det).
  cp -a "${OUTPUT_ROOT}/${name}_armv7.nb" "${OUT_ASSETS}/${name}_armv7.nb"
  ls -lh "${OUT_ASSETS}/${name}_armv7.nb"
}

# Shapes align with production tier / rec buffers (see HOST_PADDLE_USE / NativePaddleEngine)
optimize_one det_mono det "x:1,1,64,64:1,1,1280,1280:1,1,4096,4096" uint8
optimize_one rec_v3_mono rec_v3 "x:1,1,48,32:1,1,48,320:1,1,48,1280" none
optimize_one rec_numeric_mono rec_numeric "x:1,1,48,32:1,1,48,320:1,1,48,1280" none

echo "Done. Assets: $OUT_ASSETS"
echo "Tailor lists (merge into paddle-models/seed/armv7 if library rebuild): $OUTPUT_ROOT/*_armv7/"
