# project-facts.md — Stable "where things live" facts (app worktree)

Contains only locations and structure facts that remain true across work on this tree (after merge + new worktree for different effort).

Read in full early on startup/new cycle to avoid find/discovery commands.

## Sandbox (shared via symlink)
- `dev-ai-interaction -> ../dev-ai-interaction`
- Absolute path for writes: `/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/`
- `dev-ai-interaction/plans/` — designated active plan (user names exact file)
- `dev-ai-interaction/historical-plans/` — archived plans
- `dev-ai-interaction/implementation-failure-logs/` — scan on startup / "new planning cycle"
- `dev-ai-interaction/.planning-agent-prompt.txt` — planner restart prompt from master

## At worktree root
- `ENGINEERING_LOG.md` (append-only at end)
- `project-facts.md` (this file)
- `TODO.md` (backlog; planner edit exception)
- Launchers: `run-grok*` (use `../` when inside worktree)
- `build_app`, `get-builds-tag.sh`
- `update-rules.sh` (run from orchestration root), set-worktree-perms, set-sandbox-perms, setup-project, etc.
- `.grok/config.toml` + `.grok/hooks/`
- `project.config.example`

## Application source (stable layout)
- `app/` (Android root with gradle, src/)
- `app/src/main/AndroidManifest.xml` — `<application android:largeHeap="true">` requests larger Java heap for the process
- Package: `app/src/main/java/com/davidlang/vehicleexpensesautomated/`
- Core harness locations (ui/util/):
  - OcrEngine.kt, OcrHarness.kt, NativePaddleEngine.kt
  - IcrsMath.kt and related
- Data/UI: Vehicle.kt (data/model/), VehicleViewModel.kt (ui/vehicle/)
- Experiment UI when present: `ui/experiment/`
- Pump experiment: `ui/experiment/ExperimentPumpScreen.kt` — display snapshot targets via `PUMP_*` consts (~2720); binPeak binarization window via `BIN_PEAK_BINARIZE_DELTA` const (~2728, default for `captureBinPeakSnapshotsFromRedbox`, d=0 when valley quantized hist has <=10 positive bins); selection OCR JSON via `costVolDecisionData_*` metadata from `ocrPumpRectsAsisAndDigits` + `buildCostVolDecisionDataJson`; binPeak debug images only on expanded B/C/F (`captureBinPeakSnapshotsFromRedbox` call sites); `findPeaksFromHistBins` (~2578) uses exact all-positive 1-bin peaks for valley (<=10 nz), else `OdometerOcrUtils.findPeakBinsFromHistogram` (~799); binPeak snapshots use `takeSnapshot(b.mat)` on workspace.s binary scratch; binPeak stroke-width vSW/hSW via `binPeakComputeStrokeWidths` → `NativeImageUtils.calculateHistogramWithThresholdH` → `nativeCalculateHistogramWithThresholdH` in `app/src/main/cpp/NativeImageUtils.cpp` (~1692)
- Specs: `docs/specs/` (ISOTROPIC_COORDINATE_SPEC.md)

Update only with new stable location facts valid for future unrelated work. Current-effort details go in the active plan or ENGINEERING_LOG.md.
