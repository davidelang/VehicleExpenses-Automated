# Unscheduled exp_det_ab nbs (host only)

Kept in git for ad-hoc probes. **Not** under `app/src/main/assets/`, so they are not in the APK.

Scheduled on device: `assets/paddle/exp_det_ab/{product_det,PP-OCRv4_mobile_det}_*.nb` only.

Dropped from `MultiScaleDetRunner.DET_MODELS` / pump columns:
- `PP-OCRv5_mobile_det_*` — too many 0-box cells
- `PP-OCRv4_server_det_*`, `PP-OCRv5_server_det_*` — ~1/10 speed, never scheduled
