# Obsolete: Experiment Pump Sets (multi-set flows + dead helpers)

## Overview

The pump experiment harness in `ExperimentPumpScreen.kt` schedules independent flow columns. Each flow receives a **fresh master copy** and its own processor.

## Current active (2026-08-11+)

| Flow | Det | Expand / blue |
|------|-----|----------------|
| **Set G--** | product | calculated verts + horiz 0.5 (production ref) |
| **Set P** | product | interior-energy residual (`ContentExpandUtils`, jump off) |
| **Set P4** | **PP-OCRv4_mobile_det** | same interior-energy expand as P |

## Parked columns (2026-08-11 — removed from `flows`)

| Flow | Why parked |
|------|------------|
| **H0…H2.0** horiz A/B | Horiz sweep done; data on dual devices |
| **L / M** heat dilate | Dilate A/B done; not needed for P/P4 focus |
| **N / O / Q** content modes | Dual / edge / v0.25+dual — P kept as preferred residual |
| **P-jump** | Jump+retract implemented as option (`enableJump=true`); not scheduled until A/B wanted |

## Historical multi-set list (earlier simplify)

| Flow display name | Processor | Stretch / blue method (summary) |
|-------------------|-----------|----------------------------------|
| `Set D (clip edges, calculated)` | `procD` | Clip edges; calculated blue |
| `Set E (valley push, calculated)` | `procE` | Valley push; calculated blue |
| `Set G (none, calculated)` | `procG` | No stretch; 8-size vert list |
| `Set G- (6 pass, none, calculated)` | `procGMinus` | 6-pass reduce |
| `Set G-- (4 pass, none, calculated)` | `procGMinusMinus` | 4-pass; **Quick Fill** production verts |
| `Set I (D+E+G hybrid, calculated)` | `procI` | Deskew once; staged G then D then E verts |

Also defined but not active in older trees: `procA`, `procB`, `procC`, `procF`, `procH`.

**Production Quick Fill** continues to use `OcrHarness` + `PumpCostVolUtils` with `SET_G_MINUS_MINUS_VERT_FACTORS` — **not deleted**.

## Last known functional state

| Item | Value |
|------|--------|
| **Annotated tag** | `obsolete-experiment-pump-multi-sets` |
| **Commit (tag peel)** | `91e94f53b67db84eadcac16485ab2738054c208d` |
| **Subject** | Merge branch instruction into master |

**Recovery:**

```text
git show obsolete-experiment-pump-multi-sets:app/src/main/java/com/davidlang/vehicleexpensesautomated/ui/experiment/ExperimentPumpScreen.kt
git show obsolete-experiment-pump-multi-sets:app/src/main/java/com/davidlang/vehicleexpensesautomated/ui/experiment/ExperimentPumpScreen.kt | rg -n 'val procA|val procD|fun captureBinPeak|fun makeGProc|val flows'
```

Alignment multi-set tag (related effort): `obsolete-experiment-alignment-sets-a-e` (same pre-delete commit).

## Superseded by / retained

| Retired flow / cluster | Superseded by |
|------------------------|---------------|
| Set D, E, G, G- columns | Not replaced in experiment; production/compare focus is G-- + I |
| Sets A, B, C, F, H (already inactive or dead procs) | Historical only; recover from tag |
| BinPeak stack (B/C/F) | Not used by G--/I |
| ML discovery multi-scale buffers for experiment ML columns | G--/I use paddle discovery paths only |

**Retained live paths:** `makeGProc` (G-- instance only), `procI`, hybrid stage helpers, shared redbox/OCR/stitch/classify helpers used by those two flows.

## Retired sets / columns

### Set A — baseline ML+Paddle (already inactive)

- **What:** Dual ML Kit + Paddle discovery baseline; populated ML + PD path results.
- **Why dropped earlier / now deleted:** Noise and cost; not in active flows list; slower than calculated G-family paths.

### Set B / C / F — binPeak expanded object blue

- **What:** Peak-binarize reds → object blues → OCR + HTML peak images (`captureBinPeakSnapshotsFromRedbox` cluster).
- **Why removed:** Heavy; superseded for experiment by calculated G-- / I; inactive before final trim.

### Set D — clip edges, calculated

- **What:** Clip-edge stretch; calculated blue; vert factors tuned for 0-loss on dual-device retest.
- **Why removed:** Speed; retained experiment signal is G-- + I only. Hybrid Set I still embeds D-stage verts internally via `iDVert` (not a separate column).

### Set E — valley push, calculated

