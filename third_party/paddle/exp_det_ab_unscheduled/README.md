# Unscheduled exp_det_ab nbs (host only)

**Not** under `app/src/main/assets/`, so they are not in the APK.

Scheduled on device: `assets/paddle/exp_det_ab/{product_det,PP-OCRv4_mobile_det}_*.nb` only
(`MultiScaleDetRunner.DET_MODELS`). See `docs/obsolete/EXPERIMENT_DET_MODELS.md`.

Still in this directory (small; gitignored from APK by not being assets):
- `PP-OCRv5_mobile_det_*` — dropped from the matrix (too many 0-box cells)

**Server dets (never scheduled; ~1/10 speed):** Git **LFS**, not regular blobs
(GitHub 100 MiB hook). After clone: `git lfs pull`.
- `PP-OCRv4_server_det_*` (~109 MiB)
- `PP-OCRv5_server_det_*` (~84 MiB)

Requires `git-lfs` (`sudo apt install git-lfs`). Rebuild without LFS:
`app/src/main/assets/paddle/scripts/convert_v5_pir_to_nb.sh`.
`third_party/paddle/tests/server_det_probe` takes an explicit `--model` path.
