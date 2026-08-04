# paddle (Paddle-Lite) pin — VehicleExpenses

| | |
|--|--|
| **Fork** | `https://github.com/davidelang/Paddle-Lite.git` |
| **Pin SHA** | `c6a9b9ada00e3b37aa80c21407e7d8e240595ebb` = tip of **`pr-x86-android-mobile-gap`** |
| **Stack** | pin SHA + **full historical patches/** + **patches-int8/** (see build recipe below) |
| **build_time** | `few_hours` (Docker image once + per-ABI Android build) |
| **reproducible** | `false` (NDK, Docker base, third-party tarball, timestamps) |
| **Products** | `artifact/jni/{arm64-v8a,x86_64}/libpaddle_*.so`, optional `PaddlePredictor.jar` |
| **Strip** | Host `llvm-strip --strip-unneeded` (historical Jul-2026 recipe; NDK28 link gate in `./build`) |

**Authoritative process doc:** `docs/reference/PADDLE_PIN_BUILDS.md`  
**Is-vs-should report (post-merge cleanup):** `dev-ai-interaction/scratch/paddle-pin-is-vs-should-20260804.md`

---

## Reproduce (library binaries) — historical recipe under third_party

Build runs in Docker (NDK r20b), sources from **`third_party/paddle/src`**, products to **`src/bin/<abi>/`**.

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64   # host; container uses JDK8
./third_party/fetch-deps ro paddle
# optional: skip image rebuild if ve-paddle-int8 or paddle-build-int8-20.04 exists
export PADDLE_SKIP_IMAGE_BUILD=1
export PADDLE_DOCKER_IMAGE=paddle-build-int8-20.04   # or ve-paddle-int8
export PADDLE_ABIS="arm64-v8a x86_64"
# arm64 defaults to tailor if third_party/paddle/tailor_models/armv8 exists
export PADDLE_ARM64_PROFILE=tailor   # or slim
export PADDLE_X86_PROFILE=slim
./third_party/paddle/build
# Collect: pin-local artifact/ *and* app jniLibs/libs (independent [[artifact]] rows)
./third_party/get-artifacts paddle
```

### What `./build` does (matches Jul-2026 working path)

1. Copy pin `src` into container workdir  
2. **`patches/`** full-file apply (`scripts/apply_patches.sh`) — build-system + external glog/gflags/openblas + code  
3. **`patches-int8/`** apply (`scripts/apply_int8_patches.sh`)  
4. **x86:** `patch_x86_thin_jni.py` → thin jni + light  
5. **arm64 tailor:** `--with_strip=ON --opt_model_dir=tailor_models/armv8` when profile=tailor  
6. `build_android.sh` with `--android_stl=c++_static`, `--with_arm82_fp16=ON` on armv8  
7. Host **`llvm-strip --strip-unneeded`**  
8. **NDK28 link gate** (minimal shared link against each `.so`)  

Env: `PADDLE_ABIS`, `PADDLE_SKIP_IMAGE_BUILD`, `PADDLE_DOCKER_IMAGE`, `PADDLE_ARM64_PROFILE`, `PADDLE_X86_PROFILE`.

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
| armeabi-v7a | **not** in default `PADDLE_ABIS` | deferred |

Models: `app/src/main/assets/paddle/prod_u8fp16/*.nb` (host `opt`, not this build).

---

## Model artifact matrix (separate from lib build)

See `docs/reference/PADDLE_PIN_BUILDS.md`. Tailor lists for arm64 live in `third_party/paddle/tailor_models/armv8/`.
