<!-- Paste as GitHub PR body for pr-upstream-cleanup-restack → base: develop -->
<!-- Demo: https://github.com/PaddlePaddle/Paddle-Lite/pull/8688 -->
### PR devices
x86, Arm, Framework

### PR types
Bug fixes

### PR changes
Backends, API

### Description

Several independent build-system and kernel fixes found while enabling cross-platform Android / Linux mobile builds. Safe multi-ABI foundation (not Android-x86-specific).

**1. X86 kernels**
- **AVX-512 CRF (`crf_decoding.cc`):** Fix undeclared `alpha_value` and invalid `this` usage that break AVX-512 CRF builds.
- **X86 INT8 im2col (`im2col.cc`):** Add missing explicit `signed char` instantiations so quantized models link on x86.

**2. CMake / packaging**
- **mkldnn.cmake:** Fix unquoted variable that breaks CMake configure in some environments.
- **os/common.cmake:** Allow empty `ARM_TARGET_OS` for desktop Linux host builds.
- **build_android.sh:** Map internal arch names (e.g. `armv8`) to NDK ABI strings (`arm64-v8a`) when invoking CMake.
- **build_linux.sh:** Small robustness fixes consistent with Android packaging.
- **copy_libs.cmake + lite/CMakeLists.txt:** Location-agnostic artifact packaging instead of fragile `cp` paths.

**3. Core**
- **dim.h:** Stream operator for `DDimLite` that works with both Android `replace_stl` and Linux `std::ostream`.

**Local verification**
- Rebased on current `develop`.
- Android x86_64 and armv8 JNI smoke builds succeed when stacked with the follow-on mobile-gap PR.
- Commit message includes `test=develop` (required to trigger project CI).

**Stack**
- This is **PR1** of: cleanup → Android x86 mobile-gap → calib-safe uint8 dequant.
