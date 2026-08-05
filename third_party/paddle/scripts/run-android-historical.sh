#!/usr/bin/env bash
# In-container historical VE Android slim (or tailor) build.
# Env: ABI, ARCH, EXTRA_FLAGS, PADDLE_PROFILE=slim|tailor
# Mounts: /pin-src, /patches, /patches-int8, /apply_patches.sh, /apply_int8_patches.sh,
#         /patch_x86_thin_jni.py, /patch_tailor_depthwise_common.py (optional),
#         /tailor_models (optional), /output
set -euo pipefail
: "${ABI:?}"
: "${ARCH:?}"
PROFILE=${PADDLE_PROFILE:-slim}

WORKDIR=/workspace/Paddle-Lite
rm -rf "$WORKDIR"
mkdir -p "$WORKDIR"
# copy pin tree (writable work); products go to /output only
tar -C /pin-src --exclude=./bin --exclude=./build -cf - . | tar -C "$WORKDIR" -xf -
cd "$WORKDIR"

# third-party libs: pin may ship empty placeholders; fetch official tarball when incomplete.
# Preserve pin flatbuffers pre-build (DENSE_TENSOR) across fetch.
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

# 1) Full historical patch set (build-system + external + code) — the Jul working recipe
cp -f /apply_patches.sh /tmp/apply_patches.sh
chmod +x /tmp/apply_patches.sh
PATCHES=/patches bash /tmp/apply_patches.sh

# 2) INT8 / uint8 calib + analytic quant layer
cp -f /apply_int8_patches.sh /tmp/apply_int8_patches.sh
chmod +x /tmp/apply_int8_patches.sh
PATCH_ROOT=/patches-int8 bash /tmp/apply_int8_patches.sh

# 3) Thin x86 jni (753KB wrapper + light) — historical slim_x86 shape
if [[ "$ARCH" == "x86_64" || "$ARCH" == "x86" ]]; then
  if [[ -f /patch_x86_thin_jni.py ]]; then
    echo "Applying thin x86 jni patch…"
    python3 /patch_x86_thin_jni.py lite/api/CMakeLists.txt || {
      echo "WARN: thin jni patch failed (api_CMakeLists may already differ); continuing"
    }
  fi
fi

# Disable heavy benchmark target if present (publish steps must not require the binary)
if [[ -f lite/api/tools/benchmark/CMakeLists.txt ]]; then
  echo 'add_custom_target(benchmark_bin)' > lite/api/tools/benchmark/CMakeLists.txt
fi
# Historical lite_CMakeLists still tried to cp benchmark_bin on x86 tiny_publish; patches
# neutralize that, but belt-and-suspenders here too:
if [[ -f lite/CMakeLists.txt ]]; then
  sed -i 's|COMMAND cp "${PADDLE_BINARY_DIR}/lite/api/tools/benchmark/benchmark_bin" "${INFER_LITE_PUBLISH_ROOT}/bin"|COMMAND true  # skip benchmark_bin|g' lite/CMakeLists.txt || true
  sed -i 's|add_dependencies(tiny_publish_cxx_lib benchmark_bin)|# add_dependencies(tiny_publish_cxx_lib benchmark_bin)|g' lite/CMakeLists.txt || true
  sed -i 's|add_dependencies(publish_inference_cxx_lib benchmark_bin)|# add_dependencies(publish_inference_cxx_lib benchmark_bin)|g' lite/CMakeLists.txt || true
fi

chmod +x lite/tools/build_android.sh

# Map ARCH/ABI → subdirectory under /tailor_models (paddle-models pin layout).
tailor_subdir_for() {
  case "$1" in
    armv8|arm64-v8a) echo armv8 ;;
    armv7|armeabi-v7a) echo armv7 ;;
    x86_64|x86) echo x86_64 ;;
    *) echo "$1" ;;
  esac
}

