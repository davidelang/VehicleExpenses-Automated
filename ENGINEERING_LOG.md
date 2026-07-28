# Engineering Activity Log

This log tracks the implementation, refactoring, and deployment activities performed by the Application Engineer session.

## [2026-06-09] - Odometer Setup UI & Filter Optimization
- **Activity:** Merged `improve-vehicle-odo-setup` branch into master.
- **Improvements:**
    - **Pixel-Aware Gap Welding:** Replaced bounding-box segment welding with an iterative $X/Y$ pixel-scan `nativeConnectSegmentsH` in `NativeImageUtils.cpp`. Reliably bridges sub-$0.5 \times SW$ gaps while preserving critical empty spaces (e.g. the center of a '0').
    - **Forensic Pruning:** Optimized JSON alignment reports by excluding large JPEGs and path data for non-winning vehicles. Reduced log drop rates.
    - **Filter Relaxation:** Relaxed rolling filter horizontal pairing restriction to `1.0 * vSW` to catch misaligned digit halves.
    - **UI/UX:** Consolidated crop adjustment buttons in `ManageVehiclesScreen` and added isotropic corner dragging.
- **Files Modified:**
    - `app/src/main/cpp/NativeImageUtils.cpp`
    - `app/src/main/java/com/davidlang/vehicleexpensesautomated/ui/experiment/ExperimentAlignmentScreen.kt`
    - `app/src/main/java/com/davidlang/vehicleexpensesautomated/ui/vehicle/ManageVehiclesScreen.kt`

## [2026-06-08] - Horizontal Wide Filter Precision Fix
- **Activity:** Merged `fix-j-imagefilter` branch into master. Corrected the native Horizontal Wide Filter logic to prevent over-aggressive digit trimming.
- **Improvements:**
    - **Logic Refinement:** Transitioned from aggregate (total row) pixel summation to **contiguous max-run** tracking in `NativeImageUtils.cpp`.
    - **Digit Integrity:** Ensured that valid digits connected to frames (e.g., Set J, image 94) are no longer sliced by the frame-removal filter.
    - **Verification:** Audited all other run-length loops in the native codebase and confirmed they correctly handle contiguity.
- **Files Modified:**
    - `app/src/main/cpp/NativeImageUtils.cpp`

## [2026-06-07] - Kotlin Whitespace & Style Rectification
- **Activity:** Merged `fix-whitespace` branch into master. Performed a repository-wide automated cleanup of Kotlin formatting violations.
- **Improvements:**
    - **Linting Compliance:** Corrected 559 whitespace and style violations across 18 Kotlin files, achieving 0 violations in the `audit_kotlin_whitespace.py` report.
    - **Formatting:** Standardized control keyword spacing (e.g., `if (`), removed trailing whitespace, and collapsed redundant empty lines.
    - **Build Integrity:** Verified build success via `./build_app` and moved the global `works` tag.
- **Files Modified:**
    - `app/src/main/java/com/davidlang/vehicleexpensesautomated/ui/util/OdometerOcrUtils.kt`
    - `app/src/main/java/com/davidlang/vehicleexpensesautomated/ui/experiment/ExperimentAlignmentScreen.kt`
    - (16 other Kotlin source files)

## [2026-06-07] - Odometer OCR Tweak & Filtering Integration
- **Activity:** Merged `tweak-odo-ocr` branch into master. Integrated a major overhaul of the odometer extraction pipeline including advanced native filtering and reporting diagnostics.
- **Improvements:**
    - **Native Filtering:** Implemented robust Horizontal and Vertical wide component filters in `NativeImageUtils.cpp` using contiguous run logic to sever noise connections (frame/grid lines) without damaging characters.
    - **OCR Precision:** Added a 4px bounding box expansion in `OdometerOcrUtils.kt` to compensate for CNN stride loss in the Paddle detector, improving character recall.
    - **Forensics:** Added PBM P4 encoding for 1bpp forensic image exports and updated the HTML report to include "Pre-Rolling" stage snapshots for better debugging of the binarization pipeline.
    - **Protocol:** Enforced the "Spec vs. Reference Precedence" mandate in `GEMINI.md`.
- **Files Modified:**
    - `app/src/main/cpp/NativeImageUtils.cpp`
    - `app/src/main/java/com/davidlang/vehicleexpensesautomated/ui/experiment/ExperimentAlignmentScreen.kt`
    - `app/src/main/java/com/davidlang/vehicleexpensesautomated/ui/util/OdometerOcrUtils.kt`
    - `GEMINI.md`

## [2026-05-30] - Restore Paddle Numeric Parity
- **Activity:** Restored odometer numeric recognition accuracy by swapping the recognizer from the degraded custom numeric model (`rec_numeric_mono`) to the official pre-trained V3 model (`rec_v3_mono`) while keeping the constrained argmax CTC decoding logic.
- **Improvements:**
    - **OCR Engine Stabilization:** Restored Paddle Numeric Greedy accuracy to 91.0% (within 2 images of the 92.4% May 24th peak).
    - **Logic Refinement:** Replaced custom model with `sharedRecognizerV3` paired with `ALLOWED_DIGITS` (`1..10`) and `ALLOWED_DIGITS_DECIMAL` (`1..10` + `93`) index sets, ensuring full flexibility to dynamically change allowed characters on a per-call basis.
- **Analysis of Crop Shifts:**
    - Verified that 5 deskew angles, 47 discovery landmarks, 146 alignment calculations, and 100 ML Kit OCR results changed.
    - Traced the shifts back to **32-pixel Aligned Letterboxing** (padding) in `OdometerOcrUtils.kt` which slightly altered the coordinates fed to ML Kit landmark discovery, causing alignment matrices and crop windows to shift by a few pixels.
- **Files Modified:**
    - `app/src/main/java/com/davidlang/vehicleexpensesautomated/ui/util/NativePaddleEngine.kt`
    - `TODO.md`

## [2026-05-22] - ICRS Coordinate Migration
- **Activity:** Migrated the entire repository from anisotropic normalization to Isotropic Center-Relative Space (ICRS) to solve alignment geometry bugs and support arbitrary aspect ratios.
- **Improvements:**
    - **Architecture:** Implemented ICRS math (Origin at center, short-edge scaling) in `IcrsMath.kt`.
    - **Data Layer:** Executed DB Schema Migration (v6 to v7) adding the `isIcrs` flag. Standardized `VehicleViewModel` for direct ICRS hydration.
    - **Alignment Core:** Refactored `anchorAlign` and `anchorAlignNative` to use a **Unified ICRS Matrix Formulation**, ensuring resolution and aspect-ratio invariance.
    - **ROI Infrastructure:** Standardized `BufferSet.ManagedCrop` to natively consume ICRS coordinates, ensuring 100% geometric parity across rendering paths.
    - **Forensics:** Integrated raw landmark coordinate serialization into the experiment reports for high-fidelity auditing.
- **Major Lesson Learned:**
    - Sequential application of `postScale` and `postTranslate` in Android `Matrix` causes scale-translation coupling. Switched to a unified affine transform via `setValues` to ensure pixel deltas remain absolute regardless of zoom factor.
- **Files Modified:**
    - `app/src/main/java/com/davidlang/vehicleexpensesautomated/ui/util/IcrsMath.kt`
    - `app/src/main/java/com/davidlang/vehicleexpensesautomated/ui/util/ImageAlignmentUtils.kt`
    - `app/src/main/java/com/davidlang/vehicleexpensesautomated/ui/util/BufferSet.kt`
    - `app/src/main/java/com/davidlang/vehicleexpensesautomated/data/model/Vehicle.kt`
    - `app/src/main/java/com/davidlang/vehicleexpensesautomated/ui/vehicle/VehicleViewModel.kt`
    - `app/src/main/java/com/davidlang/vehicleexpensesautomated/ui/experiment/ExperimentAlignmentScreen.kt`

## [2026-04-14] - v45 Logic Restoration & Scoping
- **Activity:** Restored 7-segment cleanup and 180° rotation recovery logic following a git reset.
- **Improvements:**
    - Centralized mapping in `clean7SegmentDigits` (h->4, L->7, E->3, G->9, etc.).
    - Implemented `refineNumericResult` to isolate cleaning to numeric displays only.
    - **Bug Fix:** Removed 7-segment cleaning from the Landmark Discovery phase to ensure raw text is used for vehicle identification anchors.
    - Updated odometer parsing to support 4–7 digits.
