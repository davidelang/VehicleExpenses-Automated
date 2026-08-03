#!/usr/bin/env bash
# Runs inside ve-paddle-int8 container.
# Env: ABI, ARCH, EXTRA_FLAGS
# Mounts: /pin-src (RO pin tree), /patches-int8, /apply_int8_patches.sh, /output
set -euo pipefail
: "${ABI:?}"
: "${ARCH:?}"

WORKDIR=/workspace/Paddle-Lite
rm -rf "$WORKDIR"
mkdir -p "$WORKDIR"
# copy pin tree (writable work)
tar -C /pin-src --exclude=./bin --exclude=./build -cf - . | tar -C "$WORKDIR" -xf -
cd "$WORKDIR"

# third-party libs: pin may ship empty placeholder dirs (gflags/glog/…);
# replace with official prebuilt tarball when incomplete.
# IMPORTANT: pin's flatbuffers/pre-build matches DENSE_TENSOR rename in framework.fbs
# (post-#10600). The stock third-party-651c7c4 tarball still has LOD_* names and will
# break x86 (selected_rows → fbs traits). Preserve pin pre-build headers across fetch.
need_tp=0
if [[ ! -d third-party ]]; then
  need_tp=1
elif [[ ! -f third-party/gflags/CMakeLists.txt && ! -f third-party/gflags/CMakeLists.txt.in ]]; then
  need_tp=1
fi
if [[ "$need_tp" -eq 1 ]]; then
  echo "Fetching Paddle-Lite third-party-651c7c4.tar.gz …"
  FBS_SAVE=""
  if [[ -d third-party/flatbuffers/pre-build ]]; then
    FBS_SAVE=$(mktemp -d)
    cp -a third-party/flatbuffers/pre-build "$FBS_SAVE/"
    echo "  saved pin flatbuffers pre-build (DENSE_TENSOR schema)"
  fi
  rm -rf third-party
  wget -q https://paddlelite-data.bj.bcebos.com/third_party_libs/third-party-651c7c4.tar.gz
  tar -zxf third-party-651c7c4.tar.gz
  rm -f third-party-651c7c4.tar.gz
  if [[ -n "$FBS_SAVE" && -d "$FBS_SAVE/pre-build" ]]; then
    mkdir -p third-party/flatbuffers
    rm -rf third-party/flatbuffers/pre-build
    cp -a "$FBS_SAVE/pre-build" third-party/flatbuffers/pre-build
    rm -rf "$FBS_SAVE"
    echo "  restored pin flatbuffers pre-build over tarball"
  fi
fi

# INT8 / uint8 calib + analytic quant layer (on top of pin SHA)
# Script is often bind-mounted RO — copy then exec (never chmod the mount).
cp -f /apply_int8_patches.sh /tmp/apply_int8_patches.sh
chmod +x /tmp/apply_int8_patches.sh
PATCH_ROOT=/patches-int8 bash /tmp/apply_int8_patches.sh

# Android x86_64 uses OpenBLAS (WITH_MKL=OFF). Pin mklml.h always #includes <mkl.h>;
# wrap with LITE_WITH_MKL so OpenBLAS builds compile (from patches-x86-openblas).
if [[ "$ARCH" == "x86_64" || "$ARCH" == "x86" ]]; then
  if [[ -d /patches-x86-openblas/code/lite ]]; then
    echo "Applying x86 OpenBLAS (no-MKL) overlay…"
    cp -a /patches-x86-openblas/code/lite/. lite/
  fi
fi

# Disable heavy benchmark target if present
if [[ -f lite/api/tools/benchmark/CMakeLists.txt ]]; then
  echo 'add_custom_target(benchmark_bin)' > lite/api/tools/benchmark/CMakeLists.txt
fi

chmod +x lite/tools/build_android.sh
# shellcheck disable=SC2086
./lite/tools/build_android.sh --arch="$ARCH" --toolchain=clang \
  --with_java=ON --with_cv=OFF --with_extra=ON --with_log=OFF \
  --with_benchmark=OFF \
  --android_stl=c++_static \
  --with_exception=ON \
  $EXTRA_FLAGS

BUILD_DIR=$(ls -d build.lite.android.* 2>/dev/null | head -1)
test -n "$BUILD_DIR" || { echo "FAIL: no build.lite.android.* dir"; ls -la; exit 1; }

mkdir -p "/output/$ABI"
JNI=$(find "$BUILD_DIR" -name 'libpaddle_lite_jni.so' | head -1)
LIGHT=$(find "$BUILD_DIR" -name 'libpaddle_light_api_shared.so' | head -1 || true)
test -n "$JNI" && test -f "$JNI" || { echo "FAIL: jni missing"; find "$BUILD_DIR" -name '*.so' | head -40; exit 1; }

cp -f "$JNI" "/output/$ABI/libpaddle_lite_jni.so"
if [[ -n "${LIGHT:-}" && -f "$LIGHT" ]]; then
  cp -f "$LIGHT" "/output/$ABI/libpaddle_light_api_shared.so"
fi

# Prefer SONAME fix on arm JNI (loader identity)
if command -v patchelf >/dev/null && [[ "$ABI" == arm* ]]; then
  patchelf --set-soname libpaddle_lite_jni.so "/output/$ABI/libpaddle_lite_jni.so" || true
fi

# Optional: java jar from jni build
JAR=$(find "$BUILD_DIR" -name 'PaddlePredictor.jar' | head -1 || true)
if [[ -n "${JAR:-}" && -f "$JAR" ]]; then
  cp -f "$JAR" /output/PaddlePredictor.jar
fi

echo "=== kernel string checks ($ABI) ==="
# Prefer light_api (kernels); fall back to jni. Use grep -a (binary-safe) — some
# images lack binutils strings or pipefail-false-negative on short stamps.
CHECKS=()
[[ -f "/output/$ABI/libpaddle_light_api_shared.so" ]] && CHECKS+=("/output/$ABI/libpaddle_light_api_shared.so")
[[ -f "/output/$ABI/libpaddle_lite_jni.so" ]] && CHECKS+=("/output/$ABI/libpaddle_lite_jni.so")
[[ ${#CHECKS[@]} -gt 0 ]] || { echo "FAIL: no .so to check under /output/$ABI"; exit 1; }
for need in int8_to_fp32 int8_to_fp16 uint8_to_fp32 uint8_to_fp16 fp32_to_uint8; do
  found=0
  for CHECK in "${CHECKS[@]}"; do
    if grep -aFq "$need" "$CHECK" 2>/dev/null; then
      found=1
      break
    fi
  done
  [[ "$found" -eq 1 ]] || { echo "FAIL missing $need in ${CHECKS[*]}"; exit 1; }
done
ls -lh "/output/$ABI/"
echo "SLIM_BUILD_DONE abi=$ABI"
