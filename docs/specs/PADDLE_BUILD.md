---
type: intent-spec
status: locked
ai_directive: "This is an upstream specification for the Paddle-Lite library build. DO NOT modify this document to match the codebase. If the code deviates from this spec, the code is wrong. Modifications to this file require a dedicated 'Strategy' turn and explicit user approval."
---

# Paddle-Lite Custom Build Specification

To support the advanced features required by the Vehicle Expenses Automated app (specifically dynamic shapes and cross-platform host/android support), a custom build of the Paddle-Lite core is maintained.

## 1. Source & Branches
The custom modifications are tracked in a fork of the official Paddle-Lite repository.

*   **Fork Repository:** (Reference the internal git fork for Paddle-Lite)
*   **Branches:**
    *   `feature/dynamic-shape-android`: Contains the logic for `NNADAPTER_DYNAMIC_SHAPE_INFO` on mobile.
    *   `fix/amd64-android-parity`: Ensures mathematical consistency between host-side `opt` results and device-side inference.

## 2. Build Configuration
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
