# Unscheduled exp_det_ab nbs (host only)

**Not** under `app/src/main/assets/`, so they are not in the APK.

Scheduled on device: `assets/paddle/exp_det_ab/{product_det,PP-OCRv4_mobile_det}_*.nb` only
(`MultiScaleDetRunner.DET_MODELS`). See `docs/obsolete/EXPERIMENT_DET_MODELS.md`.

Still in this directory (small; gitignored from APK by not being assets):
- `PP-OCRv5_mobile_det_*` — dropped from the matrix (too many 0-box cells)

**Removed from git** (GitHub 100MB limit; never scheduled; ~1/10 speed):
- `PP-OCRv4_server_det_*` (~109MB)
- `PP-OCRv5_server_det_*` (~84MB)

Those names are gitignored. Rebuild host-only with
`app/src/main/assets/paddle/scripts/convert_v5_pir_to_nb.sh` if you need a
local probe; do not commit them. `third_party/paddle/tests/server_det_probe`
takes an explicit `--model` path.
