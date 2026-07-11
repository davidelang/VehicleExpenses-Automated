---
type: intent-spec
status: locked
ai_directive: "This is an upstream specification for the Paddle-Lite library build. DO NOT modify this document to match the codebase. If the code deviates from this spec, the code is wrong. Modifications to this file require a dedicated 'Strategy' turn and explicit user approval."
---

# Paddle-Lite Custom Build Specification

To support the advanced features required by the Vehicle Expenses Automated app (specifically dynamic shapes and cross-platform host/android support), a custom build of the Paddle-Lite core is maintained.

## 1. Source & Branches
The custom modifications are tracked in a fork of the official Paddle-Lite repository.

*   **Fork Repository:** (Internal git fork for Paddle-Lite)
*   **Active Branches (Prepared locally for PR):**
    *   `pr-upstream-cleanup`: **Foundational.** Essential bug fixes required to build Paddle-Lite for ANY platform (ARM or X86). Includes AVX-512 CRF decoding fixes and INT8 linker support.
    *   `pr-x86-android-mobile-gap`: **Platform-Specific.** Additional patches required specifically for Android Emulator support (`x86_64`). Bridges the "Mobile Gap" and fixes AVX2 scoping.
    *   `pr-int8-activation-input`: **INT8-only.** `keep_quantized_weights` + `analytic_input_quant_pass`. Depends on merged PR patches; lives in `patches-int8/` only (never modify `patches/`).

## 1b. INT8 Docker Build Matrix (Image vs Run Container)

| Layer | Image tag | Dockerfile | Patches |
|-------|-----------|------------|---------|
| Base PR | `paddle-build-20.04` | `Dockerfile` | `apply_patches.sh` → `patches/` |
| INT8 layer | `paddle-build-int8-20.04` | `Dockerfile.int8` | `apply_int8_patches.sh` → `patches-int8/` |

```bash
docker build -t paddle-build-20.04 -f Dockerfile .
docker build -t paddle-build-int8-20.04 -f Dockerfile.int8 .
```

Per-target **run containers** (one named container per arch):

| Container | Command |
|-----------|---------|
| `paddle_int8_build_linux` | `./lite/tools/build_linux.sh --arch=x86` |
| `paddle_int8_build_arm64` | `./lite/tools/build_android.sh --arch=armv8 ...` |
| `paddle_int8_build_arm32` | `./lite/tools/build_android.sh --arch=armv7 ...` |
| `paddle_int8_build_x86_64` | `./lite/tools/build_android.sh --arch=x86_64 ...` |

## 2. Technical Highlights from Custom Branches

### Bridging the "Mobile Gap"
Paddle-Lite traditionally assumed X86 meant Desktop (`LITE_ON_TINY_PUBLISH=OFF`). Our custom patches enable `LITE_ON_TINY_PUBLISH=ON` for X86, allowing it to compile as a lightweight mobile library for Android Emulators.

### AVX2 Scoping Fix
Fixed an architectural bug where `-mavx2` flags were lost when building the final `PADDLELITE_OBJS` library. Flags are now explicitly re-applied in the `lite/api/` scope to ensure performance on `x86_64` without triggering `SIGILL` crashes.

### OpenBLAS Optimization
Statically linked OpenBLAS is optimized for emulators by setting `DYNAMIC_ARCH=0` and `ONLY_CBLAS=1`, drastically reducing the binary size of the X86 Android library.

### Protobuf & STL Isolation
Wrapped `framework.pb.h` includes in `#ifndef LITE_ON_TINY_PUBLISH` and refined `DDimLite` stream operators to support both `std::ostream` (Desktop) and Paddle's lightweight `replace_stl::ostream` (Mobile).

## 3. Build Configuration
The following `build.sh` flags are critical for producing compatible binaries:

```bash
# Example Android Build Command
./lite/tools/build_android.sh \
  --arch=armv8 \
  --toolchain=clang \
  --with_cv=ON \
  --with_extra=ON \
  --with_exception=ON \
  --with_static_libcxx=OFF
```

## 3. Dynamic Shape Support
Standard Paddle-Lite `opt` binaries often strip dynamic shape information. Our custom build ensures that:
1.  The `opt` tool correctly parses the `NNADAPTER_DYNAMIC_SHAPE_INFO` environment variable.
2.  The resulting `.nb` (Naive Buffer) files contain the correct metadata to initialize predictors with variable input tensors.

## 4. Monochrome (1-Channel) Logic
The custom build includes specialized kernels for 1-channel convolution to maximize performance on odometer crops. This avoids the overhead of replicating single-channel data into three-channel ARGB buffers.
