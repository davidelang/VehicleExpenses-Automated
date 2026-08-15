# Deskew angle review

Multi-method skew estimation from **single-scale host det** (long-edge **2048**, `prod_u8fp32_u8` x86_64 model), then an interactive HTML UI to confirm/select ground-truth angles.

## Product assumptions (encoded in estimators)

- **Most** text is horizontal; **some** components are far from horizontal (not “all near-horizontal”).
- Deskew uses **one** resolution (2048), not multi-scale (multi-scale is post-deskew for boxes).
- Text is **sparse**; dash objects can be **near-square** (minAreaRect alone is unstable).
- **Production reports one angle.** Several estimators may **cross-check** (e.g. median vote) to produce that single value — never multi-try OCR per image rules.
- No per-image special-casing; no artificial angle candidates; no aspect-ratio filters.

## Prerequisites

```bash
# Host det binary + SO + MKL
bash scripts/build-heatmap-stage-host.sh
# Ensure libmklml_intel.so sits next to heatmap_stage_host (see run_phase1 / host out dir)

# Python with OpenCV
# ~/miniconda3/envs/paddle_env_v3/bin/python
```

## Phase 1 (broad methods + first human pass)

```bash
export LD_LIBRARY_PATH=third_party/paddle/tests/heatmap_stage_host/out:$LD_LIBRARY_PATH

~/miniconda3/envs/paddle_env_v3/bin/python scripts/deskew-angle-review/run_phase1.py \
  --sets dash,pump \
  --out /home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/scratch/deskew-angle-review-YYYYMMDD
```

## Phase 2 (slim methods + priors + yellow guides + odo crops)

Reuses phase1 heat dumps (no re-det):

```bash
~/miniconda3/envs/paddle_env_v3/bin/python scripts/deskew-angle-review/run_phase2.py \
  --phase1-out …/scratch/deskew-angle-review-20260807 \
  --selections …/deskew_angle_selections.json \
  --out …/scratch/deskew-angle-review-phase2-…
```

Phase-2 methods:

| Method | Role |
|--------|------|
| `hough_thr0.2` | Hough on heat edges |
| `cpp_style_thr1/255` | App C++-style buckets @ LSB thr |
| `cpp_style_thr0.2` | Same @ 0.20 |
| `median_rect_thr0.2` | Median of blob minAreaRect angles (no aspect filter) |
| **`vote_median_hough_cpp_med`** | **Production-style single output** = median of {hough, cpp_lsb, median_rect} |

Dropped: soft_pca, proj, vote_trim, aspect filters.

UI: most-negative → most-positive by prior/vote; yellow guide lines on deskew; prior selection yellow border (no-click keeps prior on export).

### Device / emulator odo crops (dash)

App: **Deskew odo crop export** on Heatmap Stage (or deep link).

```bash
PKG=com.davidlang.vehicleexpensesautomated
BASE=/sdcard/Android/data/$PKG/files

# 1) Manifest (top-level preferred so app can create export tree with correct setgid)
adb push <phase2>/dash_bins_for_device.json $BASE/dash_bins_for_device.json
adb shell chmod 666 $BASE/dash_bins_for_device.json

# 2) Dash photos (chmod dir so app can read shell-pushed files)
adb push research/photos/dash/. $BASE/dash_photos/
adb shell "chmod 777 $BASE/dash_photos; chmod 666 $BASE/dash_photos/*"

# 3) Run (needs vehicles with landmarks + odo ICRS in DB)
adb shell am start -a android.intent.action.VIEW \
  -d 'vehicleexpenses://experiment/odo-export?auto=1'
# logcat: HeatmapStage DeskewOdoCrop

# 4) Pull + merge into HTML
adb pull $BASE/deskew_odo_export/crops /tmp/crops/
~/miniconda3/envs/paddle_env_v3/bin/python scripts/deskew-angle-review/merge_odo_crops.py \
  --out <phase2-out> --crops /tmp/crops \
  --selections …/deskew_angle_selections.json
```

If shell cannot list crops, the export tree was shell-owned without setgid — delete it and re-run so the **app** creates `deskew_odo_export/` (same pattern as `heatmap_stage/`).

## Review UI

```bash
cd <out-dir>
python3 -m http.server 8766
# open http://127.0.0.1:8766/index.html
```

## Deskew rotation sign

App applies Android `Matrix.postRotate(-deskewAngle)` (Android positive = **clockwise**).  
OpenCV `getRotationMatrix2D` positive = **counter-clockwise**, so previews use **`+deskewAngle`** in OpenCV to match the app.
