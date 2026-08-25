# Unscheduled exp_det_ab nbs (host only)

Kept in git for ad-hoc probes. **Not** under `app/src/main/assets/`, so they are not in the APK.

Scheduled on device: APK `assets/paddle/exp_det_ab/` is **`product_det` only**.

Dropped / unscheduled (host probes only; restore v4-in-APK from tag `obsolete-p4-p5-det`):
- `PP-OCRv4_mobile_det_*` — start-seed parity with product; unique p4 wins were horrid 67/78 (`docs/obsolete/DROP_P4_P5_DET.md`)
- `PP-OCRv5_mobile_det_*` — too many 0-box cells
- `PP-OCRv4_server_det_*`, `PP-OCRv5_server_det_*` — ~1/10 speed, never scheduled
