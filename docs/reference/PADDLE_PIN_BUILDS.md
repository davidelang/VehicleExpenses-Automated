# Paddle-Lite pin builds (VehicleExpenses)

**Status:** Reference process (2026-08-03).  
**Pin tree:** `third_party/paddle/`  
**Locked product intent (do not casually edit):** `docs/specs/PADDLE_BUILD.md`, `HOST_PADDLE_USE.md`, `PADDLE_PR_DESCRIPTIONS.md`

This document is the **how-to** for configuring, building, and validating the Paddle **library** and **model** artifacts the app consumes. It is written for humans and agents using **libpin** (`fetch-deps` / `build` / `get-artifacts`).

---

## 1. What we ship vs what we build

### 1.1 Library (JNI / Java)

| Artifact | App path (ship) | Pin product path |
|----------|-----------------|------------------|
| `libpaddle_lite_jni.so` | `app/src/main/jniLibs/<abi>/` | `third_party/paddle/artifact/jni/<abi>/` |
| `libpaddle_light_api_shared.so` | x86_64 only (today) | `artifact/jni/x86_64/` |
| `PaddlePredictor.jar` | `app/libs/` | `artifact/PaddlePredictor.jar` |

ABIs of interest: **arm64-v8a** (devices), **x86_64** (emulators), **armeabi-v7a** (legacy / head units — keep building when capacity allows).

### 1.2 Models (Naive Buffer `.nb`)

| Kind | Channels | Quant | App path (example) |
|------|----------|-------|---------------------|
| Detection | mono 1ch | u8→fp16 path | `assets/paddle/prod_u8fp16/det_{armv8,x86_64}.nb` |
| Recognition v3 | mono | u8→fp16 | `rec_v3_*.nb` |
| Recognition numeric | mono | u8→fp16 | `rec_numeric_*.nb` |

Built with host **`opt`** (INT8-capable build of the same fork), **not** by the Android slim JNI script alone.

### 1.3 Two pipelines

```text
                    ┌─────────────────────────────┐
  Git pin + patches │  Docker Android slim build  │ → jniLibs / jar
                    └─────────────────────────────┘

  FP32/mono export → convert_mono → opt (fp32 nb)
                                 → opt --quant_model (int8 nb) → assets
```

---

## 2. Source topology (GitHub + layers)

### 2.1 Fork

- **Remote:** `https://github.com/davidelang/Paddle-Lite.git`  
- **Upstream lineage:** PaddlePaddle/Paddle-Lite (develop / release history)

### 2.2 PR branches named `pr-*` (validated 2026-08-03)

| Branch | Tip | Stack relation |
|--------|-----|----------------|
| `pr-upstream-cleanup` | `2ca96249` | Base PR: general build robustness |
| `pr-x86-android-mobile-gap` | `c6a9b9ad` | **Contains cleanup** + x86 Android mobile gap + OpenBLAS strip |
| `pr-calib-safe-uint8-dequant` | `d718308c` | **Single commit on `develop`**, **not** a git descendant of x86-gap |

```text
develop ──┬── cleanup ── x86-android-mobile-gap     ← libpin git_sha
          └── calib-safe-uint8-dequant              ← not stacked in git
```

**Implication:** “Three stacked PRs” is **true for cleanup→x86**, **false for int8 calib as a third git branch**. Production combines:

1. **Checkout** `pr-x86-android-mobile-gap` (`c6a9b9ad`), and  
2. **Apply** `third_party/paddle/patches-int8/` (file layer; broader than the single calib commit — includes analytic quant pass, keep_quantized_weights, JNI, both ARM/x86 calib kernels, opt flags, etc.).

### 2.3 What each layer is for

| Layer | Why VE needs it |
|-------|------------------|
| cleanup | Build on modern hosts; INT8 link; packaging; DDimLite logging |
| x86 mobile gap | Emulator `x86_64` JNI with tiny_publish; AVX2 scoping; NDK/compiler fixes |
| patches-int8 / calib PR | Greyscale uint8 feed, safe dequant (no buffer overread), opt quant passes, SONAME helper |

---

## 3. Libpin layout

```text
third_party/paddle/
  libpin.toml           # pin identity + [[artifact]]
  SOURCE.md             # short map
  build                 # host entry (Docker orchestration)
  scripts/
    Dockerfile.int8     # Ubuntu 20.04 + NDK r20b + JDK8 + patchelf
    run-android-slim.sh # in-container: copy pin, apply int8, build_android.sh
    apply_int8_patches.sh
  patches-int8/         # INT8/u8 layer (full-file copies under code/)
  src/                  # materialized git @ pin (gitignored)
  artifact/             # stable outputs after get-artifacts
```

