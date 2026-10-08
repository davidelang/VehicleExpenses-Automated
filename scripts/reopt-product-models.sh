#!/usr/bin/env bash
# Re-opt product .nb models with the current pin's opt (must match host/device light SO pin).
#
# Usage (opt from `./third_party/fetch-deps build paddle` → build-opt step):
#   ./scripts/reopt-product-models.sh
#
# Step 0 regenerates the mono pdmodels ($MODEL_ROOT/{det,rec_v3,rec_numeric}_mono +
# mono_norm.json) from the original 3-channel models with convert_mono.py
# (sum-of-weights). REOPT_SKIP_CONVERT=1 reuses existing mono dirs.
# PADDLE_PY = python with paddle (static API; FLAGS_enable_pir_api=0 is set here).
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

# Explicit feed normalisation per model, pixel units: x = (u - mean) / std.
# Must match the mono_norm.json written by convert_mono.py (sum-of-weights
# version); refuses legacy (np.mean) conversions.
expect_norm() {  # $1 = mono model dir under MODEL_ROOT
  local d=$1 mean std
  case "$d" in
    det_mono)                     mean=128;   std=58.395 ;;
    rec_v3_mono|rec_numeric_mono) mean=127.5; std=127.5 ;;
    *) echo "ERROR: no normalisation entry for $d" >&2; return 1 ;;
  esac
  python3 - "$MODEL_ROOT/$d/mono_norm.json" "$mean" "$std" <<'PY' || return 1
import json, sys
p, m, s = sys.argv[1], float(sys.argv[2]), float(sys.argv[3])
try:
    n = json.load(open(p))
except OSError:
    sys.exit(f"ERROR: {p} missing: re-run convert_mono.py (sum-of-weights version)")
if n.get("legacy_mean") or abs(n["mean"] - m) > 1e-6 or abs(n["std"] - s) > 1e-3:
    sys.exit(f"ERROR: {p}: mean={n['mean']} std={n['std']} legacy={n.get('legacy_mean')}; expected {m}/{s}")
PY
  echo "--analytic_input_mean=$mean --analytic_input_std=$std"
}

CONVERT_MONO="$VE/app/src/main/assets/paddle/scripts/convert_mono.py"
PADDLE_PY="${PADDLE_PY:-/home/dlang/miniconda3/envs/paddle_env_v3/bin/python}"
if [[ "${REOPT_SKIP_CONVERT:-0}" != "1" ]]; then
  if [[ "$(basename "$MODEL_ROOT")" != models ]]; then
    echo "ERROR: convert_mono.py batch mode writes <root>/models/*_mono; MODEL_ROOT must end in /models (or REOPT_SKIP_CONVERT=1)" >&2
    exit 1
  fi
  echo "=== step 0: convert_mono.py --models-root $(dirname "$MODEL_ROOT") ==="
  FLAGS_enable_pir_api=0 "$PADDLE_PY" "$CONVERT_MONO" --models-root "$(dirname "$MODEL_ROOT")"
fi

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
  expect_norm "$d" >/dev/null || exit 1
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
  local nf; nf=$(expect_norm "$model_dir") || exit 1
  local -a norm; read -ra norm <<<"$nf"
  local -a args=(
    --model_file="$MODEL_ROOT/$model_dir/inference.pdmodel"
    --param_file="$MODEL_ROOT/$model_dir/inference.pdiparams"
    --optimize_out="$out_base"
    --valid_targets="$target"
    --optimize_out_type=naive_buffer
    --analytic_input_quant=true
    --analytic_input_dtype=uint8
    "${norm[@]}"
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
  # opt builds turn LOG(INFO) into VLOG(1); OPT_GLOG_V=1 shows the pass lines
  # ("analytic_input_quant_pass: ... folded input offset ...") at the cost of a long log.
  GLOG_v="${OPT_GLOG_V:-0}" "$OPT_TOOL" "${args[@]}" 2>&1 | tee "$SCRATCH/log_${name}_${suffix}.txt"
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
  # Plain cp: overwrite in place, keep the dest's owner/group/mode. (cp -a tries to set
  # times/owner, which fails when ai-coder writes a group-writable file owned by dlang.)
  cp "$src" "$dest_dir/$dest_name"
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
