#!/usr/bin/env bash
# Build amd64/linux Paddle-Lite light SO from the same pin + patches as Android.
# Output: third_party/paddle/artifact/linux-x86_64/libpaddle_light_api_shared.so
#         (and copy under scratch for heatmap stage host)
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
VE=$(cd "$HERE/.." && pwd)
PIN="$VE/third_party/paddle"
SRC="$PIN/src"
OUT="$PIN/artifact/linux-x86_64"
SCRATCH="${SCRATCH:-/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/scratch/heatmap-stage-host}"
IMAGE="${PADDLE_LINUX_IMAGE:-paddle-build-int8-20.04}"

mkdir -p "$OUT" "$SCRATCH" "$SRC/bin"

if [[ ! -d "$SRC/lite" ]]; then
  echo "ERROR: pin src missing at $SRC (need checked-out Paddle-Lite pin)" >&2
  exit 1
fi

echo "Building Linux light via $IMAGE …"
docker run --rm \
  -v "$SRC:/pin-src:ro" \
  -v "$PIN/patches:/patches:ro" \
  -v "$PIN/patches-int8:/patches-int8:ro" \
  -v "$PIN/scripts/apply_patches.sh:/apply_patches.sh:ro" \
  -v "$PIN/scripts/apply_int8_patches.sh:/apply_int8_patches.sh:ro" \
  -v "$OUT:/output" \
  -e "PADDLE_ALLOW_FAST_MATH=${PADDLE_ALLOW_FAST_MATH:-0}" \
  "$IMAGE" \
  bash -c 'set -euo pipefail
    rm -rf /workspace/Paddle-Lite
    # copy pin (not link) so patches can write
    cp -a /pin-src /workspace/Paddle-Lite
    cd /workspace/Paddle-Lite
    git config --global --add safe.directory /workspace/Paddle-Lite || true
    # third-party deps (pin tree often omits unpacked third-party/)
    # Preserve pin flatbuffers pre-build (DENSE_TENSOR) across tarball fetch — same as
    # run-android-historical.sh.
    FBS_SAVE=""
    if [[ -d third-party/flatbuffers/pre-build ]]; then
      FBS_SAVE=$(mktemp -d)
      cp -a third-party/flatbuffers/pre-build "$FBS_SAVE/"
      echo "saved pin flatbuffers pre-build (DENSE_TENSOR)"
    fi
    if [[ ! -d third-party/gflags || -z "$(ls -A third-party/gflags 2>/dev/null || true)" ]]; then
      echo "Fetching Paddle-Lite third-party-651c7c4.tar.gz …"
      wget -q -O /tmp/tp.tgz \
        https://paddlelite-data.bj.bcebos.com/third_party_libs/third-party-651c7c4.tar.gz
      mkdir -p third-party
      tar -xzf /tmp/tp.tgz -C third-party --strip-components=1
      rm -f /tmp/tp.tgz
    fi
    if [[ -n "$FBS_SAVE" && -d "$FBS_SAVE/pre-build" ]]; then
      mkdir -p third-party/flatbuffers
      rm -rf third-party/flatbuffers/pre-build
      cp -a "$FBS_SAVE/pre-build" third-party/flatbuffers/pre-build
      rm -rf "$FBS_SAVE"
      echo "restored pin flatbuffers pre-build over tarball"
    fi
    # Prefer full publish (with_extra calib kernels). tiny_publish lacks calib for u8 .nb.
    # Use full_publish target when available.
    bash /apply_patches.sh
    bash /apply_int8_patches.sh
    # product-safe flags (mirror android historical when possible)
    if [[ "${PADDLE_ALLOW_FAST_MATH:-0}" != "1" ]]; then
      for f in cmake/os/common.cmake cmake/linux.cmake; do
        [[ -f "$f" ]] || continue
        sed -i "s/-ffast-math//g; s/-Ofast/-O2/g" "$f" || true
      done
    fi
    chmod +x lite/tools/build_linux.sh
    # full_publish so calib/u8 kernels exist for prod u8fp* .nb files
    ./lite/tools/build_linux.sh --arch=x86 --with_extra=ON --with_log=ON \
      --with_exception=ON --with_cv=OFF --with_python=OFF \
      --with_static_lib=OFF --with_avx=ON \
      full_publish
    BUILD=$(ls -d build.lite.linux* 2>/dev/null | head -1)
    LIGHT=$(find "$BUILD" -name "libpaddle_light_api_shared.so" | head -1)
    FULL=$(find "$BUILD" -name "libpaddle_full_api_shared.so" | head -1 || true)
    if [[ -z "$LIGHT" || ! -f "$LIGHT" ]]; then
      echo "FAIL: no light SO; found:"
      find "$BUILD" -name "*.so" | head -40
      exit 1
    fi
    cp -a "$LIGHT" /output/libpaddle_light_api_shared.so
    [[ -n "$FULL" && -f "$FULL" ]] && cp -a "$FULL" /output/libpaddle_full_api_shared.so || true
    ls -lh /output/
    for need in uint8_to_fp32 uint8_to_fp16 fp32_to_uint8; do
      strings /output/libpaddle_light_api_shared.so | grep -q "$need" \
        && echo "OK stamp $need" \
        || echo "WARN missing $need"
    done
    echo LINUX_LIGHT_BUILD_DONE
  '

cp -a "$OUT/libpaddle_light_api_shared.so" "$SCRATCH/"
sha256sum "$OUT/libpaddle_light_api_shared.so" | tee "$OUT/SHA256"
ls -lh "$OUT"
echo "Linux light ready: $OUT/libpaddle_light_api_shared.so"