### 3.1 Happy path (libraries)

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
# Docker required; long first image build
./third_party/fetch-deps ro paddle
./third_party/fetch-deps build paddle
# Optional ABIs:
# PADDLE_ABIS="arm64-v8a x86_64 armeabi-v7a" ./third_party/paddle/build
```

Promote into the app (manual / plan-approved):

```bash
cp third_party/paddle/artifact/jni/arm64-v8a/libpaddle_lite_jni.so app/src/main/jniLibs/arm64-v8a/
cp third_party/paddle/artifact/jni/x86_64/libpaddle_lite_jni.so app/src/main/jniLibs/x86_64/
# light_api if present
cp third_party/paddle/artifact/PaddlePredictor.jar app/libs/   # if produced
```

### 3.2 Env knobs

| Variable | Meaning |
|----------|---------|
| `PADDLE_ABIS` | Space-separated ABIs (default `arm64-v8a x86_64`) |
| `PADDLE_SKIP_IMAGE_BUILD=1` | Reuse `ve-paddle-int8` image |
| `PADDLE_DOCKER_IMAGE` | Override image tag |
| `LIBPIN_NO_BWRAP` / `LIBPIN_NO_LANDLOCK` | Sandbox off (Docker still needs host privileges for daemon) |

### 3.3 Docker note

The container **copies** pin `src` to a writable workdir, applies `patches-int8`, runs `lite/tools/build_android.sh`. Pin tree on host can stay RO. Network: third-party tarball from bcebos on first use inside the container.

---

## 4. Build matrix (libraries)

| Target | `--arch` | Notes |
|--------|----------|--------|
| arm64-v8a | `armv8` | `--with_arm82_fp16=ON`; patchelf SONAME on jni; **tailor** default when models present |
| x86_64 | `x86_64` | Emulator; thin jni + **light**; **no HW fp16 backbone** (see §4.1). OpenBLAS via `patches/external/openblas.cmake`: **`USE_THREAD=1 NUM_THREADS=4`** (not `NUM_THREADS=1` — that stubs multi-thread and caps host CPU at ~1 core; see `patches-x86-openblas/README.md`) |
| armeabi-v7a | `armv7` | Real limited **ARMv7 head units**; **fp32 calib only** — never `ARM82_FP16` for product (see §4.1) |

Common flags (slim):

```text
--toolchain=clang --with_java=ON --with_cv=OFF --with_extra=ON
--with_log=OFF --with_benchmark=OFF --android_stl=c++_static --with_exception=ON
```

**Post-check** (kernel string stamps on jni/light):

| Stamp | arm64-v8a | x86_64 | armeabi-v7a |
|-------|-----------|--------|-------------|
| `int8_to_fp32`, `uint8_to_fp32`, `fp32_to_uint8` | required on jni or light | required on **light** (thin jni is wrapper) | required (product path) |
| `int8_to_fp16`, `uint8_to_fp16` | required (`--with_arm82_fp16=ON`) | optional on light (soft); backbone still fp32 | **SKIP / not product** — true v7 does not use HW fp16 |

### 4.1 Precision limitations (by design)

**armeabi-v7a (true ARMv7-A head units):**  
Do not ship Paddle’s `LITE_WITH_ARM82_FP16` for this ABI. That flag compiles with `-march=armv8.2-a+fp16` (wrong ISA class for limited v7 chips → SIGILL risk).  
“Soft fp16” (store half, compute via convert + fp32 NEON) is **not** a free win: on weak v7 CPUs convert overhead usually makes it **worse or no better than fp32**. **Product decision: fp32 calib path only; do not implement soft-fp16 for head units.**

**x86_64 / AMD64 Android emulator:**  
No competitive HW fp16 conv stack in this pin. Host `opt` may enable kFP16 places so analytic quant can insert `int8_to_fp16` / `uint8_to_fp16`, then **type-precision cast demotes the backbone to float** (`opt_base.cc` comment). Same class of limitation as “could not get real fp16 match on AMD64” — accept fp32 mid-graph; do not block on “true” x86 fp16 parity with arm64.

**arm64-v8a:** HW fp16 is the product path (`prod_u8fp16/*_armv8.nb`).

### 4.2 Tailor and size (library, not precision)

`LITE_BUILD_TAILOR` drops unused kernels using model lists (`.nb` + `.tailored_*`). **This is the main SO size lever**, independent of fp16 on armv7.

| Historical (sandbox Jul 2026) | Slim | Tailored |
|-------------------------------|------|----------|
| arm64 jni | ~5.5 MB | **~1.6 MB** |
| x86_64 light | ~9.5 MB | **~3.7 MB** |
| x86_64 jni | ~0.7 MB | ~30–50 KB (thin/tailor) |

| Current pin (NDK r28c, 2026-08-04) | Profile | Size |
|------------------------------------|---------|------|
| arm64 | tailor | ~1.8 MB |
| armv7 | **tailor** (2026-08-04 pin) | **~0.75 MB** (was slim ~3.0 MB) |
| x86_64 light | **slim** (default) | ~10 MB; x86 tailor experimental (android-x86 drops KernelRegistrar) |

**armv7 tailor landed** with `paddle-models` armv7 lists (fp32 calib, no arm82 FP16). **x86_64** stays slim by default (`PADDLE_X86_PROFILE=slim`) until a force-keep/whole-archive fix lands for strip-kernel registries.

---

## 5. Model pipeline (detailed)

### 5.1 Prerequisites

- Host `opt` built from the **same INT8-capable tree** (Linux x86 `opt` binary). Historical: `dev-ai-interaction/paddle-build/output/int8_linux/opt_linux_x86_int8` or research `opt_linux_x86`.
- Python env for mono conversion: see `HOST_PADDLE_USE.md` (e.g. conda `paddle_env_v3`).
- Upstream/export FP32 inference graphs under a `models/` root (not always in git).

### 5.2 Monochrome conversion

```bash
# app/src/main/assets/paddle/scripts/convert_mono.py
# Weight averaging / graph edit: 3ch → 1ch input
python convert_mono.py …   # see script and HOST_PADDLE_USE
```

### 5.3 FP32 optimize (ARGB + mono)

```bash
# optimize_models.sh — sets OPT_TOOL, MODEL_ROOT, dynamic shapes via NNADAPTER_DYNAMIC_SHAPE_INFO
./optimize_models.sh
```

Dynamic shape examples (locked tables in `HOST_PADDLE_USE.md`):

| Model | Mono example |
|-------|----------------|
| Det | `x:1,1,64,64:1,1,1280,1280:1,1,4096,4096` |
| Rec v3 | `x:1,1,48,32:1,1,48,320:1,1,48,1280` |

### 5.4 INT8 mono optimize

```bash
# optimize_mono_int8_models.sh
# Requires INT8 opt (analytic_input_quant_pass)
OPT_TOOL=/path/to/opt_linux_x86_int8 ./optimize_mono_int8_models.sh
```

Flags concept: `--quant_model=true --quant_type=QUANT_INT8` plus dynamic shapes.

### 5.5 Runtime contract (app)

No float mean/std at feed for the mono int8 path:

```text
int8_t q = (int8_t)(uint8_pixel ^ 128);
```

Must match what `analytic_input_quant_pass` baked into the `.nb`. See `HOST_PADDLE_USE.md` §4.

### 5.6 Prod layout

Ship selected `.nb` under `assets/paddle/prod_u8fp16/` with `en_dict.txt`. Naming today uses `det_armv8.nb` / `det_x86_64.nb` etc. (armv8 ≡ arm64 device models).

---

## 6. Validation

### 6.1 Library

- ABI present under `artifact/jni/…`
- Kernel string checks (above)
- App link: load `NativePaddleEngine` / OpenCV init still OK
- Emulator **5554** (x86_64): Alignment + Pump **First 10** short reports; compare JSON semantically to a baseline if changing JNI

### 6.2 Models

- Host or on-device OCR smoke (existing experiment screens)
- No SEGV on exact-size greyscale `ShareExternalMemory` (calib PR motivation)

### 6.3 Time expectations

| Work | Time |
|------|------|
| Docker image | tens of minutes (first) |
| One ABI slim | tens of minutes–hours |
| Models + full matrix + restarts | **hours to low tens of hours** historically |

---

## 7. Historical sandbox map

| Path | Role |
|------|------|
| `dev-ai-interaction/paddle-build/` | Old Docker + `patches/` + `patches-int8/` + slim/tailored scripts + `output/` |
| `dev-ai-interaction/paddle_models/` | Model intermediate trees |
| `dev-ai-interaction/Paddle-Lite-upstream/` | Upstream mirror cache |

Prefer **libpin** for new rebuilds; leave sandbox until outputs are fully promoted.

---

## 8. Troubleshooting

| Symptom | Check |
|---------|--------|
| `cannot obtain <sha>` on `fetch-deps ro` | Network / sandbox write to `src/.git`; try `LIBPIN_NO_BWRAP=1` once or ensure host `~/git` has objects |
| JNI missing after docker | `build.lite.android.*` path; arch flag; disk |
| Kernel string FAIL | INT8 patches not applied; wrong light vs jni check file |
| Emulator SIGILL | AVX flags / not using x86_64 mobile build |
| SEGV on greyscale | Missing calib safe dequant / wrong model opt pass |
| App still old .so | Forgot copy from `artifact/` to `jniLibs` + reinstall |

---

## 9. Related files

| File | Content |
|------|---------|
| `third_party/paddle/SOURCE.md` | Short pin map |
| `third_party/paddle/build` | Host Docker entry |
| `docs/specs/PADDLE_BUILD.md` | Locked build intent |
| `docs/specs/HOST_PADDLE_USE.md` | Host opt / mono / uint8 contract |
| `docs/specs/PADDLE_PR_DESCRIPTIONS.md` | PR write-ups for upstream |
| `app/src/main/assets/paddle/scripts/*` | Model conversion scripts |
