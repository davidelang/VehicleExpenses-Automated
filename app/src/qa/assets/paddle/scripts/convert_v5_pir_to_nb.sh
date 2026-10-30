#!/usr/bin/env bash
# Convert PP-OCRv5 det models (PIR inference.json) → classic pdmodel → mono → product .nb
#
# Requires:
#   - conda env paddle_env   (paddle 3.x + paddle2onnx + onnxsim)  OR set PADDLE3_PY
#   - conda env paddle_env_v3 (paddle 2.6.x + x2paddle)           OR set PADDLE26_PY
#   - third_party/paddle/artifact/linux-x86_64/opt_linux_x86_int8
#
# Usage (from repo worktree root, e.g. agent-4):
#   ./app/src/main/assets/paddle/scripts/convert_v5_pir_to_nb.sh
#   ./app/src/main/assets/paddle/scripts/convert_v5_pir_to_nb.sh mobile   # mobile only
#   ./app/src/main/assets/paddle/scripts/convert_v5_pir_to_nb.sh server   # server only
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
# scripts → paddle → assets → main → src → app → worktree (6 up)
WT=$(cd "$SCRIPT_DIR/../../../../../.." && pwd)
# Prefer monorepo root for sandbox models
if [[ -d "$WT/../dev-ai-interaction/research/models/det_ab_src" ]]; then
  REPO=$(cd "$WT/.." && pwd)
elif [[ -d "$WT/dev-ai-interaction/research/models/det_ab_src" ]]; then
  REPO=$WT
else
  REPO=${VE_REPO:-/home/dlang/git/VehicleExpenses-automated}
fi

SRC_ROOT=${SRC_ROOT:-$REPO/dev-ai-interaction/research/models/det_ab_src}
CLASSIC_ROOT=${CLASSIC_ROOT:-$REPO/dev-ai-interaction/research/models/det_ab_classic_from_pir}
MONO_ROOT=${MONO_ROOT:-$REPO/dev-ai-interaction/research/models/det_ab_mono_product}
SCRATCH=${SCRATCH:-$REPO/dev-ai-interaction/scratch/exp_det_v5_product_opt}
# v5 mobile/server are not scheduled — write next to git-hosted unscheduled nbs, not APK assets.
ASSETS=${ASSETS:-$WT/third_party/paddle/exp_det_ab_unscheduled}
OPT=${OPT_TOOL:-$WT/third_party/paddle/artifact/linux-x86_64/opt_linux_x86_int8}
export LD_LIBRARY_PATH="$(dirname "$OPT"):${LD_LIBRARY_PATH:-}"

PADDLE3_PY=${PADDLE3_PY:-/home/dlang/miniconda3/envs/paddle_env/bin/python}
PADDLE26_PY=${PADDLE26_PY:-/home/dlang/miniconda3/envs/paddle_env_v3/bin/python}
PADDLE2ONNX=${PADDLE2ONNX:-/home/dlang/miniconda3/envs/paddle_env/bin/paddle2onnx}

export PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True

MODELS=(PP-OCRv5_mobile_det PP-OCRv5_server_det)
case "${1:-all}" in
  mobile) MODELS=(PP-OCRv5_mobile_det) ;;
  server) MODELS=(PP-OCRv5_server_det) ;;
  all) ;;
  *) echo "usage: $0 [all|mobile|server]"; exit 2 ;;
esac

mkdir -p "$CLASSIC_ROOT" "$MONO_ROOT" "$SCRATCH" "$ASSETS" /tmp/v5_pir_nb_work

