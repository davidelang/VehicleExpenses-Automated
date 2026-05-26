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

Adding a string to this list automatically adds two columns (ML and Paddle) to the HTML report.

### Step 2: Implement Flow-Specific Logic
Inside the `flows.forEach { flowName -> ... }` loop, use `if` or `when` statements to apply specific logic based on the `flowName`.

Example:
```kotlin
if (flowName == "New Set") {
    // Apply different contrast stretch or different deskew engine
    OdometerOcrUtils.applyContrastStretch(workspace.p.mat, 0.10f)
} else {
    OdometerOcrUtils.automaticContrastStretch(workspace.p.mat)
}
```

### Step 3: Populate the Branch
Results are stored in the `branch` object provided for each iteration:
* `branch.images["ML"] = ...`
* `branch.images["PD"] = ...`
* `branch.pathResults["ML"] = ...`

## 3. Best Practices
* **Standardized Colors:** Use `Color.RED` for raw detections and `Color.rgb(255, 165, 0)` (ORANGE) for final merged results.
* **Safety:** Always check if Base64 strings are empty before adding them to the branch to prevent broken links in the report.
