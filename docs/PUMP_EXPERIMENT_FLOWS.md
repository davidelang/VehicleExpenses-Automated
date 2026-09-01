# Pump Experiment N-Sets Architecture

This document describes the tree-based reporting architecture used in the Pump Experiment and the **current active flows**.

## Active flows (current)

Each column = fresh master. H/L/M/N/O/Q parked — `docs/obsolete/EXPERIMENT_PUMP_SETS.md`. Device HTML is one `pump_report_<ts>.html` per run (no `_partN`) with in-page column / orig / dump / rec-crop / look-ink / Rows filters and prev/next photo.

**13 columns:** `Set G--` (live QF) plus **12** (`3` masks × `2` bound strategies × `2` orientations). The six `*-base` columns are **unscheduled**. **Prod / G--** discovery **224+608**. v4/p4 dropped (`docs/obsolete/DROP_P4_P5_DET.md`). **Live QF** = G--: product det, **224+608**, verts **0.1/0.3/0.4/1.1**, horiz **0.5**. Scheduled `walk2`, legacy `color`, and `color2` are **parked**. Masks: **energy** = INTERIOR_ENERGY expand (jump-prod-like, `maxFrac=0.4`); **gray** = 7seg Y Otsu; **color** = 7seg `color_adaptive` (`chromaMode=4`: meanChromaInk&lt;12 **or** `uInk·uBg≥0.50` → walk gray Y; else tint with panel-hue veto — chromatic panel (cos≥0.50 vs uBg) is not ink; Y-near or ink-hue can be ink without matching stroke hue). Bound: **tight** inset 16px (1 heat cell); **edge-retract** 1px expand if edge has ink else retract inward (50% cap, no outward jump). Tight+retract vertical walk: consecutive `hasBar` then **one** gap jump ≤ `0.5×sPx` empty if a `hasBar` appears (at most one per side; empty-edge peek **is** that jump). Jump once then pad k. Ingest **keeps UV**; rec still **Y**. Pepper fill (one H then one V; gap≤4 and 2×gap≤lead; no iterate) **before** glare `dropWide` **11×** `max(v0,4)`. L/R jump **iterative 1px** (`max_jumps=4`). Gray/color official `final` / PD blue = **k=0** (extras k=1..4). Energy `ocrScales` `[1.0]`. Dump-details **inkSweep**: per-seed 1-D V/H ink scores vs offset (same function as live expand/jump). Hidden with Dump details: two SVG sparklines per box (score, thr line, seed ticks, walk/jump ticks). Gray/color `minRun` is per-seed (`min(0.5s, 0.4×max in-seed run)`); edge-retract cap is **50% seedH** (`BLOCKED_RETRACT_LIMIT`); H jump is still **0.4×walked-blue H**, max 4, miss → 1px back to ink else stay. G-- has no `inkSweep`. Rot profiles step ±v/±u.

| Flow display name | Det | Expand | Notes |
|-------------------|-----|--------|-------|
| `Set G-- (4 pass, none, calculated)` | product **224+608** | calculated verts **0.1/0.3/0.4/1.1**; thr **u8≥1**; horiz **0.5** | Experiment product-det reference **and live QF**. Heat dumps off |
| `Set ink-energy-tight` | product **224+608** | AABB INTERIOR_ENERGY; seed inset **16px**; L/R jump | `final` = energy |
| `Set ink-energy-retract` | product **224+608** | Same; T/B 1px expand-or-retract | edge-retract |
| `Set ink-gray-tight` | product **224+608** | Greyscale Otsu 7seg; inset **16px**; pad k; jump once | `finalKind=ink` k=0 |
| `Set ink-gray-retract` | product **224+608** | Same; T/B 1px expand-or-retract | edge-retract |
| `Set ink-color-tight` | product **224+608** | `color_adaptive` 7seg (`chromaMode=4`); inset **16px** | metadata `color_adaptive` |
| `Set ink-color-retract` | product **224+608** | Same; T/B 1px expand-or-retract | edge-retract |
| `Set rot-energy-tight` | product **224+608** | Oriented INTERIOR_ENERGY; u/v inset **16px**; jump ±u | no deskew |
| `Set rot-energy-retract` | product **224+608** | Same; ±v 1px expand-or-retract | edge-retract |
| `Set rot-gray-tight` | product **224+608** | Oriented Y 7seg; inset **16px**; k-pad ±v; jump ±u | `finalKind=ink` k=0 |
| `Set rot-gray-retract` | product **224+608** | Same; ±v 1px expand-or-retract | edge-retract |
| `Set rot-color-tight` | product **224+608** | Oriented `color_adaptive` 7seg; inset **16px** | metadata `color_adaptive` |
| `Set rot-color-retract` | product **224+608** | Same; ±v 1px expand-or-retract | edge-retract |

