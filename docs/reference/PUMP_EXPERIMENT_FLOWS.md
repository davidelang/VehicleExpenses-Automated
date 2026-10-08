# Pump Experiment N-Sets Architecture

Current heatmap-expand attempts used by the pump experiment. Quick Fill’s generic camera stages (deskew, find ink, expand, OCR, classify) are in [QUICK_FILL_CAMERA.md](QUICK_FILL_CAMERA.md). This file is the detail those stages point at for **how expansion is tried today**. It is not the product overview.

## Active flows (current)

Each column = fresh master. H/L/M/N/O/Q parked — `docs/obsolete/EXPERIMENT_PUMP_SETS.md`.

Only `Set rot-G--` is scheduled. Product det at 224 and 608, vertical factor 0.34, pad p54 (0.54 / 0.55). Quick Fill is a separate copy of that rot-G-- at 0.34 / p54, not G-- and not G4.

| Flow display name | Det | Expand | Notes |
|-------------------|-----|--------|-------|
| `Set rot-G--` | product det **224 and 608** | vertical factor **0.34**; pad **p54** (0.54 / 0.55) | The only scheduled column. Quick Fill is a separate copy of this rot-G-- at 0.34 / p54, not G-- and not G4. |

### Retired history: ten-column table

The old ten-column table is retired history. It is not scheduled. Quick Fill is not G-- and not G4.

| Flow display name | Det | Expand | Notes |
|-------------------|-----|--------|-------|
| `Set G-- (4 pass, none, calculated)` | product | calculated verts; thr **u8≥1**; horiz **0.5** | Experiment product-det reference + heat dumps |
| `Set G4 (v4 det, calculated 0.0-2.5)` | **PP-OCRv4_mobile_det** | G-style calculated verts **0.0 / 0.1 / 0.3**; horiz **0.5**; deskew | v4 det A/B vs G--; retired history, not live Quick Fill |
| `Set P4-jump (v4 + energy + jump, S OCR)` | **PP-OCRv4_mobile_det** | AABB energy **maxFrac=0.4**; if a red **hits the cap**, OCR G4 verts (0/0.1/0.3) on that red instead | Control. `final` = energy-or-G. Jump L/R only. **No extra vert pad** |
| `Set P4-m65 (v4 + mean0.65 frozen + jump)` | **PP-OCRv4_mobile_det** | Frozen seed width; stop when strip **\|∇\| mean** &lt; **0.65** × seed; then **+0.08×seedH** each tip; L/R jump. If a red **hits the cap**, OCR G verts **0.00 / 0.05 / 0.15** | `final` = energy-or-G |
| `Set P4-gx (v4 + gx0.55 frozen + jump)` | **PP-OCRv4_mobile_det** | Frozen width; energy = **\|∂I/∂x\|**; stop at **0.55** × seed `gx`; **+0.08×seedH** each tip; L/R jump. Cap → G **0.00 / 0.05 / 0.15** | `final` = energy-or-G |
| `Set P4-xycut (v4 + xycut-gx frozen + jump)` | **PP-OCRv4_mobile_det** | Frozen width; **XY-cut** on the `gx` row profile; **+0.15×seedH** each tip; L/R jump. Cap → G **0.00 / 0.05 / 0.15** | `final` = energy-or-G |
| `Set P4-rot-jump (v4 oriented + jump, S OCR)` | **PP-OCRv4_mobile_det** | Oriented **m65** energy (ratio **0.65**, width frozen on the height walk, **+0.08×seedH** each tip) + L/R jump. If a red **hits the cap**, OCR G verts **0.00 / 0.05 / 0.15** on that red. **No per-seed vert sweep** | `final` = energy-or-G. `scaleVariants` has `energy` and `energy_or_g` |
| `Set Prod-jump (product + energy + jump, S OCR)` | **product_det** | Same AABB hybrid; fallback **G--** verts (0.1/0.3/0.4/1.1); **maxFrac=0.4** | Product det A/B vs P4-jump |
| `Set Prod-m65 (product + mean0.65 frozen + jump)` | **product_det** | Same frozen-width mean \|∇\| **0.65** + **0.08** pad + L/R jump as P4-m65, but **maxFrac=2.5** and product det. Cap → G **0.00 / 0.05 / 0.15** | Unleashed energy so short product reds can grow. Compare expand/OCR to P4-m65 (v4; now leashed 0.4) and to last-run P4-m65 at 2.5 |
| `Set Prod-rot (product oriented + jump, S OCR)` | **product_det** | Same oriented m65 energy + G-on-cap **0.00 / 0.05 / 0.15**; no sweep | Product det A/B vs P4-rot |

