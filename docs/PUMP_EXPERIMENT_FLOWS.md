# Pump Experiment N-Sets Architecture

This document describes the tree-based reporting architecture used in the Pump Experiment and the **current active flows**.

## Active flows (current)

Each column = fresh master. H/L/M/N/O/Q parked — `docs/obsolete/EXPERIMENT_PUMP_SETS.md`.

Ten columns (product / G-- plus ink-prod-color2, ink-prod-color3, and ink-prod-walk2). **Prod / G--** discovery **224+608**. v4/p4 columns dropped (`docs/obsolete/DROP_P4_P5_DET.md`, tag `obsolete-p4-p5-det`). **Live QF** = experiment **G--**: product det, **224+608**, verts **0.1/0.3/0.4/1.1**, horiz **0.5**. Keep G--, **ink-prod**, **jump-prod**, **rot-ink-prod**, their `-color` siblings, **ink-prod-color2**, **ink-prod-color3**, and **ink-prod-walk2**. HTML `toSortedMap()` is gray then color sibling. Ingest **keeps UV**; Paddle det/rec still **Y** (`populateMonoUInt8` on `mat`). Color expand: fused `hypot(|∇Y|,|∇C|)` for jump vertical walk; ink 7seg on chromaMag (seed median chromaMag &lt; 8 → Y fallback). **ink-prod-color2** samples stroke chromaticity $\vec{u}_{\text{ink}}$ and background at $\pm s_{\text{px}}$, then walks native `seg7One` on a 255/0 **tintMask** (`chromaMode=2`; glare/metal blacked out). **ink-prod-color3** is the same tintMask (`chromaMode=3`); glare `dropWide` is **11×** `max(v0,4)` on AABB and rot (color2 included). L/R jump-retract stays on **Y magnitude**. Expand energy `final` / ink `finalKind=ink`. Energy `maxFrac` stays **0.40**. Ink: walk **once** (empty skip **0.5`s`**, start peek **0.5`s`**, safety cap **2.5×red H**, **no k-pad in the walk**); then pad **k=1** (official) and **k=0,2,3,4** (unofficial pool) each tip (k clamp to remaining cap), jump, OCR **k=0..4** (`scaleVariants` `kind=ink`, `s`=k). Official `final` / PD blue = **k=1**. **ink-prod-walk2** is the same product-det 7seg + k OCR as ink-prod except peek/gap **2.0`s`** (vs ink-prod **0.5`s`**) and freeze empty peek only if `seedH ≥ 4s` (bar fragments still walk). Do not OCR k=1 twice. Energy columns still skip vertical grow on a side when the 1px strip just outside red T/B is already empty; the red is never retracted. Energy `ocrScales` stay `[1.0]`.

| Flow display name | Det | Expand | Notes |
|-------------------|-----|--------|-------|
| `Set G-- (4 pass, none, calculated)` | product **224+608** | calculated verts **0.1/0.3/0.4/1.1**; thr **u8≥1**; horiz **0.5** | Experiment product-det reference **and live QF**. Heat dumps off |
| `Set ink-prod` | product **224+608** | Seed-ROI Otsu stroke `s`. Walk **once** (k=0 in walk) to gap ≥ **0.5`s`**; peek **0.5`s`**; cap **2.5×red H**. Then `padVertByStrokes` **k=1** official / **k=0,2,3,4** unofficial; Horz jump after each pad. OCR **k=0..4**; skip extra-k rec when k=1 **asis** has `[A-Za-z]` and no `[0-9]` (reuse k=1 asis/digits; `recB64` empty) | Experiment only. PD / `final` = k=1. Pool `scaleVariants` `kind=ink` `s`=0..4. `s_per_red` |
| `Set ink-prod-color` | product **224+608** | Same knobs; walk on **chromaMag**; median chromaMag &lt; **8** → Y. Jump on Y | Color sibling. Rec still Y |
| `Set ink-prod-color2` | product **224+608** | Seed `s` on Y; $\vec{u}_{\text{ink}}$ from stroke pixels; bg at $\pm 1s$; **tintMask** 255=ink / 0=blackout (`u_p·u_ink` ≥ 0.5 + Y polarity; chroma &lt; 8 → Y contrast). Native `seg7One` on the mask. Jump on Y | Experiment. Metadata `content_expand_chroma=color2`. Rec still Y |
| `Set ink-prod-color3` | product **224+608** | Same tintMask as color2; glare `dropWide` **11×** `max(v0,4)` (same as color2 / ink-prod). Native `seg7One` on the mask. Jump on Y | Experiment. Metadata `content_expand_chroma=color3`. Rec still Y |
| `Set ink-prod-walk2` | product **224+608** | Same as ink-prod except peek/gap **2.0`s`** (vs **0.5`s`**); freeze empty peek only if `seedH ≥ 4s` (bar fragments still walk). Then pad **k=1** official / **k=0,2,3,4** unofficial; OCR **k=0..4** | Experiment A/B. Metadata `seg7_gap_frac=2.0` `seg7_freeze_min_hs=4`. Rec still Y |
| `Set jump-prod` | **product_det** **224+608** | AABB energy **maxFrac=0.4**; L/R jump MAGNITUDE 0.65 | `final` = energy |
| `Set jump-prod-color` | **product_det** **224+608** | Vertical walk on fused `hypot(\|∇Y\|,\|∇C\|)`; L/R jump on **Y mag** | Color sibling. Rec still Y |
| `Set rot-ink-prod` | **product_det** **224+608** | Oriented det + `pruneOrientedQuads` (**no deskew**). 7seg walk along red **normals** in source (`±v`), k-pad along `v`, jump along `±u`. Rec warps **finished** blue (`INTER_CUBIC`). OCR **k=0..4** | `finalKind=ink`. PD / `final` = k=1. Blues are parallelograms, not AABB |
| `Set rot-ink-prod-color` | **product_det** **224+608** | Same oriented det; chromaMag 7seg on `±v` strips (seed-interior median chromaMag &lt; **8** → Y) + Y jump along `±u`. Rec warps finished blue | Color sibling |

