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
- `dev-ai-interaction/paddle-build/patches-int8/` — INT8-only Paddle-Lite deltas (`apply_int8_patches.sh`, `Dockerfile.int8`); separate from `patches/` PR branches
- `dev-ai-interaction/paddle-build/Dockerfile.int8` — int8 arm JNI builds must run `patchelf --set-soname libpaddle_lite_jni.so` post-android-build via `/workspace/set_jni_soname.sh` (arm64/armv7 `java/so/libpaddle_lite_jni.so` lacks linker SONAME otherwise)
- `dev-ai-interaction/research/optimize_mono_int8_models.sh` — host-side INT8 `.nb` conversion script
- `dev-ai-interaction/scripts/deploy-golden-pump-photos.sh` — push GOLDEN_SUBSET and/or flat pump zips to device
- `dev-ai-interaction/research/photos/pump/` — pump experiment source photos; `pump-zips/` holds generated flat zips

## Tracked documentation (`docs/` in app worktrees)
- `docs/specs/` — hard requirements; if docs and code disagree, **docs are authoritative**
- `docs/reference/` — documents current code/artifacts; if docs and code disagree, **code is authoritative**
- `docs/obsolete/` — retired approaches kept for possible future reuse (not current contract)
- Sandbox (`dev-ai-interaction/`) is generally **not** git-tracked; durable docs that must survive in git belong under `docs/` (pick the subdir by authority level above), not the sandbox

### Documentation layers (what belongs where)

| Layer | Path | Git tracked? | Purpose |
|-------|------|--------------|---------|
| Build contract | `docs/specs/PADDLE_BUILD.md` | YES | Fork rebuild (images, containers, branches) |
| Host/runtime INT8 contract | `docs/specs/HOST_PADDLE_USE.md` | YES | XOR remap, conversion scripts, BufferSet pattern |
| Build env tags | `docs/specs/BUILD_ENVIRONMENT.md` | YES | Docker image names, output dirs |
| Stable pointers | `project-facts.md` | YES | Where specs/sandbox/scripts live (no effort narrative) |
| Activity log | `ENGINEERING_LOG.md` | YES | Append-only merge/effort history |
| Fork patch sources | `dev-ai-interaction/paddle-build/patches-int8/` | Sandbox | INT8 Paddle-Lite deltas |
| Docker build outputs | `dev-ai-interaction/paddle-build/output/` | NEVER | `.so`, `opt`, benchmarks (ephemeral) |
| Build status | `dev-ai-interaction/paddle-build/VERIFICATION_PROGRESS.md` | Sandbox | Container run evidence |
| Conversion script | `dev-ai-interaction/research/optimize_mono_int8_models.sh` | Sandbox | Host model conversion |
| Deployment verification | `dev-ai-interaction/research/verify_int8_deployment.sh` | Sandbox | Forensic md5/grep gate |
| Provenance / audits | `dev-ai-interaction/paddle-int8-provenance-*.md`, `dev-ai-interaction/research/paddle-int8-*.md` | Sandbox | Effort artifacts |
| Compliance / failure logs | `dev-ai-interaction/compliance-report-*.md`, `dev-ai-interaction/implementation-failure-logs/` | Sandbox | Checker output until PASS |
| Active plan | `dev-ai-interaction/plans/` | Sandbox | Turn contract |
| Deployed models | `app/src/main/assets/paddle/*_int8_*.nb` | YES | Runtime INT8 assets |
| Deployed jni/jar | `app/src/main/jniLibs/`, `app/libs/PaddlePredictor.jar` | YES | Runtime Paddle stack |

## At worktree root
- `ENGINEERING_LOG.md` (append-only at end; use ONLY `./append-to-engineering-log` or `@file` wrapper to add entries)
- `project-facts.md` (this file)
- `TODO.md` (backlog; planner edit exception)
- Launchers: `run-grok*` (use `../` when inside worktree), `build_app`, `get-builds-tag.sh`, `append-to-engineering-log` (required wrapper for ENGINEERING_LOG.md entries; direct edits blocked)
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
- Quick fill UI: `ui/fuel/QuickFillupScreen.kt` — 3-panel A (camera) / B (controls) / C (fields); `panelAContent` bottom-center letterbox + zoom in right- or bottom-blank (no separate D panel); portrait B row order save-shutter-mode; `ui/components/CameraPreview.kt` provides `CameraZoomControl` callback
- Experiment UI when present: `ui/experiment/`
- Pump experiment: `ui/experiment/ExperimentPumpScreen.kt` — display snapshot targets via `PUMP_*` consts (~2720); binPeak binarization window via `BIN_PEAK_BINARIZE_DELTA` const (~2728, default for `captureBinPeakSnapshotsFromRedbox`, d=0 when valley quantized hist has <=10 positive bins); selection OCR JSON via `costVolDecisionData_*` metadata from `ocrPumpRectsAsisAndDigits` + `buildCostVolDecisionDataJson`; binPeak debug images only on expanded B/C/F (`captureBinPeakSnapshotsFromRedbox` call sites); `findPeaksFromHistBins` (~2578) uses exact all-positive 1-bin peaks for valley (<=10 nz), else `OdometerOcrUtils.findPeakBinsFromHistogram` (~799); binPeak snapshots use `takeSnapshot(b.mat)` on workspace.s binary scratch; red-box prune keeps top 6 by area (B–G paths); binPeak vSW/hSW from `NativeImageUtils.calculateHistogramWithThresholdH` (`nativeCalculateHistogramWithThresholdH` in `app/src/main/cpp/NativeImageUtils.cpp`) uses direct rect-interval run walking (de-overlap wide bias, all-black prefilter, exact-span discard), uncapped run lengths, 8192-bin long-lived buffers; binPeak stroke-width vSW/hSW via `binPeakComputeStrokeWidths` → `NativeImageUtils.calculateHistogramWithThresholdH` → `nativeCalculateHistogramWithThresholdH` in `app/src/main/cpp/NativeImageUtils.cpp`
- Specs: `docs/specs/` (ISOTROPIC_COORDINATE_SPEC.md)

Update only with new stable location facts valid for future unrelated work. Current-effort details go in the active plan or ENGINEERING_LOG.md.