**Heat→rect cell halo:** native `packHeatmapBoxes` grows every det box (AABB and oriented) by `kPaddleDetHeatCellPx` (4) on the output heat array — one Paddle 4×4 feed cell. `heatW/feedW` cannot reveal that (product tensor is already 1:1). The old Kotlin `rectExpandPx` AABB pad is gone; G and jump/rot share the same native reds.

**P-rot / P4-rot-jump / Prod-rot path:** one `minAreaRect` detect per scale; keep 8-corners. Nested/poke merge is **oriented** (`pruneOrientedQuads`): the keeper keeps its tilt; a smaller box that pokes out moves only the keeper sides that need to cover it, each along its own normal. Same gates as G (contain, 40px poke, similar-overlap, top-N) but never an AABB union. `expandOriented` (+ L/R jump); warp OCR at each `ocrScales` entry. Does **not** call `runDiscoveryPaddle`.

**HTML rec buffers:** each column’s PD cell shows the 48×W crop actually fed to recognize (`scaleVariants[].candidates[].recB64` + `recW`/`recH`, or G `candidates[].recB64`).

### Set P expand tunables (`ContentExpandUtils`)

| Param | Default (P/P4) | Jump columns | Meaning |
|-------|----------------|--------------|---------|
| `mode` | `INTERIOR_ENERGY` | same | Sobel energy strip grow |
| `maxFrac` | `1.0` | **0.4 AABB and rot** | Cap pad = fraction of **seed height** per side. Hit cap → G verts (jump: G4 0/0.1/0.3; m65/gx/xycut/rot: 0.00/0.05/0.15; Prod-jump: G--). |
| `enableJump` | `false` | **`true`** on jump columns | After grow: jump **L/R only** by `jumpFrac`×H. If still in text, grow **L/R only** (same cap). Else retract to energy edge, then L/R `retractClearFrac` (0.30×H). No vertical jump/clear/post-jump grow. |
| `jumpFrac` | `0.40` | same | Horizontal jump distance / expanded height |
| `ocrScales` | `[1.0]` | **`[1.0]`** on jump/rot (`pJumpOcrScales`) | After one expand, OCR each **height-only** S (`final` = first). Width is jump/clear only. Put 1.05 / 1.1–1.8 back on the list to re-sweep; S>1 lost more than it gained on v0.98-212. |
| `energyRatio` | `0.45` | P4-jump `0.45`; **P4-m65 and P4-rot / Prod-rot `0.65`**; **P4-gx `0.55`** | Keep growing while strip energy ≥ this × seed interior |
| `vertEnergy` | `MAGNITUDE` | P4-gx / P4-xycut use `GX` / `XYCUT_GX` | MAGNITUDE = \|∇\|; GX = \|∂I/∂x\|; XYCUT_GX = peak-isolate on gx profile |
| `freezeHorzDuringVert` | `false` | **`true` on m65 / gx / xycut / P4-rot / Prod-rot** | First grow is top/bottom only (seed width frozen) |
| `vertPadFrac` | `0` | **m65/gx/P4-rot/Prod-rot `0.08`**; **xycut `0.15`**; jump `0` | After vertical stop, pad each tip by this × seedH (one scale, not a G list). Chosen on v0.98-229 167×2 to put H/GT p50 near 1.0 without growing swallow. |
| count pullback | post | all energy columns | After official energy box: first run-count valley below 0.45×seed median. AABB walks image y / Sobel-x; rot walks ±v / \|∇I·û\|. Additive only. OCR as `scaleVariants` `kind=energy_count`; official `final` unchanged. |

Metadata on expand columns: `content_expand_jump` / `content_expand_jump_frac` / `content_expand_ocr_scales` (assembly `ocrScales` / `finalOcrScale`). JSON `scaleVariants[]` has per-S rects/cands/final.

**UI subset button:** **Horiz-affected (76)** still filters photos; the scheduled column is only `Set rot-G--`. Deep link: `vehicleexpenses://experiment/pump?auto=horiz`.

**Report `#` / JSON `line_number`:** 1-based index in sorted full `pump_photos` (`allPhotos`), not the subset `index+1`. Subset runs still process only subset files.

**Parked** (source, not scheduled): P / P-jump / P4 / P-rot / P4-rot, H* horiz, L/M dilate, N/O/Q content modes, G-dense, K, Set I.