**Heat→rect cell halo:** native `packHeatmapBoxes` grows every det box (AABB and oriented) by `kPaddleDetHeatCellPx` (4) on the output heat array — one Paddle 4×4 feed cell. `heatW/feedW` cannot reveal that (product tensor is already 1:1). The old Kotlin `rectExpandPx` AABB pad is gone; G and jump/rot share the same native reds.

**rot-ink-prod path:** one `minAreaRect` detect per scale; keep 8-corners. Map boxes by content `targetW`/`targetH` (same as G/jump), **not** 32-aligned outer size. Nested/poke merge is **oriented** (`pruneOrientedQuads`). Rot-ink: 7seg walk **along red normals** in the original image (`±v`, freeze `u` span; peek/gap/cap same as AABB ink) via JNI `nativeSeg7OrientedMany`; jump-retract along `±u` via `nativeJumpOrientedMany` (iterative 1px per side, `max_jumps=4`; Y Sobel or chromaMag per `chromaMode`; one jump batch per k). Kotlin bodies are fallback if native returns null. Off-image samples are **not ink** (a fully OOB strip is not a bar; walk gap-stops instead of capping into the void). Then k-pad along `v` (Kotlin, no image). Geometry stays quads: expand, pad, jump, chroma, PD, classify input = `OrientedQuad`. **Never** `toAabb()` except the finished-blue rec warp (`warpQuadToHorizontalStrip`). PD reds/blues are quad edges (`pumpQuadEdgeAnns`). `dropWide` is **11×** `max(v0,4)` on the **u/v-resampled seed mat** and on the **oriented look-band** (seed Otsu; frozen `u`; not image AABB). Skip extra-k rec when official (k=0) asis has letters and no digits (same predicate as AABB ink). Rec warps the **finished** blue only (`padUv` along `u`/`v` from short-axis `bh` at scale `48/bh`, `INTER_CUBIC`, `BORDER_CONSTANT` black OOB; pivot BL = two smallest-x then largest y, flatten the rightward side). Cell halo remains native `kPaddleDetHeatCellPx=4` (same as G). OCR `kind=ink` **k=0..4** (`final` = k=0) on rot-gray/rot-color. JNI `nativeExpandOriented` is the scheduled rot-energy path. Does **not** call `runDiscoveryPaddle`.

**HTML rec buffers:** each column’s PD cell shows the 48×W crop actually fed to recognize (`scaleVariants[].candidates[].recB64` + `recW`/`recH`, or G `candidates[].recB64`). Display is **1:1** with that crop: `height:48px; width:auto` (or `recW` px); wrap (`flex:0 0 auto`); no `width:48%` / `width:100%` / `max-width` shrink. Empty `recB64` is omitted (AABB skip extra-k reuses official (k=0) asis/digits with no extra rec thumbs). Caption: 9px `$lab`; **asis** / **digits** at **12px**. `S=` heading and PD `<small>` unchanged.

**HTML look ink:** B.p 1:1 five-state YUV overlay; `takeSnapshot` fit-inside A.s width × 48 (uniform; not independent 4000×48). JPEG files under `look_ink/` next to the HTML (`<img src='look_ink/…'>`); JSON stores paths only, not base64. Snapshot dest is crop-sized JPEG on a standing BufferSet. Packed 256/608 + ShareExternal for G--/product det (224 letterbox uses the 256 set). Cyan T/B; yellow gap-land. **Bright** = walk ink after `dropWide`: **white** combined not-poison, **green** combined in poison. **Dim** = not ink: **dim red** poison never combined; **dim blue** drop-wide zeroed in poison; **dim grey** drop-wide zeroed not-poison; black otherwise. A wide run that straddles clean/poison is grey then blue on the same row. Caption `minRun` / `sPx` / `glareW` / `bandTop`/`bandBot` / `noPeak`. Sibling **Look ink** checkbox. Official walk / rec unchanged. JPEG smear OK.

