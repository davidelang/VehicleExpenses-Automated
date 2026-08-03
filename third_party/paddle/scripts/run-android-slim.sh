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

# third-party libs (once per container life — download if missing)
if [[ ! -d third-party ]]; then
  wget -q https://paddlelite-data.bj.bcebos.com/third_party_libs/third-party-651c7c4.tar.gz
  tar -zxf third-party-651c7c4.tar.gz
  rm -f third-party-651c7c4.tar.gz
fi

# INT8 / uint8 calib + analytic quant layer (on top of pin SHA)
chmod +x /apply_int8_patches.sh
PATCH_ROOT=/patches-int8 bash /apply_int8_patches.sh

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
CHECK="/output/$ABI/libpaddle_lite_jni.so"
[[ -f "/output/$ABI/libpaddle_light_api_shared.so" ]] && CHECK="/output/$ABI/libpaddle_light_api_shared.so"
for need in int8_to_fp32 int8_to_fp16 uint8_to_fp32 uint8_to_fp16 fp32_to_uint8; do
  strings "$CHECK" | grep -q "$need" || { echo "FAIL missing $need in $CHECK"; exit 1; }
done
ls -lh "/output/$ABI/"
echo "SLIM_BUILD_DONE abi=$ABI"