**v0.98-71 crash fix:** L/M `getStructuringElement` aborted (`normalizeAnchor`); dilate now uses `Mat::ones(3,3)` + try/catch (v0.98-72+). **No successful L–Q data before that fix.**

**Horiz (history):**
* G-- / production: `SET_G_HORIZ_FACTOR = 0.5` × expanded blue height (each side).
* Prior G-dense/K trial: `SET_G_DENSE_HORIZ_FACTOR = 1.0` (**2×**). Mixed: ~25 fields lower min_v, ~34 higher on phone.

**Heat thr (product u8):**
* G-- / H* / L–Q discovery: `HEAT_THR_U8_GE1` → on if **u8 ≥ 1**
* (parked K): `HEAT_THR_U8_GE2` → on if **u8 ≥ 2**  

**Heat dumps:** `dumpHeats` stays false on every column, including G--. Pump runs do not write `pump_reports/heatmap/`. The captured set is `latest-report/heatmap/tablet/`. The in-model upsample is two stride-2 transposed convolutions inside the det `.nb`. File format, when a dump exists: `reportDir/pump_heats_<timestamp>/<photo>/scale{224,608,1024}_heatmap.u8z` + `.meta.json`. Magic `HMU8`, w/h LE u32, comp=1 (zlib), raw_len, payload. See `HeatmapU8Dump.kt`.

**7seg Y Otsu fill (red/seed only, not energy):** `(look-bin + 8-connected recovered poison ink) / seed area`; recovered is **in** look-bin (`hasBar`); walk/`sPx` from the **final** seed; band **0.05–0.45**. Ceiling (`fill>0.45`) less-ink extras (`retryWhy=2`, `nRetry` = extras). Floor extras (`retryWhy=1`, cap 3) if `fill<0.05` **or** `nLookBin==0`. In-band SW-discard remaining valleys (`retryWhy=3`, first in-band **and** accepted sPx). look_ink `attempts[]` has per-evaluate thr/fill/keep/poison (energy has none).

**Seed poison (7seg) — five-step combine:** (1) Whole-seed ink/bg + stroke (Y Otsu+pepper). (2) Poison **areas** = 1453 fat/wide first-Otsu CCs (`min(h,v)-run > 3×max(v0,4)` or `h-run > 11×max(v0,4)`; if `v0≤4` also `h-run > 0.25×seedW`) **union** T/B brightness bands (mean/p50 Y vs interior `≥ max(16, 0.5×|dInk|)`, height `max(sPx, round(0.12×seedH))`; **all pixels** in the band, not only first-Otsu ink). 1453 is the fat-CC detector only. (3) Re-process excluding those areas (clean Otsu; keep stroke if solid else re-peak `v0_clean`; **glare-first second Otsu** on the dark remainder of `keepClean` when first Otsu inverted onto a large poison sheet). (4) Local Otsu on poison **pixels** only (`keepR`), never the blob bounding box and never look pixels outside the seed. Peak>4 → those pixels’ ink bits are valid; no-peak → do not OR a sheet. Look-strip rows outside the seed use **clean** (or tint/`srcIsBin`) only — no blob x-range extrusion off-seed. (5) **OR** peaked poison pixels with the clean sample into one U8 255 **seed** plane; empty combined may OR a dark-digit clean sample (`dInk≤-10`, 5–40% ink) without changing `v0Clean`/look-outside. Look-strip 255/0 then 255-flood (`lookAlloc` poison/254) then recover (walk-edge bands, `kind[]` ink). Walk `hasBar` is 254 or `kind==kKindInk`. Walk `sPx`/`minRun`/`glareW` stay **clean**. Combine is not “one global threshold with poison forced 0.”

**Energy traces:** Off. Full lossless jump+rot dumps already exist from `2026-08-15_09-38-52` / `09-39-09`. P4-jump / P4-rot no longer write `expand_energy_<ts>/`. Official path and unofficial `energy_count` OCR are unchanged.

**Oriented expand:** P4-rot / Prod-rot grow + count walk run in JNI (`ContentExpandNative.cpp`) so Sobel samples stay in native memory. Geometry is unchanged (±v / `|∇I·û|`). Kotlin path remains as fallback if native returns null, and when energy traces are on.

**Rot uv:** picked **once on the seed** — **u** = edge closest to horizontal (tie → longer), **v** rotated so **vy≥0** (bottom = lower flatter side). Expand / jump / k-pad / `horiz_pad` only translate sides in that frame; they do not re-pick axes from walked corners.

