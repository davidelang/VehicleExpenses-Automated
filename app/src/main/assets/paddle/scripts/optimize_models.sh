#!/bin/bash
# Unified Model Optimization Script for Paddle-Lite
# Supports both ARGB (3-channel) and Monochrome (1-channel) models with Dynamic Shapes.

OPT_TOOL="./opt_linux_x86"
MODEL_ROOT="./models"
ASSET_ROOT="../../app/src/main/assets/paddle"

# Ensure we are in the research directory or provide full paths
if [ ! -f "$OPT_TOOL" ]; then
    echo "Error: $OPT_TOOL not found. Please run this script from the dev-ai-interaction/research directory."
    exit 1
fi

optimize_model() {
    local m_dir=$1
    local out_base=$2
    local shape_info=$3
    
    echo "--- Optimizing $out_base ---"
    export NNADAPTER_DYNAMIC_SHAPE_INFO="$shape_info"
    
    # ARM Target (v7 and v8 use the same .nb from 'arm' target)
    $OPT_TOOL \
      --model_file="${MODEL_ROOT}/${m_dir}/inference.pdmodel" \
      --param_file="${MODEL_ROOT}/${m_dir}/inference.pdiparams" \
      --optimize_out="${ASSET_ROOT}/${out_base}_armv7" \
      --valid_targets=arm
    cp "${ASSET_ROOT}/${out_base}_armv7.nb" "${ASSET_ROOT}/${out_base}_armv8.nb"
    
    # x86 Target
    $OPT_TOOL \
      --model_file="${MODEL_ROOT}/${m_dir}/inference.pdmodel" \
      --param_file="${MODEL_ROOT}/${m_dir}/inference.pdiparams" \
      --optimize_out="${ASSET_ROOT}/${out_base}_x86_64" \
      --valid_targets=x86
}

echo "Generating ARGB Models (3 Channels)..."
# 1. Detection ARGB (128-4000, optimized for 1280)
optimize_model "det" "det_v4_4000" "x:1,3,128,128:1,3,1280,1280:1,3,4000,4000"
# 2. Recognition V3 ARGB
optimize_model "rec_v3" "rec_v3" "x:1,3,48,32:1,3,48,320:1,3,48,1280"
# 3. Numeric V2 ARGB
optimize_model "en_number/en_number_mobile_v2.0_rec_infer" "rec_numeric" "x:1,3,32,32:1,3,32,320:1,3,32,1024"

echo -e "\nGenerating Monochrome Models (1 Channel)..."
# 1. Detection Mono
optimize_model "det_mono" "det_v4_4000_mono" "x:1,1,128,128:1,1,1280,1280:1,1,4000,4000"
# 2. Recognition V3 Mono
optimize_model "rec_v3_mono" "rec_v3_mono" "x:1,1,48,32:1,1,48,320:1,1,48,1280"
# 3. Numeric V2 Mono
optimize_model "rec_numeric_mono" "rec_numeric_mono" "x:1,1,32,32:1,1,32,320:1,1,32,1024"

echo "All models optimized and deployed to assets."