- **Files Modified:**
    - `app/src/main/java/com/davidlang/vehicleexpensesautomated/ui/util/OdometerOcrUtils.kt`

## [2026-04-14] - Alignment & Identity Refactoring
- **Activity:** Refactored the experiment harness into a dynamic, registry-based architecture.
- **Improvements:**
    - Created `AlignmentEngine` and `IdentityEngine` interfaces.
    - Implemented `AlignmentRegistry` and `IdentityRegistry` for dynamic algorithm execution.
    - Generalized reporting metadata to support an arbitrary number of engines.
    - **Harness Timing:** Implemented centralized execution timers in `ExperimentAlignmentScreen` to fix 0ms reporting bugs.
    - **Independent Execution:** Decoupled identity algorithms (Feature, Arg, Embedding, Consensus, Tiered, Veto) to run independently for research comparison.
- **Files Modified:**
    - `app/src/main/java/com/davidlang/vehicleexpensesautomated/ui/util/AlignmentEngine.kt`
    - `app/src/main/java/com/davidlang/vehicleexpensesautomated/ui/util/AlignmentRegistry.kt`
    - `app/src/main/java/com/davidlang/vehicleexpensesautomated/ui/util/IdentityEngine.kt`
    - `app/src/main/java/com/davidlang/vehicleexpensesautomated/ui/util/ImageAlignmentUtils.kt`
    - `app/src/main/java/com/davidlang/vehicleexpensesautomated/ui/experiment/ExperimentAlignmentScreen.kt`

## [2026-04-14] - TFLite Stability & High-Res Upgrade
- **Activity:** Upgraded `PaddleOcrEngine` (TFLite) to research-grade models and enforced stability.
- **Improvements:**
    - **File Access:** Implemented `copyAssetToInternal` to avoid "compressed asset" TFLite errors.
    - **Graceful Failure:** Added `try/catch` to interpreter calls to prevent mismatched models from crashing the app.
    - **High-Res Support:** Overwrote 1x1 placeholders with 1280px detection and 640px/80-step/97-class recognition models.
    - **Normalization:** Standardized grayscale normalization in `TfLiteOcrEngine`.
- **Files Modified:**
    - `.gitignore`
    - `app/src/main/assets/tflite/paddle/*`
    - `app/src/main/java/com/davidlang/vehicleexpensesautomated/ui/util/PaddleOcrEngine.kt`
    - `app/src/main/java/com/davidlang/vehicleexpensesautomated/ui/util/TfLiteOcrEngine.kt`

## [2026-04-14] - Paddle-Lite Integration (Initial Phase)
- **Activity:** Integrated native Paddle-Lite v2.14rc assets and initial engine implementation.
- **Improvements:**
    - Integrated optimized `.nb` models and library dependencies.
    - Created `NativePaddleEngine.kt` with 1280px support.
    - **JNI Isolation:** Moved `.so` libraries to assets to prevent fatal "autoloading" segmentation faults while investigating kernel mismatches.
- **Files Modified:**
    - `app/src/main/jniLibs/` (emptied)
    - `app/src/main/assets/libs_backup/` (new)
    - `app/src/main/java/com/davidlang/vehicleexpensesautomated/ui/util/NativePaddleEngine.kt`

## [Legacy Completion Migrated from TODO.md]
- [b860046] Fix: Correct HTML report file size rotation logic to accurately count bytes
- [2f1a582] Deep Trace Phase 2d: Finalize report to include missing global discovery images, method scores/times, and tier reached
- [9a07669] Deep Trace Phase 2c: Implement 5-step OCR trace (Raw, Gray, Bile, CLAHE, OTSU) across 3 engines with timings

## 2026-06-22 - Merge 16k-pages (doc-only)

- Merged branch `16k-pages` into `master` (--no-ff): added `docs/reference/16k-pages-compatibility-notes.md` and TODO.md future-work backlog for deferred native 16KB alignment.
- No app-source or native migration changes in this merge.

## 2026-06-23 - Merged fix-todo TODO reconstruction into master

- Restored reconstructed TODO.md (109 lines) from fix-todo/builds commit b18e93fb via direct checkout on master (commit fb7e4bb4).
- Full git-history union + 2026-06-23 user feedback markers verified (unclipBox rejected, ICRS normalization completed, Dashboard Polarity and Location Lookup Worker pending).
- ./build_app succeeded; master builds tag updated to fb7e4bb4.
- User handling root cause of generate_pr/sync overwriting TODO separately.

## 2026-06-23 - Merged stop-bufferset-realloc into master

- BufferSet capacity-reuse resize: allocatedByteCount tracking, reuse-within-capacity branch (no alloc/delete), grow-drop-old-first with Mat safe redirect.
- Temporary HIST_DIAG logging in ExperimentAlignmentScreen.kt + NativeImageUtils.cpp for alignment histogram crash diagnosis.
- Plans: bufferset-reuse-pixel-allocation-on-resize-no-spike-grow-drop-old-first-20260623-plan.md, add-temporary-histogram-and-buffer-diagnostics-for-alignment-crash-20260623-plan.md.
- Compliance reports: PASS (both).
- ./build_app succeeded; master builds tag updated.

## 2026-06-27 - fix-pump-experiment branch history imported at merge

Imported substantive ENGINEERING_LOG entries from `fix-pump-experiment` (smart merge, append-only). Test/planner noise and failed deploy-restore phase attempts excluded.

- 2026-06-18: ICRS filter unify, red-box storage, odo aligned viz, pump analysis scripts (plan cycle start).
- 2026-06-16: Pump heatmap JNI + per-char recognition probs in JSON (partial execution cycles).
- 2026-06-22: BinPeak stroke hist setSize crash investigation; alignment crash after pump fixes; bufferset fullres resize timing; revert alignment bin-trials hist to crop-based; golden pump redeploy; ALIGN_HIST_DIAG instrumentation plans.
- 2026-06-23: Default buffer 4080x3072; revert 4f4abf16 wrapper test; 32k histogram buffer; integrate stop-bufferset-realloc from master; log Mat headers for hist crash; direct rect walk for H hist; pump unzip flatten + deploy script; clear jsonfrag per row streaming.
- 2026-06-24: Additive ZIP extract on pump + alignment experiment screens.
- 2026-06-25–27: Quick Fill Set G integration; PR review blocker fixes (deskew, zip-slip, BufferSet OOM); de-abstract Quick Fill Set G into OcrHarness.extractQuickFillSetGCostVol.
- Merged at `279a4681` with review `reviews/review-fix-pump-experiment-20260627-v2.md` (conditionally merge-ready).

## 2026-06-28 - tweak-quick-fill branch work (imported at merge, append-only)

Imported substantive entries from `tweak-quick-fill` branch ENGINEERING_LOG. Per-phase micro-entries and duplicate execution-start notes excluded.

- QuickFill 3-panel UI (`QuickFillupScreen.kt`): Save disk icon always in B; portrait B order save-shutter-mode; content-sized C with portrait centering; vehicle field `value=vehicleName` (fixes post-keypad label reversion); D-panel/zoomD removed (zoom via `panelAContent` right/bottom blanks); portrait camera fill (full-width 4:3, volume field 84.dp max).
- `CameraPreview.kt`: `AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY`; `CameraZoomControl` + zoom state observer wired from Quick Fill.
- `DatabaseModule.kt`: migration callback param `database` → `db`; `menuAnchor` deprecation fixes in Settings and ManageVehicles screens.
- History squashed to 4 logical commits (`backup-tweak-quick-fill` = 6163eaf2); PR doc `dev-ai-interaction/PRs/PR-tweak-quick-fill.md`.
- Compliance PASS: layout/vehicle-display, portrait fill/order/centering, volume-width plans.

## 2026-06-28 - Merged tweak-quick-fill into master

- Merged branch `tweak-quick-fill` into `master` (--no-ff, ours-strategy + selective checkout; ENGINEERING_LOG/TODO/project-facts handled per merge protocol).
- App source: QuickFillupScreen, CameraPreview, DatabaseModule, SettingsScreen, ManageVehiclesScreen.
- `./build_app` gate pending this commit.
- PR reference: `dev-ai-interaction/PRs/PR-tweak-quick-fill.md`. User device test required before `works` tag.

