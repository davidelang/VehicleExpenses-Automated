<!-- Paste as GitHub PR body for pr-calib-safe-uint8-dequant-restack → base: davidelang/pr-x86-android-mobile-gap (or restack equivalent) -->
<!-- Demo: https://github.com/PaddlePaddle/Paddle-Lite/pull/8688 -->
### PR devices
Arm, x86

### PR types
Bug fixes, Performance optimization

### PR changes
Kernels

### Description

Safe int8/uint8 calib dequant for ARM (and matching x86 calib aliases) so mono/greyscale camera frames can bind raw **uint8** luma without a host xor-128 expand to int8, and without SEGV on exact-size external buffers.

**Bug:** ARM `int8_to_fp32` used software-pipelined NEON loads that read past `numel` on buffers that are exactly size N (e.g. `ShareExternalMemory` greyscale planes) → intermittent SEGV.

**Fix / features**
- Rewrite ARM `int8_to_fp32` to stop over-read past `numel`.
- Add `uint8_to_fp32` / `uint8_to_fp16` and `int8_to_fp16` with the same scale contract as int8 after `q = (int8_t)(u ^ 128)`.
- Register `calib` / `calib_once` aliases on **ARM** and **x86**.

**Why it matters**
- Smaller/faster mobile paths when input is already 8-bit greyscale (no host int8 expand buffer).
- Safer binding for exact external memory tensors.

**Depends on:** cleanup + Android x86 mobile-gap stack so CI/review can also exercise Android x86_64 if desired. Functionally this kernel work is independent of x86-gap; the stack base is for product/build completeness.

**Local verification**
- Android armv8 JNI build: binary contains `uint8_to_fp16`, `uint8_to_fp32`, `int8_to_fp32`, `int8_to_fp16`.
- Android x86_64 JNI build still succeeds on the stacked tip.
- Commit message includes `test=develop`.

**Stack**
- **PR3** of: cleanup → Android x86 mobile-gap → **this PR**.
- Please set GitHub **base branch** to the mobile-gap PR branch.
