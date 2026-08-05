# paddle (Paddle-Lite) pin — VehicleExpenses

| | |
|--|--|
| **Fork** | `https://github.com/davidelang/Paddle-Lite.git` |
| **Pin SHA** | `c6a9b9ada00e3b37aa80c21407e7d8e240595ebb` = tip of **`pr-x86-android-mobile-gap`** |
| **Stack** | pin SHA + **full historical patches/** + **patches-int8/** (see build recipe below) |
| **build_time** | `few_hours` (Docker image once + per-ABI Android build) |
| **reproducible** | `false` (NDK, Docker base, third-party tarball, timestamps) |
| **Products** | `artifact/jni/{arm64-v8a,x86_64}/libpaddle_*.so`, optional `PaddlePredictor.jar` |
| **Strip** | Host `llvm-strip` via `PADDLE_STRIP_MODE` (default `unneeded`; NDK app link gate in `./build`) |
| **Build NDK** | **r28c** verified 2026-08-04 (`PADDLE_NDK_VERSION=r28c`, image `ve-paddle-ndk28c`); historical r20b still supported |
| **Tailor inputs** | Prefer `third_party/paddle-models` (non-git `[[source]]` pin); fallback `tailor_models/` |

### Verified products (NDK r28c, 2026-08-04)

| ABI | Profile | jni (strip-unneeded) | light | NDK28 link | INT8 notes |
|-----|---------|----------------------|-------|------------|------------|
| arm64-v8a | tailor + fp16 | ~1.8 MB | ~1.8 MB | OK | jni: `uint8_to_fp16`, `int8_to_fp16`, `fp32_to_uint8` |
| armeabi-v7a | **tailor** + int8 | **~0.75 MB** | **~0.75 MB** | OK | jni: `uint8_to_fp32`, `int8_to_fp32`, `fp32_to_uint8` (no fp16; no `PADDLE_WITH_ARMV7_FP16`) |
| x86_64 | slim thin-jni | ~52 KB | ~10 MB | OK | calib stamps on **light** (thin jni is wrapper). **Do not default tailor** — see below |

### Precision policy by ABI (product decision)

Target **armeabi-v7a** = real, limited **ARMv7-A head units** (not “32-bit ABI on ARMv8.2 FP16 HW”).

| ABI | Mid-graph precision | Why |
|-----|---------------------|-----|
| **arm64-v8a** | **HW fp16** (`--with_arm82_fp16=ON`) | Real half-precision NEON; matches `prod_u8fp16/*_armv8.nb` |
| **armeabi-v7a** | **fp32 calib only** (`int8/uint8→fp32`) | True v7 chips must **not** assume `ARM82_FP16` / `-march=armv8.2-a+fp16` (SIGILL risk). Soft/storage fp16 (store f16, compute via convert+fp32) is **usually worse or equal** to pure fp32 on these CPUs (convert cost, no HW half ALU). **Not worth a product soft-fp16 path.** Keep fp32 library path. |
| **x86_64 (emulator / AMD64 Android)** | **fp32 backbone after input calib** | x86 has no useful HW fp16 conv path in this stack. `opt` may advertise kFP16 so analytic quant can insert `*_to_fp16`, then **type-precision cast demotes the backbone to float** (`opt_base.cc`). Same family of limitation as “no real fp16 match on AMD64” — document, don’t chase. |

Do **not** enable `PADDLE_WITH_ARMV7_FP16` for head-unit product pins.

### Library size: tailor (where the real win is)

`LITE_BUILD_TAILOR` keeps only kernels needed by given `.nb` models. Historical sandbox sizes (Jul 2026, same class of recipe):

| ABI | Slim (full kernel set) | Tailored | Approx gain |
|-----|------------------------|----------|-------------|
| arm64-v8a | ~5.5 MB jni | **~1.6 MB** | ~3.4× smaller |
| x86_64 light | ~9.5–10 MB | (broken on NDK r28c android-x86) | pin default **slim**; `PADDLE_X86_PROFILE=tailor` experimental only |
| x86_64 jni | ~0.7 MB (or thin ~50 KB) | thin ~30–50 KB | already small wrapper |
| armeabi-v7a | ~3.0 MB | **~0.75 MB** (2026-08-04 pin) | ~4× smaller with `paddle-models` armv7 lists |

**armeabi-v7a tailor landed** (fp32 calib, no arm82 fp16): jni/light ~0.75 MB strip-unneeded with stamps `uint8_to_fp32`, `int8_to_fp32`, `fp32_to_uint8`.

**x86_64:** thin jni is already tiny. `LITE_BUILD_TAILOR` on android-x86 (NDK r28c) **drops static `KernelRegistrar`** from strip TUs (final light SO missing `fp32_to_uint8` / calib kernels) even after no-LTO and `__attribute__((used))` experiments. **Default `PADDLE_X86_PROFILE` = slim.** Revisit tailor only with a proven force-keep / whole-archive fix.

Tailor does **not** require fp16 on armv7 — tailor works with fp32 calib graphs too.

**Implemented multi-ABI tailor:** `paddle-models` packs `armv8` + `armv7` + `x86_64`; `./build` selects **tailor** when that subdir exists. Rebuild:

```bash
./third_party/fetch-deps ro paddle-models
PADDLE_ABIS="arm64-v8a armeabi-v7a x86_64" PADDLE_SKIP_IMAGE_BUILD=1 \
  PADDLE_DOCKER_IMAGE=ve-paddle-ndk28c PADDLE_NDK_VERSION=r28c \
  ./third_party/paddle/build
```

**Multi-ABI emulator matrix:** `docs/reference/PADDLE_ABI_EMULATOR_TEST.md`

**Authoritative process doc:** `docs/reference/PADDLE_PIN_BUILDS.md`  
**Is-vs-should report (post-merge cleanup):** `dev-ai-interaction/scratch/paddle-pin-is-vs-should-20260804.md`  
**armv7 plan (fp32 product; FP16 workstream B declined for true-v7):** `dev-ai-interaction/plans/paddle-armv7-fp16-and-functional-calib-20260803-plan.md`

---

## Reproduce (library binaries) — historical recipe under third_party

Build runs in Docker (default **NDK r20b**), sources from **`third_party/paddle/src`**, products to **`src/bin/<abi>/`**.

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64   # host; container uses JDK8
./third_party/fetch-deps ro paddle
./third_party/fetch-deps ro paddle-models            # tailor .nb inputs → paddle-models/src
# optional: skip image rebuild if ve-paddle-int8 or paddle-build-int8-20.04 exists
export PADDLE_SKIP_IMAGE_BUILD=1
export PADDLE_DOCKER_IMAGE=paddle-build-int8-20.04   # or ve-paddle-int8
export PADDLE_ABIS="arm64-v8a armeabi-v7a x86_64"
# Defaults: tailor per ABI if paddle-models/src/{armv8,armv7,x86_64} exists
# export PADDLE_ARM64_PROFILE=tailor
# export PADDLE_ARMV7_PROFILE=tailor
# export PADDLE_X86_PROFILE=tailor
export PADDLE_STRIP_MODE=unneeded
export PADDLE_DOCKER_IMAGE=ve-paddle-ndk28c
export PADDLE_NDK_VERSION=r28c
export PADDLE_SKIP_IMAGE_BUILD=1
./third_party/paddle/build
./third_party/get-artifacts paddle
```

### What `./build` does (sequenced tools)

1. Resolve **tailor dir** (`paddle-models/src` → `tailor_models` → env override)  
2. Ensure Docker image (`PADDLE_NDK_VERSION`, default r20b; product image **r28c** as `ve-paddle-ndk28c`)  
3. Per ABI: copy pin `src` → container; apply **`patches/`** + **`patches-int8/`**; x86 thin jni; `build_android.sh`  
4. **Tailor** when profile=tailor: `--with_strip=ON --opt_model_dir=/tailor_models/{armv8|armv7|x86_64}`  
5. **armv7:** fp32 calib only (no `ARM82_FP16` product)  
6. Host strip per `PADDLE_STRIP_MODE` (default `--strip-unneeded`)  
7. **App NDK link gate** for arm64, x86_64, and armeabi-v7a when products exist  
8. **`paddle_so_smoke`** multi-ABI ELF/stamp gate (`./third_party/paddle/test` → `scripts/paddle-so-smoke.sh`); skip with `PADDLE_SKIP_SO_SMOKE=1`
9. **`paddle_ocr_qemu`** multi-ABI functional OCR under QEMU (det→deskew→crop→rec); **default on** for pin `./build` and `./test`; skip with `PADDLE_SKIP_OCR_QEMU=1`

Standalone re-test (no rebuild): `./third_party/paddle/test`  
(SO smoke + OCR QEMU by default. Device Kotlin path: `PADDLE_OCR_FUNCTIONAL=1`.)  
Docs: `docs/reference/PADDLE_SO_SMOKE.md`

Env: `PADDLE_ABIS`, `PADDLE_SKIP_IMAGE_BUILD`, `PADDLE_DOCKER_IMAGE`, `PADDLE_ARM64_PROFILE`, `PADDLE_ARMV7_PROFILE`, `PADDLE_X86_PROFILE`, `PADDLE_TAILOR_DIR`, `PADDLE_STRIP_MODE`, `PADDLE_NDK_VERSION`, `PADDLE_WITH_ARMV7_FP16`.

**Deprecated:** `scripts/run-android-slim.sh` (int8-only + strip-debug) — kept for reference; do not use for product.

---

## Git branch stack

| Branch | Tip (short) | Role |
|--------|-------------|------|
| `pr-upstream-cleanup` | `2ca96249` | Foundational build/robustness |
| `pr-x86-android-mobile-gap` | `c6a9b9ad` | **libpin git_sha** + x86 mobile gap |
| Full-file `patches/` | (vendored under `third_party/paddle/patches/`) | Jul historical overlay — required for link-clean arm64 c++_static products |
| `patches-int8/` | file copies | INT8/uint8 calib + analytic quant |

Ideal later: fold remaining patch deltas into git PRs so full-file copies shrink (see is-vs-should report).

---

## What the app should ship (historical working shape)

| ABI | Product | Approx size |
|-----|---------|-------------|
| arm64-v8a | model-tailored `libpaddle_lite_jni.so` | ~1.6 MB |
| x86_64 | thin `libpaddle_lite_jni.so` + `libpaddle_light_api_shared.so` | ~0.75 MB + ~10 MB |
| armeabi-v7a | **not** in default `PADDLE_ABIS` | opt-in: `PADDLE_ABIS="… armeabi-v7a"` (slim+int8; see armv7 plan) |

Runtime models:

| ABI | Assets | Path id |
|-----|--------|---------|
| arm64-v8a | `prod_u8fp16/*_armv8.nb` | `uint8_fp16_u8` |
| x86_64 | `prod_u8fp16/*_x86_64.nb` | `uint8_fp16_u8` |
| **armeabi-v7a** | `prod_u8fp32_u8/*_armv7.nb` | `uint8_fp32_u8` |

Rebuild armv7 models: `app/src/main/assets/paddle/scripts/optimize_armv7_prod_u8fp32_u8.sh` (host INT8 `opt`).

---

## Model / tailor inputs (separate pin)

| Pin | Role |
|-----|------|
| `third_party/paddle-models` | Non-git `[[source]]` pack: armv8 `.nb` + `.tailored_*` for **library** tailor |
| `third_party/paddle/tailor_models/` | Historical fallback mount (same files; prefer paddle-models) |
| `app/…/assets/paddle/` | **Runtime** OCR models shipped in the APK |

```bash
./third_party/fetch-deps ro paddle-models
# ./build mounts paddle-models/src as /tailor_models when present
```

See `docs/reference/PADDLE_PIN_BUILDS.md` and `third_party/paddle-models/SOURCE.md`.