**Heat→rect cell halo:** native `packHeatmapBoxes` grows every det box (AABB and oriented) by `kPaddleDetHeatCellPx` (4) on the output heat array — one Paddle 4×4 feed cell. `heatW/feedW` cannot reveal that (product tensor is already 1:1). The old Kotlin `rectExpandPx` AABB pad is gone; G and jump/rot share the same native reds.

**rot-ink-prod path:** one `minAreaRect` detect per scale; keep 8-corners. Nested/poke merge is **oriented** (`pruneOrientedQuads`). Rot-ink: 7seg walk **along red normals** in the original image (`±v`, freeze `u` span; peek/gap/cap same as AABB ink) via JNI `nativeSeg7OrientedMany`; jump-retract along `±u` via `nativeJumpOrientedMany` (one Sobel mag per photo, one jump batch per k). Kotlin bodies are fallback if native returns null. Off-image samples are **not ink** (a fully OOB strip is not a bar; walk gap-stops instead of capping into the void). Then k-pad along `v` (Kotlin, no image). Geometry stays quads: expand, pad, jump, chroma, PD, classify input = `OrientedQuad`. **Never** `toAabb()` except the finished-blue rec warp (`warpQuadToHorizontalStrip`). PD reds/blues are quad edges (`pumpQuadEdgeAnns`). `dropWide` is **11×** `max(v0,4)` on the **u/v-resampled seed mat** only (stroke `s`; same 11× as AABB ink; not image AABB). Rec warps the **finished** blue only (`inflate` pad from short-axis `bh` at scale `48/bh`, `INTER_CUBIC`). OCR `kind=ink` **k=0..4** (`final` = k=1). JNI `nativeExpandOriented` remains for parked energy-rot helpers. Does **not** call `runDiscoveryPaddle`.

**HTML rec buffers:** each column’s PD cell shows the 48×W crop actually fed to recognize (`scaleVariants[].candidates[].recB64` + `recW`/`recH`, or G `candidates[].recB64`). Empty `recB64` is omitted (AABB skip extra-k reuses k=1 asis/digits with no extra rec thumbs). Caption: 9px `$lab`; **asis** / **digits** at **12px**. `S=` heading and PD `<small>` unchanged.

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
| `vertEnergy` | `MAGNITUDE` | xycut-p4 `XYCUT_GX`; chi2-p4 `CHI2` | MAGNITUDE = \|∇\|; GX = \|∂I/∂x\|; XYCUT_GX = peak-isolate on gx; CHI2 = 16-bin row hist vs seed (`chi2K=3.5`, consec 2). Jump still MAGNITUDE 0.65 |
| `freezeHorzDuringVert` | `false` | **`true` on xycut / chi2-p4** | First grow is top/bottom only (seed width frozen) |
| `vertPadFrac` | `0` | **xycut `0.15`**; **chi2-p4 `0.08`**; jump `0` | After vertical stop, pad each tip by this × seedH (one scale, not a G list). Chosen on v0.98-229 167×2 to put H/GT p50 near 1.0 without growing swallow. |
| count pullback | post | all energy columns | After official energy box: first run-count valley below 0.45×seed median. AABB walks image y / Sobel-x; rot walks ±v / \|∇I·û\|. Additive only. OCR as `scaleVariants` `kind=energy_count`; official `final` unchanged. |

