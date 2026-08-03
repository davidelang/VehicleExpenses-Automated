# paddle (Paddle-Lite) pin — VehicleExpenses

| | |
|--|--|
| **Fork** | `https://github.com/davidelang/Paddle-Lite.git` |
| **Pin SHA** | `c6a9b9ada00e3b37aa80c21407e7d8e240595ebb` = tip of **`pr-x86-android-mobile-gap`** |
| **Stack** | `pr-upstream-cleanup` ⊂ `pr-x86-android-mobile-gap` (validated). INT8 runtime/opt layer is **not** fully stacked as a third git branch — see below. |
| **build_time** | `few_hours` (Docker image once + per-ABI Android build; historical runs `few_hours`–`tens_of_hours` with restarts) |
| **reproducible** | `false` (NDK, Docker base, third-party tarball, timestamps) |
| **Products** | `artifact/jni/{arm64-v8a,x86_64}/libpaddle_*.so`, optional `PaddlePredictor.jar` |

**Authoritative process doc:** `docs/reference/PADDLE_PIN_BUILDS.md`  
**Locked intent specs (do not edit lightly):** `docs/specs/PADDLE_BUILD.md`, `HOST_PADDLE_USE.md`, `PADDLE_PR_DESCRIPTIONS.md`

---

## Reproduce (library binaries)

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
# Docker + network for image + third-party tarball
./third_party/fetch-deps ro paddle
./third_party/fetch-deps build paddle
# After success, copy into app when promoting:
#   artifact/jni/* → app/src/main/jniLibs/*
#   artifact/PaddlePredictor.jar → app/libs/
```

Env knobs: `PADDLE_ABIS`, `PADDLE_SKIP_IMAGE_BUILD=1`, `PADDLE_DOCKER_IMAGE`.

---

## Git branch stack (validated 2026-08-03)

| Branch | Tip (short) | Role |
|--------|-------------|------|
| `pr-upstream-cleanup` | `2ca96249` | Foundational build/robustness (AVX-512 CRF, INT8 link, CMake, packaging) |
| `pr-x86-android-mobile-gap` | `c6a9b9ad` | **Includes cleanup** + Android x86_64 “mobile gap” + OpenBLAS strip |
| `pr-calib-safe-uint8-dequant` | `d718308c` | ARM int8/uint8 calib dequant overread fix — **based on `develop`, not on x86-gap** |

```text
develop ──┬── pr-upstream-cleanup ── pr-x86-android-mobile-gap   ← libpin git_sha
          └── pr-calib-safe-uint8-dequant   (single commit; not git-stacked on x86-gap)
```

**VE production needs both:**

1. Pin checkout = **x86-gap tip** (mobile + cleanup).  
2. **INT8 layer** = `patches-int8/` file copies (`apply_int8_patches.sh`) — includes analytic quant pass, keep_quantized_weights, calib kernels, JNI MobileConfig, **and** content aligned with the calib PR; plus post-build `patchelf` soname helper.

Ideal future cleanup: rebase/cherry-pick `pr-calib-safe-uint8-dequant` onto `pr-x86-android-mobile-gap` and fold remaining `patches-int8` into a fourth PR; until then **pin SHA + patches-int8** is the contract.

Historical sandbox still has full-file `patches/` applied onto `release/v2.14` inside Docker (`dev-ai-interaction/paddle-build/`). Libpin prefers **git pin + int8 layer** instead of re-applying the entire PR tree as copies.

---

## What the app ships today

| Asset | Location |
|-------|----------|
| JNI | `app/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86_64}/libpaddle_lite_jni.so` (+ `libpaddle_light_api_shared.so` on x86_64) |
| Java | `app/libs/PaddlePredictor.jar` |
| Models | `app/src/main/assets/paddle/prod_u8fp16/*.nb` (+ `en_dict.txt`) |

Models are **not** produced by `./third_party/paddle/build` — separate **host `opt`** pipeline (below).

---

## Model artifact matrix (separate from lib build)

Pipeline lives under `app/src/main/assets/paddle/scripts/` + host `opt` from an INT8-capable build:

| Stage | Script / tool | Output idea |
|-------|----------------|-------------|
| FP32 3ch / mono graphs | `convert_mono.py`, training export | `inference.pdmodel` + params |
| FP32 `.nb` | `optimize_models.sh` + host `opt` | det/rec ARGB + mono, armv7 + x86_64, dynamic shapes |
| INT8 mono `.nb` | `optimize_mono_int8_models.sh` + `opt` with quant flags | `*_int8_*.nb` with analytic input quant |
| Prod ship | copy into `prod_u8fp16/` | u8 input + fp16 path as used by app |

Channel axis: **mono = 1**, **ARGB path = 3**. Runtime uint8→int8 contract: `q = b ^ 128` (see `HOST_PADDLE_USE.md`).

Dynamic shape env for `opt`: `NNADAPTER_DYNAMIC_SHAPE_INFO` (tables in that spec).

---

## Docker / time

| Step | Typical time |
|------|----------------|
| Docker image `ve-paddle-int8` (first) | tens of minutes |
| Each Android ABI slim build | ~tens of minutes–hours |
| Full matrix + models + restarts | historically **hours–low tens of hours** |

Failures usually: NDK/toolchain, missing third-party tarball, kernel string check fail, disk.

---

## Sandbox (historical)

`dev-ai-interaction/paddle-build/` — Dockerfile, `patches/`, `patches-int8/`, slim/tailored scripts, `output/`. Superseded as **SoT** by this pin; keep as research cache until deleted intentionally.
