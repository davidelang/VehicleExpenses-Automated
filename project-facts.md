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
- Package: `app/src/main/java/com/davidlang/vehicleexpensesautomated/`
- Core harness locations (ui/util/):
  - OcrEngine.kt, OcrHarness.kt, NativePaddleEngine.kt
  - IcrsMath.kt and related
- Data/UI: Vehicle.kt (data/model/), VehicleViewModel.kt (ui/vehicle/)
- Experiment UI when present: `ui/experiment/`
- Pump experiment: `ui/experiment/ExperimentPumpScreen.kt` — display snapshot targets via `PUMP_*` consts (~2720); binPeak binarization window via `BIN_PEAK_BINARIZE_DELTA` const (~2728, default for `captureBinPeakSnapshotsFromRedbox`); selection OCR JSON via `costVolDecisionData_*` metadata from `ocrPumpRectsAsisAndDigits` + `buildCostVolDecisionDataJson`; binPeak debug images gated by `findPeaksFromHistBins` (~2581) which calls `OdometerOcrUtils.findPeakBinsFromHistogram` (~799) and returns only positive-count union-hist bins; binPeak snapshots use `takeSnapshot(b.mat)` on workspace.s binary scratch
- Specs: `docs/specs/` (ISOTROPIC_COORDINATE_SPEC.md)

Update only with new stable location facts valid for future unrelated work. Current-effort details go in the active plan or ENGINEERING_LOG.md.
