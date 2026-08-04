#!/bin/bash
# Applies INT8-only deltas ON TOP of existing PR patches (apply_patches.sh).
# Branch: pr-int8-activation-input — do not modify patches/ (PR branches).
set -e

PATCH_ROOT="${PATCH_ROOT:-/patches-int8}"

echo "Applying INT8 API patches (keep_quantized_weights)..."
cp "${PATCH_ROOT}/code/lite/api/paddle_api.h" lite/api/paddle_api.h
cp "${PATCH_ROOT}/code/lite/api/light_api.h" lite/api/light_api.h
cp "${PATCH_ROOT}/code/lite/api/light_api.cc" lite/api/light_api.cc
cp "${PATCH_ROOT}/code/lite/api/light_api_impl.cc" lite/api/light_api_impl.cc
cp "${PATCH_ROOT}/code/lite/api/cxx_api_impl.cc" lite/api/cxx_api_impl.cc
cp "${PATCH_ROOT}/code/lite/api/paddle_use_passes.h" lite/api/paddle_use_passes.h

echo "Applying INT8 MIR pass (analytic_input_quant_pass)..."
cp "${PATCH_ROOT}/code/lite/core/optimizer/mir/analytic_input_quant_pass.h" \
   lite/core/optimizer/mir/analytic_input_quant_pass.h
cp "${PATCH_ROOT}/code/lite/core/optimizer/mir/analytic_input_quant_pass.cc" \
   lite/core/optimizer/mir/analytic_input_quant_pass.cc
cp "${PATCH_ROOT}/code/lite/core/optimizer/mir/output_calib_pass.h" \
   lite/core/optimizer/mir/output_calib_pass.h
cp "${PATCH_ROOT}/code/lite/core/optimizer/mir/output_calib_pass.cc" \
   lite/core/optimizer/mir/output_calib_pass.cc
cp "${PATCH_ROOT}/code/lite/core/optimizer/optimizer.cc" lite/core/optimizer/optimizer.cc

echo "Applying output calib x86 kernel (fp32_to_uint8)..."
cp "${PATCH_ROOT}/code/lite/kernels/x86/calib_compute.h" lite/kernels/x86/calib_compute.h
cp "${PATCH_ROOT}/code/lite/kernels/x86/calib_compute.cc" lite/kernels/x86/calib_compute.cc

echo "Applying output calib arm kernel (fp32_to_uint8)..."
mkdir -p lite/kernels/arm
cp "${PATCH_ROOT}/code/lite/kernels/arm/calib_compute.h" lite/kernels/arm/calib_compute.h
cp "${PATCH_ROOT}/code/lite/kernels/arm/calib_compute.cc" lite/kernels/arm/calib_compute.cc

echo "Applying arm math type_trans (int8/uint8 -> fp32/fp16 dequant)..."
mkdir -p lite/backends/arm/math/fp16
cp "${PATCH_ROOT}/code/lite/backends/arm/math/type_trans.h" lite/backends/arm/math/type_trans.h
cp "${PATCH_ROOT}/code/lite/backends/arm/math/type_trans.cc" lite/backends/arm/math/type_trans.cc
cp "${PATCH_ROOT}/code/lite/backends/arm/math/fp16/type_trans_fp16.h" \
   lite/backends/arm/math/fp16/type_trans_fp16.h
cp "${PATCH_ROOT}/code/lite/backends/arm/math/fp16/type_trans_fp16.cc" \
   lite/backends/arm/math/fp16/type_trans_fp16.cc

echo "Applying opt tool patches (output_calib_precision flag)..."
cp "${PATCH_ROOT}/code/lite/api/tools/opt.cc" lite/api/tools/opt.cc
cp "${PATCH_ROOT}/code/lite/api/tools/opt_base.h" lite/api/tools/opt_base.h
cp "${PATCH_ROOT}/code/lite/api/tools/opt_base.cc" lite/api/tools/opt_base.cc

echo "Applying INT8 JNI / Java MobileConfig..."
cp "${PATCH_ROOT}/code/lite/api/android/jni/src/com/baidu/paddle/lite/MobileConfig.java" \
   lite/api/android/jni/src/com/baidu/paddle/lite/MobileConfig.java
cp "${PATCH_ROOT}/code/lite/api/android/jni/native/convert_util_jni.h" \
   lite/api/android/jni/native/convert_util_jni.h

echo "Applying x86 SSE signature fix (clang constexpr compat)..."
cp "${PATCH_ROOT}/code/lite/backends/x86/math/elementwise_common_broadcast_config.h" \
   lite/backends/x86/math/elementwise_common_broadcast_config.h

echo "All INT8 patches applied successfully."

echo "Installing JNI SONAME post-build helper (patchelf --set-soname for arm ABIs)..."
cat > /workspace/set_jni_soname.sh << 'EOF'
#!/bin/bash
# Post-build: set ELF SONAME on libpaddle_lite_jni.so for arm64/armv7 int8 builds.
set -e
if [ $# -ne 1 ]; then
  echo "Usage: $0 <path-to-libpaddle_lite_jni.so>" >&2
  exit 1
fi
JNI_SO="$1"
if [ ! -f "$JNI_SO" ]; then
  echo "ERROR: $JNI_SO not found" >&2
  exit 1
fi
patchelf --set-soname libpaddle_lite_jni.so "$JNI_SO"
echo "SONAME set on $JNI_SO"
readelf -d "$JNI_SO" | grep -E 'SONAME|NEEDED' || true
EOF
chmod +x /workspace/set_jni_soname.sh
echo "JNI SONAME helper installed at /workspace/set_jni_soname.sh"

echo "Installing repeatable int8 Android build wrapper (build + set SONAME)..."
cat > /workspace/build_int8_android_with_soname.sh << 'EOF'
#!/bin/bash
# Build int8 Paddle-Lite Android JNI for armv7/armv8, then set ELF SONAME.
set -e
ARCH=""
while [ $# -gt 0 ]; do
  case "$1" in
    --arch)
      ARCH="$2"
      shift 2
      ;;
    *)
      echo "Usage: $0 --arch armv7|armv8" >&2
      exit 1
      ;;
  esac
done
if [ -z "$ARCH" ]; then
  echo "Usage: $0 --arch armv7|armv8" >&2
  exit 1
fi
case "$ARCH" in
  armv7)
    BUILD_DIR="build.lite.android.armv7.clang"
    LIB_DIR="inference_lite_lib.android.armv7"
    ;;
  armv8)
    BUILD_DIR="build.lite.android.armv8.clang"
    LIB_DIR="inference_lite_lib.android.armv8"
    ;;
  *)
    echo "ERROR: unsupported arch '$ARCH' (use armv7 or armv8)" >&2
    exit 1
    ;;
esac
if [ -f /patches/build_android.sh ]; then
  cp /patches/build_android.sh lite/tools/build_android.sh
  chmod +x lite/tools/build_android.sh
fi
cd /workspace/Paddle-Lite
./lite/tools/build_android.sh --arch="$ARCH" --toolchain=clang --with_java=ON --with_cv=OFF --with_extra=ON --with_arm82_fp16=ON full_publish
JNI_SO="${BUILD_DIR}/${LIB_DIR}/java/so/libpaddle_lite_jni.so"
/workspace/set_jni_soname.sh "$JNI_SO"
echo "=== SONAME verification for $ARCH ==="
readelf -d "$JNI_SO" | grep -E 'SONAME|NEEDED' || true
EOF
chmod +x /workspace/build_int8_android_with_soname.sh
echo "INT8 Android build wrapper installed at /workspace/build_int8_android_with_soname.sh"