- **What:** Valley-push stretch; calculated blue; per-red hist dual visuals via `longLivedHistogramBuffer`.
- **Why removed:** Speed; Set I embeds E-stage verts via `iEVert`.

### Set G — full 8-size none, calculated

- **What:** `makeGProc(SET_G_VERT_FACTORS, …)` full G vert list.
- **Why removed:** G-- (k=4) is the production Quick Fill path and the retained experiment column; full G is slower with little retained-report value.

### Set G- — 6-pass reduce

- **What:** `makeGProc(SET_G_MINUS_VERT_FACTORS, …)`.
- **Why removed:** Intermediate between G and G--; G-- kept as production-aligned experiment column.

### Set H — E+G hybrid (already inactive)

- **What:** Hybrid of E and G stages as a separate column.
- **Why removed:** Superseded by Set I hybrid design; dead processor.

### Set I / Set G-- — **NOT retired**

Documented here only for contrast: both remain live with **bit-identical** processor semantics (no drive-by refactors inside bodies).

## Removed helpers (full catalog)

Source file unless noted: `app/src/main/java/com/davidlang/vehicleexpensesautomated/ui/experiment/ExperimentPumpScreen.kt`. Recover via `obsolete-experiment-pump-multi-sets`.

### Already dead (definition-only orphans)

| Symbol | Kind | Approx. lines | What it did | Used by | Why removed | Recovery |
|--------|------|---------------|-------------|---------|-------------|----------|
| `combinePhotoFragmentsIntoJson` | private fun | ~73 | Concat JSON fragment files into one report JSON | None (stream path used) | Already dead | search `fun combinePhotoFragmentsIntoJson` at tag |
| `generateCdfB64` | private fun | ~3158 | CDF plot base64 from mat | None | Already dead | `fun generateCdfB64` |
| `applyRecognitionHeuristics` | private fun | ~3744 | Text cleanup heuristics on hunk OCR strings | None | Already dead | `fun applyRecognitionHeuristics` |
| `drawHunksOnBitmap` | private fun | ~3750 | Draw hunk rects on bitmap | None | Already dead | `fun drawHunksOnBitmap` |
| `pumpCreateScaledBase64` | private fun | ~3760 | Scale bitmap → base64 JPEG | None | Already dead | `fun pumpCreateScaledBase64` |
| `pGetFullLandmarksFromJson` | private fun | ~3332 | Parse landmark JSON into `TextBlock` list | None (orphan) | Already dead (orphan sweep) | `fun pGetFullLandmarksFromJson` at tag |
| `pToEvenInt` | private fun | ~3360 | Round float to even int | None (orphan) | Already dead (orphan sweep) | `fun pToEvenInt` at tag |

### Processors (retired / inactive)

| Symbol | Kind | Approx. lines | What it did | Used by | Why removed | Recovery |
|--------|------|---------------|-------------|---------|-------------|----------|
| `procA` | local suspend lambda | ~1064 | Set A full ML+Paddle pipeline into branch | Former flows / leftover def | Set A retired | `val procA` at tag |
| `procB` | local suspend lambda | ~1265 | Set B binPeak path | Former flows | B retired | `val procB` |
| `procC` | local suspend lambda | ~1394 | Set C binPeak + per-red hist visuals | Former flows | C retired | `val procC` |
| `procD` | local suspend lambda | ~1613 | Set D clip-edges calculated path | Active multi-set flows | D column removed | `val procD` |
| `procE` | local suspend lambda | ~1783 | Set E valley-push calculated path | Active multi-set flows | E column removed | `val procE` |
| `procF` | local suspend lambda | ~2008 | Set F binPeak path | Former flows | F retired | `val procF` |
| `procG` | local val (`makeGProc` instance) | ~2305 | Full G vert list processor | Active multi-set flows | G column removed | `val procG` |
| `procGMinus` | local val (`makeGProc` instance) | ~2306 | G- k=6 processor | Active multi-set flows | G- column removed | `val procGMinus` |
| `procH` | local suspend lambda | ~2407 | E+G hybrid column | Former flows | H retired | `val procH` |
| `regularGVert` | local val | ~1055 | Alias to `SET_G_VERT_FACTORS` for full G | `procG` only | G removed | `regularGVert` |
| flow display strings D/E/G/G- in `flows` + `flowProcessors` map entries | config | ~384–391, ~2569–2576 | Scheduled multi-column run | Experiment runner | Only G-- + I remain | `val flows` / `flowProcessors` at tag |

**Keep (not in this table as removed):** `makeGProc` factory, `procGMinusMinus`, `procI`.