STRIP_FLAGS=""
if [[ "$PROFILE" == "tailor" ]]; then
  TAILOR_SUB="$(tailor_subdir_for "$ARCH")"
  if [[ ! -d "/tailor_models/$TAILOR_SUB" ]]; then
    # fallback: ABI-named dir
    TAILOR_SUB="$(tailor_subdir_for "$ABI")"
  fi
  if [[ ! -d "/tailor_models/$TAILOR_SUB" ]]; then
    echo "FAIL: tailor profile needs /tailor_models/$TAILOR_SUB (.nb + .tailored_*)" >&2
    echo "  (mounted from paddle-models/src or PADDLE_TAILOR_DIR; have: $(ls /tailor_models 2>/dev/null | tr '\n' ' '))" >&2
    exit 1
  fi
  if [[ ! -f "/tailor_models/$TAILOR_SUB/.tailored_kernels_list" ]]; then
    echo "FAIL: missing /tailor_models/$TAILOR_SUB/.tailored_kernels_list" >&2
    exit 1
  fi
  if [[ -f /patch_tailor_depthwise_common.py ]]; then
    python3 /patch_tailor_depthwise_common.py lite/kernels/CMakeLists.txt || true
  fi
  if [[ -f /patch_tailor_conv_copy_safe.py ]]; then
    python3 /patch_tailor_conv_copy_safe.py lite/kernels/CMakeLists.txt || true
  fi
  STRIP_FLAGS="--with_strip=ON --opt_model_dir=/tailor_models/$TAILOR_SUB"
  echo "PROFILE=tailor (LITE_BUILD_TAILOR opt_model_dir=/tailor_models/$TAILOR_SUB)"
else
  echo "PROFILE=slim (tiny_publish, full kernel set, host strip-unneeded later)"
fi

# LTO + --gc-sections drops static KernelRegistrar (x86 tailor historically;
# armv7 slim/tailor product also: light API Run() + all-zero heatmaps despite
# stamp substrings in the SO). Apply keep-registry for ALL Android builds.
# Do NOT use USE_LITE_KERNEL force-refs: touch_* is DCE'd within strip TUs at -O3
# (unreferenced), so those refs become undefined symbols at link.
if [[ -f cmake/os/common.cmake ]]; then
  sed -i -E 's/-flto(=thin)?//g' cmake/os/common.cmake
  echo "keep-registry: stripped -flto from cmake/os/common.cmake"
fi
if [[ -f cmake/postproject.cmake ]]; then
  sed -i 's/check_linker_flag(-Wl,--gc-sections)/# check_linker_flag(-Wl,--gc-sections)  # keep registries/' \
    cmake/postproject.cmake 2>/dev/null || true
fi
for f in cmake/postproject.cmake lite/CMakeLists.txt; do
  [[ -f "$f" ]] || continue
  sed -i 's/-Wl,--gc-sections//g' "$f" 2>/dev/null || true
done
# Also strip LTO from CMAKE_CXX_FLAGS_RELEASE if set in toolchain files.
for f in cmake/os/common.cmake cmake/linux.cmake cmake/cross_compiling/android.cmake; do
  [[ -f "$f" ]] || continue
  sed -i -E 's/-flto(=thin)?//g' "$f" 2>/dev/null || true
done
if [[ -f lite/core/op_registry.h ]] && ! grep -q 'TAILOR_KEEP_REGISTRY' lite/core/op_registry.h; then
  sed -i 's/static paddle::lite::KernelRegistrar  */static paddle::lite::KernelRegistrar __attribute__((used)) /' \
    lite/core/op_registry.h && \
    echo "/* TAILOR_KEEP_REGISTRY */" >>lite/core/op_registry.h && \
    echo "keep-registry: marked KernelRegistrar used in op_registry.h"
fi

