# Obsolete: Experiment Alignment Sets (A / E report columns + helpers)

## Overview

The multi-set alignment experiment harness in `ExperimentAlignmentScreen.kt` formerly ran three report columns after a shared deskew/angle calculation:

| Key | Display | Rotate angle source | Post-align refinement | Report pathway |
|-----|---------|---------------------|----------------------|----------------|
| `set_a` | Set A | `deskewResA.mlAngle` | ML Kit iterative + non-char-aware Paddle iterative | `pathways["set_a"]` |
| `set_e` | Set E | `deskewResA.paddleCppAngle` | Non-char-aware Paddle iterative only | `pathways["set_e"]` |
| `set_j` | Set J (CC Speedup) | `deskewResA.paddleOptimizedAngle` | Char-aware Paddle iterative (`useCharAware=true`) | `pathways["set_j"]` |

**Simplified experiment (retained):**

1. Shared `calculateAverageTextAngle` (unchanged).
2. **Silent mlAngle vehicle-ID lock** — same rotate/discovery/Tier-1 veto as former first pipeline (Set A angle), **without** writing `pathways["set_a"]` and **without** `runMLKitIterative` / Set A paddle iterative. This preserves the multi-set lock semantics that Set J used when A ran first (`globalWinnerId` only set when still null).
3. **Full Set J report path only** — rotate `paddleOptimizedAngle`, discovery, honor lock, `disambiguateLandmarks` + `anchorAlign` + snapshot + `runPaddleValleyIterative(..., useCharAware=true, pipelineKey="set_j")` with Raw + Bin-Trials; emit **`pathways["set_j"]` only**.

**Primary success criterion:** `pathways["set_j"]` JSON (odometer / harness / nested pathway payloads) must match multi-set runs on the same photo + vehicle DB + models. Speed comes only from not running Set A/E report columns and their ML iterative path.

ID lock formerly from first pipeline Set A (`mlAngle`); retained as silent pass for Set J JSON parity.

## Last known functional state

| Item | Value |
|------|--------|
| **Annotated tag** | `obsolete-experiment-alignment-sets-a-e` |
| **Commit (tag peel)** | `91e94f53b67db84eadcac16485ab2738054c208d` |
| **Subject** | Merge branch instruction into master |

**Recovery:**

```text
git show obsolete-experiment-alignment-sets-a-e:app/src/main/java/com/davidlang/vehicleexpensesautomated/ui/experiment/ExperimentAlignmentScreen.kt
# Search for a symbol at the tag:
git show obsolete-experiment-alignment-sets-a-e:app/src/main/java/com/davidlang/vehicleexpensesautomated/ui/experiment/ExperimentAlignmentScreen.kt | rg -n 'fun runMLKitIterative|fun createScaledBase64|PipelineConfig\("set_a"'
```

Also useful: branch marker `simplify_experiments-start` (same simplification effort start; dedicated obsolete tags above are authoritative for multi-set A/E bodies).

## Superseded by / retained

| Retired | Superseded by |
|---------|----------------|
| Set A **report** column (`pathways["set_a"]`, Set A ML + Set A Paddle harness headers) | Silent mlAngle ID lock (no pathway) + Set J for report |
| Set E full pass (`paddleCppAngle` + set_e pathway) | Not replaced — unused for production; Set J is the retained experiment column |
| Outer JSON convenience reading only `pathways["set_a"]` | Outer fields retargeted to locked winner / `pathways["set_j"]` (**nested set_j serialize logic unchanged**) |

**Production Quick Fill** continues to use `OcrHarness.runSetJPipeline` — **not** this experiment screen. Production paths are out of scope for this obsolescence.

## Retired sets / columns

### Set A (`set_a`) — report column removed; silent lock retained

- **Angle:** `mlAngle` from `OdometerOcrUtils.calculateAverageTextAngle`.
- **Role:** First pipeline in A → E → J order; almost always set `globalWinnerId` after rotate + `performLandmarkDiscovery` + Tier-1 veto. Also ran `runMLKitIterative` (“Set A ML”) and non-char-aware paddle iterative; wrote `pathways["set_a"]` and HTML/JSON columns.
- **Why removed (report path):** Slower and less accurate than Set J for experiment comparison; production already standardizes on Set J-style path in `OcrHarness`.
- **Why lock kept:** Naïvely running only Set J changes the vehicle ID when A’s mlAngle discovery/veto winner would have differed from J’s own first-non-null lock — breaking exact `pathways["set_j"]` parity.

### Set E (`set_e`) — fully removed

- **Angle:** `paddleCppAngle`.
- **Role:** Second experiment column; rotate + discovery + align + non-char-aware paddle iterative; `pathways["set_e"]`.
- **Why removed:** No effect on Set J nested results once lock is already set by silent pass; slower; retired for speed.

### Phantom harness header “Set A ML”

- **Role:** `harnessEngineNames = listOf("Set A ML") + pipelines.map { "${it.displayName} Paddle" }` forced an HTML/report column for ML Kit even when only paddle pathways existed.
- **Why removed:** Goes with Set A ML iterative report path.

## Removed helpers (full catalog)

Every symbol below was experiment-local in `ExperimentAlignmentScreen.kt` (unless noted). Recover via tag above.

### Already dead (definition-only orphans)

| Symbol | Kind | Source (approx.) | What it did | Used by | Why removed | Recovery |
|--------|------|------------------|-------------|---------|-------------|----------|
| `createScaledBase64` | private fun | ~1799 | Scale bitmap → base64 JPEG into optional target buffer | None (orphan) | Already dead | `git show <tag>:…ExperimentAlignmentScreen.kt` → `fun createScaledBase64` |
| `drawCropBoxesOnReference` | private fun | ~1808 | Draw vehicle crop boxes on a reference bitmap | None (orphan) | Already dead | search `fun drawCropBoxesOnReference` at tag |
| `toEvenInt` | private fun | ~1844 | Round float to even int | None (orphan) | Already dead (orphan sweep) | `fun toEvenInt` at tag |