### BinPeak cluster (B/C/F only)

| Symbol | Kind | Approx. lines | What it did | Used by | Why removed | Recovery |
|--------|------|---------------|-------------|---------|-------------|----------|
| `captureBinPeakSnapshotsFromRedbox` | private suspend fun | ~3024 | Peak-binarize reds → objects → OCR + images/metadata | B/C/F procs | Sets removed | `fun captureBinPeakSnapshotsFromRedbox` |
| `findPeaksFromHistBins` | private fun | ~3008 | Peaks + heights from combined redbox hist bins JSON | binPeak capture | B/C/F only | `fun findPeaksFromHistBins` |
| `ocrBinPeakRectsAsisAndDigits` | private suspend fun | ~2878 | OCR as-is + digits on binPeak rects | binPeak capture | B/C/F only | `fun ocrBinPeakRectsAsisAndDigits` |
| `takeBinPeakAnnotatedSnapshot` | private suspend fun | ~2992 | Annotated JPEG base64 of binarized mat + red/blue rects | binPeak capture | B/C/F only | `fun takeBinPeakAnnotatedSnapshot` |
| `binPeakComputeBlueRectsPerRed` | private fun | ~2820 | Object blue unions intersecting reds (Y-overlap seed) | binPeak capture | B/C/F only | `fun binPeakComputeBlueRectsPerRed` |
| `binPeakComputeStrokeWidths` | private fun | ~2854 | Vertical/horizontal stroke width estimates on binary | binPeak capture | B/C/F only | `fun binPeakComputeStrokeWidths` |
| `validBinPeakRects` | private fun | ~2849 | Filter valid rects for binPeak | binPeak helpers | B/C/F only | `fun validBinPeakRects` |
| `binPeakRectsIntersect` | private fun | ~2810 | Rect intersection test | blue rect compute | B/C/F only | `fun binPeakRectsIntersect` |
| `binPeakYOverlapHeight` | private fun | ~2813 | Y-overlap height between rects | blue rect compute | B/C/F only | `fun binPeakYOverlapHeight` |
| `shrinkBlueRectForOcr` | private fun | ~2867 | Shrink full blue rect for OCR crop | binPeak OCR path | B/C/F only | `fun shrinkBlueRectForOcr` |
| `componentStatsToJson` | private fun | ~2976 | Serialize connected-component stats to JSON | binPeak metadata | B/C/F only | `fun componentStatsToJson` |
| `parseBinPeakKeyToPeakNum` | private fun | ~3195 | Parse `binPeak_N_*` image keys → peak number | HTML builder | B/C/F report | `fun parseBinPeakKeyToPeakNum` |
| `buildBinPeakHtmlForBranch` | private fun | ~3204 | HTML fragment for per-peak uncleaned/cleaned images | `pBuildHtmlRowDynamic` | Empty after B/C/F gone | `fun buildBinPeakHtmlForBranch` |
| `BIN_PEAK_BINARIZE_DELTA` | private const | ~3234 | Brightness delta for peak binarize | binPeak capture | B/C/F only | `BIN_PEAK_BINARIZE_DELTA` |
| `matToPbmP4Base64` | private fun | ~2933 | P4 1-bit packed PBM base64 for binPeak debug binaries | binPeak capture when `generateP4` | Primarily binPeak (delete if only used there) | `fun matToPbmP4Base64` |

### C/E per-red histogram visuals

| Symbol | Kind | Approx. lines | What it did | Used by | Why removed | Recovery |
|--------|------|---------------|-------------|---------|-------------|----------|
| `longLivedHistogramBuffer` | local `BufferSet` | ~368 | Scratch BufferSet for per-red rect+hist JPEG | procC / procE dual visual loops | C/E removed | `longLivedHistogramBuffer` at tag |
| `histPlotCrop` | local crop id | ~371 | 186×300 plot crop inside longLived buffer | C/E per-red hist render | C/E removed | `histPlotCrop` at tag |
| per-red `redboxHistC_*` / dual visual loops | code inside procC/E | ~1547–1576, ~1928–1957 | Emit per-red rect + hist images into branch | Sets C, E | C/E removed | inside `procC`/`procE` at tag |

### B/D retracted blue helpers

| Symbol | Kind | Approx. lines | What it did | Used by | Why removed | Recovery |
|--------|------|---------------|-------------|---------|-------------|----------|
| `doBOrDRetractedBlueAndPD` | local suspend fun | ~1021 | Uniformity retract blue from expanded reds + PD snapshot path | Historical B/D (mostly commented at call sites) | B/D removed; unreferenced after proc delete | `fun doBOrDRetractedBlueAndPD` |
| `computeRetractedBluePixelRects` | local suspend fun | ~1002 | Compute retracted blue pixel rects | `doBOrDRetractedBlueAndPD` | Same | `fun computeRetractedBluePixelRects` |