## 2026-06-28 - tweak-quick-fill merge build gate

- ./build_app succeeded on merge commit; master builds tag ed1b5220.

## 2026-07-02 - merge test append

## 2026-07-02 - Phase 0: G-family migration research (agent-5 → agent-6)

- Executing plan: dev-ai-interaction/plans/migrate-pump-g-family-hybrids-from-agent5-clean-history-pr-20260702-plan.md
- agent-6 at cb5b43aa [improve_pump_calculated_expand]; agent-5 at 3ee025ec [improve-landmark-selection]
- Confirmed agent-6 legacy: SET_G_VERT_FACTORS = (1..8).map { it/10f }; no G-/G-- consts; ExperimentPumpScreen flows end at procG; index dispatch flowProcessors[i]
- agent-5 has 4 semantic commits (40fc0929, 5fa1363b, fb769fa4, 3ee025ec) plus 5 duplicate dispatch-fix commits to avoid importing
- Diff stat cb5b43aa..3ee025ec: ExperimentPumpScreen +505/-117, PumpCostVolUtils +11/-, OcrHarness +6/-

## 2026-07-02 - G-family migration complete (agent-5 → agent-6)

- Phases 1-4 executed on improve_pump_calculated_expand
- Key files match agent-5 3ee025ec: PumpCostVolUtils (SET_G/G-/G--), OcrHarness (Quick Fill G--), ExperimentPumpScreen (makeGProc, list-based dispatch, H/I hybrids)
- Prerequisite: OdometerOcrUtils hybrid stretch/valley helpers ported for procH/procI
- No agent-5 duplicate dispatch-fix commits imported

## 2026-07-02 - Disable pump experiment Sets A/B/C/F/H

- Executed disable-experiment-sets-a-b-c-f-h-20260702-plan.md on improve_pump_calculated_expand.
- flows + flowProcessors now run only D, E, G, G-, G--, I (calculated paths).
- blueMethodPrefixes trimmed (B/C/F/H removed); procA/B/C/F/H lambdas retained as dead code for easy re-enable.

## 2026-07-02 - Prepare PR for pump experiment: G-family sizes (G/G-/G--), disable A/B/C/F/H, hybrids kept, quick-fill on G--

## 2026-07-02 - PR preparation complete for improve_pump_calculated_expand. Generated PR doc in dev-ai-interaction/PRs/. History has phased commits for disable + G-family. Next: review on master agent.

## 2026-07-02 - improve_pump_calculated_expand merge build gate

- ./build_app succeeded on master after merge commit 5b3cee47 (JDK 17 required in this environment).
- master builds tag updated.

## 2026-07-02 - pump-vert-lists-max7

- Expanded SET_G_VERT_FACTORS to 7 factors; G- to 5; G-- to [0.3, 1.2].
- Aligned ExperimentPumpScreen regularGVert/DVert/EVert lists.

## 2026-07-10 - Merge int8-paddle-processing into master

- Merged branch `int8-paddle-processing` (rewritten history from `9aac4d0d`); production OCR path **uint8_fp16_u8** only.
- Shipped `assets/paddle/prod_u8fp16/` models; removed legacy multi-path assets and PrecCampaign harness.
- arm64-v8a: model-tailored `libpaddle_lite_jni.so` (~1.6MB). x86_64: slim jni + `libpaddle_light_api_shared.so`. armeabi-v7a: fat multi-path jni (interim).
- Restored host scripts under `app/src/main/assets/paddle/scripts/`; added `optimize_mono_int8_models.sh`.
- Updated `docs/specs/HOST_PADDLE_USE.md`, `PADDLE_BUILD.md`, `PADDLE_PR_DESCRIPTIONS.md`.
- Added `PROBLEM_IMAGES_UINT8_U8` (26-image regression/gain set) + Problem Images button on pump experiment screen.
- Merge hygiene: ENGINEERING_LOG append-only import; TODO smart union; `project-facts.md` full rewrite; fork-drift files reconciled to master (LandmarkDebugDialog, gradle.properties, OdometerOcrUtils, ManageVehiclesScreen).

## 2026-07-11 - Merge operational-improvements into master

- Merged branch `operational-improvements` (12 logical commits from `9aac4d0d`); int8 paddle stack on master preserved.
- **Quick Fill:** media permission (camera then media); loud photo save failures; multi-photo JSON (`dash`/`pump`) on save; auto `isPartialFill`; clear fields after save; portrait camera panel A.
- **Reports:** per-vehicle summary with interim-gallon MPG legs; last-5 full fills; expense totals/categories; `docs/reference/REPORTS_METRICS.md`.
- **Expenses:** vehicle dropdown; QuickFill-style camera/zoom; DB v8 `vendor`+`odometer`; list→edit `expense/{id}`; zoom-pan photo; metadata preserved on edit.
- **Assets/docs:** lightened launcher icon safe-zone padding; `docs/reference/APP_LAUNCHER_ICON.md`.
- **Review fixes:** CSV quote/escape + parse; Sheets column parity; `VolumeUnits.kt` preferred-unit storage + Settings lock; edit flash/save await; permission sequencing.
- Merge hygiene: ENGINEERING_LOG append; TODO smart union; `project-facts.md` full rewrite.
- Manual device testing deferred to user post-merge.

## 2026-07-12 - Merge improve_pump_calculated_expand into master

- Dual-device shared vert lists (D/E/G/G-/G--/Set I) from 2026-07-11 Phone+Emulator retest
- Quick Fill pump path now uses G-- k=4 [0.1,0.3,0.4,1.1]
- Pump experiment JSON/HTML reports include Build.MODEL device field
- Branch fast-forward merge @ 191e284b

## 2026-07-12 - post-sync-vehicle-definition-rehydration plan

- Executing post-sync-vehicle-definition-rehydration-20260712-plan.md (Phases 1-7)
- Phase 1: VehicleRepository updateVehiclePreservingTimestamp (no stamp on updatedAt)

## 2026-07-12 - post-sync-vehicle-definition-rehydration Phase 2

- PhotoBackupCoordinator: vehicle download/remint/manifest upload uses updateVehiclePreservingTimestamp

## 2026-07-12 - post-sync-vehicle-definition-rehydration Phase 3

- SpreadsheetSyncCoordinator: mergeVehicleLww with definition overlay (crops, landmarks, cloudManifest, local paths)

## 2026-07-12 - post-sync-vehicle-definition-rehydration Phase 4

- PhotoBackupCoordinator.downloadMissingVehicleAssets; SpreadsheetSyncCoordinator post-sync hook after vehicles tab

## 2026-07-12 - post-sync-vehicle-definition-rehydration Phases 5-6

- ManageVehiclesScreen: rehydrate on definition snapshot change; full download hydrate; auto discoveryResults from landmark JSON

## 2026-07-12 - post-sync-vehicle-definition-rehydration Phase 7 complete

- project-facts: post-sync vehicle definition merge, no-stamp asset writes, Manage Vehicles rehydrate, Quick Fill reads Room after sync
- All 7 phases built and tagged

## 2026-07-12 - background-sync-worker-hilt-reliability plan execution start

- Plan: background-sync-worker-hilt-reliability-20260712-plan.md
- Goal: fix SyncWorker/PhotoBackupWorker Hilt instantiation (NoSuchMethodException on cold start)
- Phase 1: add androidx hilt-compiler KSP if missing

## 2026-07-12 - background-sync-worker-hilt-reliability Phase 2

- Removed WorkManagerModule double-init (Configuration.Provider is sole WM config path)
- Manifest: narrow WorkManagerInitializer remove instead of entire InitializationProvider

## 2026-07-12 - background-sync-worker-hilt-reliability Phase 3

- Inject SyncManager + PhotoBackupManager in Application (no manual SyncManager(this))
- Cold start: schedule periodic from destination only; removed triggerImmediateSync on launch

## 2026-07-12 - background-sync-worker-hilt-reliability Phase 4

- Verified existing LaunchedEffect save paths call rescheduleBackgroundSync/Backup on spreadsheet and photo config screens (no code change required)

