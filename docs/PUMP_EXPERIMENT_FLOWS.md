# Pump Experiment N-Sets Architecture

This document describes the tree-based reporting architecture used in the Pump Experiment and the **current active flows**.

## Active flows (current)

Each column = fresh master. H/L/M/N/O/Q parked — `docs/obsolete/EXPERIMENT_PUMP_SETS.md`.

Nine columns. **P4 / v4** discovery **224+1024** (no 2048). **Prod / G--** discovery **224+608**. Heatmap dump tiers stay 224/608/1024/2048. Experiment **G4** column is gone. **Live QF** (`OcrHarness.extractQuickFillG4CostVol`) = experiment **G4-vjump** recipe: v4 det, **224+1024**, verts **0.0/0.1/0.2/0.5**, L/R jump-retract (no 0.5×H). Keep G--, **G4-vjump**, **ink-p4** (v4 AABB ink; was 7seg-stroke / P4-ink), **ink-prod**, **jump-p4**, **jump-prod**, **rot-ink-p4**, **rot-ink-prod**, **xycut-p4**. HTML `toSortedMap()` order is family then `-p4`/`-prod`. Expand energy `final` / ink `finalKind=ink`. Energy `maxFrac` stays **0.40**. Ink: walk **once** (empty skip **0.5`s`**, start peek **0.5`s`**, safety cap **2.5×red H**, **no k-pad in the walk**); then pad **k=1 / 2 / 3**×`s` each tip (clamp to remaining cap), jump, OCR all three (`scaleVariants` `kind=ink`, `s`=k). Official `final` / PD blue = **k=1**. Energy columns still skip vertical grow on a side when the 1px strip just outside red T/B is already empty; the red is never retracted. No 0–0.50 rot sweep. Energy `ocrScales` stay `[1.0]`.

| Flow display name | Det | Expand | Notes |
|-------------------|-----|--------|-------|
| `Set G-- (4 pass, none, calculated)` | product **224+608** | calculated verts; thr **u8≥1**; horiz **0.5** | Experiment product-det reference. Heat dumps off |
| `Set G4-vjump` | **PP-OCRv4_mobile_det** **224+1024** | Calculated height pads **0.0 / 0.1 / 0.2 / 0.5** (4-pass greedy from tablet 00-10-30 exact-pool); width = P4-jump L/R jump-retract (`jumpFrac=0.40`, `retractClearFrac=0.30`, `energyRatio=0.65`, `maxFrac=0.4`). **No** 0.5×H, **no** energy vertical walk | Live QF uses this recipe. PD = reds + jumped blues (no orange) |
| `Set ink-p4` | **PP-OCRv4_mobile_det** **224+1024** | Same v4 reds as G4-vjump. Seed-ROI Otsu stroke `s` (vSW H-path; drop CCs ≫ 3s). Fallback `0.08×seedH` also if stroke-count share < **0.30** or max non-span horiz run ≥ **0.50×seedW**. Vert: freeze width, walk **once** (k=0 in walk) to gap ≥ **0.5`s`**; start peek **0.5`s`** outside red (freeze that side only if the peek is empty); cap **2.5×red H** per side (**safety**). Then `padVertByStrokes` **k=1/2/3** (always, frozen sides too) clamped to remaining cap; Horz jump after **each** pad (`jumpFrac=0.40`, `retractClearFrac=0.30`, `energyRatio=0.65`, `maxFrac=0.4`). OCR all three. **No** 8s / 2s, **no** G-list | Experiment only. Not QF live. PD / `final` = k=1 blues. Pool `scaleVariants` k=1/2/3 `kind=ink`. Per-red `s` in assembly |
| `Set ink-prod` | product **224+608** | Same ink knobs as ink-p4 on product reds (walk once; OCR k=1/2/3; official k=1) | Experiment only. PD / `final` = k=1. Pool `scaleVariants` k=1/2/3 `kind=ink`. `s_per_red` |
| `Set jump-p4` | **PP-OCRv4_mobile_det** **224+1024** | AABB energy **maxFrac=0.4**; cap stops the walk only | Control. `final` = energy. Jump L/R only. **No extra vert pad** |
| `Set jump-prod` | **product_det** **224+608** | Same AABB energy; **maxFrac=0.4**; no G-- stitch | Product det A/B vs jump-p4 |
| `Set rot-ink-p4` | **PP-OCRv4_mobile_det** **224+1024** | Oriented det + `pruneOrientedQuads` (**no deskew**). Then AABB `expand7segFromSeed` **once** (gap/peek **0.5`s`**, cap **2.5×** safety, k=0 in walk) + `padVertByStrokes` k=1/2/3 + `jumpRetractHorizontal` + `orientedFromAabb`. OCR all three. **No** new ±v 7seg walker | `finalKind=ink`. PD / `final` = k=1. Pool `scaleVariants` k=1/2/3 `kind=ink`. `s_per_red` |
| `Set rot-ink-prod` | **product_det** **224+608** | Same oriented det as rot-ink-p4 on product; AABB ink walk once; OCR k=1/2/3; official k=1 | `finalKind=ink`. PD / `final` = k=1. Pool `scaleVariants` k=1/2/3 `kind=ink`. `s_per_red` |
| `Set xycut-p4` | **PP-OCRv4_mobile_det** **224+1024** | Frozen width; **XY-cut** on the `gx` row profile; **+0.15×seedH** each tip; L/R jump. Cap = leash | `final` = energy |