### Becomes dead with Set A/E report removal

| Symbol / artifact | Kind | Source (approx.) | What it did | Used by | Why removed | Recovery |
|-------------------|------|------------------|-------------|---------|-------------|----------|
| `PipelineConfig` entries `set_a`, `set_e` | config in `pipelines` list | ~328–331 | Registered multi-pipeline loop keys, display names, deskew time + angle getters | Multi-pipeline report / loop | Retired for speed; silent lock is not a PipelineConfig row | tag file ~`val pipelines` |
| `pathways["set_a"]` / `pathways["set_e"]` emission | report branch | ~550 | Filled per-pipeline `PhotoPathwayResult` for A/E | HTML columns + JSON serialize | Retired columns | multi-pipeline `forEach` at tag |
| Set A ML harness header string | report | ~333 | Forced “Set A ML” column in HTML header | `harnessEngineNames` | Phantom column without set_a | `harnessEngineNames` at tag |
| Outer `pathways["set_a"]` winner/discovery defaults | report keys | ~675, ~758 | Top-level JSON `winner` and discovery dump when only set_a was consulted | `serializePhotoResultToJson` | Retarget outer fields only; do not change nested set_j serialize | `serializePhotoResultToJson` at tag |
| `runMLKitIterative` | private suspend fun | ~2140 | ML Kit iterative odo refinement (Raw + Bin-Trials stages) into harness map | Set A post-split only (`pipeline.key == "set_a"`) | A report column removed | `fun runMLKitIterative` at tag |
| `runBinTrialsMLKit` | private suspend fun | ~1485 | ML Kit bin-trial recognition on odo crop from hist bins | `runMLKitIterative` only | Call graph dies with ML iterative | `fun runBinTrialsMLKit` at tag |
| Full Set E loop iteration | loop body via `set_e` PipelineConfig | ~434–558 | `paddleCppAngle` rotate + discovery + align + non-char-aware paddle iterative + pathway | set_e column | No effect on set_j if lock already set | pipelines list + forEach at tag |
| Set A paddle iterative call (non-char-aware) | call site | ~536 when key set_a | `runPaddleValleyIterative(..., useCharAware=false, pipelineKey="set_a")` | set_a column | Report path removed; silent lock does not call it | set_a branch of forEach at tag |

### Optional / not removed in this effort (if still present)

Non-`set_j` branches inside `runBinTrialsPaddle` may remain for code simplicity unless later grep-proven dead. **If** removed later, add full branch rows here before delete. **Must not** change set_j branch outputs when touching that function.

## Not removed (explicit keep list)

Keep these so executors do not over-delete while trimming:

| Keep | Role |
|------|------|
| `OdometerOcrUtils.calculateAverageTextAngle` | Shared deskew; produces `mlAngle`, `paddleCppAngle`, `paddleOptimizedAngle` |
| Silent **mlAngle** ID lock | Reset B from A; rotate `mlAngle`; `performLandmarkDiscovery`; Tier-1 veto; set `globalWinnerId` / `primaryVetoResultsGlobal` if non-vetoed; **no** pathway, **no** ML iterative |
| Full **Set J** path | Reset B; rotate `paddleOptimizedAngle`; discovery; honor lock (`if (globalWinnerId == null)` only); `disambiguateLandmarks`; `anchorAlign`; snapshot; `runPaddleValleyIterative(..., useCharAware=true, pipelineKey="set_j")`; Raw + Bin-Trials; `pathways["set_j"]` |
| `performLandmarkDiscovery` | Query landmarks for lock + Set J |
| `runPaddleValleyIterative` | Set J (and any residual) paddle refinement |
| `runBinTrialsPaddle` | Set J bin-trial path (char-aware / set_j branches) |
| `findValleyMidpoints` | Valley refinement support |
| Set J hist helpers still referenced (`generateGatedHistogramB64`, etc. as used by set_j) | Report / refinement diagnostics for retained path |
| Report serializers for set_j pathway | `serializePathwayToJson`, vehicle pathway serialize, HTML row builders for remaining column |
| GOLDEN_SUBSET / FAILING_SUBSET UI maps | Experiment photo selection |
| Ingest A→B, probe dims, buffer hygiene, rotate helper | Shared prefix |
| `ImageAlignmentUtils` veto / disambiguate / anchorAlign | Shared identity + align (not experiment-only) |
| `ui/util/OcrHarness.kt` | Production Set J — **do not modify** for this simplify |

## Reasons for obsolescence

1. **Speed:** Running Set A + Set E full passes (extra rotates, discoveries, ML iterative, dual paddle iterative columns) multiplies wall-clock per photo without improving the retained experiment signal (Set J).
2. **Accuracy of retired columns:** Removed options were slower/less accurate relative to Set J for the experiment comparison the team keeps; this is **not** a change to Set J’s algorithm.
3. **Maintainability:** One report column + documented silent lock is easier to reason about than three coupled pathways where outer `winner` defaulted to set_a.
4. **Parity discipline:** Silent lock documents the historical coupling so future agents do not “simplify” by dropping the lock and silently changing set_j JSON.

## Related docs / tags

- `docs/obsolete/ALIGNMENT_ALGORITHMS.md` — older ORB/hub align removals (different scope).
- `docs/obsolete/REFINEMENT_STRATEGIES.md` — legacy single-pass refinement.
- Pump counterpart: `docs/obsolete/EXPERIMENT_PUMP_SETS.md` + tag `obsolete-experiment-pump-multi-sets`.