## 2026-07-12 - background-sync-worker-hilt-reliability Phase 5

- SyncWorker + PhotoBackupWorker: needsRemoteConsent returns Result.failure() (no tight retry loop)

## 2026-07-12 - background-sync-worker-hilt-reliability Phase 6 complete

- project-facts: Hilt WM worker KSP requirement, Configuration.Provider sole init, cold-start schedule-only
- End forensic: SyncWorker_AssistedFactory + PhotoBackupWorker_AssistedFactory generated; no WorkManagerModule; Application injects SyncManager/PhotoBackupManager

## 2026-07-12 - sheet-oldest-first-and-incremental-sync plan execution

- Executing plan dev-ai-interaction/plans/sheet-oldest-first-and-incremental-sync-20260712-plan.md (Phases 1-9)
- Goals: preserve photo paths on sheet merge, FULL vs PENDING_ONLY photo modes, 15-min background interval, oldest-first + incremental sheet writes

## 2026-07-12 - sheet-oldest-first-and-incremental-sync plan complete

- Phases 1-9: photo path preserve + rebind, FULL/PENDING_ONLY photo modes, frequencyMinutes (15m min), oldest-first incremental sheet writes
- Tag: fix_syncing_and_settings/builds at 2483fc62+ (Phase 9 project-facts)

## 2026-07-12 - cloudmanifest-multi-dest-pending-and-remint plan start

- Executing dev-ai-interaction/plans/cloudmanifest-multi-dest-pending-and-remint-20260712-plan.md (5 phases, no Room migration)

## 2026-07-12 - cloudmanifest-multi-dest Phase 1 complete

- Upload pending paths in PhotoBackupCoordinator now use hasEntryForDest (not hasRole fallback)

## 2026-07-12 - cloudmanifest-multi-dest Phase 2 complete

- Added CloudManifest.bindLocalDestAfterDownload (add-only merge); post-download uses merge not remint rewrite

## 2026-07-12 - cloudmanifest-multi-dest Phase 3 complete

- PENDING_ONLY rebinds all vehicle refs before pending recount/early exit; pending breakdown logging

## 2026-07-12 - cloudmanifest-multi-dest Phase 4 complete

- stripObsoleteRoles removes vehicle_landmarks; merge/upload paths ignore landmarks role

## 2026-07-12 - cloudmanifest-multi-dest plan complete (Phases 1-5)

- Multi-dest upload pending (hasEntryForDest), bindLocalDestAfterDownload, PENDING_ONLY rebind, strip landmarks; project-facts updated

## 2026-07-12 - Vehicle rename fuel sheet tab migrate

- Executing plan dev-ai-interaction/plans/vehicle-rename-fuel-sheet-tab-migrate-20260712-plan.md
- Phases 1-6: GoogleSheetsClient list/rename tabs, orphan fuel tab detection, syncFuelTabs rename/merge, optional hint prefs, CSV verify + project-facts
- Baseline tag: fix_syncing_and_settings/builds

## 2026-07-12 - Vehicle rename fuel sheet tab migrate (complete)

- Phases 1-6: GoogleSheetsClient listSheetTitles/renameTab; orphan fuel tab detect by Vehicle Sync ID; sync-time rename or merge+delete; FuelTabRenameHintStore for offline renames; CSV verified flat Fuel_entries.csv unaffected; project-facts updated

## 2026-07-12 - CSV export/import sheet parity plan

- Starting execution of csv-export-import-sheet-parity-20260712-plan.md (Phases 1-6)
- Goals: sheet headers + row helpers, per-vehicle fuel CSVs, legacy import, Vehicle Sync ID filled, oldest-first sort, deleted rows

## 2026-07-12 - CSV export/import sheet parity complete

- Phases 1-6: CsvManager uses GoogleSheetsClient headers + row helpers; per-vehicle Fuel - {name}.csv; Expenses.csv; Vehicle Sync ID filled on export; header-based import + legacy filenames; oldest-first sort + deleted rows; project-facts updated

## 2026-07-12 - multi-currency-row-persist-20260712-plan

- Phase 0 start: persist currency on fuel/expense rows (Room v10→v11); plan dev-ai-interaction/plans/multi-currency-row-persist-20260712-plan.md

## 2026-07-12 - multi-currency-row-persist Phase 1 PASS

- Room v11: currency TEXT on fuel_entries + expense_entries; MIGRATION_10_11; tag e860282b

## 2026-07-12 - multi-currency-row-persist Phase 2 PASS

- CurrencyCodes.kt: fromSymbolOrCode, display/format helpers; tag 4870a457

## 2026-07-12 - multi-currency-row-persist Phase 3 PASS

- QuickFill + ExpenseEntry + FuelEntry save persist normalized currency; tag 659cc136

## 2026-07-12 - multi-currency-row-persist Phase 4 PASS

- FUEL/EXPENSE_HEADERS Currency column; fuelToRow/rowToFuel/expenseToRow/rowToExpense; tag 05535ae2

## 2026-07-12 - multi-currency-row-persist Phase 5 PASS + plan complete

- Reports/lists row currency; mixed aggregates per-currency; $/mi n/a when mixed; tag ced04b24

## 2026-07-12 - Multi Google destinations (plan multi-google-destinations-20260712)

- Executing plan dev-ai-interaction/plans/multi-google-destinations-20260712-plan.md
- Phases 1-7: store multi upsert, spreadsheet/photo UI lists, coordinators loop all enabled dests, workers schedule min interval + strictest constraints

## 2026-07-13 - rclone photo storage backend (plan: rclone-photo-storage-backend-20260713-plan.md)

- Execution start: wire full librclone AAR; PhotoSyncBackend port; PhotoProvider.RCLONE; RcloneRuntime; upload/download via rclone RPC; coordinator dispatch for all enabled rclone dests.
- Depends on multi-Google destinations (done). Supersedes WebDAV/SAF-first path.

## 2026-07-13 - rclone photo storage backend complete (Phases 0-8)

- Shipped app/libs/librclone.aar (full backends ~91MB); removed lite.
- PhotoSyncBackend port: GoogleDrivePhotoBackend + RclonePhotoBackend via gomobile RPC.
- PhotoProvider.RCLONE + configJson + SAF rclone.conf import UI.
- Coordinator dispatches all enabled Drive + rclone dests; manifest provider=rclone; fileId=object key.
- Startup smoke: RcloneLoader + rcloneInitialize + core/version.

## 2026-07-13 - Google dest browse/pick/create UI (google-dest-browse-pick-create-ui-20260713-plan)

- Executing Phases 1-6: remove Sheet ID field; URL+browse; create-in-dialog for Sheets and Drive folders.

## 2026-07-13 - Google dest browse/pick/create UI complete

- Phases 1-6: Sheet URL-only + browse/create-in-dialog; Drive folder URL + browse; GoogleDriveBrowserClient + GoogleDriveBrowserDialog; project-facts updated.

## 2026-07-13 - rclone config create UI (plan rclone-config-create-ui-20260713)

- Starting phased execution per plan: RPC wrappers, provider catalog, list/create/edit/delete remotes UI, OAuth handoff, polish.
- Builds on import-only rclone photo backend; goal is in-app remote create/manage without desktop conf import.

## 2026-07-13 - rclone config create UI phases 3-6

- UI: list remotes dialog (browse icon), create/edit wizard, delete remote; import demoted to advanced.
- OAuth: Chrome Custom Tabs for auth URLs from non-interactive config/create steps.
- RcloneRuntime: config/get for edit type; PhotoBackupViewModel exposes list/create/update/delete.

## 2026-07-13 - photo-backend-onedrive-and-other-label plan

- Start Phase 1: rename user-facing Rclone→Other; migrate json rclone→other on load

## 2026-07-13 - photo-backend-onedrive-and-other-label plan Phase 1

- PhotoProvider.OTHER (migrate json rclone→other on load); user-facing Rclone→Other strings

## 2026-07-13 - photo-backend-onedrive-and-other-label plan Phases 2-7 complete

- PhotoProvider.ONEDRIVE + MSAL sign-in (MicrosoftOneDriveAuth.kt)
- Managed rclone onedrive remote (RcloneOneDriveSetup.kt); OneDrive simplified form
- Other picker (renamed from Rclone); Phase 6 folder browse skipped
- project-facts.md updated