convert_one() {
  local name=$1
  local src=$SRC_ROOT/$name
  local work=/tmp/v5_pir_nb_work/$name
  local classic=$CLASSIC_ROOT/$name
  local mono=$MONO_ROOT/$name
  mkdir -p "$work" "$classic" "$mono"

  echo "======== $name ========"
  [[ -f $src/inference.json && -f $src/inference.pdiparams ]] || {
    echo "missing PIR sources under $src"; return 1
  }

  echo "[1/5] paddle2onnx"
  "$PADDLE2ONNX" \
    --model_dir="$src" \
    --model_filename=inference.json \
    --params_filename=inference.pdiparams \
    --save_file="$work/model.onnx" \
    --opset_version=11 \
    --enable_onnx_checker=True \
    --optimize_tool=None

  local onnx=$work/model.onnx
  if [[ $name == *server* ]]; then
    echo "[1b] onnxsim (fold BN — required for server x2paddle)"
    "$PADDLE3_PY" - <<PY
import onnx
from onnxsim import simplify
m = onnx.load("$work/model.onnx")
for inp in m.graph.input:
    if inp.name == "x":
        t = inp.type.tensor_type
        del t.shape.dim[:]
        for d in (1, 3, 640, 640):
            dim = t.shape.dim.add(); dim.dim_value = d
ms, ok = simplify(m, check_n=0, perform_optimization=True)
assert ok, "onnxsim check failed"
onnx.save(ms, "$work/model_sim.onnx")
print("nodes", len(m.graph.node), "->", len(ms.graph.node),
      "BN left", sum(1 for n in ms.graph.node if n.op_type=="BatchNormalization"))
PY
    onnx=$work/model_sim.onnx
  fi

  echo "[2/5] x2paddle (paddle 2.6) → classic pdmodel"
  rm -rf "$work/x2p"
  "$PADDLE26_PY" -m x2paddle.convert \
    --framework=onnx \
    --model="$onnx" \
    --save_dir="$work/x2p" \
    --input_shape_dict="{'x':[-1, 3, -1, -1]}"
  cp -f "$work/x2p/inference_model/model.pdmodel" "$classic/inference.pdmodel"
  cp -f "$work/x2p/inference_model/model.pdiparams" "$classic/inference.pdiparams"

  echo "[3/5] convert_mono (RGB→1ch mean first conv)"
  "$PADDLE26_PY" - <<PY
import os, numpy as np, paddle
from pathlib import Path
model_dir = Path("$classic") / "inference"
output_dir = Path("$mono")
paddle.enable_static()
exe = paddle.static.Executor(paddle.CPUPlace())
prog, feed_names, fetch_targets = paddle.static.load_inference_model(str(model_dir), exe)
block = prog.block(0)
input_var = block.var(feed_names[0])
print("input", feed_names[0], input_var.shape, "-> [-1,1,-1,-1]")
input_var.desc.set_shape([-1, 1, -1, -1])
for op in block.ops:
    if op.type != "conv2d":
        continue
    wname = op.input("Filter")[0]
    wvar = block.var(wname)
    w = np.array(paddle.static.global_scope().find_var(wname).get_tensor())
    print("first conv", wname, w.shape)
    nw = np.mean(w, axis=1, keepdims=True)
    paddle.static.global_scope().find_var(wname).get_tensor().set(nw, paddle.CPUPlace())
    wvar.desc.set_shape(nw.shape)
    break
else:
    raise SystemExit("no conv2d")
output_dir.mkdir(parents=True, exist_ok=True)
inputs = [block.var(n) for n in feed_names]
paddle.static.save_inference_model(
    str(output_dir / "inference"), inputs, list(fetch_targets), exe, program=prog
)
print("mono ok", output_dir)
PY

  # Product det/mobile: arm mid-graph fp16 is fine on phone.
  # Server det on arm: --enable_fp16=true yields **all-zero heatmaps** on Pixel
  # (product light SO) at every side that does not SEGV; re-opt with
  # enable_fp16=false (uint8→fp32 mid→uint8 heat) restores non-zero heat up to
  # side≈640. Host x86 was always fp32 mid — that is why host looked "fine".
  # Independently, phone still SIGSEGV for server feed side ≳640 (fp16 or fp32).
  echo "[4/5] opt arm + x86 (server arm = fp32 mid; mobile arm = fp16 mid)"
  if [[ $name == *server* ]]; then
    arm_fp16=false
  else
    arm_fp16=true
  fi
  for pair in "arm:${arm_fp16}:armv8" "x86:false:x86_64"; do
    IFS=: read -r tgt fp16 arch <<<"$pair"
    out=$SCRATCH/${name}_${arch}
    "$OPT" \
      --model_file="$mono/inference.pdmodel" \
      --param_file="$mono/inference.pdiparams" \
      --optimize_out="$out" \
      --valid_targets="$tgt" \
      --enable_fp16="$fp16" \
      --analytic_input_quant=true \
      --analytic_input_dtype=uint8 \
      --output_calib_precision=uint8 \
      --record_tailoring_info=true
    # opt writes ${out}.nb
    cp -f "${out}.nb" "$ASSETS/${name}_${arch}.nb"
    ls -lh "$ASSETS/${name}_${arch}.nb"
    echo "  stamps: $(strings "${out}.nb" | grep -E 'uint8_to_fp|fp32_to_uint8' | sort -u | tr '\n' ' ')"
  done
  echo "[5/5] $name done"
}

for m in "${MODELS[@]}"; do
  convert_one "$m"
done

echo "All requested models converted. Assets:"
ls -lh "$ASSETS"/PP-OCRv5_*.nb 2>/dev/null || true