**Edge-count (same run):** After energy stop, additive run-count valley pullback, then a **one-direction** pad: pulled tip steps back toward energy by `0.10×seedH` (not past energy); energy-stop tip whose run-count is still ≥ `0.45×cSeed` grows that tip only by `0.08×seedH`. Cap-stops do not grow. Grow is skipped when the energy box is already `> 2.4×seedH` so the 48 px rec crop does not shrink the digits. A few pixels into the neighboring row is acceptable. Official `final` stays `energy` / `energy_or_g`. Extra `scaleVariants` entry `kind=energy_count` is scored unofficially only.

* AABB (`countPullbackVertical`): Sobel-x on image rows at seed width; walk image y.
* Oriented (`countPullbackOriented`): walk **±v** (normal to the long edges). Each step is a seed-width strip of **|∇I·û|** (derivative along the text axis). Do **not** AABB the quad first — that walk is a different geometry.

**Rec buffer:** `recB64` is a JPEG preview of the **48×W crop** actually fed to recognize (plus `recW`/`recH`). That JPEG is visual QA only — recognize already ran on the raw crop.

**Rec canvas:** one **4096×48** buffer everywhere. Engine infers a `createCrop(0,0,needW,48)` slice (`needW = 32-align(48×srcW/srcH)`, clip only at 4096). Aspect kept; unused canvas width is never fed to CRNN.

**HTML look ink:** spliced into the report as `data:image/jpeg;base64` (fetch is HTML+JSON only; no `look_ink/` folder).

**Background:** Pump **Run Test** / First 10 / Selected / Horiz-affected use `ExperimentJobRunner` + `ExperimentForegroundService` (dataSync FGS + partial wake lock), same as multi-scale / heatmap. Leaving the screen or locking the phone does **not** cancel the job. Do not launch `runPumpExperiment` from `rememberCoroutineScope` (that died on Pixel 6 Pro with `ForgottenCoroutineScopeException` after screen lock).

**Product path A/B (automatic):** `runPumpExperiment` forces  
- **emulator** → `prod_u8fp32_u8` (true fp32 mid-graph)  
- **phone** → `prod_u8fp16` (true fp16)  
ABI-split debug APKs ship only that pack + that ABI’s natives (`app-arm64-debug.apk` / `app-x86_64-debug.apk` / `app-armv7-debug.apk`).  
Recorded in JSON `product_path` / `product_dir` and branch metadata. Run both devices in parallel; compare OCR, heats, and wall times (`t_total_flow_ms`, `t_pd_inference_*`).

## Multi-scale det + expand P (device matrix)

**Screen:** drawer → **Multi-scale det + expand P** (`ExperimentMultiScaleDetScreen` / `MultiScaleDetRunner`).  
**Deep link:** `vehicleexpenses://experiment/multiscale_det?auto=1`

| | |
|--|--|
| **Background** | Jobs use **`ExperimentJobRunner`** (app-scoped) + **`ExperimentForegroundService`** (dataSync FGS + partial wake lock) so leaving the UI does not cancel multi-hour runs. |
| **Det models (columns)** | `product_det` only. v4/v5 mobile and server dets are unscheduled and not in the APK (`third_party/paddle/exp_det_ab_unscheduled/`). See `docs/obsolete/EXPERIMENT_DET_MODELS.md`. |
| **Threads** | 4 |
| **Scales (rows)** | long-edge matrix with strategy rows (single / square / hspan / vspan); maxLite caps product 2048 / v4 1504 |
| **Tiling** | when outer > model maxLite: square / hspan / vspan with ≥30% overlap max-merge heat |
| **Expand** | **P** = `ContentExpandUtils.Mode.INTERIOR_ENERGY`, `maxFrac=1.0`, jump **off** (option exists) |
| **Overlay** | semi-transparent **red** heat fill + **red** seed rect + **blue** expand-P rect (BGR→RGB on export) |
| **Photos** | Existing **pump_photos** + **dash_photos**; expense seeded from APK `experiment/receipt/PXL_20260809_094107925.jpg` |
| **Reports** | `files/multi_scale_det_reports/` **flat** — `multi_scale_det_report_<ts>.html`, `results_<ts>.sparse`, `status/manifest/cursor_<ts>.*` filled by collapser (1 min ticks); tray only: `cells/` (pre-merge fragments, purged). No `run_*` subdirs. Fetch via `fetch_latest_reports.py`. |
| **Pipeline** | Stage 1 skeleton → stage 2 **scale sets** (`4096` alone → `2048` alone → ≤1024); one predictor per model; global `bufferSetA/B` 4096² capacity; `MEM`/`DETECT_*` during detect; collapser row-major. |

