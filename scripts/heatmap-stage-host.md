# Heatmap stage matrix — how to run all environments

## Goal

Prove **source → feed → heatmap** identity (not full OCR).  
Angles (`paddle_cpp_angle` / C `heatmapToAngle`) are logged only until heat matches.

| Cell | Run |
|------|-----|
| arm64 + fp16 | Phone: drawer **Heatmap stage** → fp16 |
| arm64 + fp32 | Phone: **Heatmap stage** → fp32 |
| x86_64/android + fp16 | Emu: same APK |
| x86_64/android + fp32 | Emu: same APK |
| x86_64/**linux** + fp16/fp32 | Host scripts below |

One multi-ABI APK holds both model packs (`prod_u8fp16` + `prod_u8fp32_u8`).

## Android (phone + emu)

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
# ensure pump_photos populated (device corpus or push research photos)
adb logcat -s HeatmapStage:I
# UI: Heatmap stage → Run both packs
adb pull /sdcard/Android/data/com.davidlang.vehicleexpensesautomated/files/heatmap_stage/ ./hm-pull/
```

## Linux host

```bash
# 1) Linux light SO (same pin + patches as Android; ~long)
scripts/build-linux-light.sh

# 2) Host CLI
scripts/build-heatmap-stage-host.sh

# 3) Run both packs on research photos
scripts/run-heatmap-stage-host.sh
# OUT under dev-ai-interaction/scratch/heatmap-stage-host/run-*
```

Note: host mono convert uses OpenCV imread (DNG may skip). Prefer JPG/PNG or preconverted mono for source-CRC match with Android LibRaw path.

## Compare any two runs

```bash
scripts/compare-heatmap-stage.py \
  --a path/A/results.jsonl --a-label pixel-fp32 \
  --b path/B/results.jsonl --b-label emu-fp32
```

Gates: **1 source** → **2 feed** → **3 heat** (stop blaming later stages if earlier fails).

## Elimination order

1. source differ → decode (LibRaw vs OpenCV)  
2. feed differ → scale/pad (`prepareScale` / INTER_AREA / tier pad)  
3. heat differ → Lite SO / `.nb` / ISA / Android-x86 patches  
4. only then: C `heatmapToAngle`

## Preprocess isolation (2026-08-07 triage)

Phone arm64 vs emu x86 on the same files (`PreprocessStageDump` / deep link  
`vehicleexpenses://experiment/preprocess?auto=triage`):

| Stage | Cross-arch |
|-------|------------|
| LibRaw **unpack** (Bayer) | **Bit-identical** |
| LibRaw **dcraw_process → RGB** | Sparse **±1** only (when it differs) |
| RGB→Y | Follows RGB; no larger errors |
| JPG source_y | **Bit-identical** |
| INTER_AREA feed | Sparse **±1** (1–13 pixels typical) |

So QEMU/host cannot claim Android parity until it runs **the same LibRaw develop + OpenCV INTER_AREA**, not PGM+nearest.  
Compare dumps: `scripts/compare-preprocess-dumps.py --a pixel_run --b emu_run`.

### Harness parity target

1. **Preprocess gate** (before paddle): match Android `source_y` / `feed` bins bit-exact (or fail).  
2. Build NDK `preprocess` CLI for arm64 + x86_64 product ABIs (same sources as app), run under qemu-user.  
3. Only then det-heat on **identical feeds** so QEMU heat matches device heat when SO/model match.
