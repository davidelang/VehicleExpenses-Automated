# Pull Request (app): int8-paddle-processing — production uint8_fp16_u8

**Branch:** `int8-paddle-processing`  
**Base:** `master`  
**Scope:** VehicleExpenses Android app only (not Paddle-Lite upstream).

## Summary
Ship production OCR path **uint8_fp16_u8** (raw greyscale u8 feed, fp16 compute graphs, u8 heatmap on det) with pruned assets and arm64 model-tailored Paddle Lite JNI. Multi-path campaign harness removed.

## Changes
- Default / only runtime path: u8 feed + heat thresh 0; models under `assets/paddle/prod_u8fp16/`
- **arm64-v8a:** true `LITE_BUILD_TAILOR` jni ~1.6MB (prod kernels only)
- **x86_64:** strip-only slim (full kernels, ~9.5MB light) — same prod models; true tailor deferred (space only)
- **armeabi-v7a:** unchanged fat lib; tailor/drop deferred
- Removed: `PrecCampaignBatchActivity`, `PrecisionPath` multi-path matrix, Manifest campaign entry

## Test plan
- [ ] Phone arm64: app OCR (pump/odo) with shipped prod models
- [ ] Emulator x86_64: same (expects larger jni; same speed for prod path)
- [ ] Confirm no PrecCampaign activity / no multi-path switch API

## Non-goals / follow-ups
- True tailor x86_64 and armv7 (APK size only; see backlog)
- Upstream Paddle PRs (separate)

## History
Rewritten so true arm tailor + docs precede productize; no drop/restore harness detour.
