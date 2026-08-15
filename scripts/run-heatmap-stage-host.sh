#!/usr/bin/env bash
# Run Linux amd64 heatmap stage on research photos (both model packs).
# Requires: scripts/build-linux-light.sh && scripts/build-heatmap-stage-host.sh
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
VE=$(cd "$HERE/.." && pwd)
PHOTOS="${PHOTOS:-/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/research/photos/pump}"
OUT="${OUT:-/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/scratch/heatmap-stage-host/run-$(date +%Y%m%d-%H%M%S)}"
BIN="${BIN:-$VE/third_party/paddle/tests/heatmap_stage_host/out/heatmap_stage_host}"
ASSETS_X86="$VE/app/src/x86_64/assets/paddle"
HOST_NBS="$VE/third_party/paddle/prod_models_host"
WORKDIR="$OUT/work"
mkdir -p "$OUT" "$WORKDIR"

if [[ ! -x "$BIN" ]]; then
  echo "Building host binary…"
  bash "$HERE/build-heatmap-stage-host.sh"
fi
[[ -x "$BIN" ]] || { echo "no binary"; exit 1; }

# Convert research photos → mono PGM (OpenCV if available, else ImageMagick)
python3 - <<'PY' "$PHOTOS" "$WORKDIR"
import sys, os
from pathlib import Path
src, dst = Path(sys.argv[1]), Path(sys.argv[2])
dst.mkdir(parents=True, exist_ok=True)
try:
    import cv2
except ImportError:
    print("Need opencv-python for mono convert", file=sys.stderr)
    sys.exit(1)
exts = {".jpg", ".jpeg", ".png", ".dng"}
n = 0
for p in sorted(src.iterdir()):
    if p.suffix.lower() not in exts:
        continue
    # DNG: try imread (may fail); skip if unreadable
    im = cv2.imread(str(p), cv2.IMREAD_GRAYSCALE)
    if im is None:
        # try color then gray
        im = cv2.imread(str(p), cv2.IMREAD_COLOR)
        if im is None:
            print("skip", p.name)
            continue
        im = cv2.cvtColor(im, cv2.COLOR_BGR2GRAY)
    out = dst / (p.stem + ".pgm")
    # write P5
    h, w = im.shape[:2]
    with open(out, "wb") as f:
        f.write(f"P5\n{w} {h}\n255\n".encode())
        f.write(im.tobytes())
    n += 1
print(f"converted {n} → {dst}")
PY

export LD_LIBRARY_PATH="$(dirname "$BIN"):${LD_LIBRARY_PATH:-}"

run_pack() {
  local pack=$1 path_id=$2 det=$3
  local jsonl="$OUT/${path_id}_results.jsonl"
  : >"$jsonl"
  echo "=== Linux pack $path_id det=$det ==="
  local i=0
  for pgm in "$WORKDIR"/*.pgm; do
    [[ -f "$pgm" ]] || continue
    i=$((i + 1))
    echo "  [$i] $(basename "$pgm")"
    "$BIN" --det "$det" --image "$pgm" --product-path "$path_id" \
      --label "linux-x86_64" --out-jsonl "$jsonl" --threads 1 \
      >/dev/null || echo "FAIL $pgm" >&2
  done
  # manifest
  cat >"$OUT/${path_id}_manifest.json" <<EOF
{
  "label": "linux-x86_64",
  "product_path": "$path_id",
  "det": "$det",
  "n": $i,
  "so": "$(sha256sum "$(dirname "$BIN")/libpaddle_light_api_shared.so" | awk '{print $1}')",
  "photos": "$PHOTOS"
}
EOF
  echo "Wrote $jsonl ($i lines)"
}

run_pack fp16 uint8_fp16_u8 "$HOST_NBS/prod_u8fp16_det_x86_64.nb"
run_pack fp32 uint8_fp32_u8 "$ASSETS_X86/prod_u8fp32_u8/det_x86_64.nb"

echo "HOST DONE under $OUT"
ls -la "$OUT"
