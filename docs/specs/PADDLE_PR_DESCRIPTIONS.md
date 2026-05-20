---
type: intent-spec
status: locked
ai_directive: "This is an upstream specification for the Paddle-Lite PRs. DO NOT modify this document to match the codebase. If the code deviates from this spec, the code is wrong. Modifications to this file require a dedicated 'Strategy' turn and explicit user approval."
---

# Paddle-Lite Proposed PR Descriptions

This document stores the prepared Pull Request descriptions for the local Paddle-Lite branches. These have been carefully drafted to explain the technical necessity and impact of the changes for upstream consideration.

## PR 1: Upstream Stability and Build Cleanup (Foundational)
**Branch:** `pr-upstream-cleanup`

### Description
This PR includes several independent bug fixes and build system robustness improvements identified while developing cross-platform support for Android X86 emulators. These changes improve stability and build reliability for both ARM mobile and X86 desktop/server targets.

### Key Changes
- **AVX-512 CRF Decoding Bug Fix:** Fixed an undeclared `alpha_value` and an invalid `this` pointer in `lite/backends/x86/jit/more/intrinsic/crf_decoding.cc` that broke compilation on modern Desktop Linux environments.
- **X86 INT8 Linker Fix:** Added missing explicit `signed char` instantiations to `im2col.cc` and `avx/conv_utils.cc` to resolve "undefined reference" errors in quantized models.
- **CMake Robustness:** Properly quoted `${WITH_MKLML}` and `${CBLAS_PROVIDER}` in `mkldnn.cmake` and allowed an empty `ARM_TARGET_OS` in `common.cmake`.
- **Universal Artifact Packaging:** Replaced fragile `cp` commands with a location-agnostic `lite/tools/copy_libs.cmake` helper script.
- **Universal DDimLite Logging:** Refined the `DDimLite` stream operator to support both `std::ostream` (Desktop) and `replace_stl::ostream` (Android Mobile).

---

## PR 2: Bridging the X86 Android "Mobile Gap" (Emulator Extension)
**Branch:** `pr-x86-android-mobile-gap`

### Description
This PR bridges the "Mobile Gap" in Paddle Lite's build system, enabling the `x86_64` and `x86` backends to compile directly for Android targets (e.g., Android Studio Emulators) without breaking existing Desktop Linux compatibility.

### Key Changes
- **NDK ABI Conflict:** Removed hardcoded `CMAKE_C_COMPILER` overrides in `android.cmake` that forced incorrect host machine linking.
- **Cross-Compilation Propagation:** Correctly propagated `CROSS_COMPILE_CMAKE_ARGS` to external projects like `xbyak` and `openblas`.
- **The "Tiny Publish" Override:** Updated conditions in `CMakeLists.txt` to permit `LITE_ON_TINY_PUBLISH=ON` for X86 mobile targets.
- **AVX2 Scoping Fix:** Fixed a bug where `-mavx2` flags were lost during the final library assembly; flags are now re-applied in the `lite/api/` scope.
- **Protobuf & STL Isolation:** Wrapped Protobuf includes in mobile guards and stubbed the `VarType` enum to prevent crashes when Protobuf is stripped.
- **NNAPI Stability:** Explicitly disabled NNAPI for the Android `x86_64` target to prevent initialization crashes observed in emulators.
