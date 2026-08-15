#!/usr/bin/env bash
# Build heatmap_stage_host against Linux light SO.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
VE=$(cd "$HERE/.." && pwd)
SRC="$VE/third_party/paddle/tests/heatmap_stage_host/heatmap_stage_host.cpp"
INC="$VE/app/src/main/cpp/include"
LIGHT="${LIGHT_SO:-$VE/third_party/paddle/artifact/linux-x86_64/libpaddle_light_api_shared.so}"
OUT_DIR="${OUT_DIR:-$VE/third_party/paddle/tests/heatmap_stage_host/out}"
mkdir -p "$OUT_DIR"

if [[ ! -f "$LIGHT" ]]; then
  echo "Missing Linux light SO: $LIGHT" >&2
  echo "Run: scripts/build-linux-light.sh" >&2
  exit 1
fi
if [[ ! -f "$INC/paddle/paddle_api.h" ]]; then
  echo "Missing headers under $INC/paddle" >&2
  exit 1
fi

LIGHT_DIR=$(cd "$(dirname "$LIGHT")" && pwd)
# Intel OpenMP (MKLML) required by current Linux light SOs
if [[ ! -f "$LIGHT_DIR/libiomp5.so" ]]; then
  echo "Fetching libiomp5.so (MKLML)…"
  TMP=$(mktemp -d)
  wget -q -O "$TMP/mklml.tgz" \
    http://paddlepaddledeps.bj.bcebos.com/Glibc225_vsErf_mklml_lnx_2019.0.1.20181227.tgz
  tar -xzf "$TMP/mklml.tgz" -C "$TMP"
  IOMP=$(find "$TMP" -name 'libiomp5.so' | head -1)
  cp -a "$IOMP" "$LIGHT_DIR/libiomp5.so"
  rm -rf "$TMP"
fi

g++ -O2 -std=c++14 -I"$INC" -I"$INC/paddle" \
  "$SRC" -o "$OUT_DIR/heatmap_stage_host" \
  -L"$LIGHT_DIR" -Wl,-rpath,"$OUT_DIR" -Wl,-rpath,"$LIGHT_DIR" \
  -Wl,--unresolved-symbols=ignore-in-shared-libs \
  -lpaddle_light_api_shared -lpthread -ldl

cp -a "$LIGHT" "$OUT_DIR/libpaddle_light_api_shared.so"
cp -a "$LIGHT_DIR/libiomp5.so" "$OUT_DIR/libiomp5.so"
ls -lh "$OUT_DIR"
echo "Built $OUT_DIR/heatmap_stage_host"
echo "Run with: LD_LIBRARY_PATH=$OUT_DIR:\$LD_LIBRARY_PATH $OUT_DIR/heatmap_stage_host …"
