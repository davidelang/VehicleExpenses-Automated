# ocr_functional — multi-ABI paddle OCR gates

## Primary: QEMU per ABI (what you asked for)

ISA-faithful test of **product** `libpaddle_light_api_shared.so` + production `.nb` models under **qemu-user** (not the Android UI emulator).

| Stage | What |
|-------|------|
| 1 | Load mono fixture `fixtures/skewed_hello.pgm` |
| 2 | **Det** (uint8) → heatmap → boxes + **angle** |
| 3 | **Deskew** (rotate −angle) |
| 4 | Det again → **crop** largest box |
| 5 | **Rec** V3 CTC → compare to `ABCD12345` |

```bash
# Default pin build / pin test (wired in):
./third_party/paddle/build          # steps 6–7: SO smoke + OCR QEMU
./third_party/paddle/test           # same gates without rebuild

# Skip OCR only:
PADDLE_SKIP_OCR_QEMU=1 ./third_party/paddle/test

# Standalone:
./scripts/paddle-ocr-qemu.sh
./third_party/paddle/tests/ocr_functional/qemu/run.sh
```

| ABI | QEMU | Full pipeline | Models |
|-----|------|---------------|--------|
| **arm64-v8a** | `qemu-aarch64` | **Yes** — angle ≈15°, OCR exact | `prod_u8fp16/*_armv8.nb` |
| **x86_64** | `qemu-x86_64` | **Yes** — same | `prod_u8fp16/*_x86_64.nb` |
| **armeabi-v7a** | `qemu-arm` + bwrap PID1 | **Yes** — angle ≈15°, OCR exact (fixed 2026-08-04) | `prod_u8fp32_u8/*_armv7.nb` |

### armv7 zero-heatmap — root cause & fix (2026-08-04)

**Symptom:** After feed + `Run()`, det heatmap was all zeros (`uint8 max=0`). Repro on Pixel + QEMU.

**Root cause:** `optimize_armv7_prod_u8fp32_u8.sh` passed **`--quant_model=true --quant_type=QUANT_INT8`**. That post-training weight quant produced a broken armv7 det graph (size ~1.0MB vs healthy ~2.5MB). Slim/full SOs and feed path were fine — float mono models OCR-passed on the same SO.

**Fix:**
- Re-opt **without** `quant_model`: analytic uint8 input only; det keeps `--output_calib_precision=uint8`; **rec keeps float CTC out** (no output calib).
- Product armv7 runtime: **slim** light/jni (~3.1MB) by default (`PADDLE_ARMV7_PROFILE=slim`).
- Keep-registry (no LTO / KernelRegistrar `used`) applied for all Android paddle builds.

**Verify:** QEMU + Pixel `paddle_ocr_functional` → angle ≈−14.4°, OCR `ABCD12345` edit=0.

### How it runs

1. **NDK build** of tiny **loader** + `libpaddle_ocr_core.so` (links paddle).  
   Directly linking paddle into the main ELF crashes in Bionic constructors under QEMU; **dlopen** of the core SO is reliable.
2. **Bionic rootfs** cache (`qemu/rootfs/<abi>/`, gitignored): Android linker + libc/libm/libdl from adb once (Pixel → arm*, 5554 → x86_64) + NDK `libc++_shared` + stub `liblog`.
3. **qemu-*** `-L rootfs` with `LD_LIBRARY_PATH` = stage dir first.

### Fixtures

| File | Role |
|------|------|
| `fixtures/skewed_hello.png` | RGB source (+15°, text `ABCD12345`) |
| `fixtures/skewed_hello.pgm` | Mono for QEMU harness |
| `fixtures/en_dict.txt` | CTC dict |
| `fixtures/expected.json` | Expectations (also used by instrumented test) |

---

## Secondary: Android instrumented (emulator/device)

Full **VE Kotlin** path (`OcrFunctionalPipeline`: same angle/deskew/detect/crop/recognize helpers) on a running Android process:

```bash
./scripts/paddle-ocr-functional.sh --serial emulator-5554
```

Validates app wiring (BufferSet, OpenCV, assets). **Not multi-ABI** (AVD uses x86_64 jniLibs only).

---

## Layer map

| Layer | Tool | Multi-ABI? |
|-------|------|------------|
| A SO smoke | `paddle_so_smoke` / QEMU | ELF + stamps, 3 ABIs |
| **A2 functional** | **`paddle-ocr-qemu.sh`** | **det→deskew→crop→rec** (arm64 / armv7 / x86 full) |
| A2′ app | `paddle-ocr-functional.sh` | VE Kotlin on device |
| B First 10 | Align/Pump on 5554 | opportunistic full app |