**Heat→rect cell halo:** native `packHeatmapBoxes` grows every det box (AABB and oriented) by `kPaddleDetHeatCellPx` (4) on the output heat array — one Paddle 4×4 feed cell. `heatW/feedW` cannot reveal that (product tensor is already 1:1). The old Kotlin `rectExpandPx` AABB pad is gone; G and jump/rot share the same native reds.

**rot-ink-p4 / rot-ink-prod path:** one `minAreaRect` detect per scale; keep 8-corners. Nested/poke merge is **oriented** (`pruneOrientedQuads`): the keeper keeps its tilt; a smaller box that pokes out moves only the keeper sides that need to cover it, each along its own normal. Same gates as G (contain, 40px poke, similar-overlap, top-N) but never an AABB union. Rot-ink: AABB 7seg walk **once** (k=0), pad k=1/2/3, jump, then `orientedFromAabb`; OCR `kind=ink` at k=1/2/3 (`final` = k=1). JNI `nativeExpandOriented` (one-sided T/B skip) remains for parked energy-rot helpers. Energy rot (parked) still warps OCR at each `ocrScales` entry. Does **not** call `runDiscoveryPaddle`.

**HTML rec buffers:** each column’s PD cell shows the 48×W crop actually fed to recognize (`scaleVariants[].candidates[].recB64` + `recW`/`recH`, or G `candidates[].recB64`). Caption: 9px `$lab`; **asis** / **digits** at **12px**. `S=` heading and PD `<small>` unchanged.

**Rec canvas:** one **4096×48** buffer everywhere. Engine infers a `createCrop(0,0,needW,48)` slice (`needW = 32-align(48×srcW/srcH)`, clip only at 4096). Aspect kept; unused canvas width is never fed to CRNN.

### Set P expand tunables (`ContentExpandUtils`)