**Rec canvas:** one **4096×48** buffer everywhere. Engine infers a `createCrop(0,0,needW,48)` slice (`needW = 32-align(48×srcW/srcH)`, clip only at 4096). Aspect kept; unused canvas width is never fed to CRNN.

### Set P expand tunables (`ContentExpandUtils`)

| Param | Default (P/P4) | Jump columns | Meaning |
|-------|----------------|--------------|---------|
| `mode` | `INTERIOR_ENERGY` | same | Sobel energy strip grow |
| `maxFrac` | `1.0` | **0.4 AABB and rot** | Cap pad = fraction of **seed height** per side. Hit cap **stops grow**; official `final` stays the energy crop (no G-list rescue). |
| `enableJump` | `false` | **`true`** on jump columns | After grow: independent L/R iterative jump by `jumpFrac`×H (`max_jumps=4`). Miss → 1px step-back to first ink column. No OR-coupled sides. No vertical jump. |
| `jumpFrac` | `0.40` | same | Horizontal jump distance / expanded height |
| `ocrScales` | `[1.0]` | **`[1.0]`** on jump/rot (`pJumpOcrScales`) | After one expand, OCR each **height-only** S (`final` = first). Width is jump/clear only. Put 1.05 / 1.1–1.8 back on the list to re-sweep; S>1 lost more than it gained on v0.98-212. |
| `energyRatio` | `0.45` | P4-jump / P4-xycut / Prod-jump `0.65` | Keep growing while strip energy ≥ this × seed interior |
| `vertEnergy` | `MAGNITUDE` | xycut-p4 `XYCUT_GX`; chi2-p4 `CHI2` | MAGNITUDE = \|∇\|; GX = \|∂I/∂x\|; XYCUT_GX = peak-isolate on gx; CHI2 = 16-bin row hist vs seed (`chi2K=3.5`, consec 2). Jump still MAGNITUDE 0.65 |
| `freezeHorzDuringVert` | `false` | **`true` on xycut / chi2-p4** | First grow is top/bottom only (seed width frozen) |
| `vertPadFrac` | `0` | **xycut `0.15`**; **chi2-p4 `0.08`**; jump `0` | After vertical stop, pad each tip by this × seedH (one scale, not a G list). Chosen on v0.98-229 167×2 to put H/GT p50 near 1.0 without growing swallow. |
| count pullback | post | all energy columns | After official energy box: first run-count valley below 0.45×seed median. AABB walks image y / Sobel-x; rot walks ±v / \|∇I·û\|. Additive only. OCR as `scaleVariants` `kind=energy_count`; official `final` unchanged. |

Metadata on expand columns: `content_expand_jump` / `content_expand_jump_frac` / `content_expand_ocr_scales` (assembly `ocrScales` / `finalOcrScale`). JSON `scaleVariants[]` has per-S rects/cands/final.

**UI subset actions** on the Pump experiment screen are a compact wrapping equal-cell grid (not `FlowRow`; no per-button captions; meaning stays here). **Horiz-affected (76)** still filters photos; columns are the full active set above (19). Deep link: `vehicleexpenses://experiment/pump?auto=horiz`. **Prod-ink fail (68)** is the **k=0** union (ink-prod or rot-ink-prod not exact) on start-113 phone `14-45-50` + tablet `14-46-20`, excluding 15 fields no scheduled column pool-exacts (`union_k0`); columns still the full scheduled set (19). Deep link: `vehicleexpenses://experiment/pump?auto=prodinkfail`. **Mixed fail (76)** is trusted GT and **not** all-column exact (exact only; relaxed = fail) on phone `pump_results_2026-08-30_08-45-44.json` (13 columns): skip no-GT (2), untrusted both fields (7), all-trusted-exact (54), nobody exact (29). Deep link: `vehicleexpenses://experiment/pump?auto=mixedfail`. Jump/retract/extend: energy columns keep Sobel **mean** vs `energyRatio`; gray/color jump `colHas` uses the same per-seed `usedMinRun` as vertical `hasBar` on the **seed** T/B (v span), not walked-box center; color tint is from the red seed, not rebuilt from the walked AABB. Extra unofficial `scaleVariants` `kind=horiz_pad` at each ink **k=0..4** (`s=k`): L/R `0.5×` **that k box’s** H (rot: `±u` × `0.5×` k `bh`); letter-skip omits extra k and its pad; official `final` still k=0; PD extra blue = k=0 pad only. Energy columns keep one pad. G-- verts / orange unchanged. **Det dump (prod × deskew/rot)** is a one-shot discover button (not every experiment run): two recipes `prod-deskew` / `prod-rot`, no expand/OCR, all `pump_photos`, sidecar `pump_reports/pump_det_boxes_<ts>/`. Deep link: `vehicleexpenses://experiment/pump?auto=detdump`. Fetch pulls the newest `pump_det_boxes_<ts>/` even without a matching `pump_results`.