**Not removed if still called from G--/I:** `doBOrDRedOnlyImage` (red-only PD image helper shared by calculated paths).

### ML discovery buffers + Set A ML pieces

| Symbol | Kind | Approx. lines | What it did | Used by | Why removed | Recovery |
|--------|------|---------------|-------------|---------|-------------|----------|
| `mlDiscoveryBuffers` | local `mapOf` BufferSets + release | ~356, ~2668 | Scale-tier BufferSets for ML Kit discovery | Primarily Set A ML; leftover chosenScale/chosenBuffer locals in several procs | No ML columns remain | `mlDiscoveryBuffers` at tag |
| `chosenScale` / `chosenBuffer` locals | locals inside procs | e.g. ~1101, ~1643, … | Pick ML scale buffer | ML pick inside procs | With ML buffers / A | inside each proc at tag |
| `mlBlocksRaw` + ML `pathResults` / `images["ML"]` wiring | code inside procA | inside procA | ML Kit discovery + report column | Set A | A removed | `procA` body at tag |

## Not removed (explicit keep list)

| Keep | Role |
|------|------|
| `makeGProc` | Factory for G-family calculated none-stretch processors; **G-- only** instance retained |
| `procGMinusMinus` / G-- flow display name | Retained experiment column |
| `procI` / Set I flow display name | Retained hybrid experiment column |
| `iGVert` / `iDVert` / `iEVert` | Set I stage vert lists |
| `SET_G_MINUS_MINUS_VERT_FACTORS` (from `PumpCostVolUtils`) | G-- verts; also Quick Fill |
| `hybridRunDiscoveryStage` | Set I staged discovery |
| `hybridAppendStageOcr` | Set I stage OCR append |
| `captureRedboxData` | Redbox hist/meta for live paths |
| `ocrPumpRectsAsisAndDigits` + `experimentRecSet1024x48` | Pump rect OCR |
| `getFinal` + `stitchHunksHorizontally` / `groupLanesByVerticalGap` / `findBestLanePair` / `expandHunkContext` / `performHunkRecognition` / `mergeGeometryIntoHunks` | Lane/hunk assembly + recognition |
| `runDiscoveryPaddle` | Paddle discovery |
| `prepareScale` | Scale prep for discovery |
| `doCrossScaleRedboxFilter` / `doCrossScaleRedboxFilterPixel` | Cross-scale redbox filter |
| `pruneRedPixelsTopN` | Top-N red prune |
| `doBOrDRedOnlyImage` | If still called from G--/I |
| `generateHistogramB64` | If root `hist1` still used |
| `buildCostVolDecisionDataJson` (if present) | Decision metadata for remaining flows |
| `pSerializePhotoResultToJson` / `pBuildHtmlHeader` / `pBuildHtmlRowDynamic` | Reporting for remaining flows (may trim binPeak HTML after cluster delete) |
| `PumpCostVolUtils` / `OcrHarness` / Quick Fill | Production — **do not modify** for this simplify |
| `SET_G_VERT_FACTORS` / `SET_G_MINUS_VERT_FACTORS` in shared utils | Shared defaults/history; Quick Fill needs G-- constants; plan says leave shared utils |

## Reasons for obsolescence

1. **Speed:** Multi-column pump experiments run each flow on a full master copy; dropping D/E/G/G- (and dead A–H) roughly halves or better wall-clock while keeping the two decision-relevant columns.
2. **Accuracy / relevance:** G-- matches Quick Fill production verts; Set I is the hybrid under active comparison. Other columns were slower/less useful for current decisions.
3. **Maintainability:** ~3.7k-line file with many dead procs and binPeak stacks is hard to navigate; catalogued deletion with tags restores recoverability without keeping compile weight.
4. **No production change:** Quick Fill and `OcrHarness` paths unchanged; only experiment screen + docs.

## Related docs / tags

- Live architecture notes: `docs/PUMP_EXPERIMENT_FLOWS.md` (updated after simplify to G-- + I only).
- Alignment counterpart: `docs/obsolete/EXPERIMENT_ALIGNMENT_SETS.md` + `obsolete-experiment-alignment-sets-a-e`.
- Shared pump classification / verts live in `ui/util/PumpCostVolUtils.kt` and related production harness code — not archived here.