## 2026-07-13 - Execute photo-backend-onedrive-and-other-label plan

- Execution start: S3 first-class, 4-way picker, universal test contract, Other kind groups, backend prune

## 2026-07-13 - Phase E: build_photo.sh curated backend prune script

- Added dev-ai-interaction/rclone-build/build_photo.sh (sed-based curated imports)
- Docker AAR rebuild attempted in background; app still ships existing librclone_full.aar until photo AAR lands
- Catalog COMPILED_OUT_TYPES denylist filters UI regardless of binary

## 2026-07-13 - Phase F: photo backend plan polish complete

- project-facts.md: S3, 4-way picker, test contract, kind groups, build_photo.sh
- TODO.md updated for full plan scope delivery

## 2026-07-13 - librclone photo AAR ship: execution start

- Phase 1: harden build_photo.sh (Python import rewrite + post-patch validation)
- Baseline full AAR: 90973260 bytes (~87MB) app/libs/librclone.aar

## 2026-07-13 - Phase 1: build_photo.sh hardened

- Replaced fragile sed with Python import-block rewrite
- Post-patch validation: fail if backend/all or COMPILED_OUT imports remain
- Curated import list aligned with RcloneProviderCatalog.COMPILED_OUT_TYPES
- Docker invocation documented in script header
- Mirror copy to SCRIPT_DIR/librclone_photo.aar when output/ sibling exists

## 2026-07-13 - Phase 2: photo AAR Docker build complete

Size gate PASSED (photo strictly smaller than full):
| Metric | Before (full) | After (photo) |
| AAR | 90973260 bytes (87M) | 78966060 bytes (76M) |
| arm64 libgojni.so | 92569568 bytes (89M) | 80240896 bytes (77M) |
| armeabi-v7a libgojni.so | 87263660 bytes (84M) | 75381820 bytes (72M) |
| x86_64 libgojni.so | 98095168 bytes (94M) | 85201248 bytes (82M) |
Photo AAR = 86% of full (not ~100% — prune applied).

## 2026-07-13 - Phase 3: ship photo AAR + app hygiene

- Shipped output/librclone_photo.aar to app/libs/librclone.aar
- Removed RcloneRuntime.smokeCreateLocalRemote (local compiled out)
- Removed dead chunker from RcloneProviderCatalog more map
- Removed local wizard special-case in RcloneRemoteDialogs.buildParameters
- Updated project-facts.md with photo-curated AAR sizes

## 2026-07-13 - Phase 4: absence verification + completion

Absence verification (arm64 libgojni.so strings):
- backend/hdfs, memory, local, http, googlephotos: 0 hits each
- Keep-set: s3=519, onedrive=561, webdav=330, azureblob=372 hits

build_app SUCCESS; tag fix_syncing_and_settings/builds @ 52047138

## 2026-07-13 - Photo backend cleanup execution start

- Phase 1: OneDrive coordinator refresh before test/sync/upload paths

## 2026-07-13 - Photo backend cleanup Phase 2

- S3 Test: skip setupS3Remote when managed conf exists and form secrets blank

## 2026-07-13 - Photo backend cleanup Phase 3

- Removed hubic/amazonclouddrive from RcloneProviderCatalog
- OneDrive session-expired message in RclonePhotoBackend test path

## 2026-07-13 - Photo backend cleanup Phase 4 complete

- project-facts: OneDrive refresh on coordinator sync paths
- Marked photo-backend cleanup, onedrive-label, and librclone-ship plans COMPLETE

## 2026-07-13 - Spreadsheet concurrent providers execution start

- Baseline tag: 2b1edcca
- Plan: spreadsheet-concurrent-providers-product-surface-20260713-plan.md
- Phases 1-7 execution

## 2026-07-13 - TabularShareApi phases 1-7 complete

- Package data/sync/tabular/: TabularShareApi, TabularSchema, backends (Google/Excel/EtherCalc/CsvZip/Other stub)
- SpreadsheetSyncCoordinator wired through TabularShareApi (no GoogleSheetsClient import)
- CsvManager thin wrapper over exportCsvZip/importCsvZip
- SpreadsheetSyncScreen 4-way picker (Sheets/Excel/EtherCalc/Other)
- Build tag: fix_syncing_and_settings-start-183-g08306f9e

## 2026-07-13 - Phase 1: self-host docs promoted