# Always try to harvest products into /output (even if a later step fails).
copy_products_to_output() {
  local abi="${ABI:-unknown}"
  local bd jni light jar
  mkdir -p "/output/$abi"
  # Prefer newest build.lite.android.* dir (avoid ls/glob parse edge cases).
  bd="$(find . -maxdepth 1 -type d -name 'build.lite.android.*' -printf '%T@ %p\n' 2>/dev/null \
    | sort -nr | head -1 | cut -d' ' -f2- || true)"
  if [[ -z "$bd" || ! -d "$bd" ]]; then
    echo "copy_products: no build.lite.android.* under $(pwd)" >&2
    return 1
  fi
  echo "copy_products: BUILD_DIR=$bd"
  jni="$(find "$bd" -name 'libpaddle_lite_jni.so' -print -quit 2>/dev/null || true)"
  light="$(find "$bd" -name 'libpaddle_light_api_shared.so' -print -quit 2>/dev/null || true)"
  jar="$(find "$bd" -name 'PaddlePredictor.jar' -print -quit 2>/dev/null || true)"
  if [[ -n "$jni" && -f "$jni" ]]; then
    cp -f "$jni" "/output/$abi/libpaddle_lite_jni.so"
    echo "copy_products: jni $(stat -c%s "/output/$abi/libpaddle_lite_jni.so") bytes"
  else
    echo "copy_products: jni missing under $bd" >&2
    find "$bd" -name '*.so' 2>/dev/null | head -40 >&2 || true
    return 1
  fi
  if [[ -n "$light" && -f "$light" ]]; then
    cp -f "$light" "/output/$abi/libpaddle_light_api_shared.so"
    echo "copy_products: light $(stat -c%s "/output/$abi/libpaddle_light_api_shared.so") bytes"
  fi
  if [[ -n "$jar" && -f "$jar" ]]; then
    cp -f "$jar" /output/PaddlePredictor.jar
  fi
  return 0
}

# shellcheck disable=SC2086
./lite/tools/build_android.sh --arch="$ARCH" --toolchain=clang \
  --with_java=ON --with_cv=OFF --with_extra=ON --with_log=OFF \
  --with_benchmark=OFF \
  --android_stl=c++_static \
  --with_exception=ON \
  ${EXTRA_FLAGS:-} \
  ${STRIP_FLAGS:-}

echo "build_android.sh finished (pwd=$(pwd)); harvesting products…"
copy_products_to_output || {
  echo "FAIL: could not copy paddle products for ABI=$ABI" >&2
  exit 1
}

# Do NOT patchelf arm dynsym here — historical working SOs did not need it;
# patchelf can leave LOCAL ABS markers that NDK28 lld rejects.

echo "=== kernel string checks ($ABI profile=$PROFILE) ==="
CHECKS=()
[[ -f "/output/$ABI/libpaddle_light_api_shared.so" ]] && CHECKS+=("/output/$ABI/libpaddle_light_api_shared.so")
[[ -f "/output/$ABI/libpaddle_lite_jni.so" ]] && CHECKS+=("/output/$ABI/libpaddle_lite_jni.so")

stamp_present() {
  local need="$1" f
  for f in "${CHECKS[@]}"; do
    grep -aFq "$need" "$f" 2>/dev/null && return 0
  done
  return 1
}

# Stamp expectations depend on ABI product precision (see SOURCE.md §Precision policy).
fail=0
case "$ABI" in
  arm64-v8a)
    # HW fp16 prod path
    for need in uint8_to_fp16 fp32_to_uint8; do
      if stamp_present "$need"; then echo "  PASS  $need"
      else echo "  FAIL  $need (arm64 fp16 prod)"; fail=1; fi
    done
    ;;
  armeabi-v7a)
    # True v7: fp32 calib only (no ARM82_FP16 product)
    for need in uint8_to_fp32 int8_to_fp32 fp32_to_uint8; do
      if stamp_present "$need"; then echo "  PASS  $need"
      else echo "  FAIL  $need (armv7 fp32 calib)"; fail=1; fi
    done
    for need in uint8_to_fp16 int8_to_fp16; do
      if stamp_present "$need"; then echo "  WARN  $need present (unexpected on true-v7 product)"
      else echo "  SKIP  $need (not product on armv7)"; fi
    done
    ;;
  x86_64|x86)
    # Soft input calib may expose fp16 stamps on light; backbone is float
    for need in fp32_to_uint8; do
      if stamp_present "$need"; then echo "  PASS  $need"
      else echo "  FAIL  $need (x86)"; fail=1; fi
    done
    for need in uint8_to_fp32 int8_to_fp32 uint8_to_fp16 int8_to_fp16; do
      if stamp_present "$need"; then echo "  PASS  $need"
      else echo "  SKIP  $need (optional on x86 thin/tailor)"; fi
    done
    ;;
  *)
    echo "FAIL: unknown ABI $ABI" >&2
    exit 1
    ;;
esac
[[ "$fail" -eq 0 ]] || exit 1

echo "  STATUS: PASS ($ABI profile=$PROFILE)"
ls -lh "/output/$ABI/"
echo "HISTORICAL_BUILD_DONE abi=$ABI profile=$PROFILE"
