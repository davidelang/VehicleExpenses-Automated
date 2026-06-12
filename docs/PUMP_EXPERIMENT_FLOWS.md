# Pump Experiment N-Sets Architecture

This document describes the tree-based reporting architecture used in the Pump Experiment and provides instructions for future agents on how to add or modify experiment flows.

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

Each "Flow" (e.g., "Set A") is a sub-branch of the root tree. The reporting logic (`pBuildHtmlRowDynamic` and `pSerializePhotoResultToJson`) automatically walks this tree to generate columns and JSON objects. **Do not modify the reporting functions when adding new flows.**

## 2. How to Add a New Flow

### Step 1: Configure the Flow List
In `ExperimentPumpScreen.kt`, locate the `flows` list inside the `runPumpExperiment` function:

```kotlin
// Configure experiment flows here. (See: docs/PUMP_EXPERIMENT_FLOWS.md for instructions)
val flows = listOf("Set A", "New Set")
```

Adding a string to this list **normally** adds two columns (ML and Paddle) to the HTML report. Exception: documented pure-pump / ML-free flows (see below) only produce a Paddle column; the reporting builders auto-omit the ML th/td for those flow names.

### Step 2: Implement Flow-Specific Logic
Use an array (list) of processor functions/lambdas (or references to them), in the same order as the `flows` list. Iterate the array (e.g. `flows.forEachIndexed { i, _ -> ... flowProcessors[i](ws, br, det, w, h) }` or zip). Each processor entry is a self-contained function/lambda whose body is the linear list of steps for that path (transform, deskew/tilt, discovery, extraction, viz, populate only its branch keys). No `if (flowName == "Set X")` or hard-coded per-set function names inside the per-path code itself -- the array + index/zip is how the dispatch selects and iterates the function for each flow (per the clarification: "an array of functions that you can iterate over is fine", avoiding ugly hard-coding of names like "setAmlkit"/"setApaddle" at call sites or inside paths).

Example (skeletal):
```kotlin
val flowProcessors = listOf(
    { ws, br, det, w, h -> /* linear steps for Set A (stretch, standard tilt, ml+pd, both results/viz) */ },
    { ws, br, det, w, h -> /* linear for Set B (pump-only, paddleCpp tilt, pd only) */ },
    { ws, br, det, w, h -> /* linear for Set C (pump-only + valley bin-test: hist, midpoints, per-bin binarize, discovery per version, stack composite for PD, best for path) */ }
)
flows.forEachIndexed { i, _ ->
    val branch = root.getBranch(flows[i])
    ... common setup (ws copy, discoveryDetails) ...
    flowProcessors[i](workspace, branch, discoveryDetails, imgW, imgH)
}
```
The old `if`/`when` inside a single `forEach` on flowName is the tangled mess being refactored away (old body remains temp during transition; full cleanup when processors are filled and old body removed). Pure-pump sets (B, C) just don't populate ML keys in their processor. Do not modify the reporting builders (pBuild*); they auto-handle via the branch data and flow names.

See ExperimentPumpScreen.kt for the current array + dispatch (C entry is the dedicated per-path for the valley bin-test from alignment Set J).

### Step 3: Populate the Branch
Results are stored in the `branch` object provided for each iteration:
* `branch.images["ML"] = ...`
* `branch.images["PD"] = ...`
* `branch.pathResults["ML"] = ...`
* `branch.metadata["tilt"] = ...` (captured per-flow after the tilt selection; used by the first column to report "Tilt per set: ..." for each set)

## ML-free / pump-only flows (e.g. "Set B")
For flows that deliberately skip ML Kit entirely (pump-only numeric path):
- Guard the entire ML discovery block (`extractFromPhotoBitmapRaw`, mlBlocksRaw population), `mlHunks`, `images["ML"]`, and `pathResults["ML"]` with `if (flowName != "ThePureFlow") { ... }`.
- Only populate `images["PD"]` and `pathResults["Paddle"]` (plus any flow-specific metadata such as the per-flow tilt).
- The header and row builders contain the matching `if (flow != "ThePureFlow")` / `if (name != "ThePureFlow")` around the ML th/td so the column is omitted automatically.
- The first (left) column will render per-set tilt angles (from `branch.metadata["tilt"]` values captured inside the flow loop after the deskew angle selection). This makes the different deskew choices (e.g. standard angle vs. paddleCppAngle) visible on every report row.
- Set B is the reference implementation of this pattern. When adding future pure-pump sets, follow the same conditional structure around all ML Kit work.

## 3. Best Practices
* **Standardized Colors:** Use `Color.RED` for raw detections and `Color.rgb(255, 165, 0)` (ORANGE) for final merged results.
* **Safety:** Always check if Base64 strings are empty before adding them to the branch to prevent broken links in the report.
* **Per-set metadata (tilt etc.):** Store flow-specific values (tilt, timings) in `branch.metadata` under clear keys. The first column and metaHtml will surface them for diagnostics without changing the core reporting functions.
