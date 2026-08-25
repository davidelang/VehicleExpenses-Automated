# Obsolete / not scheduled: det models for multi-scale & pump expand experiments

## PP-OCRv5 mobile det — too weak for reliable heat/boxes

**Status:** Removed from multi-scale matrix (`MultiScaleDetRunner.DET_MODELS`).

**Evidence (selected-sample multi-scale runs 2026-08-10, phone + emu):**

- High rate of **0-box** cells across many photos and scales.
- Not uncommon for **all scales** on a photo to return empty heat / no boxes for v5 mobile.
- Heatmap overlays often blank → not useful for expand-P or box comparison.

**Conclusion:** v5 mobile is too poor at detecting anything for this experiment’s purpose.

**v4 mobile also dropped** (2026-08-24/25): start-seed parity with product; unique p4 wins were horrid rows 67/78. See `docs/obsolete/DROP_P4_P5_DET.md` + tag `obsolete-p4-p5-det`. Scheduled det is **product_det** only. Do not still prefer v4.

## PP-OCRv4 / v5 **server** det — too slow for real-time / multi-scale

**Status:** Never in multi-scale scheduled models (dropped earlier).

**Evidence:**

- Roughly **~1/10** the speed of mobile/product paths.
- **Tens of seconds per analysis** cell on device — unacceptable for any user-wait or full multi-scale matrix.

**Conclusion:** Server dets may be more accurate but are not candidates for real-time or multi-scale sweep work on phone/emu.

## Recovery

- Scheduled APK `exp_det_ab` is **`product_det` only**. v4 mobile nbs live under `third_party/paddle/exp_det_ab_unscheduled/` after `obsolete-p4-p5-det`.
- Restore v4-in-APK + QF G4-vjump + p4 columns from tag `obsolete-p4-p5-det` (see `DROP_P4_P5_DET.md`).
- Multi-scale list: `MultiScaleDetRunner.DET_MODELS` (product only after that drop).
