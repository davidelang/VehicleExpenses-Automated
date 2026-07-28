# Pump Experiment N-Sets Architecture

This document describes the tree-based reporting architecture used in the Pump Experiment and the **current active flows** after the simplify-experiments trim.

## Active flows (current)

Only two columns run (each on a fresh master copy):

| Flow display name | Processor | Notes |
|-------------------|-----------|-------|
| `Set G-- (4 pass, none, calculated)` | `makeGProc(SET_G_MINUS_MINUS_VERT_FACTORS, …)` → `procGMinusMinus` | Same verts as Quick Fill production (`PumpCostVolUtils` / `OcrHarness`) |
| `Set I (D+E+G hybrid, calculated)` | `procI` | Deskew once; staged G/D/E verts (`iGVert` / `iDVert` / `iEVert`); one combined classify |

**Retired multi-set flows and dead helpers** (recover with tags + full catalog):

- Doc: `docs/obsolete/EXPERIMENT_PUMP_SETS.md`
- Tag: `obsolete-experiment-pump-multi-sets` (`git show obsolete-experiment-pump-multi-sets:app/src/main/java/.../ExperimentPumpScreen.kt`)

Removed clusters include: sets A–H / D / E / G / G- columns, binPeak stack, ML discovery buffers, C/E longLived hist visuals, already-dead legacies. **Not removed:** `makeGProc`, hybrid stage helpers, shared redbox/OCR/stitch paths used by G--/I.

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

Each flow is a sub-branch of the root tree. Reporting (`pBuildHtmlRowDynamic` and `pSerializePhotoResultToJson`) walks this tree for columns and JSON. Prefer not modifying reporting when only adding/removing flows via the `flows` list + processor map.

## 2. How to configure flows

In `ExperimentPumpScreen.kt`, inside `runPumpExperiment`:

```kotlin
val flows = listOf(
    "Set G-- (4 pass, none, calculated)",
    "Set I (D+E+G hybrid, calculated)"
)
// ...
val flowProcessors = listOf(
    "Set G-- (4 pass, none, calculated)" to procGMinusMinus,
    "Set I (D+E+G hybrid, calculated)" to procI,
)
```

Each processor is self-contained. Dispatch selects by flow display name. Do not reintroduce retired multi-set processors without recovering them from the obsolete tag and updating obsolete docs.

### Populate the branch

* `branch.images["PD"]` / `branch.pathResults["Paddle"]` for pump-only calculated paths
* `branch.metadata["tilt"]` and timing keys for diagnostics

ML Kit columns are no longer part of the active experiment (G--/I are paddle/calculated paths).

## 3. Best Practices

* **Standardized Colors:** `Color.RED` for raw detections; `Color.rgb(255, 165, 0)` (ORANGE) for final merged results; blue for intermediate stretch/blue rects.
* **Safety:** Check Base64 strings before adding to branch images.
* **Per-set metadata:** Store tilt/timings in `branch.metadata`.
* **Production isolation:** Quick Fill / `OcrHarness` / `PumpCostVolUtils` are separate from this experiment screen — do not “sync” experiment-only deletes into production without an explicit plan.
* **Parity:** When changing G-- or I processor bodies, treat JSON parity as the primary criterion (same labels/fields as before on the same inputs).

## Related

* Alignment experiment simplify: `docs/obsolete/EXPERIMENT_ALIGNMENT_SETS.md` + tag `obsolete-experiment-alignment-sets-a-e` (silent mlAngle ID lock + Set J only).
