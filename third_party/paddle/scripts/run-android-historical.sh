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

STRIP_FLAGS=""
if [[ "$PROFILE" == "tailor" ]]; then
  if [[ "$ARCH" != "armv8" && "$ABI" != "arm64-v8a" ]]; then
    echo "FAIL: tailor profile only supported for armv8/arm64-v8a (got ARCH=$ARCH ABI=$ABI)" >&2
    exit 1
  fi
  if [[ ! -d /tailor_models/armv8 ]]; then
    echo "FAIL: tailor profile needs /tailor_models/armv8 (.nb + .tailored_*)" >&2
    exit 1
  fi
  if [[ -f /patch_tailor_depthwise_common.py ]]; then
    python3 /patch_tailor_depthwise_common.py lite/kernels/CMakeLists.txt || true
  fi
  STRIP_FLAGS="--with_strip=ON --opt_model_dir=/tailor_models/armv8"
  echo "PROFILE=tailor (LITE_BUILD_TAILOR via --with_strip=ON)"
else
  echo "PROFILE=slim (tiny_publish, full kernel set, host strip-unneeded later)"
fi

# shellcheck disable=SC2086
./lite/tools/build_android.sh --arch="$ARCH" --toolchain=clang \
  --with_java=ON --with_cv=OFF --with_extra=ON --with_log=OFF \
  --with_benchmark=OFF \
  --android_stl=c++_static \
  --with_exception=ON \
  $EXTRA_FLAGS \
  $STRIP_FLAGS

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

# Do NOT patchelf arm dynsym here — historical working SOs did not need it;
# patchelf can leave LOCAL ABS markers that NDK28 lld rejects.

JAR=$(find "$BUILD_DIR" -name 'PaddlePredictor.jar' | head -1 || true)
if [[ -n "${JAR:-}" && -f "$JAR" ]]; then
  cp -f "$JAR" /output/PaddlePredictor.jar
fi

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

if [[ "$PROFILE" == "tailor" ]]; then
  # prod u8fp16 path stamps
  for need in uint8_to_fp16 fp32_to_uint8; do
    if stamp_present "$need"; then
      echo "  PASS  $need"
    else
      echo "  FAIL  $need (tailor prod path)" >&2
      exit 1
    fi
  done
else
  REQUIRED=(int8_to_fp32 uint8_to_fp32 fp32_to_uint8)
  OPTIONAL_FP16=(int8_to_fp16 uint8_to_fp16)
  case "$ABI" in
    arm64-v8a|x86_64|x86) REQUIRE_FP16=1 ;;
    armeabi-v7a) REQUIRE_FP16=0 ;;
    *) echo "FAIL: unknown ABI $ABI" >&2; exit 1 ;;
  esac
  fail=0
  for need in "${REQUIRED[@]}"; do
    if stamp_present "$need"; then echo "  PASS  $need"
    else echo "  FAIL  $need"; fail=1; fi
  done
  for need in "${OPTIONAL_FP16[@]}"; do
    if stamp_present "$need"; then echo "  PASS  $need"
    elif [[ "$REQUIRE_FP16" -eq 1 ]]; then echo "  FAIL  $need"; fail=1
    else echo "  SKIP  $need"; fi
  done
  [[ "$fail" -eq 0 ]] || exit 1
fi

echo "  STATUS: PASS ($ABI profile=$PROFILE)"
ls -lh "/output/$ABI/"
echo "HISTORICAL_BUILD_DONE abi=$ABI profile=$PROFILE"
