<!-- Paste as GitHub PR body for pr-x86-android-mobile-gap-restack → base: davidelang/pr-upstream-cleanup (or restack equivalent) -->
<!-- Demo: https://github.com/PaddlePaddle/Paddle-Lite/pull/8688 -->
### PR devices
x86, Arm

### PR types
New features, Bug fixes

### PR changes
Backends, API

### Description

Bridges the Android **x86 / x86_64** “mobile gap” so emulator ABIs can build with `LITE_ON_TINY_PUBLISH=ON` and NDK clang, without breaking desktop Linux x86 or ARM Android.

Upstream historically treated X86 as desktop (full publish / MKL) and ARM as mobile (tiny publish). Compiling the x86 backend for Android NDKs hit ABI, sysroot, and dependency mismatches. This PR makes Android x86 a first-class tiny-publish target.

**Depends on:** the cleanup PR (ABI mapping, `copy_libs.cmake`, im2col int8 instantiations, etc.).

**1. CMake / NDK**
- **android.cmake:** x86 and x86_64 triples + libc++ STL libs; use **`ANDROID_NDK`** for sysroot/toolchain (no hardcoded host path).
- **CMakeLists.txt:** Allow `LITE_ON_TINY_PUBLISH` with `(LITE_WITH_ARM OR LITE_WITH_X86)`.
- **common.cmake / postproject.cmake / simd.cmake:** Isolate x86 backend includes; pass `CROSS_COMPILE_CMAKE_ARGS`; cross-compile-friendly AVX settings / `TryRunResults.cmake`.
- **openblas.cmake:** Android sysroot/target for the Makefile OpenBLAS build; `DYNAMIC_ARCH=0` + `ONLY_CBLAS=1` for smaller static Android links.
- **lite/api CMake:** Re-apply needed x86 flags for `PADDLELITE_OBJS` / JNI packaging paths.

**2. Source**
- **data_type.h / selected_rows:** Tiny-publish-safe paths without desktop Protobuf leakage.
- **mklml.h / mklml.cc:** Gate MKL with `LITE_WITH_MKL` so Android NDK builds with `WITH_MKL=OFF` (OpenBLAS) do not `#include <mkl.h>`.
- **logging.cc / port.h:** Android + x86 logging compatibility.

**3. Isolation**
- X86 backend CMake is only pulled when `LITE_WITH_X86` is on so ARM Android configs stay clean.

**Local verification**
- Docker NDK r20b image: `build_android.sh --arch=x86_64` produces `libpaddle_lite_jni.so`.
- ARM armv8 build still succeeds with this stack.
- Commit message includes `test=develop`.

**Stack**
- **PR2** of: cleanup → **this PR** → calib-safe uint8 dequant.
- Please set GitHub **base branch** to the cleanup PR branch (not bare `develop`) so review shows only this delta.