- Copied sandbox research to docs/reference/self-host/ (INDEX, README, photos/*, tabular/*, vendor-links)
- README edited for tracked path (removed sandbox-only references)
- USER_GUIDE.md: pointer to self-host/INDEX.md

## 2026-07-13 - Phase 2: SyncSetupDocs + HelpScreen

- Added ui/util/SyncSetupDocs.kt (BASE URL, index/photo/tabular helpers, open with toast fallback)
- HelpScreen: Self-hosted sync setup section with tappable index link
- Build tag ee03e6d0

## 2026-07-13 - Phase 3: photo setup help links

- PhotoBackupScreen: MinIO/S3 setup help + Other footer link to photos README
- RcloneRemoteDialogs: self-host kind guide + per-type Setup help (webdav/sftp/ftp/smb/seafile)

## 2026-07-13 - Phase 4: spreadsheet help links

- SpreadsheetSyncScreen: Self-hosted spreadsheet servers link to tabular README
- EtherCalc form: Setup help link to tabular/ethercalc.md

## 2026-07-13 - Phase 5: self-host docs handoff

- project-facts.md: docs/reference/self-host/ + SyncSetupDocs.kt location
- Sandbox plan marked COMPLETE
- Final build tag pending

## 2026-07-13 - Self-host sync docs execution complete

- All 5 phases delivered; final build tag d3292fc4 (fix_syncing_and_settings/builds)
- Operator test: Help index, Photo Other/S3 links, Spreadsheet hub + EtherCalc links

## 2026-07-13 - Expense multi-vehicle + multi-photo schema execution

- Execution start: 6-phase plan expense-multi-vehicle-and-multi-photo-schema-20260713-plan.md
- Baseline: tag d3292fc4, DB v11, branch fix_syncing_and_settings
- Phase 1: ExpensePhotoUrls helper (parse/format/isMulti/listUris, max 20 pages, role + remote file name helpers)

## 2026-07-13 - Expense multi-vehicle + multi-photo schema execution (cont.)

- Phase 2: Room v12 vehicleSyncIdsJson + MIGRATION_11_12 backfill; DAO multi-vehicle query; repository single-vehicle JSON on save

## 2026-07-13 - Expense multi-vehicle + multi-photo schema execution (cont.)

- Phase 3: TabularSchema Vehicle Sync IDs column + multi photo/vehicle row encode/decode; GoogleSheetsClient delegates to TabularSchema

## 2026-07-13 - Expense multi-vehicle + multi-photo schema execution (cont.)

- Phase 4: SpreadsheetSyncCoordinator + CsvZip resolve multi vehicle sync ids; primary vehicleId from first syncId; export via TabularSchema expenseToRow

## 2026-07-13 - Expense multi-vehicle + multi-photo schema execution (cont.)

- Phase 5: PhotoBackupCoordinator multi-page expense upload/download/pending; roles expense_receipt / expense_receipt_k

## 2026-07-13 - Expense multi-vehicle + multi-photo schema execution (complete)

- Phase 6: project-facts + TODO updated; sandbox plan marked COMPLETE
- All 6 phases built; DB v12; schema ready for future multi-vehicle/multi-page expense UI

## 2026-07-13 - Execution: spreadsheet Other Tier A backends

- Started execution of spreadsheet-other-tier-a-backends plan (Phases 1-8, minimum 1-4).
- Baseline: fix_syncing_and_settings/builds (2178642c).

## 2026-07-13 - Spreadsheet Other Tier A backends complete

- Phases 1-6 delivered: foundation + Baserow + NocoDB + PocketBase + Supabase + Airtable.
- Phase 7 deferred: Firebase, Zoho Sheet, OnlyOffice, Collabora (picker shows coming soon).
- Phase 8: project-facts + stub shrink + cheatsheet updates.

## 2026-07-13 - Expense multi-page pending/download consistency execution

- Execution start: fix FULL sync download gate and computePendingBreakdown in PhotoBackupCoordinator.kt (plan expense-multi-page-pending-download-consistency-20260713)

## 2026-07-13 - Execution: deferred Other spreadsheet backends (Phases 0-8)

- Started execution on branch `fix_syncing_and_settings`; baseline `fix_syncing_and_settings/builds` (d6263042).
- Phase 0 spike: OnlyOffice/Collabora NO-GO for headless cell/range API (see sandbox `research/self-host-sync-docs/onlyoffice-collabora-spike-20260713.md`).
- Proceeding with Firebase (Firestore REST) + Zoho Sheet (OAuth + grid API); Phases 6-7 skipped.

## 2026-07-13 - Deferred Other spreadsheet backends — execution complete

- Phase 0: OnlyOffice/Collabora NO-GO (no headless cell/range REST); Phases 6-7 skipped.
- Phases 1-2: `FirebaseTabularClient` + `FirebaseTabularBackend` (Firestore REST, RowDb pattern).
- Phases 3-4: `ZohoSheetAuth` + `ZohoSheetClient` + `ZohoSheetTabularBackend` (OAuth + grid API).
- Phase 5: Catalog `implemented=true` for Firebase + Zoho; setup forms + cheatsheets + INDEX/VENDOR_LINKS.
- Phase 8: `DeferredTabularBackendStub` scoped to OnlyOffice/Collabora only; `project-facts.md` updated.
- Build tag: `fix_syncing_and_settings/builds` → `fix_syncing_and_settings-start-200-g5792a689`.

## 2026-07-13 - Git history cleanup + PR prep — execution start

- Branch `fix_syncing_and_settings`; baseline 200 commits (`afe2d307..5792a689`).
- Mission: squash to ~6-10 logical commits, backup tag, force-with-lease push, generate PR doc.
- Deleted junk `eng_log_complete.txt`; committing pending ENGINEERING_LOG hygiene.

## 2026-07-13 - Git history cleanup + PR prep — execution complete

- Squashed `master..HEAD` from ~201 commits to **9** logical commits (soft-reset thematic rebuild).
- Backup tag: `backup-fix_syncing_and_settings` @ `5a411a0881592668b4411ad1a66c7f96d2278bf0`.
- Cleaned HEAD: `68106bf177e21855b8c9cf5892060057101f887b`; tree diff vs backup **empty**.
- Build: `./build_app` SUCCESS; tag `fix_syncing_and_settings-start-9-g68106bf1`.
- Force push: **FAILED** (`git@github.com: Permission denied (publickey)`); user must push manually: `git push --force-with-lease origin fix_syncing_and_settings`.
- PR artifact: `dev-ai-interaction/PRs/PR-fix_syncing_and_settings.md`.

## 2026-07-13 - Post-PR sync fixes execution start

- Approved plan: post-pr-review-sync-fixes-20260713-plan.md
- Baseline tag: 68106bf1 on fix_syncing_and_settings
- 9 phases: docs, bugs #1-14, frequency UI, backfill splash, failure UX

## 2026-07-13 - Post-PR sync fixes execution complete

- All 9 phases delivered; final tag fix_syncing_and_settings/builds @ 46f7fabe
- Docs: USER_GUIDE, SYNC_BEHAVIOR.md, self-host README recovery note
- Bugs #1-14 + frequency hours UI + backfill splash + SyncFailureStore UX

## 2026-07-13 - fix_syncing_and_settings merged into master (merge hygiene completion)

- Integrated branch fix_syncing_and_settings at edad48d3 onto local master (pre-merge afe2d307).
- Special-file protocol post-hoc: ENGINEERING_LOG via wrapper; project-facts consolidated; TODO smart union + merge line.
- Orchestration gap plan: dev-ai-interaction/plans/orchestration-master-startup-merge-instructions-20260713-plan.md.

## 2026-07-13 - fix_syncing_and_settings merge build gate

- ./build_app succeeded on master @ b04c9e68 (JDK 17); builds tag updated.

## 2026-07-13 - Dead code inventory Phase 1 start

- Approved plan: dev-ai-interaction/plans/dead-code-aggressive-review-20260713-plan.md (Phase 1 only)
- Branch: code-cleanup; deliverable: dev-ai-interaction/research/dead-code-inventory-20260713.md
- No tracked source deletions this phase

## 2026-07-13 - Dead code inventory Phase 1 complete

- Deliverable: dev-ai-interaction/research/dead-code-inventory-20260713.md
- 22 DELETE-CANDIDATE files (~900 Kotlin lines) across orphan UI, utils, legacy data/storage
- 1 REVIEW item: ConflictResolutionScreen (TODO backlog — defer delete)
- 4 DEFER-EXPERIMENT-ONLY files; production pump/Quick Fill path untouched
- No tracked source changes; STOP per plan awaiting Phase 2+ approval

## 2026-07-13 - Dead code Phase 2 start

- Approved: orphan UI stub deletion per dead-code-inventory-20260713.md
- Deleting 10 unwired UI files; keeping ConflictResolutionScreen (REVIEW/defer)

## 2026-07-13 - Dead code Phase 2 complete (build env strike)

- Commit 159c7ff6: deleted 10 orphan UI stubs (-559 lines); ConflictResolutionScreen kept
- kspDebugKotlin OK; full build_app FAILED (NDK libc++_shared.so permission denied for ai-coder)
- Awaiting NDK perm fix or dlang build before Phase 3 gate

## 2026-07-13 - Phase 2 build gate passed (NDK + JDK 17)

- NDK libc++_shared.so now readable (dlang:ai-code 664); configureCMake + buildCMake OK
- Default JDK 25 lacks JAVA_COMPILER for Hilt; build succeeded with JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
- BUILD SUCCESSFUL; tag code-cleanup/builds @ ba00d3c6; describe code-cleanup-start-2-gba00d3c6

## 2026-07-13 - Gradle JDK 17 project config fix

- gradle.properties: org.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64 (daemon must be full JDK not JRE-only default-java)
- settings.gradle.kts: foojay-resolver-convention 1.0.0 for jvmToolchain(17) provisioning
- Verified ./gradlew :app:hiltJavaCompileDebug without JAVA_HOME env
- Plan updated: Phase 5 compiler warnings/hygiene (not TODO.md); env issues = report not workaround

## 2026-07-13 - Dead code Phase 3 start

- Orphan utils: DiscoveryOcrUtils, ImageHashUtils, LocationUtils, MLKitMonoStrategy
- Prune dead OcrEngineStrategy, HarnessRunDef, ReportCollector from OcrHarness.kt

## 2026-07-13 - Dead code Phase 3 complete + Phase 4 start

- Phase 3: commit 4c58e0e0 (-180 lines); tag code-cleanup/builds updated
- Phase 4: legacy data/storage dead code + comment hygiene

## 2026-07-13 - Dead code Phases 3-4 complete

- Phase 3: 4c58e0e0 orphan utils + OcrHarness dead types (-180)
- Phase 4: c77e04b3 legacy data/storage dead code (-179); ExpenseEntryScreen comment fix
- Remaining plan: Phase 5 warnings, Phase 6 docs, Phase 7 handoff

## 2026-07-13 - Dead code Phase 5 start: compiler warnings and deprecation hygiene

## 2026-07-13 - Phase 5-6: compiler warning fixes + docs alignment (dead-code-aggressive-review)

## 2026-07-13 - Dead code Phases 5-7 complete (plan finished)

- Phase 5: commit 4a3233f7 — ExpenseEntryScreen always-true branch; SyncDestinationModels frequencyHours suppress; PhotoBackupScreen Icons.AutoMirrored + menuAnchor(MenuAnchorType); RcloneRemoteDialogs menuAnchor overload; GoogleDriveAuth/GoogleSheetsAuth @file:Suppress(DEPRECATION) pending Credential Manager migration
- Phase 6: NAVIGATION_MAP (start=quickfill, pump experiment, unwired ConflictResolution); API.md pruned deleted symbols; ARCHITECTURE.md PhotoBackupCoordinator path
- Phase 7: repo grep — zero hits for 22 deleted files/symbols in production Kotlin; BUILD SUCCESSFUL; tag code-cleanup/builds @ 4a3233f7
- Net removals Phases 2-4: ~918 Kotlin lines; ConflictResolutionScreen + experiment UIs retained per plan
- Inventory final disposition: dev-ai-interaction/research/dead-code-inventory-20260713.md

## 2026-07-13 - Code review fixes: auth adapter, frequency migration, expense mode, schedule UI, DB v13 schema cleanup

## 2026-07-13 - Settings screen decomposition (#5): split Photo/Spreadsheet edit forms, shared list/card/picker/scaffold UI

## 2026-07-13 - Git history cleanup + PR prepared (code-cleanup)

- Squashed 13 messy commits to 8 logical commits; backup tag backup-code-cleanup @ 85672713
- Cleaned HEAD: d2685eab; tree diff vs backup empty; BUILD SUCCESSFUL
- PR artifact: dev-ai-interaction/PRs/PR-code-cleanup.md
- Ready for Master review via run-grok-master

## 2026-07-14 - code-cleanup merged into master

- Merge commit ca671196 via temp worktree merge-temp-code-cleanup (ENGINEERING_LOG preserved; branch entries appended via wrapper)
- Master forensic review approved; DB v13 + settings decomposition + auth/frequency fixes accepted
- Post-merge: project-facts consolidated; TODO dead-code item closed; build_app gate pending

## 2026-07-14 - code-cleanup merge build gate passed

- ./run-as-primary ./build_app SUCCESS on master @ f0446cd7; builds tag updated
- First ai-coder build hit processDebugJavaRes chmod 770 on foreign-owned app/build; dlang euid via run-as-primary succeeded

## 2026-07-14 - Sync/camera cleanup batch start

- Plan: dev-ai-interaction/plans/sync-camera-cleanup-20260714-plan.md
- Phase 1: multi-dest photo sync — SyncDestinationStore context-aware isPhotoConfigured/enabledPhoto; expense download uses per-dest ctx in loop

## 2026-07-14 - Sync/camera cleanup batch complete

- Phases 1-5: multi-dest photo sync, fuel sort+jitter, VM cleanup, experiment gate, CameraResolutionPicker, Quick Fill instruction line
- Tag: full-code-review1/builds @ 598d72f9
- Infra launcher changes stashed (stash: infra launcher sync touch) — not merged

## 2026-07-14 - Overnight compile verification (deploy deferred)

- ./build_app (no new commits): BUILD SUCCESSFUL 51s; tag full-code-review1/builds → f7efbd89 (housekeeping atop infra 28d83ea3 + feature batch through 54869259).
- ./gradlew :app:assembleDebug --quiet: succeeded (second compile path).
- Prior emulator UI smoke (5556/5554): Quick Fill instruction line, experiment gate, spreadsheet sync toast, reports, settings — pass; no experiment runs started.
- APK: app/build/outputs/apk/debug/app-debug.apk @ describe full-code-review1-start-10-gf7efbd89.
- Paused: awaiting user deploy (./deploy or gradlew installDebug per branch exception), then full device test pass on wake.

## 2026-07-14 - Sync UX polish and PR prep (full-code-review1)

- **Activity:** Completed sync/camera review branch UX fixes; cleaned git history; prepared local PR.
- **Sync UX:** Live per-destination status (no toasts); red error text; multi-line per-dest summaries; Drive photo upsert prevents duplicate uploads; experiment drawer toggle without restart.
- **Verification:** Pixel 6 manual 3-dest spreadsheet sync clean in logcat; user confirmed experiment toggle and status line.
- **Process:** Logical commit history + `backup-full-code-review1` tag; `./generate_pr.sh` for Master review.

## 2026-07-15 - Pump classifier sim Step 0.5 (baseline parity)

- Fixed classify_baseline to faithful Kotlin port: enrich_candidates_kotlin uses cleanDecimal on digits, full fractional dp length, no v<=0.2 filter, label-based cand identity.
- --verify-baseline: 0 mismatches on reference JSON; baseline replay matches device G/I metrics (GAP 34/38).
- evaluate() and pool_recovery_rate: per-axis ? handling aligned with pump_deep_analysis.
- CLI: --classifier global_pair_v1 (improved alias); classify_improved preserved as competitor.
- global_pair_v1 replay GAP: Set G 12, Set I 15 (vs device 34/38).

## 2026-07-15 - spatial_first v1 sim (not goal-complete)

- User correctly noted goal incomplete without new-algorithm sim results.
- Implemented classify_spatial() + --classifier spatial + --compare-all GAP table.
- Baseline gate still 0 mismatches.
- Replay on reference JSON (individual-axis GAP):
  - device/baseline: G 34, I 38
  - global_pair_v1: G 12, I 15 (best)
  - spatial_first v1: G 52, I 44 (worse than device; needs Steps 2-3 tuning)
- Known failure mode: truncated junk vol picks (e.g. 68 vs 13.617).

## 2026-07-15 - spatial_first within-cluster ranking v2

- Investigated spatial_first failures: ~70% cross-cluster junk pairing (68 vol), ~30% within-cluster integer vs decimal.
- Added score_cluster_reading signals: decimal preference, digit count, prob, consensus, cost/vol US shape bonuses, junk fragment penalty, repair variants with parent consensus.
- Cross-cluster pairing: junk vol -120, vol_shape +35, cost_shape +20.
- Unit tests: dev-ai-interaction/test_cluster_reading_picker.py (12 cases).
- GAP v1→v2: Set G 52→46, Set I 44→42. Baseline parity still 0.

## 2026-07-15 - Pump OCR settings: 8 red boxes, Y-band prefs

- Added PumpOcrSettings.kt: max red boxes (default 8), label Y-band extra fraction (smallest value-cluster rect height), ratio band lo/hi; resolution-independent helpers (smallestRect, labelCenterYInValueBand, rectCenterY).
- ExperimentPumpScreen + OcrHarness + PumpCostVolUtils prune via settings (Quick Fill reads maxRedBoxes).
- SettingsScreen: max red boxes always visible; Y-band + ratio fields under experiment screens.
- classifyCostVolFromBoxOcr unchanged (selector experiments continue in sim).

## 2026-07-16 - role_band in app + local PR prepared

- **Activity:** Ported sim `role_band` to `PumpRoleBandClassifier.kt`; wired `classifyCostVolFromBoxOcr`; ratio defaults 2/30; build green.
- **History:** 2 logical commits; `backup-improve-pump-classification` @ 8833b5d4.
- **PR:** `dev-ai-interaction/PRs/PR-improve-pump-classification.md` — ready for Master review/merge.

## 2026-07-16 - User manual with screenshots — execution start
- Approved plan: user-manual-with-screenshots (review comments applied)
- Scope: docs/user-manual.md + images, rewrite Help + USER_GUIDE, About URL, Google sync setup, icons, on-screen help
- Out of scope: Import Old Pictures, Alignment/Pump experiments
- Device: emulator-5556; real-device photo shots deferred phase
- First action: eng-log

## 2026-07-16 - User manual screenshots + Help rewrite progress
- Captured emulator-5556 screens → docs/user-manual/images/*.jpg
- Wrote docs/user-manual.md (icons, Google Sheets/Drive setup, real-device follow-up phase)
- Rewrote docs/reference/USER_GUIDE.md
- Rewrote HelpScreen; fixed About manual URL
- On-screen help text: QuickFill, ManageVehicles, ExpenseEntry/List, Reports, Settings, sync list descriptions
- Next: ./build_app

## 2026-07-16 - User manual execution complete
- Commit 21ed0885: docs/user-manual.md + 17 screenshots, Help rewrite, About URL, on-screen tips, USER_GUIDE rewrite
- ./build_app success; tag instruction/builds (instruction-start-1-g21ed0885)
- NDK libc++ was mode 660 dlang:dlang (blocked ai-coder); fixed a+r via docker alpine mount for multi-user SDK
- Emulator captures; real-device OCR photo shots listed in manual follow-up section
- Ready for user deploy + test Help/manual/screens

## 2026-07-16 - Integrate phone screenshots into user manual
- Wire R1–R6 images into docs/user-manual.md; remove emulator-placeholder / follow-up sections
- Prefer phone crops/landmarks/quickfill; keep emulator for chrome-only where no phone shot
- Commit docs images (jpg only; drop raw png/notes from tree if present)

## 2026-07-16 - Manual: Quick Fill auto vehicle + multi-device backup section
- Correct Quick Fill vehicle auto-detect from dash landmarks
- Restructure §5 as backups/multi-device sync; list spreadsheet + photo targets
- todo-append missed fill / partial economy tracking

## 2026-07-16 - Full manual URL without GitHub login
- Point Help/About at public raw.githubusercontent.com URL (no account)
- Absolute image URLs in user-manual.md for raw view; centralize link helper

## 2026-07-16 - Local PR prepared (instruction / user manual)
- History cleaned: 3 logical commits; backup-instruction at pre-cleanup tip
- ./generate_pr.sh → dev-ai-interaction/PRs/PR-instruction.md
- instruction/builds at f92f2282; ready for Master review/merge
- Continue on same branch OK until merge; after merge use new branch

## 2026-07-16 - Post-merge: instruction rebased/reset onto master
- Master tip abda44ef (Merge branch instruction into master)
- instruction fully contained in master; git reset --hard master
- Session continues on instruction at post-merge tip for next planning cycle
- instruction/builds retargeted via ./build_app

## 2026-07-16 - Illustrated HTML user manual (screenshots in browser)
- raw .md is plain text; generate docs/user-manual.html with images
- UserManualDocs opens public HTML via jsDelivr (no GitHub login, images render)

## 2026-07-16 - Browser manual is HTML (not raw markdown)
- docs/user-manual.md remains edit source; docs/user-manual.html is browser-facing with screenshots
- Add scripts/render-user-manual.sh; regenerate HTML + asset package; USER_GUIDE points to HTML

## 2026-07-16 - Document HTML manual pipeline; render; local PR
- docs/reference/USER_MANUAL_BUILD.md + CONTRIBUTING / ENVIRONMENT_SETUP / USER_GUIDE / project-facts
- ./scripts/render-user-manual.sh then commit outputs
- prepare local PR-instruction for master update

## 2026-07-16 - Local PR-instruction (HTML manual) ready for Master
- Cleaned to 2 commits; backup-instruction set
- PR: dev-ai-interaction/PRs/PR-instruction.md
- Ask Master: Please review PR-instruction

## 2026-07-16 - Master merge PR-instruction (HTML illustrated manual)
- Independent review PASS: HTML pipeline, WebView assets, USER_MANUAL_BUILD docs; no experiment/import scope creep
- Merged instruction (7d8aa877) into master via merge-branch-into-master (FF index path)
- project-facts: HTML edit/render orientation; missed-fill TODO left open
- POST-MERGE gate: 34+ feature paths staged including assets and .kt

## 2026-07-27 - simplify-experiments execution start

- Approved plan: dev-ai-interaction/plans/simplify-experiments-keep-setj-gmm-seti-20260727-plan.md
- Role=Coder branch=simplify_experiments HEAD=91e94f53
- Baseline: no simplify_experiments/builds yet (first successful build_app will create it)
- Next: Phase 0 durable obsolete tags

## 2026-07-27 - Phase 0 obsolete tags

- Created annotated tags at HEAD 91e94f53:
- obsolete-experiment-alignment-sets-a-e
- obsolete-experiment-pump-multi-sets
- No source edits this phase

## 2026-07-27 - Phase 1 alignment obsolete doc

- Added docs/obsolete/EXPERIMENT_ALIGNMENT_SETS.md (sets A/E + helper catalog + silent lock note)
- No Kotlin experiment changes this phase

## 2026-07-27 - Phase 2 pump obsolete doc

- Added docs/obsolete/EXPERIMENT_PUMP_SETS.md (sets A-H/D/E/G/G- + full helper catalog; G--/I retained)
- No Kotlin experiment changes this phase

## 2026-07-27 - Phases 3-6b alignment simplify

- Phase 3: silent mlAngle ID lock + Set J only report column; drop Set A ML header
- Phase 4: outer JSON winner/discovery_landmarks from pathways set_j
- Phase 5-6: removed runMLKitIterative + runBinTrialsMLKit (no callers)
- Phase 6b: removed createScaledBase64 + drawCropBoxesOnReference orphans
- Set J iterative body left bit-identical (char-aware, Raw+Bin-Trials)

## 2026-07-27 - Phases 7-13 pump simplify

- flows + flowProcessors = G-- and Set I only
- deleted procA-F, procG, procGMinus, procH bodies
- deleted binPeak stack, longLived/histPlot, ML discovery buffers, B/D retract helpers
- deleted already-dead combinePhotoFragments/generateCdf/applyRecognitionHeuristics/drawHunks/pumpCreateScaledBase64
- kept makeGProc, procGMinusMinus, procI, hybrid helpers, doBOrDRedOnlyImage

## 2026-07-27 - Phases 14-16 final

- Orphan sweep: removed already-dead pGetFullLandmarksFromJson, pToEvenInt, toEvenInt; restored prepareScale + getHistStats after expression-body delete mishap
- Updated obsolete docs with orphan rows
- Updated docs/PUMP_EXPERIMENT_FLOWS.md for G-- + I only
- Isolation: only experiment screens + docs/obsolete + eng-log (+ PUMP_EXPERIMENT_FLOWS); OcrHarness/fuel/data untouched

## 2026-07-28 - alignment-json-device-field execution start

- Approved plan: dev-ai-interaction/plans/alignment-json-device-field-match-pump-20260728-plan.md
- Branch=simplify_experiments baseline tag simplify_experiments/builds @ 1b12ec33
- Scope: ExperimentAlignmentScreen.kt only — add root device=Build.MODEL to match pump JSON

## 2026-07-28 - Phase 1 Build.import

- ExperimentAlignmentScreen: import android.os.Build

## 2026-07-28 - Phase 2-3 alignment JSON device field

- Inserted root device=Build.MODEL after version before total_photos (match pump)
- Forensic: key device, source Build.MODEL, field order parity with ExperimentPumpScreen jsonHeader
- Only ExperimentAlignmentScreen.kt product code touched

## 2026-07-28 - fix-alignment-report-empty-vehicles execution start

- Approved plan: dev-ai-interaction/plans/fix-alignment-report-empty-vehicles-deep-analysis-20260728-plan.md
- Branch=simplify_experiments baseline simplify_experiments/builds @ c3e8d07c
- Scope: fail-fast empty cachedRefs, root meta, deep_analysis empty-metrics message

## 2026-07-28 - Phases 1-3 fail-fast + root meta

- Guard: cachedRefs empty → onLog error + return before buffers/JSON/photo loop
- JSON header only after usable-ref guard
- Root fields: usable_vehicle_refs, vehicles_in_db (keep device/version/timestamp/total_photos)

## 2026-07-28 - Phase 4-5 deep_analysis + isolation

- deep_analysis.py: explicit ERROR when metrics empty but results non-empty (vehicles/No match counts + restore-data hint)
- Isolation: only ExperimentAlignmentScreen.kt product change; silent-lock/Set J body unchanged
- Final tag simplify_experiments/builds @ d71dfb56

## 2026-07-28 - Local PR prepared: simplify_experiments

- History: 9 process commits → 4 logical (backup-simplify_experiments @ c59db84f messy tip)
- Cleaned HEAD: bda771cc; builds tag simplify_experiments/builds retargeted
- PR: dev-ai-interaction/PRs/PR-simplify_experiments.md
- Plans: simplify-experiments-keep-setj-gmm-seti, alignment-json-device-field, fix-alignment-report-empty-vehicles
- Pre-submit review PASS; no TODO closes; ask Master: Please review PR-simplify_experiments

## 2026-07-28 - Master merge PR-simplify_experiments

- Independent review PASS: silent mlAngle ID lock + Set J; pump G-- + I; device/meta + empty-ref fail-fast; obsolete docs/tags; production isolation
- Merged simplify_experiments (109b0d6a) into master via merge-branch-into-master (FF index path)
- POST-MERGE gate: 5 non-special feature paths staged (.kt + docs) + eng-log third-version
- TODO/project-facts: no branch delta; restore_special kept master
- build_app OK → master commit 194123c7; builds tag updated
- No works tag. Cleanup: ./remove_worktree.sh simplify_experiments from repo root when ready

