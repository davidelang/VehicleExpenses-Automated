# Obsolete / not scheduled: det models for multi-scale & pump expand experiments

## PP-OCRv5 mobile det — too weak for reliable heat/boxes

**Status:** Removed from multi-scale matrix (`MultiScaleDetRunner.DET_MODELS`).

**Evidence (selected-sample multi-scale runs 2026-08-10, phone + emu):**

- High rate of **0-box** cells across many photos and scales.
- Not uncommon for **all scales** on a photo to return empty heat / no boxes for v5 mobile.
- Heatmap overlays often blank → not useful for expand-P or box comparison.

**Conclusion:** v5 mobile is too poor at detecting anything for this experiment’s purpose. Prefer **product_det** and **PP-OCRv4_mobile_det**.

## PP-OCRv4 / v5 **server** det — too slow for real-time / multi-scale

**Status:** Never in multi-scale scheduled models (dropped earlier).

**Evidence:**

- Roughly **~1/10** the speed of mobile/product paths.
- **Tens of seconds per analysis** cell on device — unacceptable for any user-wait or full multi-scale matrix.

**Conclusion:** Server dets may be more accurate but are not candidates for real-time or multi-scale sweep work on phone/emu.

## Recovery

- Assets may still ship under `assets/paddle/exp_det_ab/` for ad-hoc probes.
- Multi-scale list: `MultiScaleDetRunner.DET_MODELS` (product + v4 mobile only as of this doc).
- Pump Set P4 loads `PP-OCRv4_mobile_det` via `NativePaddleEngine.loadExperimentDetTiers` for that column only.
