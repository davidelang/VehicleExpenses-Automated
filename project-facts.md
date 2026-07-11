# project-facts.md — Stable "where things live" facts (app worktree)

Contains only verifiable locations and structure facts that remain true across work on this tree (after merge + new worktree for different effort). No branch names, tags, commit hashes, or effort narrative.

Read in full early on startup/new cycle to avoid find/discovery commands.

## Sandbox (shared via symlink)
- `dev-ai-interaction -> ../dev-ai-interaction` (separate sandbox git for analysis/plans/paddle-fork tooling)
- Absolute path for writes: `/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/`
- `dev-ai-interaction/plans/` — designated active plan (user names exact file)
- `dev-ai-interaction/historical-plans/` — archived plans
- `dev-ai-interaction/implementation-failure-logs/` — scan on startup / "new planning cycle"
- `dev-ai-interaction/.planning-agent-prompt.txt` — planner restart prompt from master
- `dev-ai-interaction/paddle-build/patches-int8/` — Paddle-Lite INT8/u8 fork patches (`apply_int8_patches.sh`, `Dockerfile.int8`)
- `dev-ai-interaction/paddle-build/Dockerfile.int8` — post-build `patchelf --set-soname libpaddle_lite_jni.so` via `/workspace/set_jni_soname.sh` for arm jni outputs
- `dev-ai-interaction/scripts/deploy-golden-pump-photos.sh` — push GOLDEN_SUBSET and/or flat pump zips to device
- `dev-ai-interaction/research/photos/pump/` — pump experiment source photos; `pump-zips/` holds generated flat zips
- `dev-ai-interaction/research/imagine-icon-candidate/` — launcher icon Imagine source: README with Grok Imagine post URL; masters `app-icon-master-1024.png` / `app-icon-master-512.png`; density export under `android-export/`
- App-relevant durable docs/scripts belong in tracked `docs/` or `app/` — not sandbox-only

## Tracked documentation (`docs/` in app worktrees)
- `docs/specs/` — hard requirements; if docs and code disagree, **docs are authoritative**
- `docs/reference/` — documents current code/artifacts; if docs and code disagree, **code is authoritative**
- `docs/obsolete/` — retired approaches kept for possible future reuse (not current contract)
- Paddle host tooling guide: `docs/specs/HOST_PADDLE_USE.md` (Python env, `opt` tool, dynamic shapes)
- Paddle fork rebuild: `docs/specs/PADDLE_BUILD.md`, `docs/specs/BUILD_ENVIRONMENT.md`
- Coordinates: `docs/specs/ISOTROPIC_COORDINATE_SPEC.md`
- Launcher icon: `docs/reference/APP_LAUNCHER_ICON.md`
- Reports metrics ($/mi, volume display): `docs/reference/REPORTS_METRICS.md`

## At worktree root
- `ENGINEERING_LOG.md` (append-only; use ONLY `./append-to-engineering-log` or `@file` wrapper)
- `project-facts.md` (this file)
- `TODO.md` (backlog)
- Launchers: `run-grok*`, `build_app`, `get-builds-tag.sh`, `append-to-engineering-log`
- `update-rules.sh` (from orchestration root), `set-worktree-perms`, `set-sandbox-perms`, `setup-project`
- `.grok/config.toml` + `.grok/hooks/`
- `project.config.example`

## Application source (stable layout)
- `app/` (Android root)
- `app/src/main/AndroidManifest.xml` — `<application android:largeHeap="true">`
- Package: `app/src/main/java/com/davidlang/vehicleexpensesautomated/`
- Core OCR harness: `ui/util/OcrEngine.kt`, `OcrHarness.kt`, `NativePaddleEngine.kt`, `IcrsMath.kt`
- Production Paddle path: `NativePaddleEngine.PROD_PATH_ID` = `uint8_fp16_u8` (raw uint8 feed → fp16 compute; det uint8 heatmap thresh 0)
- Production models: `app/src/main/assets/paddle/prod_u8fp16/` (`det_*`, `rec_v3_*`, `rec_numeric_*` for armv8 + x86_64 naming)
- Host model scripts (checked in): `app/src/main/assets/paddle/scripts/` (`convert_mono.py`, `optimize_models.sh`, `optimize_mono_int8_models.sh`)
- Paddle runtime binaries: `app/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86_64}/`, `app/libs/PaddlePredictor.jar`
- JNI sizing (prod path): arm64-v8a tailored ~1.6MB; x86_64 slim jni + `libpaddle_light_api_shared.so`; armeabi-v7a fat multi-path lib (interim)
- Volume units: `ui/util/VolumeUnits.kt` — preferred G/L in prefs; `FuelEntry.gallons` stores volume in preferred unit (field name legacy)
- Quick fill UI: `ui/fuel/QuickFillupScreen.kt` — 3-panel A/B/C; multi-photo JSON in `photoUrl` (dash/pump); `ui/components/CameraPreview.kt` zoom callback
- Reports UI: `ui/reports/ReportsScreen.kt` — per-vehicle summary, last-5 full-fill legs, expense categories
- Expenses UI: `ui/expenses/ExpenseEntryScreen.kt`, `ExpenseListScreen.kt` — vehicle dropdown, camera/zoom, edit via `expense/{id}`; DB v8 adds `vendor` + `odometer`
- Data sync: `data/sync/CsvManager.kt`, `GoogleSheetsClient.kt` — quoted CSV fields; expense/fuel column parity
- Experiment UI: `ui/experiment/ExperimentPumpScreen.kt`, `ExperimentAlignmentScreen.kt`
- Pump experiment regression test set (26 images): `PROBLEM_IMAGES_UINT8_U8` in `ExperimentPumpScreen.kt` + Problem Images button
- Pump experiment binPeak/histogram details: `ExperimentPumpScreen.kt` (`PUMP_*`, `BIN_PEAK_BINARIZE_DELTA`, `findPeaksFromHistBins`, `NativeImageUtils.calculateHistogramWithThresholdH` in `NativeImageUtils.cpp`)
- Hybrid stretch helpers: `ui/util/OdometerOcrUtils.kt` — `getClipStretchLowHigh`, `getValleyPeakGrays`, `applyValleyPushWithGrays`
- Data/UI: `Vehicle.kt`, `VehicleViewModel.kt`, `ManageVehiclesScreen.kt`

Update only with new stable location facts valid for future unrelated work. Current-effort details go in the active plan or ENGINEERING_LOG.md.