| Param | Default (P/P4) | Jump columns | Meaning |
|-------|----------------|--------------|---------|
| `mode` | `INTERIOR_ENERGY` | same | Sobel energy strip grow |
| `maxFrac` | `1.0` | **0.4 AABB and rot** | Cap pad = fraction of **seed height** per side. Hit cap **stops grow**; official `final` stays the energy crop (no G-list rescue). |
| `enableJump` | `false` | **`true`** on jump columns | After grow: jump **L/R only** by `jumpFrac`×H. If still in text, grow **L/R only** (same cap). Else retract to energy edge, then L/R `retractClearFrac` (0.30×H). No vertical jump/clear/post-jump grow. |
| `jumpFrac` | `0.40` | same | Horizontal jump distance / expanded height |
| `ocrScales` | `[1.0]` | **`[1.0]`** on jump/rot (`pJumpOcrScales`) | After one expand, OCR each **height-only** S (`final` = first). Width is jump/clear only. Put 1.05 / 1.1–1.8 back on the list to re-sweep; S>1 lost more than it gained on v0.98-212. |
| `energyRatio` | `0.45` | P4-jump / P4-xycut / Prod-jump `0.65` | Keep growing while strip energy ≥ this × seed interior |
| `vertEnergy` | `MAGNITUDE` | P4-xycut uses `XYCUT_GX` | MAGNITUDE = \|∇\|; GX = \|∂I/∂x\|; XYCUT_GX = peak-isolate on gx profile |
| `freezeHorzDuringVert` | `false` | **`true` on xycut** | First grow is top/bottom only (seed width frozen) |
| `vertPadFrac` | `0` | **xycut `0.15`**; jump `0` | After vertical stop, pad each tip by this × seedH (one scale, not a G list). Chosen on v0.98-229 167×2 to put H/GT p50 near 1.0 without growing swallow. |
| count pullback | post | all energy columns | After official energy box: first run-count valley below 0.45×seed median. AABB walks image y / Sobel-x; rot walks ±v / \|∇I·û\|. Additive only. OCR as `scaleVariants` `kind=energy_count`; official `final` unchanged. |

Metadata on expand columns: `content_expand_jump` / `content_expand_jump_frac` / `content_expand_ocr_scales` (assembly `ocrScales` / `finalOcrScale`). JSON `scaleVariants[]` has per-S rects/cands/final.

**UI subset button:** **Horiz-affected (76)** still filters photos; columns are the full active set above. Deep link: `vehicleexpenses://experiment/pump?auto=horiz`. **Prod-ink fail (76)** filters to photos where ink-prod or rot-ink-prod is not exact (relax=fail) on 23-18 phone+tablet; columns still the full nine. Deep link: `vehicleexpenses://experiment/pump?auto=prodinkfail`.

**Parked** (source, not scheduled): P / P-jump / P4 / P-rot / P4-rot, H* horiz, L/M dilate, N/O/Q content modes, G-dense, K, Set I.

**v0.98-71 crash fix:** L/M `getStructuringElement` aborted (`normalizeAnchor`); dilate now uses `Mat::ones(3,3)` + try/catch (v0.98-72+). **No successful L–Q data before that fix.**

**Horiz (history):**
* G-- / production: `SET_G_HORIZ_FACTOR = 0.5` × expanded blue height (each side).
* Prior G-dense/K trial: `SET_G_DENSE_HORIZ_FACTOR = 1.0` (**2×**). Mixed: ~25 fields lower min_v, ~34 higher on phone.

**Heat thr (product u8):**
* G-- / H* / L–Q discovery: `HEAT_THR_U8_GE1` → on if **u8 ≥ 1**
* (parked K): `HEAT_THR_U8_GE2` → on if **u8 ≥ 2**  

**Heat dumps:** Off (`dumpHeats = false` on every column, including G--). Host copies already exist under `latest-report/pump_heats_2026-08-15_*`. Format if recaptured: magic `HMU8`, w/h LE u32, comp=1 (zlib), raw_len, payload. See `HeatmapU8Dump.kt`.

**Energy traces:** Off. Full lossless jump+rot dumps already exist from `2026-08-15_09-38-52` / `09-39-09`. P4-jump / P4-rot no longer write `expand_energy_<ts>/`. Official path and unofficial `energy_count` OCR are unchanged.

**Oriented expand:** P4-rot / Prod-rot grow + count walk run in JNI (`ContentExpandNative.cpp`) so Sobel samples stay in native memory. Geometry is unchanged (±v / `|∇I·û|`). Kotlin path remains as fallback if native returns null, and when energy traces are on.

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
| **Det models (columns)** | **product + v4 mobile** only (`product_det`, `PP-OCRv4_mobile_det`). **v5 mobile dropped** (too many 0-box cells). **Server dets never scheduled** (~1/10 speed). See `docs/obsolete/EXPERIMENT_DET_MODELS.md`. Assets under `paddle/exp_det_ab/`. |
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

**Not modified:** Quick Fill / `OcrHarness` / `SET_G_MINUS_MINUS_VERT_FACTORS` production constants.

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
