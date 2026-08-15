#!/usr/bin/env bash
# Produce arm64 (armv8) product models: uint8 feed → fp32 mid-graph.
#
# Same recipe as optimize_armv7_prod_u8fp32_u8.sh (valid_targets=arm, no enable_fp16).
# Used for phone fp32 vs emu/fp16 diagnostic (2026-08-06); not the default ship path.
#
#   det:     uint8 in → fp32 → uint8 heatmap out  (--output_calib_precision=uint8)
#   rec_*:   uint8 in → fp32 CTC logits out       (no output calib)
#
# Outputs:
#   app/src/main/assets/paddle/prod_u8fp32_u8/{det,rec_v3,rec_numeric}_armv8.nb
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../../../.." && pwd)"
OPT_TOOL="${OPT_TOOL:-/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/paddle-build/output/int8_linux/opt_linux_x86_int8}"
MODEL_ROOT="${MODEL_ROOT:-/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/research/models}"
OUT_ASSETS="${OUT_ASSETS:-$REPO_ROOT/app/src/main/assets/paddle/prod_u8fp32_u8}"
OUTPUT_ROOT="${OUTPUT_ROOT:-/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/scratch/armv8-prod-u8fp32-u8}"

if [[ ! -x "$OPT_TOOL" ]]; then
  echo "ERROR: opt tool not executable: $OPT_TOOL" >&2
  exit 1
fi
mkdir -p "$OUT_ASSETS" "$OUTPUT_ROOT"

optimize_one() {
  local m_dir=$1 name=$2 shape=$3 out_calib=${4:-none}
  echo "=== armv8 u8→fp32 $name (output_calib=$out_calib) ==="
  export NNADAPTER_DYNAMIC_SHAPE_INFO="$shape"
  local -a extra=()
  if [[ "$out_calib" != "none" ]]; then
    extra+=(--output_calib_precision="$out_calib")
  fi
  "$OPT_TOOL" \
    --model_file="${MODEL_ROOT}/${m_dir}/inference.pdmodel" \
    --param_file="${MODEL_ROOT}/${m_dir}/inference.pdiparams" \
    --optimize_out="${OUTPUT_ROOT}/${name}_armv8" \
    --valid_targets=arm \
    --enable_fp16=false \
    --analytic_input_quant=true \
    --analytic_input_dtype=uint8 \
    "${extra[@]}" \
    --record_tailoring_info=true
  cp -a "${OUTPUT_ROOT}/${name}_armv8.nb" "${OUT_ASSETS}/${name}_armv8.nb"
  ls -lh "${OUT_ASSETS}/${name}_armv8.nb"
}

optimize_one det_mono det "x:1,1,64,64:1,1,1280,1280:1,1,4096,4096" uint8
optimize_one rec_v3_mono rec_v3 "x:1,1,48,32:1,1,48,320:1,1,48,1280" none
optimize_one rec_numeric_mono rec_numeric "x:1,1,48,32:1,1,48,320:1,1,48,1280" none

echo "Done. Assets: $OUT_ASSETS"
