#!/bin/bash
# Convert mono FP32 models to INT8 (.nb) with dynamic weight quant.
# INT8 dynamic quant (`--quant_model=true --quant_type=QUANT_INT8`); set `OPT_TOOL` to host `opt_linux_x86_int8` from paddle-build output.
# Requires INT8-capable opt built from paddle-build-int8-20.04 (analytic_input_quant_pass).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OPT_TOOL="${OPT_TOOL:-${SCRIPT_DIR}/../paddle-build/output/int8_linux/opt_linux_x86_int8}"
MODEL_ROOT="${MODEL_ROOT:-${SCRIPT_DIR}/models}"
OUTPUT_ROOT="${OUTPUT_ROOT:-${SCRIPT_DIR}/../output/int8-mono-nb}"

if [[ ! -x "$OPT_TOOL" ]]; then
  echo "ERROR: opt tool not found or not executable: $OPT_TOOL" >&2
  exit 1
fi

mkdir -p "$OUTPUT_ROOT"

optimize_model_int8() {
    local m_dir=$1
    local out_base=$2
    local shape_info=$3

    echo "--- INT8 optimizing $out_base ---"
    export NNADAPTER_DYNAMIC_SHAPE_INFO="$shape_info"

    "$OPT_TOOL" \
      --model_file="${MODEL_ROOT}/${m_dir}/inference.pdmodel" \
      --param_file="${MODEL_ROOT}/${m_dir}/inference.pdiparams" \
      --optimize_out="${OUTPUT_ROOT}/${out_base}_int8_armv7" \
      --valid_targets=arm \
      --quant_model=true \
      --quant_type=QUANT_INT8

    cp "${OUTPUT_ROOT}/${out_base}_int8_armv7.nb" "${OUTPUT_ROOT}/${out_base}_int8_armv8.nb"

    "$OPT_TOOL" \
      --model_file="${MODEL_ROOT}/${m_dir}/inference.pdmodel" \
      --param_file="${MODEL_ROOT}/${m_dir}/inference.pdiparams" \
      --optimize_out="${OUTPUT_ROOT}/${out_base}_int8_x86_64" \
      --valid_targets=x86 \
      --quant_model=true \
      --quant_type=QUANT_INT8
}

# Detection (128-4000)
optimize_model_int8 "det_mono" "det_v4_4000_mono" "x:1,1,128,128:1,1,1280,1280:1,1,4000,4000"

# Recognition V3 + Numeric (simplified to 48 high / 1024 wide per current 1024x48 rec buffer; dropped legacy 32/320)
optimize_model_int8 "rec_v3_mono" "rec_v3_mono" "x:1,1,48,1024:1,1,48,1024:1,1,48,1024"
optimize_model_int8 "rec_numeric_mono" "rec_numeric_mono" "x:1,1,48,1024:1,1,48,1024:1,1,48,1024"

echo "INT8 mono conversion done. Output: ${OUTPUT_ROOT}/*_int8_*.nb"