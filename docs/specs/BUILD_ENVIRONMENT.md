---
type: intent-spec
status: locked
ai_directive: "This is an upstream specification. DO NOT modify this document to match the codebase. If the code deviates from this spec, the code is wrong. Modifications to this file require a dedicated 'Strategy' turn and explicit user approval."
---

# Build Environment Specification

This document defines the strict requirements for the Android build environment, focusing on the native JNI components and the custom Paddle-Lite integration.

## 1. NDK & Toolchain
The project requires a specific, standalone NDK version to maintain compatibility with the legacy Paddle-Lite JNI bindings.

*   **Version:** Android NDK r20b
*   **Path:** Root directory `android-ndk-r20b/` (to be migrated to `ndk/` subproject).
*   **CMake:** 3.10.2+

## 2. Native Dependencies

### OpenCV
*   **Version:** 4.x (Android SDK)
*   **Integration:** Linked via JNI in `app/src/main/cpp/`.

### Paddle-Lite
The project uses a **custom-built** version of the Paddle-Lite library. The standard release binaries are NOT sufficient because:
1.  **Dynamic Size Support:** Required for variable-resolution dashboard analysis.
2.  **Architecture Support:** Custom builds for `amd64` (host-side testing) and `android` (device) targets.
3.  **Symbol Conflicts:** Custom build flags are used to prevent symbol collisions with OpenCV in the `MemoryBridge` layer.
4.  **INT8 activation input:** Rebuild from `paddle-build-int8-20.04` image (`patches-int8/`, branch `pr-int8-activation-input`). Outputs land under `dev-ai-interaction/paddle-build/output/` per target; deploy `.so` + `PaddlePredictor.jar` to `app/src/main/jniLibs/` and `app/libs/`.

## 3. Python Environment
Required for model optimization and monochrome conversion scripts.
*   **Manager:** Miniconda
*   **Environment Name:** `paddle_env_v3`
*   **Path:** `~/miniconda3/envs/paddle_env_v3/bin/python`

## 4. Build Rules
*   **No Autoloading:** Native libraries (`.so`) are manually managed. Do NOT use standard Android library autoloading if it causes segmentation faults.
*   **Zero-Allocation Mandate:** Native processing in `MemoryBridge.cpp` must avoid `malloc` and STL containers to prevent memory churn and crashes.
