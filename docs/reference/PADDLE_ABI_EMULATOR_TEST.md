# Multi-ABI paddle library testing

**Status:** Process (2026-08-04)  
**Goal:** Validate `libpaddle_*` (and related native) for **arm64-v8a**, **armeabi-v7a**, and **x86_64** with the right tool for each layer.

Related: `third_party/paddle/SOURCE.md` (precision policy), `PADDLE_PIN_BUILDS.md`.

---

## 0. Priority order (locked intent)

| Order | Layer | What | When | Contends for devices? |
|-------|--------|------|------|------------------------|
| **1 (frequent)** | **`paddle_so_smoke`** | ELF machine / JNI export / precision stamps per ABI under QEMU | After every meaningful paddle (or jni) rebuild; default CI/agent gate | **No** |
| **1b (functional)** | **`ocr_functional`** | VE path: **angle → deskew → det → crop → rec** on checked-in fixture (`skewed_hello.png`) | After paddle/jni rebuild when a device is free; `PADDLE_OCR_FUNCTIONAL=1` | **Yes** (device ABI only, usually x86_64 on 5554) |
| **2 (opportunistic)** | **Full app on emulator-5554** (x86_64 Android) | Install APK, First 10 Align/Pump, semantic JSON | When 5554 is **not** busy with other work | **Yes** |
| **3 (opportunistic)** | **Full app on real hardware** (Pixel, head unit, …) | Same as (2), field ABI + timing | When device is free / release claim | **Yes** |

**Do not** invert this: 5554/real hardware are **not** the first line for multi-ABI paddle library checks. They validate the **app integration** of builds already smoke-tested at the SO level.

### Why not “Android Emulator for ARM”?

- Everyday **x86_64** AVD only executes **x86_64** jniLibs (multi-ABI APK still loads x86). It does **not** run armv7/arm64 paddle machine code.
- A full Android “ARM system image” on an x86 host is a heavyweight guest; it is **not** the preferred frequent gate and is easy to confuse with real chip testing.
- **ISA-faithful** library smoke = **QEMU user-mode** (or real silicon), not “boot the full Android UI emulator.”

---

## 1. Layer A — `paddle_so_smoke` (all three ABIs, frequent)

### 1.1 What it proves

| ABI | How the SO runs | Pass criteria (minimum) |
|-----|-----------------|-------------------------|
| **x86_64** | Native on Linux host (or QEMU-x86_64 if desired) | `dlopen` / NDK link ok; light API or tiny C++ harness runs; stamps present |
| **arm64-v8a** | **`qemu-aarch64`** (+ Android/Bionic sysroot as needed) | Same; no immediate SIGILL on load/init |
| **armeabi-v7a** | **`qemu-arm`** (armhf / Android armv7 sysroot) | Same; true-v7 product SO (no reliance on ARM82_FP16) |

### 1.1b Layer A2 — `ocr_functional` under **QEMU** (all three ABIs)

ISA-faithful functional gate of **product** `libpaddle_light_api_shared.so` + `.nb`:

1. Det (uint8) → heatmap boxes + angle  
2. Deskew (rotate −angle)  
3. Det again → crop largest box  
4. Rec V3 CTC → expect fixture text  

```bash
./scripts/paddle-ocr-qemu.sh
# seeds Bionic rootfs from adb once (Pixel → arm*, 5554 → x86_64)
```

| Piece | Path |
|-------|------|
| Fixture | `third_party/paddle/tests/ocr_functional/fixtures/skewed_hello.pgm` |
| Harness | `tests/ocr_functional/qemu/functional_main.cpp` (+ loader dlopen core) |
| Driver | `./scripts/paddle-ocr-qemu.sh` |

### 1.1c Layer A2′ — instrumented Android (optional, x86_64 AVD)

Same stages via **VE Kotlin** (`OcrFunctionalPipeline`) on a device:

```bash
./scripts/paddle-ocr-functional.sh --serial emulator-5554
```

Not multi-ABI; complements QEMU when validating app BufferSet/OpenCV wiring.

### 1.2 Host packages (library smoke)

```bash
# QEMU user for ARM ISAs
sudo apt install qemu-user-static   # or qemu-user

# NDK already used for app / link-gate
# ANDROID_NDK_HOME or $HOME/Android/Sdk/ndk/<ver>
```

Build a small NDK executable per ABI (e.g. `third_party/paddle/tests/paddle_so_smoke/`) that:

1. Loads `libpaddle_light_api_shared.so` (and jni if needed) from `artifact/jni/<abi>/` or `src/bin/<abi>/`  
2. Optionally creates a predictor from a pinned `.nb`  
3. Exits 0/1  

Run:

```text
# sketch
./paddle_so_smoke.x86_64
qemu-aarch64 -L <android-sysroot-arm64> ./paddle_so_smoke.arm64-v8a
qemu-arm     -L <android-sysroot-armv7> ./paddle_so_smoke.armeabi-v7a
```

### 1.3 Also keep host L0 (no QEMU)

Part of the same frequent gate, before or inside smoke:

- `file` / ELF machine type matches ABI  
- Stamp strings per SOURCE precision policy  
- Existing NDK28 **link-gate** in `third_party/paddle/build`  
- Models present: `prod_u8fp16/*_{armv8,x86_64}.nb`, `prod_u8fp32_u8/*_armv7.nb`

### 1.4 Frequency

| Event | `paddle_so_smoke` (3 ABIs) |
|-------|----------------------------|
| Successful `paddle/build` + `get-artifacts` | **Required** |
| Local PR touching jniLibs / paddle pin | **Required** |
| Unrelated app-only change | optional |

Report under: `dev-ai-interaction/scratch/paddle-so-smoke-YYYYMMDD-HHMM/`.

---

## 2. Layer B — Full app on emulator-5554 (x86_64 only, opportunistic)

**When:** device free; after Layer A green for the build under test.

| Step | Notes |
|------|--------|
| ABI | Emulator runs **x86_64** product only |
| Install | `./build_app` + `adb -s emulator-5554 install -r …` |
| Test | Align + Pump **First 10**, pull JSON, semantic compare to baseline |
| Does **not** prove | armv7 or arm64 `.so` execution |

Baselines: e.g. `scratch/pin-device-test-*/emu5554/`.

If 5554 is in use for another agent/experiment, **skip** Layer B; do not block the pin on library smoke alone.

---

## 3. Layer C — Full app on real hardware (opportunistic)

| Device | ABI under test |
|--------|----------------|
| Pixel / arm64 phone | arm64-v8a + `prod_u8fp16` |
| True head unit (armeabi-v7a primary) | armv7 + `prod_u8fp32_u8` |
| Other | as primary ABI |

Same idea as Layer B: full app + First 10 when free; required only for **release / field claims**, not every library iteration.

---

## 4. Gates summary

| Gate | Required |
|------|----------|
| After paddle library rebuild | **Layer A** all three ABIs (`paddle_so_smoke` + L0) |
| Before local PR (paddle/jni) | Layer A; Layer B if 5554 free |
| Multi-ABI pin “ready for Master” | Layer A green; Layer B preferred; Layer C if claiming Pixel/head-unit |
| Head-unit production claim | Layer A armv7 + **Layer C** on real v7-class device |

---

## 5. Implementation status

- [x] `third_party/paddle/tests/paddle_so_smoke/` — NDK static binary per ABI + host ELF checker  
- [x] `scripts/paddle-so-smoke.sh` — host checks + qemu-arm / qemu-aarch64 / qemu-x86_64  
- [x] Package list: `docs/reference/PADDLE_SO_SMOKE.md`  
- [ ] Optional: light-API one-shot with prod `.nb` under QEMU  
- [ ] Wrapper: if `adb devices` shows free 5554, offer Layer B First 10  
- [ ] First 10 baselines automation  

---

## 6. Open / non-goals

- Full Android ARM AVDs as the **frequent** gate — **non-goal**  
- Using 5554 to “cover” armv7 — **incorrect**  
- Soft-fp16 / ARM82 on true v7 — still not product (see SOURCE precision policy)  
- armv7 product models: **done** (`prod_u8fp32_u8/*_armv7.nb`); keep in Layer A model presence check  

---

## 7. Related product layout (runtime)

| Primary ABI | Models | Path id |
|-------------|--------|---------|
| arm64-v8a | `prod_u8fp16/*_armv8.nb` | `uint8_fp16_u8` |
| x86_64 | `prod_u8fp16/*_x86_64.nb` | `uint8_fp16_u8` |
| armeabi-v7a | `prod_u8fp32_u8/*_armv7.nb` | `uint8_fp32_u8` |

App: `NativePaddleEngine.productArchAndDir()`.