HTML layout: photo id column + scale column + **one cell per det model** (host-style matrix).

**Retired multi-set flows and dead helpers** (recover with tags + full catalog):

- Doc: `docs/obsolete/EXPERIMENT_PUMP_SETS.md`
- Tag: `obsolete-experiment-pump-multi-sets`

Removed clusters include: sets A–H / D / E / G / G- columns, binPeak stack, ML discovery. **procI** remains in source but is not scheduled.

**Quick Fill** is a separate copy of `Set rot-G--` at 0.34 / p54, not G-- and not G4. This multi-scale section does not change that copy.

## 1. The Tree Architecture (`PumpBranch`)

The experiment uses a recursive data structure called `PumpBranch` to store results.

```kotlin
data class PumpBranch(
    val name: String,
    val images: MutableMap<String, String> = mutableMapOf(),
    val pathResults: MutableMap<String, PathResult> = mutableMapOf(),
    val metadata: MutableMap<String, String> = mutableMapOf(),
    val subBranches: MutableMap<String, PumpBranch> = mutableMapOf()
)
```

Each flow is a sub-branch of the root tree. Reporting (`pBuildHtmlRowDynamic` and `pSerializePhotoResultToJson`) walks this tree for columns and JSON.

## 2. How to configure flows

In `ExperimentPumpScreen.kt`, inside `runPumpExperiment`:

```kotlin
val flows = listOf(
    "Set G-- (4 pass, none, calculated)",
    "Set G-dense (none, calculated)",
    "Set K (AABB reds, G-dense verts, calculated)",
)
// flowProcessors pairs display name → processor
```

### Populate the branch

* `branch.images["PD"]` / `branch.images["PD_red_only"]` / `branch.pathResults["Paddle"]`
* `branch.metadata["costVolDecisionData_Paddle"]` with `assembly.vertFactors`
* `branch.metadata["heatmap_box_mode"]` = `minAreaRect` or `aabb`
* `branch.metadata["heat_dump_dir"]` on G-- when dumps written

## 3. Box geometry modes

| Mode | Constant | Behavior |
|------|----------|----------|
| Production / G-- / G-dense / K | `HEATMAP_BOX_MIN_AREA_RECT` (0) | minAreaRect on supra-threshold heat CC (thr differs: see above) |
| (legacy AABB experiment) | `HEATMAP_BOX_AABB` (1) | CC stats box — retired from active K for thr A/B |

Neither includes below-threshold ink; blue/orange expansion still searches crop size for OCR @48px height.

### Heatmap post precision (armv8 product)

Product det heat is **kUInt8**. `nativeProcessHeatmap` takes the **u8 path** for both box modes:

* thr on the u8 plane (`u > thr*255`; campaign thr=0 → u≥1)
* heat CC is u8 8-flood on det `.s` (ids 1..254; no int32 labels / mask pack on A.p)
* AABB stats box **or** minAreaRect of on-label pixels
* conf = mean(u8 ROI)/255; hist bins match prior float hist of (u/255)

No full fp32 heat buffer is allocated on that path. JSON branch metadata:

* `heatmap_post_path_<scale>` = `u8` | `float`
* `t_pd_native_post_<scale>` = wall ms for that post (use for speed compare)
* `heatmap_box_mode_<scale>` / `heatmap_box_mode` = `0`/`1` or `minAreaRect`/`aabb`

fp16/fp32 heat tensors still use the convert-once float path (`heatmap_post_path=float`).

## 4. Best Practices

* **Colors:** RED = reds; BLUE = calculated blues; ORANGE = side-extended oranges.
* **Production isolation:** do not change Quick Fill verts from experiment dense lists without an explicit production decision.
* **GT `?`:** machine never decoded; pool scoring uses findable fields only; if a run matches a `?` value, strip `?` from GT.
* **JSON still embeds** JPEG base64 for `before`, `hist1`, `PD`, `PD_red_only` (cost/vol crop b64 are HTML-only).

## Related

* Alignment experiment: `docs/obsolete/EXPERIMENT_ALIGNMENT_SETS.md`
* Deep analysis: `dev-ai-interaction/latest-report/pump_deep_analysis.py`