**Report `#` / JSON `line_number`:** 1-based index in sorted full `pump_photos` (`allPhotos`), not the subset `index+1`. Subset runs still process only subset files.

**Parked** (source, not scheduled): prior ink-prod / color / color2 / walk2 / jump-prod / rot-ink-prod, P / P-jump / P-rot / Prod-m65, H* horiz, L/M dilate, G-dense, K. v4/p4 processors deleted (`obsolete-p4-p5-det`).

**v0.98-71 crash fix:** L/M `getStructuringElement` aborted (`normalizeAnchor`); dilate now uses `Mat::ones(3,3)` + try/catch (v0.98-72+). **No successful L–Q data before that fix.**

**Horiz (history):**
* G-- / production: `SET_G_HORIZ_FACTOR = 0.5` × expanded blue height (each side).
* Prior G-dense/K trial: `SET_G_DENSE_HORIZ_FACTOR = 1.0` (**2×**). Mixed: ~25 fields lower min_v, ~34 higher on phone.

**Heat thr (product u8):**
* G-- / H* / L–Q discovery: `HEAT_THR_U8_GE1` → on if **u8 ≥ 1**
* (parked K): `HEAT_THR_U8_GE2` → on if **u8 ≥ 2**  

**Heat dumps:** Off (`dumpHeats = false` on every column, including G--). Host copies already exist under `latest-report/pump_heats_2026-08-15_*`. Format if recaptured: magic `HMU8`, w/h LE u32, comp=1 (zlib), raw_len, payload. See `HeatmapU8Dump.kt`.

**Energy traces:** Off. Full lossless jump+rot dumps already exist from `2026-08-15_09-38-52` / `09-39-09`. P4-jump / P4-rot no longer write `expand_energy_<ts>/`. Official path and unofficial `energy_count` OCR are unchanged.

**JNI content expand (AABB):** Scheduled ink/rot columns use per-column natives as they land (`nativeEnergyAabbTight`/`Retract`, `nativeGrayAabbTight`/`Retract`, `nativeColorAabbTight`/`Retract`, `nativeEnergyOrientTight`/`Retract`, `nativeGrayOrientTight`/`Retract`, `nativeColorOrientTight`/`Retract`). G-- stays calculated verts. Parked / unconverted paths keep `nativeAabbGrowMany` / `nativeSeg7Many` / `nativeJumpMany` / `nativeSeg7OrientedMany` / `nativeJumpOrientedMany` / `nativeExpandOriented`. Gray and color AABB energy (jump / chi2 / xycut), ink 7seg, and Y jump-retract are JNI (`nativeAabbGrowMany` / `nativeSeg7Many` / `nativeJumpMany` in `ContentExpandNative.cpp`). Leftover `*Many` / oriented energy jump uses U8 look in `s`, not float Sobel. `nativeSeg7Many` `chromaMode`: 0 gray, 1 chromaMag, 2 **tintMask** (`fillChromaTintMask`), 3 same tintMask, 4 `color_adaptive` (meanChromaInk&lt;12 **or** `uInk·uBg≥0.50` → Y walk; else panel-hue veto; jump uses the same skip). Pepper fill (H then V, gap≤4 and 2×gap≤lead) **before** glare `dropWide` **11×** `max(v0,4)` on all AABB ink (including walk2). Scheduled **rot-ink** 7seg/jump run in JNI (`nativeSeg7OrientedMany` / `nativeJumpOrientedMany`); Kotlin `expand7segFromOrientedSeedMany` / `jumpRetractOrientedUMany` fallback if native returns null; no AABB except rec warp; rot pepper then `dropWide` **11×** on u/v seed only. Rec warps the finished blue. Parked rot-energy still uses `nativeExpandOriented`. G-- verts stay calculated. Rec still Y. Kotlin bodies are fallback if JNI returns null, and when energy traces are on.