Metadata on expand columns: `content_expand_jump` / `content_expand_jump_frac` / `content_expand_ocr_scales` (assembly `ocrScales` / `finalOcrScale`). JSON `scaleVariants[]` has per-S rects/cands/final.

**UI subset actions** on the Pump experiment screen are a compact wrapping equal-cell grid (not `FlowRow`; no per-button captions; meaning stays here). **Horiz-affected (76)** still filters photos; columns are the full active set above (10). Deep link: `vehicleexpenses://experiment/pump?auto=horiz`. **Prod-ink fail (68)** is the **k=0** union (ink-prod or rot-ink-prod not exact) on start-113 phone `14-45-50` + tablet `14-46-20`, excluding 15 fields no scheduled column pool-exacts (`union_k0`); columns still the full scheduled set (10). Deep link: `vehicleexpenses://experiment/pump?auto=prodinkfail`. **Det dump (prod × deskew/rot)** is a one-shot discover button (not every experiment run): two recipes `prod-deskew` / `prod-rot`, no expand/OCR, all `pump_photos`, sidecar `pump_reports/pump_det_boxes_<ts>/`. Deep link: `vehicleexpenses://experiment/pump?auto=detdump`. Fetch pulls the newest `pump_det_boxes_<ts>/` even without a matching `pump_results`.

**Report `#` / JSON `line_number`:** 1-based index in sorted full `pump_photos` (`allPhotos`), not the subset `index+1`. Subset runs still process only subset files.

**Parked** (source, not scheduled): P / P-jump / P-rot / Prod-m65, H* horiz, L/M dilate, G-dense, K. v4/p4 processors deleted (`obsolete-p4-p5-det`).

**v0.98-71 crash fix:** L/M `getStructuringElement` aborted (`normalizeAnchor`); dilate now uses `Mat::ones(3,3)` + try/catch (v0.98-72+). **No successful L–Q data before that fix.**

**Horiz (history):**
* G-- / production: `SET_G_HORIZ_FACTOR = 0.5` × expanded blue height (each side).
* Prior G-dense/K trial: `SET_G_DENSE_HORIZ_FACTOR = 1.0` (**2×**). Mixed: ~25 fields lower min_v, ~34 higher on phone.

**Heat thr (product u8):**
* G-- / H* / L–Q discovery: `HEAT_THR_U8_GE1` → on if **u8 ≥ 1**
* (parked K): `HEAT_THR_U8_GE2` → on if **u8 ≥ 2**  

**Heat dumps:** Off (`dumpHeats = false` on every column, including G--). Host copies already exist under `latest-report/pump_heats_2026-08-15_*`. Format if recaptured: magic `HMU8`, w/h LE u32, comp=1 (zlib), raw_len, payload. See `HeatmapU8Dump.kt`.

**Energy traces:** Off. Full lossless jump+rot dumps already exist from `2026-08-15_09-38-52` / `09-39-09`. P4-jump / P4-rot no longer write `expand_energy_<ts>/`. Official path and unofficial `energy_count` OCR are unchanged.

**JNI content expand (AABB):** Gray and color AABB energy (jump / chi2 / xycut), ink 7seg, and Y jump-retract are JNI (`nativeAabbGrowMany` / `nativeSeg7Many` / `nativeJumpMany` in `ContentExpandNative.cpp`). One Sobel set (and chromaMag on color) per photo for all seeds. `nativeSeg7Many` `chromaMode`: 0 gray, 1 chromaMag, 2 **tintMask** (`fillChromaTintMask`), 3 same tintMask; glare `dropWide` **11×** `max(v0,4)` on all AABB ink (including walk2). Scheduled **rot-ink** 7seg/jump run in JNI (`nativeSeg7OrientedMany` / `nativeJumpOrientedMany`); Kotlin `expand7segFromOrientedSeedMany` / `jumpRetractOrientedUMany` fallback if native returns null; no AABB except rec warp; rot `dropWide` **11×** on u/v seed only. Rec warps the finished blue. Parked rot-energy still uses `nativeExpandOriented`. G-- verts stay calculated. Rec still Y. Kotlin bodies are fallback if JNI returns null, and when energy traces are on.

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