**Seed poison (7seg) — five-step combine:** (1) Whole-seed ink/bg + stroke (Y Otsu+pepper). (2) Poison **areas** = 1453 fat/wide first-Otsu CCs (`min(h,v)-run > 3×max(v0,4)` or `h-run > 11×max(v0,4)`; if `v0≤4` also `h-run > 0.25×seedW`) **union** T/B brightness bands (mean/p50 Y vs interior `≥ max(16, 0.5×|dInk|)`, height `max(sPx, round(0.12×seedH))`; **all pixels** in the band, not only first-Otsu ink). 1453 is the fat-CC detector only. (3) Re-process excluding those areas (clean Otsu; keep stroke if solid else re-peak `v0_clean`). (4) Local Otsu on poison **pixels** only (`keepR`), never the blob bounding box and never look pixels outside the seed. Peak>4 → those pixels’ ink bits are valid; no-peak → do not OR a sheet. Look-strip rows outside the seed use **clean** (or tint/`srcIsBin`) only — no blob x-range extrusion off-seed. (5) **OR** peaked poison pixels with the clean sample into one U8 255 **seed** plane; one `dropWideRuns` (`glareW = 11×` clean sPx); walk `hasBar` on the look raster (runs **cross** clean/poison edges **inside** that seed plane). Walk `sPx`/`minRun`/`glareW` stay **clean**. Combine is not “one global threshold with poison forced 0.”

**Oriented expand:** P4-rot / Prod-rot grow + count walk run in JNI (`ContentExpandNative.cpp`) so Sobel samples stay in native memory. Geometry is unchanged (±v / `|∇I·û|`). Kotlin path remains as fallback if native returns null, and when energy traces are on.

**Rot uv:** picked **once on the seed** — **u** = edge closest to horizontal (tie → longer), **v** rotated so **vy≥0** (bottom = lower flatter side). Expand / jump / k-pad / `horiz_pad` only translate sides in that frame; they do not re-pick axes from walked corners.

**Edge-count (same run):** After energy stop, additive run-count valley pullback, then a **one-direction** pad: pulled tip steps back toward energy by `0.10×seedH` (not past energy); energy-stop tip whose run-count is still ≥ `0.45×cSeed` grows that tip only by `0.08×seedH`. Cap-stops do not grow. Grow is skipped when the energy box is already `> 2.4×seedH` so the 48 px rec crop does not shrink the digits. A few pixels into the neighboring row is acceptable. Official `final` is the energy crop (`energy_or_g` in JSON is the same box, not a G stitch). Extra `scaleVariants` entry `kind=energy_count` is scored unofficially only.

* AABB (`countPullbackVertical`): Sobel-x on image rows at seed width; walk image y.
* Oriented (`countPullbackOriented`): walk **±v** (normal to the long edges). Each step is a seed-width strip of **|∇I·û|** (derivative along the text axis). Do **not** AABB the quad first — that walk is a different geometry.

**Rec buffer:** `recB64` is a JPEG preview of the **48×W crop** actually fed to recognize (plus `recW`/`recH`). That JPEG is visual QA only — recognize already ran on the raw crop.

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
| **Det models (columns)** | **product_det** only. v4/v5 unscheduled: `docs/obsolete/DROP_P4_P5_DET.md`. APK `exp_det_ab` is `product_det_*.nb` only. |
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

**Live QF** copies experiment G-- (`OcrHarness.extractQuickFillGMinusMinusCostVol` + `SET_G_MINUS_MINUS_VERT_FACTORS` + `SET_G_HORIZ_FACTOR`).

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
* `connectedComponentsWithStats` on the binary mask
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
