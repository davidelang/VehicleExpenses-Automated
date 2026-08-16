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


## 2026-07-26 - reports-field-conditional-economy-chains-20260726

- Execution start for approved plan: reports-field-conditional-economy-chains-20260726-plan.md
- Branch: batch_load; no prior batch_load/builds tag (first successful ./build_app will create it)
- Scope: ReportsScreen.kt field-conditional MPG/$/mi chains + REPORTS_METRICS.md; phase 5 emulator probe after user deploy

## 2026-07-26 - reports-field-conditional-economy-chains-20260726 phases 1-4 done

- Phases 1-4 implemented and built on batch_load
- ReportsScreen.kt: full fill = odo+cost+vol+!partial; MPG breakers; $/mi segment sum
- docs/reference/REPORTS_METRICS.md rewritten to match
- Tag: batch_load/builds @ 3d29b986 (batch_load-start-4-g3d29b986)
- Phase 5 blocked on user APK deploy to emulator-5554 (device currently has versionName=instruction-start-8-g7d8aa877, not batch_load build)

## 2026-07-26 - reports-field-conditional-economy-chains phase 5 probe PASS

- Backup: test-data-backups/emulator-5554-20260726-095208
- App on emulator-5554: batch_load-start-5-ge76bbc73
- Probe cases odo_only / blank / cost_only / volume_only on Honda between full fills 2→3
- UI Honda: baseline last 15.8 avg 17.0 $/mi 0.259; odo_only same; blank avg 15.8 $/mi 0.260; cost_only avg 15.8 $/mi 0.276; volume_only avg 14.5 $/mi 0.260
- Evidence: dev-ai-interaction/research/reports-chain-probe-20260726/
- DB restored from pre-probe backup; no leftover PROBE rows

## 2026-07-26 - reports-field-conditional-economy-chains PR prepared

- Pre-submit review PASS vs plan; device probe PASS; DB restored
- Local PR: dev-ai-interaction/PRs/PR-batch_load.md
- Tag: batch_load/builds @ 1de13e02; backup-batch_load set
- Ready for Master independent review + merge

## 2026-07-26 - batch-load-ingest-merge-questions-20260726

- Execution start: Stage A (batch ingest) per approved plan batch-load-ingest-merge-questions-20260726-plan.md
- Baseline tag: batch_load/builds @ a02dc9bc; Stages B–C deferred to later turn if A fills the turn

## 2026-07-26 - batch-load Stage A complete (ingest)

- Plan: batch-load-ingest-merge-questions-20260726-plan.md Stage A only
- Added: PhotoExifMeta, FuelPhotoJson, BatchImportPending, BatchFuelImportCoordinator, hardDeleteFuelEntry
- Set I hybrid in PumpCostVolUtils.runSetICostVolExtraction + OcrHarness.runPumpCostVolPipelineSetI
- Import Old Pictures UI: Run batch import / Cancel / Review questions (list-only)
- Hilt ViewModel in ui.batch (not ui.import — Java keyword breaks KSP)
- Builds tag: batch_load/builds @ 6efd2743
- Stages B (merge) and C (apply answers) remain on same plan path — not in this turn

## 2026-07-26 - batch-dash-match-experiment-set-j-20260726

- Execution start: unify Set J (experiment + batch + Quick Fill) per plan
- Baseline: batch_load/builds @ 99d1043c

## 2026-07-26 - batch-setj approach: copy experiment logic into production

- User direction: do NOT extract shared dual-call API; copy experiment Set J into production
- Experiment remains free to tinker (future compile-out); production batch/Quick Fill own a frozen copy
- Continue plan batch-dash-match-experiment-set-j-20260726-plan.md with this approach

## 2026-07-26 - batch-dash-match-experiment-set-j production copy done

- OcrHarness.runSetJPipeline rewritten as independent copy of experiment Set J (Raw+Bin-Trials+connectSegmentsH+pickBestOdometer)
- ExperimentAlignmentScreen NOT shared — free to tinker
- Batch parseSetJOdometer: 4-7 pure digits only (no concat soup)
- Tag: batch_load/builds @ 38186950
- Device parity CSV pending user deploy + re-import after purging old batch dash rows
- Notes: dev-ai-interaction/research/batch-setj-parity-20260726/NOTES.md

## 2026-07-26 - production-setj-ref-geometry-parity-20260726

- Execution start: fix production Set J ref geometry (probe/4080, ICRS crop, Raw nestFilter)
- Baseline: batch_load/builds @ d51f30d9

## 2026-07-26 - production-setj-ref-geometry-parity done

- Probed ref dims for landmarks+align; fallback 4080×3072
- ICRS Float createCrop for odo window; Raw no nestFilter
- Experiment screen untouched (independent copy)
- Tag: batch_load/builds @ de113410
- Device compare pending user deploy; notes in research/setj-geometry-parity-20260726/

## 2026-07-26 - batch import limited button (first 20+20)

- Import UI: OutlinedButton "First 20 dash + first 20 pump" (name-sorted take)
- BatchFuelImportCoordinator.runIngest(maxDash, maxPump); LIMITED_IMPORT_COUNT=20

## 2026-07-26 - batch pump ingest without vehicle

- processPump always runs Set I; inserts with UNASSIGNED_VEHICLE_ID=0
- No ASSIGN_VEHICLE gate; merge later assigns vehicle via time/location pairing
- Unreadable pumps still pending only

## 2026-07-26 - batch-setj-reverify-and-pending-answers-20260726

- Execution start; device versionName=batch_load-start-21-gf43bb991 (geometry + unassigned pump)
- Part A first-20 re-verify then Part B clickable pending answers

## 2026-07-26 - batch-setj-reverify + pending answers

- Part A first-20 re-verify on f43bb991: 17 dash inserts + 20 pump vehicleId=0
- first20 score: see research/batch-setj-parity-20260726/first20-after-geometry.txt (gate FAIL — residual leading-digit/blank DIFFs)
- Part B: clickable pending UI + forcedVehicleId dash reprocess + skip/retry pump (build 06707a0a)
- Experiment untouched; deploy needed for Part B on device

## 2026-07-27 - batch-mirror-experiment-pipelines-setj-seti

- Execution start: batch must run experiment Set J / Set I pipelines, not OcrHarness reinvented front-end
- Baseline tag: batch_load/builds @ c112e80e

## 2026-07-27 - batch-mirror-experiment-pipelines-setj-seti done (code)

- AlignmentSetJRunner: experiment Set A ID lock + Set J runPaddleValleyIterative for batch dash
- PumpSetIRunner: experiment-equivalent Set I for batch pump
- BatchFuelImportCoordinator no longer uses runAutoFillPipeline / runPumpCostVolPipelineSetI
- Tag: batch_load/builds @ 7653d156
- Device parity first-20 still needs deploy of this APK + re-run (prior c112e80e re-verify failed)

## 2026-07-27 - Batch Stage A full-run parity PASS; Stage B plan ready

- Full batch finished (~02:35 PDT); app idle after last pump `PXL_20260411_201506380.jpg`.
- Selective purge: deleted 74 old `batch_import%` rows (`updatedAt < 1785142500000`); kept 12 non-batch + 290 this-run (ids 219–508); pending cleaned to 6 this-run items.
- Parity vs experiment: dash Set J 142 OK / 0 DIFF (4 no-vehicle pending); pump Set I 148 OK / 0 DIFF (2 unreadable both sides). Report: `dev-ai-interaction/research/batch-run-parity-20260727/`.
- Next coder plan: `dev-ai-interaction/plans/batch-stage-b-merge-engine-coder-20260727-plan.md` (merge engine + Run merge). Durable B+C: `batch-stage-b-merge-and-stage-c-questions-20260727-plan.md`.

## 2026-07-27 - batch-stage-b-merge-engine

- Execution start: Stage B merge engine per batch-stage-b-merge-engine-coder-20260727-plan.md
- Stage A parity PASS; baseline tag 83e36ca0

## 2026-07-27 - batch-stage-b-merge-engine B1–B4 code

- B1: FuelPhotoJson.unionPhotos / addPumpPhoto / addDashPhoto
- B2: FuelRowMergeEngine.planMerge (vehicle clusters ±45m, vehicleId=0 pump pair, sequence vs re-shot, CONFLICT_ODO + photo paths, hard-delete list)
- B3: BatchFuelImportCoordinator.applyMerge + Import **Run merge** button
- B4: Merge after import checkbox default **off**
- Fixtures notes: dev-ai-interaction/research/batch-stage-b-merge-fixtures-20260727.md
- Tag: batch_load/builds @ 13d7c06b
- Device Run merge still to verify after deploy

## 2026-07-27 - batch-stage-b-merge-engine device Run merge

- Deploy: adb install -r (./deploy re-exec loop as ai-coder; used direct install)
- Pre: 302 rows, v0=148, fulls=6, odo_only=140, pump_only=154
- applyMerge: updated=133 deleted=129 pending+=2 → tag code 13d7c06b
- Post: 173 rows, v0=14, fulls=130, odo_only=16, pump_only=25
- Non-batch: 9 kept (absorbed re-shot ids 6–8 same $18.40); full non-batch fills preserved
- CONFLICT_ODO pending: 9594 vs 9698; 198699 vs 98699 (photo paths attached)
- Summary: dev-ai-interaction/research/batch-stage-b-merge-20260727/merge-run-summary.txt
- Stage C deferred (image-first questions, assign→re-merge UI)

## 2026-07-27 - batch-stage-c-image-questions

- Execution start: Stage C image-first pending questions per batch-stage-c-image-questions-coder-20260727-plan.md
- Stage B done on device; tip batch_load/builds @ 0c93922c

## 2026-07-27 - batch-stage-c-image-questions done

- C1: pendingPhotoUris + image thumbs/zoom; DNG placeholder; filename secondary only
- C2: CONFLICT_ODO Keep odo / Keep both; pure wrong-odo hard-delete; pump data odo zeroed
- C3: successful answers auto applyMerge; skip does not re-merge
- Device: Keep odo 9594 → pending 8→7 + remerge updated=16 deleted=1; Skip → pending 6
- Tag: batch_load/builds @ f360ce24
- Notes: dev-ai-interaction/research/batch-stage-c-questions-20260727/smoke-notes.txt
- Residual: 1 CONFLICT_ODO, 3 no-vehicle dash, 2 unreadable pump; AMBIGUOUS_MULTI_PUMP unused

## 2026-07-27 - batch-stage-c-photo-ux-manual-entry

- Execution start: photo UX, manual entry, economyIgnored sync, tank heuristic, unknown vehicle, outliers
- Parent tip: batch_load/builds @ 27d92a09

## 2026-07-27 - batch-stage-c-photo-ux-manual-entry done

- Photo stem-dedupe; DNG OpenCV preview; fullscreen +/− + sticky actions
- Manual pump/dash/conflict odo; economyIgnored Room v14 + sync column Economy Ignored
- Reports: fills N(Mp); Unknown label; economy excludes ignored; avg filters 3× outliers
- Merge: tank maxFill+5 auto-assign; post-merge enqueue unknown + MPG_OUTLIER
- Device: merge pending+=52 (9 unknown, 42 outliers); tag batch_load/builds @ aa9b1e53
- Notes: research/batch-stage-c-photo-ux-20260727/smoke-notes.txt

## 2026-07-27 - batch-merge-window-odo-sanity

- Execution start: 15m merge window, tight dash/pump pairs, odo reverse/gap sanitizer, Flag partial, per-vehicle unknown context
- Parent tip: batch_load/builds @ 3f915150

## 2026-07-27 - batch-merge-window-odo-sanity done

- MERGE_WINDOW_MS=15m; splitTightDashPumpPairs for multi dash+pump
- FuelOdoSanitizer: gap (maxVol×mpg×3, no fallback) then reverse; bias demote later
- ODO_SUSPECT pending; FlagPartial action; per-vehicle nearest for unknown
- Device: sanitize=31, ODO_SUSPECT=31 (incl. 20119 gap); CONFLICT_ODO=1
- Tag: batch_load/builds @ 1fbe913e
- Notes: research/batch-merge-window-odo-sanity-20260727/smoke-notes.txt

## 2026-07-27 - batch-pending-gap-dedupe-timefmt

- Execution start: Mark as gap, pending rebuild/dedupe, formatTimeDelta, Clear & re-scan
- Parent tip: batch_load/builds @ 25df6455

## 2026-07-27 - batch-pending-gap-dedupe-timefmt done (code)

- Mark as gap (blank odo/cost/vol); pending full rebuild on merge; (kind,fuelEntryId) dedupe
- formatTimeDelta for neighbor/unknown deltas; Clear questions & re-scan button
- Sanitizer skips already-partial / blank; clear all pending kinds per answered fuelEntryId
- Tag: batch_load/builds @ e743f48a
- Verify: ./build_app only — no deploy/device smoke (coder not authorized to deploy for test)
- Notes: research/batch-pending-gap-dedupe-timefmt-20260727/smoke-notes.txt

## 2026-07-27 - batch-mpg-outlier-ui-clarity

- Execution start: MPG outlier UI clarity per batch-mpg-outlier-ui-clarity-20260727-plan.md
- Tip: batch_load/builds @ 47ddcf81
- Device verify only after human deploy (coder will not adb install/deploy)

## 2026-07-27 - batch-mpg-outlier-ui-clarity done (code)

- MPG_OUTLIER: end-only primary photos; leg start/end text; focus toggle prior/end
- Edit/flag/ignore/gap target focusEntryId; nearby badges LEG START / THIS FILL
- Tag: batch_load/builds @ 795c8e3b
- No device test (await human deploy)
- Notes: research/batch-mpg-outlier-ui-clarity-20260727/smoke-notes.txt

## 2026-07-27 - batch-mpg-outlier-ui-clarity (layout v2)

- Re-execute updated plan: Last/This photo blocks + 4-line context
- Prior UI (single strip + toggle) superseded by locked two-block layout

## 2026-07-27 - batch-mpg-outlier-ui-clarity layout v2 done (code)

- Two-block Last/This photos above each button; 4-line text context (before/last/this/after)
- Focus default This fill; edit/flag/ignore/gap target focus id
- toPending: thisPhotoPaths + lastPhotoPaths
- Tag: batch_load/builds @ c272c521
- No device test until human deploy
- Notes: research/batch-mpg-outlier-ui-clarity-20260727/smoke-notes.txt

## 2026-07-27 - batch-partial-flag-semantics + sanitizer-correctness

- Execution start: two plans
  - batch-partial-flag-semantics-20260727-plan.md
  - batch-partial-and-sanitizer-correctness-20260727-plan.md
- No device deploy (human deploys before test)

## 2026-07-27 - partial-flag + sanitizer-correctness done (code)

- isPartialFill explicit-only; inserts/merge/sanitizer never auto-true for incomplete
- FuelOdoSanitizer detect-only (reverse, digit_jump, gap with clean mpg); no demote updates
- Checkbox SetPartialFill; clearAutoPartialFlags repair button
- Reports display band 5–80 + formatMpg n/a outside 1–100
- Quick Fill: isPartialFill=false on save
- Tag: batch_load/builds @ ec44b782
- No device test until human deploy
- Notes: research/batch-partial-sanitizer-correctness-20260727/smoke-notes.txt

## 2026-07-27 - batch-odo-suspect-ui-per-fill

- Execution start: ODO_SUSPECT per-fill UI layout
- No device deploy until human deploys

## 2026-07-27 - batch-odo-suspect-ui-per-fill done (code)

- ODO_SUSPECT: ordered prev/cur/next dash-only photos + odo fields under each
- SaveOdoPeers multi-odo write; payload prevDashPaths/curDashPaths/nextDashPaths
- Tag: batch_load/builds @ 0da926d1
- No device test until human deploy; re-scan after deploy
- Notes: research/batch-odo-suspect-ui-per-fill-20260727/smoke-notes.txt

## 2026-07-27 - batch-sync-cross-device-partials-and-titlebar

- Execution start: soft-delete sync, origin-device partials, title bar logo/version
- No device deploy until human deploys

## 2026-07-27 - batch-sync-cross-device-partials-and-titlebar (in progress)

- Completing MainActivity yellow bar + remaining CTA; no device deploy


## 2026-07-27 - batch-sync-cross-device-partials-and-titlebar done (code)

- Merge: all live partials (sync/QF/batch); soft-delete published absorbs; lat/lon later-ts
- hasUnmatchedPartials + post-sync toast CTA (no auto-merge)
- Yellow title-bar ?N → import?review=1 expand Review questions; Help bullets
- Tag: batch_load/builds @ 5e7de408
- No device test until human deploy
- Notes: research/batch-sync-cross-device-partials-and-titlebar-20260727/smoke-notes.txt


## 2026-07-27 - batch-mpg-gap-button-and-window-fills done (code)

- MPG card: vertical Save / Missing data between last & this / Ignore (no clip)
- markAsGap MPG: insert mid-leg blank; anchors preserved; dismiss if breaker exists
- FuelEconomyChains + breaker-aware detectOutliers; windowSummary inventory
- Post-sync applyMerge after fuel LWW; SYNC_BEHAVIOR + REPORTS_METRICS
- Tag: batch_load/builds @ a50da18f
- No device test until human deploy
- Notes: research/batch-mpg-gap-button-and-window-fills-20260727/smoke-notes.txt


## 2026-07-27 - batch-no-durable-photo-copy done (code)

- processDash/processPump: source paths only; copyToDurable removed
- migrateDurablePhotoRefsToSource rewrite+delete unreferenced mirrors
- PendingPhotoUris prefer source dirs; Help note
- Tag: batch_load/builds @ a18099d1
- No device test until human deploy
- Notes: research/batch-no-durable-photo-copy-20260727/smoke-notes.txt


## 2026-07-28 - batch-stage-c-phased-questions done (code)

- StageCPhase 1–6 store + UI filter; skip/reset; clear-rescan → phase 1
- ODO chain merge + simple length-guess mode; BAD_PUMP_RATIO phase 3
- Gap from phase 3/5; post-sync remoteWins → phase 1 + rebuild
- Tag: batch_load/builds @ 8ad579ef
- No device test until human deploy
- Notes: research/batch-stage-c-phased-questions-20260728/smoke-notes.txt


## 2026-07-28 - batch-stage-c-ux-skip-photos-phase-scope done (code)

- Phase-scoped pending rebuild; Next phase regenerates only next kinds
- Skip ledger same-phase; answer journal jsonl + export (no replay)
- Unassigned vehicle id=0 fixed syncId + Fuel - Unassigned tab; picker exclusions
- Photo role dash/pump; Close z-order; CONFLICT keep-both completes
- Tag: batch_load/builds @ dab2e144
- No device test until human deploy
- Notes: research/batch-stage-c-ux-skip-photos-phase-scope-20260728/smoke-notes.txt


## 2026-07-28 - PR-batch_load prepared for Master review

- History cleaned: 57 commits → 5 logical; backup-batch_load @ ff33f355; cleaned HEAD @ 11a2c1ca
- PR: dev-ai-interaction/PRs/PR-batch_load.md (pre-submit review + plans)
- Not rebased onto master (simplify_experiments); Master merge expects eng-log special handling + possible ExperimentAlignmentScreen conflict
- Tree matches pre-cleanup tip; build_app SUCCESS after cleanup

## 2026-07-26 - reports-field-conditional-economy-chains-20260726

- Execution start for approved plan: reports-field-conditional-economy-chains-20260726-plan.md
- Branch: batch_load; no prior batch_load/builds tag (first successful ./build_app will create it)
- Scope: ReportsScreen.kt field-conditional MPG/$/mi chains + REPORTS_METRICS.md; phase 5 emulator probe after user deploy

## 2026-07-26 - reports-field-conditional-economy-chains-20260726 phases 1-4 done

- Phases 1-4 implemented and built on batch_load
- ReportsScreen.kt: full fill = odo+cost+vol+!partial; MPG breakers; $/mi segment sum
- docs/reference/REPORTS_METRICS.md rewritten to match
- Tag: batch_load/builds @ 3d29b986 (batch_load-start-4-g3d29b986)
- Phase 5 blocked on user APK deploy to emulator-5554 (device currently has versionName=instruction-start-8-g7d8aa877, not batch_load build)

## 2026-07-26 - reports-field-conditional-economy-chains phase 5 probe PASS

- Backup: test-data-backups/emulator-5554-20260726-095208
- App on emulator-5554: batch_load-start-5-ge76bbc73
- Probe cases odo_only / blank / cost_only / volume_only on Honda between full fills 2→3
- UI Honda: baseline last 15.8 avg 17.0 $/mi 0.259; odo_only same; blank avg 15.8 $/mi 0.260; cost_only avg 15.8 $/mi 0.276; volume_only avg 14.5 $/mi 0.260
- Evidence: dev-ai-interaction/research/reports-chain-probe-20260726/
- DB restored from pre-probe backup; no leftover PROBE rows

## 2026-07-26 - reports-field-conditional-economy-chains PR prepared

- Pre-submit review PASS vs plan; device probe PASS; DB restored
- Local PR: dev-ai-interaction/PRs/PR-batch_load.md
- Tag: batch_load/builds @ 1de13e02; backup-batch_load set
- Ready for Master independent review + merge

## 2026-07-26 - batch-load-ingest-merge-questions-20260726

- Execution start: Stage A (batch ingest) per approved plan batch-load-ingest-merge-questions-20260726-plan.md
- Baseline tag: batch_load/builds @ a02dc9bc; Stages B–C deferred to later turn if A fills the turn

## 2026-07-26 - batch-load Stage A complete (ingest)

- Plan: batch-load-ingest-merge-questions-20260726-plan.md Stage A only
- Added: PhotoExifMeta, FuelPhotoJson, BatchImportPending, BatchFuelImportCoordinator, hardDeleteFuelEntry
- Set I hybrid in PumpCostVolUtils.runSetICostVolExtraction + OcrHarness.runPumpCostVolPipelineSetI
- Import Old Pictures UI: Run batch import / Cancel / Review questions (list-only)
- Hilt ViewModel in ui.batch (not ui.import — Java keyword breaks KSP)
- Builds tag: batch_load/builds @ 6efd2743
- Stages B (merge) and C (apply answers) remain on same plan path — not in this turn

## 2026-07-26 - batch-dash-match-experiment-set-j-20260726

- Execution start: unify Set J (experiment + batch + Quick Fill) per plan
- Baseline: batch_load/builds @ 99d1043c

## 2026-07-26 - batch-setj approach: copy experiment logic into production

- User direction: do NOT extract shared dual-call API; copy experiment Set J into production
- Experiment remains free to tinker (future compile-out); production batch/Quick Fill own a frozen copy
- Continue plan batch-dash-match-experiment-set-j-20260726-plan.md with this approach

## 2026-07-26 - batch-dash-match-experiment-set-j production copy done

- OcrHarness.runSetJPipeline rewritten as independent copy of experiment Set J (Raw+Bin-Trials+connectSegmentsH+pickBestOdometer)
- ExperimentAlignmentScreen NOT shared — free to tinker
- Batch parseSetJOdometer: 4-7 pure digits only (no concat soup)
- Tag: batch_load/builds @ 38186950
- Device parity CSV pending user deploy + re-import after purging old batch dash rows
- Notes: dev-ai-interaction/research/batch-setj-parity-20260726/NOTES.md

## 2026-07-26 - production-setj-ref-geometry-parity-20260726

- Execution start: fix production Set J ref geometry (probe/4080, ICRS crop, Raw nestFilter)
- Baseline: batch_load/builds @ d51f30d9

## 2026-07-26 - production-setj-ref-geometry-parity done

- Probed ref dims for landmarks+align; fallback 4080×3072
- ICRS Float createCrop for odo window; Raw no nestFilter
- Experiment screen untouched (independent copy)
- Tag: batch_load/builds @ de113410
- Device compare pending user deploy; notes in research/setj-geometry-parity-20260726/

## 2026-07-26 - batch import limited button (first 20+20)

- Import UI: OutlinedButton "First 20 dash + first 20 pump" (name-sorted take)
- BatchFuelImportCoordinator.runIngest(maxDash, maxPump); LIMITED_IMPORT_COUNT=20

## 2026-07-26 - batch pump ingest without vehicle

- processPump always runs Set I; inserts with UNASSIGNED_VEHICLE_ID=0
- No ASSIGN_VEHICLE gate; merge later assigns vehicle via time/location pairing
- Unreadable pumps still pending only

## 2026-07-26 - batch-setj-reverify-and-pending-answers-20260726

- Execution start; device versionName=batch_load-start-21-gf43bb991 (geometry + unassigned pump)
- Part A first-20 re-verify then Part B clickable pending answers

## 2026-07-26 - batch-setj-reverify + pending answers

- Part A first-20 re-verify on f43bb991: 17 dash inserts + 20 pump vehicleId=0
- first20 score: see research/batch-setj-parity-20260726/first20-after-geometry.txt (gate FAIL — residual leading-digit/blank DIFFs)
- Part B: clickable pending UI + forcedVehicleId dash reprocess + skip/retry pump (build 06707a0a)
- Experiment untouched; deploy needed for Part B on device

## 2026-07-27 - batch-mirror-experiment-pipelines-setj-seti

- Execution start: batch must run experiment Set J / Set I pipelines, not OcrHarness reinvented front-end
- Baseline tag: batch_load/builds @ c112e80e

## 2026-07-27 - batch-mirror-experiment-pipelines-setj-seti done (code)

- AlignmentSetJRunner: experiment Set A ID lock + Set J runPaddleValleyIterative for batch dash
- PumpSetIRunner: experiment-equivalent Set I for batch pump
- BatchFuelImportCoordinator no longer uses runAutoFillPipeline / runPumpCostVolPipelineSetI
- Tag: batch_load/builds @ 7653d156
- Device parity first-20 still needs deploy of this APK + re-run (prior c112e80e re-verify failed)

## 2026-07-27 - Batch Stage A full-run parity PASS; Stage B plan ready

- Full batch finished (~02:35 PDT); app idle after last pump `PXL_20260411_201506380.jpg`.
- Selective purge: deleted 74 old `batch_import%` rows (`updatedAt < 1785142500000`); kept 12 non-batch + 290 this-run (ids 219–508); pending cleaned to 6 this-run items.
- Parity vs experiment: dash Set J 142 OK / 0 DIFF (4 no-vehicle pending); pump Set I 148 OK / 0 DIFF (2 unreadable both sides). Report: `dev-ai-interaction/research/batch-run-parity-20260727/`.
- Next coder plan: `dev-ai-interaction/plans/batch-stage-b-merge-engine-coder-20260727-plan.md` (merge engine + Run merge). Durable B+C: `batch-stage-b-merge-and-stage-c-questions-20260727-plan.md`.

## 2026-07-27 - batch-stage-b-merge-engine

- Execution start: Stage B merge engine per batch-stage-b-merge-engine-coder-20260727-plan.md
- Stage A parity PASS; baseline tag 83e36ca0

## 2026-07-27 - batch-stage-b-merge-engine B1–B4 code

- B1: FuelPhotoJson.unionPhotos / addPumpPhoto / addDashPhoto
- B2: FuelRowMergeEngine.planMerge (vehicle clusters ±45m, vehicleId=0 pump pair, sequence vs re-shot, CONFLICT_ODO + photo paths, hard-delete list)
- B3: BatchFuelImportCoordinator.applyMerge + Import **Run merge** button
- B4: Merge after import checkbox default **off**
- Fixtures notes: dev-ai-interaction/research/batch-stage-b-merge-fixtures-20260727.md
- Tag: batch_load/builds @ 13d7c06b
- Device Run merge still to verify after deploy

## 2026-07-27 - batch-stage-b-merge-engine device Run merge

- Deploy: adb install -r (./deploy re-exec loop as ai-coder; used direct install)
- Pre: 302 rows, v0=148, fulls=6, odo_only=140, pump_only=154
- applyMerge: updated=133 deleted=129 pending+=2 → tag code 13d7c06b
- Post: 173 rows, v0=14, fulls=130, odo_only=16, pump_only=25
- Non-batch: 9 kept (absorbed re-shot ids 6–8 same $18.40); full non-batch fills preserved
- CONFLICT_ODO pending: 9594 vs 9698; 198699 vs 98699 (photo paths attached)
- Summary: dev-ai-interaction/research/batch-stage-b-merge-20260727/merge-run-summary.txt
- Stage C deferred (image-first questions, assign→re-merge UI)

## 2026-07-27 - batch-stage-c-image-questions

- Execution start: Stage C image-first pending questions per batch-stage-c-image-questions-coder-20260727-plan.md
- Stage B done on device; tip batch_load/builds @ 0c93922c

## 2026-07-27 - batch-stage-c-image-questions done

- C1: pendingPhotoUris + image thumbs/zoom; DNG placeholder; filename secondary only
- C2: CONFLICT_ODO Keep odo / Keep both; pure wrong-odo hard-delete; pump data odo zeroed
- C3: successful answers auto applyMerge; skip does not re-merge
- Device: Keep odo 9594 → pending 8→7 + remerge updated=16 deleted=1; Skip → pending 6
- Tag: batch_load/builds @ f360ce24
- Notes: dev-ai-interaction/research/batch-stage-c-questions-20260727/smoke-notes.txt
- Residual: 1 CONFLICT_ODO, 3 no-vehicle dash, 2 unreadable pump; AMBIGUOUS_MULTI_PUMP unused

## 2026-07-27 - batch-stage-c-photo-ux-manual-entry

- Execution start: photo UX, manual entry, economyIgnored sync, tank heuristic, unknown vehicle, outliers
- Parent tip: batch_load/builds @ 27d92a09

## 2026-07-27 - batch-stage-c-photo-ux-manual-entry done

- Photo stem-dedupe; DNG OpenCV preview; fullscreen +/− + sticky actions
- Manual pump/dash/conflict odo; economyIgnored Room v14 + sync column Economy Ignored
- Reports: fills N(Mp); Unknown label; economy excludes ignored; avg filters 3× outliers
- Merge: tank maxFill+5 auto-assign; post-merge enqueue unknown + MPG_OUTLIER
- Device: merge pending+=52 (9 unknown, 42 outliers); tag batch_load/builds @ aa9b1e53
- Notes: research/batch-stage-c-photo-ux-20260727/smoke-notes.txt

## 2026-07-27 - batch-merge-window-odo-sanity

- Execution start: 15m merge window, tight dash/pump pairs, odo reverse/gap sanitizer, Flag partial, per-vehicle unknown context
- Parent tip: batch_load/builds @ 3f915150

## 2026-07-27 - batch-merge-window-odo-sanity done

- MERGE_WINDOW_MS=15m; splitTightDashPumpPairs for multi dash+pump
- FuelOdoSanitizer: gap (maxVol×mpg×3, no fallback) then reverse; bias demote later
- ODO_SUSPECT pending; FlagPartial action; per-vehicle nearest for unknown
- Device: sanitize=31, ODO_SUSPECT=31 (incl. 20119 gap); CONFLICT_ODO=1
- Tag: batch_load/builds @ 1fbe913e
- Notes: research/batch-merge-window-odo-sanity-20260727/smoke-notes.txt

## 2026-07-27 - batch-pending-gap-dedupe-timefmt

- Execution start: Mark as gap, pending rebuild/dedupe, formatTimeDelta, Clear & re-scan
- Parent tip: batch_load/builds @ 25df6455

## 2026-07-27 - batch-pending-gap-dedupe-timefmt done (code)

- Mark as gap (blank odo/cost/vol); pending full rebuild on merge; (kind,fuelEntryId) dedupe
- formatTimeDelta for neighbor/unknown deltas; Clear questions & re-scan button
- Sanitizer skips already-partial / blank; clear all pending kinds per answered fuelEntryId
- Tag: batch_load/builds @ e743f48a
- Verify: ./build_app only — no deploy/device smoke (coder not authorized to deploy for test)
- Notes: research/batch-pending-gap-dedupe-timefmt-20260727/smoke-notes.txt

## 2026-07-27 - batch-mpg-outlier-ui-clarity

- Execution start: MPG outlier UI clarity per batch-mpg-outlier-ui-clarity-20260727-plan.md
- Tip: batch_load/builds @ 47ddcf81
- Device verify only after human deploy (coder will not adb install/deploy)

## 2026-07-27 - batch-mpg-outlier-ui-clarity done (code)

- MPG_OUTLIER: end-only primary photos; leg start/end text; focus toggle prior/end
- Edit/flag/ignore/gap target focusEntryId; nearby badges LEG START / THIS FILL
- Tag: batch_load/builds @ 795c8e3b
- No device test (await human deploy)
- Notes: research/batch-mpg-outlier-ui-clarity-20260727/smoke-notes.txt

## 2026-07-27 - batch-mpg-outlier-ui-clarity (layout v2)

- Re-execute updated plan: Last/This photo blocks + 4-line context
- Prior UI (single strip + toggle) superseded by locked two-block layout

## 2026-07-27 - batch-mpg-outlier-ui-clarity layout v2 done (code)

- Two-block Last/This photos above each button; 4-line text context (before/last/this/after)
- Focus default This fill; edit/flag/ignore/gap target focus id
- toPending: thisPhotoPaths + lastPhotoPaths
- Tag: batch_load/builds @ c272c521
- No device test until human deploy
- Notes: research/batch-mpg-outlier-ui-clarity-20260727/smoke-notes.txt

## 2026-07-27 - batch-partial-flag-semantics + sanitizer-correctness

- Execution start: two plans
  - batch-partial-flag-semantics-20260727-plan.md
  - batch-partial-and-sanitizer-correctness-20260727-plan.md
- No device deploy (human deploys before test)

## 2026-07-27 - partial-flag + sanitizer-correctness done (code)

- isPartialFill explicit-only; inserts/merge/sanitizer never auto-true for incomplete
- FuelOdoSanitizer detect-only (reverse, digit_jump, gap with clean mpg); no demote updates
- Checkbox SetPartialFill; clearAutoPartialFlags repair button
- Reports display band 5–80 + formatMpg n/a outside 1–100
- Quick Fill: isPartialFill=false on save
- Tag: batch_load/builds @ ec44b782
- No device test until human deploy
- Notes: research/batch-partial-sanitizer-correctness-20260727/smoke-notes.txt

## 2026-07-27 - batch-odo-suspect-ui-per-fill

- Execution start: ODO_SUSPECT per-fill UI layout
- No device deploy until human deploys

## 2026-07-27 - batch-odo-suspect-ui-per-fill done (code)

- ODO_SUSPECT: ordered prev/cur/next dash-only photos + odo fields under each
- SaveOdoPeers multi-odo write; payload prevDashPaths/curDashPaths/nextDashPaths
- Tag: batch_load/builds @ 0da926d1
- No device test until human deploy; re-scan after deploy
- Notes: research/batch-odo-suspect-ui-per-fill-20260727/smoke-notes.txt

## 2026-07-27 - batch-sync-cross-device-partials-and-titlebar

- Execution start: soft-delete sync, origin-device partials, title bar logo/version
- No device deploy until human deploys

## 2026-07-27 - batch-sync-cross-device-partials-and-titlebar (in progress)

- Completing MainActivity yellow bar + remaining CTA; no device deploy


## 2026-07-27 - batch-sync-cross-device-partials-and-titlebar done (code)

- Merge: all live partials (sync/QF/batch); soft-delete published absorbs; lat/lon later-ts
- hasUnmatchedPartials + post-sync toast CTA (no auto-merge)
- Yellow title-bar ?N → import?review=1 expand Review questions; Help bullets
- Tag: batch_load/builds @ 5e7de408
- No device test until human deploy
- Notes: research/batch-sync-cross-device-partials-and-titlebar-20260727/smoke-notes.txt


## 2026-07-27 - batch-mpg-gap-button-and-window-fills done (code)

- MPG card: vertical Save / Missing data between last & this / Ignore (no clip)
- markAsGap MPG: insert mid-leg blank; anchors preserved; dismiss if breaker exists
- FuelEconomyChains + breaker-aware detectOutliers; windowSummary inventory
- Post-sync applyMerge after fuel LWW; SYNC_BEHAVIOR + REPORTS_METRICS
- Tag: batch_load/builds @ a50da18f
- No device test until human deploy
- Notes: research/batch-mpg-gap-button-and-window-fills-20260727/smoke-notes.txt


## 2026-07-27 - batch-no-durable-photo-copy done (code)

- processDash/processPump: source paths only; copyToDurable removed
- migrateDurablePhotoRefsToSource rewrite+delete unreferenced mirrors
- PendingPhotoUris prefer source dirs; Help note
- Tag: batch_load/builds @ a18099d1
- No device test until human deploy
- Notes: research/batch-no-durable-photo-copy-20260727/smoke-notes.txt


## 2026-07-28 - batch-stage-c-phased-questions done (code)

- StageCPhase 1–6 store + UI filter; skip/reset; clear-rescan → phase 1
- ODO chain merge + simple length-guess mode; BAD_PUMP_RATIO phase 3
- Gap from phase 3/5; post-sync remoteWins → phase 1 + rebuild
- Tag: batch_load/builds @ 8ad579ef
- No device test until human deploy
- Notes: research/batch-stage-c-phased-questions-20260728/smoke-notes.txt


## 2026-07-28 - batch-stage-c-ux-skip-photos-phase-scope done (code)

- Phase-scoped pending rebuild; Next phase regenerates only next kinds
- Skip ledger same-phase; answer journal jsonl + export (no replay)
- Unassigned vehicle id=0 fixed syncId + Fuel - Unassigned tab; picker exclusions
- Photo role dash/pump; Close z-order; CONFLICT keep-both completes
- Tag: batch_load/builds @ dab2e144
- No device test until human deploy
- Notes: research/batch-stage-c-ux-skip-photos-phase-scope-20260728/smoke-notes.txt


## 2026-07-28 - PR-batch_load prepared for Master review

- History cleaned: 57 commits → 5 logical; backup-batch_load @ ff33f355; cleaned HEAD @ 11a2c1ca
- PR: dev-ai-interaction/PRs/PR-batch_load.md (pre-submit review + plans)
- Not rebased onto master (simplify_experiments); Master merge expects eng-log special handling + possible ExperimentAlignmentScreen conflict
- Tree matches pre-cleanup tip; build_app SUCCESS after cleanup

## 2026-07-26 - reports-field-conditional-economy-chains-20260726

- Execution start for approved plan: reports-field-conditional-economy-chains-20260726-plan.md
- Branch: batch_load; no prior batch_load/builds tag (first successful ./build_app will create it)
- Scope: ReportsScreen.kt field-conditional MPG/$/mi chains + REPORTS_METRICS.md; phase 5 emulator probe after user deploy

## 2026-07-26 - reports-field-conditional-economy-chains-20260726 phases 1-4 done

- Phases 1-4 implemented and built on batch_load
- ReportsScreen.kt: full fill = odo+cost+vol+!partial; MPG breakers; $/mi segment sum
- docs/reference/REPORTS_METRICS.md rewritten to match
- Tag: batch_load/builds @ 3d29b986 (batch_load-start-4-g3d29b986)
- Phase 5 blocked on user APK deploy to emulator-5554 (device currently has versionName=instruction-start-8-g7d8aa877, not batch_load build)

## 2026-07-26 - reports-field-conditional-economy-chains phase 5 probe PASS

- Backup: test-data-backups/emulator-5554-20260726-095208
- App on emulator-5554: batch_load-start-5-ge76bbc73
- Probe cases odo_only / blank / cost_only / volume_only on Honda between full fills 2→3
- UI Honda: baseline last 15.8 avg 17.0 $/mi 0.259; odo_only same; blank avg 15.8 $/mi 0.260; cost_only avg 15.8 $/mi 0.276; volume_only avg 14.5 $/mi 0.260
- Evidence: dev-ai-interaction/research/reports-chain-probe-20260726/
- DB restored from pre-probe backup; no leftover PROBE rows

## 2026-07-26 - reports-field-conditional-economy-chains PR prepared

- Pre-submit review PASS vs plan; device probe PASS; DB restored
- Local PR: dev-ai-interaction/PRs/PR-batch_load.md
- Tag: batch_load/builds @ 1de13e02; backup-batch_load set
- Ready for Master independent review + merge

## 2026-07-26 - batch-load-ingest-merge-questions-20260726

- Execution start: Stage A (batch ingest) per approved plan batch-load-ingest-merge-questions-20260726-plan.md
- Baseline tag: batch_load/builds @ a02dc9bc; Stages B–C deferred to later turn if A fills the turn

## 2026-07-26 - batch-load Stage A complete (ingest)

- Plan: batch-load-ingest-merge-questions-20260726-plan.md Stage A only
- Added: PhotoExifMeta, FuelPhotoJson, BatchImportPending, BatchFuelImportCoordinator, hardDeleteFuelEntry
- Set I hybrid in PumpCostVolUtils.runSetICostVolExtraction + OcrHarness.runPumpCostVolPipelineSetI
- Import Old Pictures UI: Run batch import / Cancel / Review questions (list-only)
- Hilt ViewModel in ui.batch (not ui.import — Java keyword breaks KSP)
- Builds tag: batch_load/builds @ 6efd2743
- Stages B (merge) and C (apply answers) remain on same plan path — not in this turn

## 2026-07-26 - batch-dash-match-experiment-set-j-20260726

- Execution start: unify Set J (experiment + batch + Quick Fill) per plan
- Baseline: batch_load/builds @ 99d1043c

## 2026-07-26 - batch-setj approach: copy experiment logic into production

- User direction: do NOT extract shared dual-call API; copy experiment Set J into production
- Experiment remains free to tinker (future compile-out); production batch/Quick Fill own a frozen copy
- Continue plan batch-dash-match-experiment-set-j-20260726-plan.md with this approach

## 2026-07-26 - batch-dash-match-experiment-set-j production copy done

- OcrHarness.runSetJPipeline rewritten as independent copy of experiment Set J (Raw+Bin-Trials+connectSegmentsH+pickBestOdometer)
- ExperimentAlignmentScreen NOT shared — free to tinker
- Batch parseSetJOdometer: 4-7 pure digits only (no concat soup)
- Tag: batch_load/builds @ 38186950
- Device parity CSV pending user deploy + re-import after purging old batch dash rows
- Notes: dev-ai-interaction/research/batch-setj-parity-20260726/NOTES.md

## 2026-07-26 - production-setj-ref-geometry-parity-20260726

- Execution start: fix production Set J ref geometry (probe/4080, ICRS crop, Raw nestFilter)
- Baseline: batch_load/builds @ d51f30d9

## 2026-07-26 - production-setj-ref-geometry-parity done

- Probed ref dims for landmarks+align; fallback 4080×3072
- ICRS Float createCrop for odo window; Raw no nestFilter
- Experiment screen untouched (independent copy)
- Tag: batch_load/builds @ de113410
- Device compare pending user deploy; notes in research/setj-geometry-parity-20260726/

## 2026-07-26 - batch import limited button (first 20+20)

- Import UI: OutlinedButton "First 20 dash + first 20 pump" (name-sorted take)
- BatchFuelImportCoordinator.runIngest(maxDash, maxPump); LIMITED_IMPORT_COUNT=20

## 2026-07-26 - batch pump ingest without vehicle

- processPump always runs Set I; inserts with UNASSIGNED_VEHICLE_ID=0
- No ASSIGN_VEHICLE gate; merge later assigns vehicle via time/location pairing
- Unreadable pumps still pending only

## 2026-07-26 - batch-setj-reverify-and-pending-answers-20260726

- Execution start; device versionName=batch_load-start-21-gf43bb991 (geometry + unassigned pump)
- Part A first-20 re-verify then Part B clickable pending answers

## 2026-07-26 - batch-setj-reverify + pending answers

- Part A first-20 re-verify on f43bb991: 17 dash inserts + 20 pump vehicleId=0
- first20 score: see research/batch-setj-parity-20260726/first20-after-geometry.txt (gate FAIL — residual leading-digit/blank DIFFs)
- Part B: clickable pending UI + forcedVehicleId dash reprocess + skip/retry pump (build 06707a0a)
- Experiment untouched; deploy needed for Part B on device

## 2026-07-27 - batch-mirror-experiment-pipelines-setj-seti

- Execution start: batch must run experiment Set J / Set I pipelines, not OcrHarness reinvented front-end
- Baseline tag: batch_load/builds @ c112e80e

## 2026-07-27 - batch-mirror-experiment-pipelines-setj-seti done (code)

- AlignmentSetJRunner: experiment Set A ID lock + Set J runPaddleValleyIterative for batch dash
- PumpSetIRunner: experiment-equivalent Set I for batch pump
- BatchFuelImportCoordinator no longer uses runAutoFillPipeline / runPumpCostVolPipelineSetI
- Tag: batch_load/builds @ 7653d156
- Device parity first-20 still needs deploy of this APK + re-run (prior c112e80e re-verify failed)

## 2026-07-27 - Batch Stage A full-run parity PASS; Stage B plan ready

- Full batch finished (~02:35 PDT); app idle after last pump `PXL_20260411_201506380.jpg`.
- Selective purge: deleted 74 old `batch_import%` rows (`updatedAt < 1785142500000`); kept 12 non-batch + 290 this-run (ids 219–508); pending cleaned to 6 this-run items.
- Parity vs experiment: dash Set J 142 OK / 0 DIFF (4 no-vehicle pending); pump Set I 148 OK / 0 DIFF (2 unreadable both sides). Report: `dev-ai-interaction/research/batch-run-parity-20260727/`.
- Next coder plan: `dev-ai-interaction/plans/batch-stage-b-merge-engine-coder-20260727-plan.md` (merge engine + Run merge). Durable B+C: `batch-stage-b-merge-and-stage-c-questions-20260727-plan.md`.

## 2026-07-27 - batch-stage-b-merge-engine

- Execution start: Stage B merge engine per batch-stage-b-merge-engine-coder-20260727-plan.md
- Stage A parity PASS; baseline tag 83e36ca0

## 2026-07-27 - batch-stage-b-merge-engine B1–B4 code

- B1: FuelPhotoJson.unionPhotos / addPumpPhoto / addDashPhoto
- B2: FuelRowMergeEngine.planMerge (vehicle clusters ±45m, vehicleId=0 pump pair, sequence vs re-shot, CONFLICT_ODO + photo paths, hard-delete list)
- B3: BatchFuelImportCoordinator.applyMerge + Import **Run merge** button
- B4: Merge after import checkbox default **off**
- Fixtures notes: dev-ai-interaction/research/batch-stage-b-merge-fixtures-20260727.md
- Tag: batch_load/builds @ 13d7c06b
- Device Run merge still to verify after deploy

## 2026-07-27 - batch-stage-b-merge-engine device Run merge

- Deploy: adb install -r (./deploy re-exec loop as ai-coder; used direct install)
- Pre: 302 rows, v0=148, fulls=6, odo_only=140, pump_only=154
- applyMerge: updated=133 deleted=129 pending+=2 → tag code 13d7c06b
- Post: 173 rows, v0=14, fulls=130, odo_only=16, pump_only=25
- Non-batch: 9 kept (absorbed re-shot ids 6–8 same $18.40); full non-batch fills preserved
- CONFLICT_ODO pending: 9594 vs 9698; 198699 vs 98699 (photo paths attached)
- Summary: dev-ai-interaction/research/batch-stage-b-merge-20260727/merge-run-summary.txt
- Stage C deferred (image-first questions, assign→re-merge UI)

## 2026-07-27 - batch-stage-c-image-questions

- Execution start: Stage C image-first pending questions per batch-stage-c-image-questions-coder-20260727-plan.md
- Stage B done on device; tip batch_load/builds @ 0c93922c

## 2026-07-27 - batch-stage-c-image-questions done

- C1: pendingPhotoUris + image thumbs/zoom; DNG placeholder; filename secondary only
- C2: CONFLICT_ODO Keep odo / Keep both; pure wrong-odo hard-delete; pump data odo zeroed
- C3: successful answers auto applyMerge; skip does not re-merge
- Device: Keep odo 9594 → pending 8→7 + remerge updated=16 deleted=1; Skip → pending 6
- Tag: batch_load/builds @ f360ce24
- Notes: dev-ai-interaction/research/batch-stage-c-questions-20260727/smoke-notes.txt
- Residual: 1 CONFLICT_ODO, 3 no-vehicle dash, 2 unreadable pump; AMBIGUOUS_MULTI_PUMP unused

## 2026-07-27 - batch-stage-c-photo-ux-manual-entry

- Execution start: photo UX, manual entry, economyIgnored sync, tank heuristic, unknown vehicle, outliers
- Parent tip: batch_load/builds @ 27d92a09

## 2026-07-27 - batch-stage-c-photo-ux-manual-entry done

- Photo stem-dedupe; DNG OpenCV preview; fullscreen +/− + sticky actions
- Manual pump/dash/conflict odo; economyIgnored Room v14 + sync column Economy Ignored
- Reports: fills N(Mp); Unknown label; economy excludes ignored; avg filters 3× outliers
- Merge: tank maxFill+5 auto-assign; post-merge enqueue unknown + MPG_OUTLIER
- Device: merge pending+=52 (9 unknown, 42 outliers); tag batch_load/builds @ aa9b1e53
- Notes: research/batch-stage-c-photo-ux-20260727/smoke-notes.txt

## 2026-07-27 - batch-merge-window-odo-sanity

- Execution start: 15m merge window, tight dash/pump pairs, odo reverse/gap sanitizer, Flag partial, per-vehicle unknown context
- Parent tip: batch_load/builds @ 3f915150

## 2026-07-27 - batch-merge-window-odo-sanity done

- MERGE_WINDOW_MS=15m; splitTightDashPumpPairs for multi dash+pump
- FuelOdoSanitizer: gap (maxVol×mpg×3, no fallback) then reverse; bias demote later
- ODO_SUSPECT pending; FlagPartial action; per-vehicle nearest for unknown
- Device: sanitize=31, ODO_SUSPECT=31 (incl. 20119 gap); CONFLICT_ODO=1
- Tag: batch_load/builds @ 1fbe913e
- Notes: research/batch-merge-window-odo-sanity-20260727/smoke-notes.txt

## 2026-07-27 - batch-pending-gap-dedupe-timefmt

- Execution start: Mark as gap, pending rebuild/dedupe, formatTimeDelta, Clear & re-scan
- Parent tip: batch_load/builds @ 25df6455

## 2026-07-27 - batch-pending-gap-dedupe-timefmt done (code)

- Mark as gap (blank odo/cost/vol); pending full rebuild on merge; (kind,fuelEntryId) dedupe
- formatTimeDelta for neighbor/unknown deltas; Clear questions & re-scan button
- Sanitizer skips already-partial / blank; clear all pending kinds per answered fuelEntryId
- Tag: batch_load/builds @ e743f48a
- Verify: ./build_app only — no deploy/device smoke (coder not authorized to deploy for test)
- Notes: research/batch-pending-gap-dedupe-timefmt-20260727/smoke-notes.txt

## 2026-07-27 - batch-mpg-outlier-ui-clarity

- Execution start: MPG outlier UI clarity per batch-mpg-outlier-ui-clarity-20260727-plan.md
- Tip: batch_load/builds @ 47ddcf81
- Device verify only after human deploy (coder will not adb install/deploy)

## 2026-07-27 - batch-mpg-outlier-ui-clarity done (code)

- MPG_OUTLIER: end-only primary photos; leg start/end text; focus toggle prior/end
- Edit/flag/ignore/gap target focusEntryId; nearby badges LEG START / THIS FILL
- Tag: batch_load/builds @ 795c8e3b
- No device test (await human deploy)
- Notes: research/batch-mpg-outlier-ui-clarity-20260727/smoke-notes.txt

## 2026-07-27 - batch-mpg-outlier-ui-clarity (layout v2)

- Re-execute updated plan: Last/This photo blocks + 4-line context
- Prior UI (single strip + toggle) superseded by locked two-block layout

## 2026-07-27 - batch-mpg-outlier-ui-clarity layout v2 done (code)

- Two-block Last/This photos above each button; 4-line text context (before/last/this/after)
- Focus default This fill; edit/flag/ignore/gap target focus id
- toPending: thisPhotoPaths + lastPhotoPaths
- Tag: batch_load/builds @ c272c521
- No device test until human deploy
- Notes: research/batch-mpg-outlier-ui-clarity-20260727/smoke-notes.txt

## 2026-07-27 - batch-partial-flag-semantics + sanitizer-correctness

- Execution start: two plans
  - batch-partial-flag-semantics-20260727-plan.md
  - batch-partial-and-sanitizer-correctness-20260727-plan.md
- No device deploy (human deploys before test)

## 2026-07-27 - partial-flag + sanitizer-correctness done (code)

- isPartialFill explicit-only; inserts/merge/sanitizer never auto-true for incomplete
- FuelOdoSanitizer detect-only (reverse, digit_jump, gap with clean mpg); no demote updates
- Checkbox SetPartialFill; clearAutoPartialFlags repair button
- Reports display band 5–80 + formatMpg n/a outside 1–100
- Quick Fill: isPartialFill=false on save
- Tag: batch_load/builds @ ec44b782
- No device test until human deploy
- Notes: research/batch-partial-sanitizer-correctness-20260727/smoke-notes.txt

## 2026-07-27 - batch-odo-suspect-ui-per-fill

- Execution start: ODO_SUSPECT per-fill UI layout
- No device deploy until human deploys

## 2026-07-27 - batch-odo-suspect-ui-per-fill done (code)

- ODO_SUSPECT: ordered prev/cur/next dash-only photos + odo fields under each
- SaveOdoPeers multi-odo write; payload prevDashPaths/curDashPaths/nextDashPaths
- Tag: batch_load/builds @ 0da926d1
- No device test until human deploy; re-scan after deploy
- Notes: research/batch-odo-suspect-ui-per-fill-20260727/smoke-notes.txt

## 2026-07-27 - batch-sync-cross-device-partials-and-titlebar

- Execution start: soft-delete sync, origin-device partials, title bar logo/version
- No device deploy until human deploys

## 2026-07-27 - batch-sync-cross-device-partials-and-titlebar (in progress)

- Completing MainActivity yellow bar + remaining CTA; no device deploy


## 2026-07-27 - batch-sync-cross-device-partials-and-titlebar done (code)

- Merge: all live partials (sync/QF/batch); soft-delete published absorbs; lat/lon later-ts
- hasUnmatchedPartials + post-sync toast CTA (no auto-merge)
- Yellow title-bar ?N → import?review=1 expand Review questions; Help bullets
- Tag: batch_load/builds @ 5e7de408
- No device test until human deploy
- Notes: research/batch-sync-cross-device-partials-and-titlebar-20260727/smoke-notes.txt


## 2026-07-27 - batch-mpg-gap-button-and-window-fills done (code)

- MPG card: vertical Save / Missing data between last & this / Ignore (no clip)
- markAsGap MPG: insert mid-leg blank; anchors preserved; dismiss if breaker exists
- FuelEconomyChains + breaker-aware detectOutliers; windowSummary inventory
- Post-sync applyMerge after fuel LWW; SYNC_BEHAVIOR + REPORTS_METRICS
- Tag: batch_load/builds @ a50da18f
- No device test until human deploy
- Notes: research/batch-mpg-gap-button-and-window-fills-20260727/smoke-notes.txt


## 2026-07-27 - batch-no-durable-photo-copy done (code)

- processDash/processPump: source paths only; copyToDurable removed
- migrateDurablePhotoRefsToSource rewrite+delete unreferenced mirrors
- PendingPhotoUris prefer source dirs; Help note
- Tag: batch_load/builds @ a18099d1
- No device test until human deploy
- Notes: research/batch-no-durable-photo-copy-20260727/smoke-notes.txt


## 2026-07-28 - batch-stage-c-phased-questions done (code)

- StageCPhase 1–6 store + UI filter; skip/reset; clear-rescan → phase 1
- ODO chain merge + simple length-guess mode; BAD_PUMP_RATIO phase 3
- Gap from phase 3/5; post-sync remoteWins → phase 1 + rebuild
- Tag: batch_load/builds @ 8ad579ef
- No device test until human deploy
- Notes: research/batch-stage-c-phased-questions-20260728/smoke-notes.txt


## 2026-07-28 - batch-stage-c-ux-skip-photos-phase-scope done (code)

- Phase-scoped pending rebuild; Next phase regenerates only next kinds
- Skip ledger same-phase; answer journal jsonl + export (no replay)
- Unassigned vehicle id=0 fixed syncId + Fuel - Unassigned tab; picker exclusions
- Photo role dash/pump; Close z-order; CONFLICT keep-both completes
- Tag: batch_load/builds @ dab2e144
- No device test until human deploy
- Notes: research/batch-stage-c-ux-skip-photos-phase-scope-20260728/smoke-notes.txt


## 2026-07-28 - PR-batch_load prepared for Master review

- History cleaned: 57 commits → 5 logical; backup-batch_load @ ff33f355; cleaned HEAD @ 11a2c1ca
- PR: dev-ai-interaction/PRs/PR-batch_load.md (pre-submit review + plans)
- Not rebased onto master (simplify_experiments); Master merge expects eng-log special handling + possible ExperimentAlignmentScreen conflict
- Tree matches pre-cleanup tip; build_app SUCCESS after cleanup

## 2026-07-30 - Master merge PR-improve-merges

- Independent review APPROVE; audit_merge FF SUCCESS
- merge-branch-into-master.sh improve-merges (FF index path); ve-englog attempted dup tail → restored index to HEAD eng-log (no branch eng-log delta)
- Worktree ENGINEERING_LOG still has prior +a duplicate tail (skip-worktree for integrity); needs sudo chattr -a + restore when available
- project-facts: Room v16 + MergeAck locations
- Commit 4011fddd on tip after feature commits; builds tag @ 4011fddd
- BUILD SUCCESSFUL; no works tag
- Cleanup: ./remove_worktree.sh improve-merges from repo root when ready


## 2026-07-30 - Paddle-Lite PR email rewrite

- Approved plan: dev-ai-interaction/plans/paddle-upstream-fix-author-email-20260730-plan.md
- Scope: rewrite david@example.com → david@lang.hm on pr-upstream-cleanup, pr-x86-android-mobile-gap, pr-calib-safe-uint8-dequant in Paddle-Lite-upstream; force-push to push remote (davidelang/Paddle-Lite)
- No VehicleExpenses app source changes

## 2026-07-30 - Paddle-Lite PR email rewrite complete (local)

- Local identity: user.email → david@lang.hm in Paddle-Lite-upstream
- filter-branch rewrite: pr-upstream-cleanup, pr-x86-android-mobile-gap, pr-calib-safe-uint8-dequant (+ WIP hygiene heads)
- Trees identical to backup/*-pre-email; author+committer now david@lang.hm
- New tips: 2ca962497 / c6a9b9ada / d718308c5
- Force-push to davidelang/Paddle-Lite FAILED: ai-coder SSH publickey denied — user must push with credentials
- Note: dev-ai-interaction/paddle-pr-email-rewrite-backup-20260730.txt

## 2026-07-30 - TODO hygiene (master audit)

- Closed two backlog items after code/PR review (user-approved):
  - Generate UI manual / in-app guide (PR-instruction delivered)
  - Quick Fill Settings currency/volume "use system" (already on master)
- CHANGELOG § Backlog completed (2026-07-30) updated
- Email/import TODOs left open (email-connection not merged)


## 2026-07-30 - EXEC START: minor-fixes-batch-20260730-plan.md

- Branch: minor-fixes
- Plan: dev-ai-interaction/plans/minor-fixes-batch-20260730-plan.md
- Scope: Waves A–I (settings, About feedback, debug UX, Syncing page, QF notes, photo sync refs-only, fuel download API, Fuel History/edit + on-demand fetch)
- Wave J host research not execution-build

## 2026-07-30 - EXEC DONE: minor-fixes-batch-20260730-plan.md (Waves A–I)

- Wave A: fuel photos label; pump Amazon album URL; max red boxes experiment-gated
- Wave B–C: About feedback email + DeviceFeedbackInfo; debug QF compact X/max Send Delete + info
- Wave D: SyncingScreen + drawer route; Settings loses sync block; red ! → syncing
- Wave E: Quick Fill Notes field + persist/clear
- Wave F: photo FULL/PENDING no expense bulk download; pending downloads = vehicle refs only
- Wave G: scrubUnreadable fuel/expense; downloadFuelPhoto; Fuel DAO getById + VM APIs
- Wave H: Fuel History tabs + FuelEdit; fetch-from-archive on history/edit/expense/batch pending
- Wave I: project-facts orientation for Syncing/Fuel History/on-demand photo policy
- Wave J host research: not in this execution (sandbox planner)
- builds tag tip: minor-fixes/builds @ 54f64e2f (pre–project-facts commit)

## 2026-07-30 - EXEC START: reports-lab-experimental-hub-20260730-plan.md

- Branch: minor-fixes
- Scope: Reports Lab hub + 6 report sets, filters, share TEXT/CSV, Vico charts; production ReportsScreen unchanged
- Waves A–E

## 2026-07-30 - EXEC DONE: reports-lab-experimental-hub-20260730-plan.md

- Reports Lab hub + 6 sets under ui/reports/lab/; drawer always visible
- Filters + prefs (vehicle/period/custom); teaser KPIs; empty states
- Share TEXT/CSV each set; Vehicle summary pack + VIN checkbox (default off)
- Vico 3.2.3 compose + compose-m3: MPG line, unit-price line, monthly bars, category bars
- Production ReportsScreen.kt UNCHANGED
- builds: minor-fixes/builds @ 2251a8f8 (+ project-facts phase next)

## 2026-07-30 - EXEC START: minor-fixes-gaps-nits-followup-20260730-plan.md

- Branch: minor-fixes
- Gaps G1–G5: expenseHasPendingWork; Fuel History fetch label/await; fuel pump_N roles; host photo-kind REPORT

## 2026-07-30 - EXEC DONE: minor-fixes-gaps-nits-followup-20260730-plan.md

- G2: removed dead expenseHasPendingWork + unused expenseNeedsDownload
- G3+G4: Fuel History Fetch image from archive + await Fetching…
- G5: fuelRoleForTag / fuelTagFromRole for pump_N; wired upload+pending+download
- G1: photo-kind-classify script + metrics.json + REPORT.md (5-NN ~88.7%, recommendation: needs human confirm)
- builds: minor-fixes/builds @ 7f9ab6c3

## 2026-07-30 - EXEC START: unit-i18n-consistency-20260730-plan.md

- Branch: minor-fixes
- Plan path: dev-ai-interaction/plans/unit-i18n-consistency-20260730-plan.md

## 2026-07-30 - EXEC DONE: unit-i18n-consistency-20260730-plan.md

- VolumeUnits.formatVolume (+ Context overload); space-before-unit
- UnitFormat: mpg / $/mi / distanceDeltaLabel façade
- Quick Fill convert → VolumeUnits.convert; Fuel History/Edit labels; batch neighbor currency+volume
- Lab formatVolume delegates; Reports + Lab high-traffic economy labels via UnitFormat
- project-facts + TODO i18n deferred pointer
- builds: minor-fixes/builds @ 04efeb68

## 2026-07-30 - EXEC START: form-icons-fontscale-startup-20260730-plan.md

- Branch: minor-fixes
- Plan: form-icons-fontscale-startup-20260730-plan.md

## 2026-07-30 - Phase B5 note: Quick Fill font-scale

- No QF code change: existing responsive A/B/C layout retained; multi-device large-font check deferred to 5554/5556 handoff

## 2026-07-30 - EXEC DONE: fix-icons-fontscale-startup-20260730-plan.md

- R1: material-icons-core + extended (BOM)
- R5: removed copyTessdataOnce
- R2: TopAppBar wrap, Settings debug stack, Fuel History fetch multi-line, Lab banner/title softWrap
- R3: removed dead volumeLabel
- R4: Lab UnitFormat for MPG chart/summary
- R2 B5: QF no change (responsive layout retained)
- builds: minor-fixes/builds @ bb63b72f

## 2026-07-30 - Startup smoke: drop Vico compose-m3

- compose-m3 pulled material3 1.4 → NoSuchMethodError ExposedDropdownMenuBox on QuickFill
- Keep vico:compose:3.2.3 only; cold start emulator-5554 OK (pid live, no Icons/tessdata FATAL)
- Final builds: minor-fixes/builds @ 26095264 (+ facts tip)

## 2026-07-30 - minor-fixes: local PR prepared (history cleanup)

- Soft-reset history cleanup: `git tag -f backup-minor-fixes` @ pre-cleanup tip `c4222079`; `git reset --soft master`; six logical commits; TREE_MATCHES_BACKUP (`HEAD^{tree}` == backup tree `b434f073`).
- Cleaned tip `d04fb7c7` (docs); post-cleanup `./build_app` SUCCESS; `minor-fixes/builds` updated to cleaned tip.
- Pre-submit review vs five plans: scope OK; residual risks = multi-device font checklist + Lab experimental + i18n deferred.
- `./generate_pr.sh` → `dev-ai-interaction/PRs/PR-minor-fixes.md` (plans embedded + Coder pre-submit section).
- Ready for Master (`run-grok-master`) independent review + merge. Coder does not merge.

## 2026-07-30 - Trip tracking open-only fuel tripType execution start

- Approved plan: dev-ai-interaction/plans/trip-tracking-open-only-fuel-triptype-20260730-plan.md
- Branch: trip-tracking; agent-2 coder worktree
- First action eng-log; phases 1–8 schema→sync→UI→nav

## 2026-07-30 - Phase 1 schema tripType tripTypesJson

- FuelEntry.tripType; Vehicle.tripTypesJson; AppDatabase v17; MIGRATION_16_17 registered
- Building phase 1

## 2026-07-30 - Phase 2 TripTypes helper + inherit

- data/trip/TripTypes.kt pure parse/format/seed/reorder
- VehicleRepository.insertVehicle stamps tripTypesJson from inherit or seed

## 2026-07-30 - Phase 3 Tabular Trip Type columns

- FUEL_HEADERS Trip Type; VEHICLE_HEADERS Trip Types JSON; maps + GoogleSheetsClient delegate

## 2026-07-30 - Phase 4 TripTimeline helpers

- isTripStart, tripStartsForVehicle, currentOpenTrip, buildTripStart

## 2026-07-30 - Phase 5+6 TripTrackingScreen manual + manage types

- Manual vehicle/odo/type, Start + Close(Personal), datetime override
- ManageTripTypesDialog add/rename/reorder → vehicle.tripTypesJson

## 2026-07-30 - Phase 7 camera odo path

- TripTrackingScreen CameraPreview + OcrHarness.runAutoFillPipeline fills vehicle/odo

## 2026-07-30 - Phase 8 nav + docs

- MainActivity route triptracking + drawer after Quick Fill; NAVIGATION_MAP; project-facts Room v17 + trip locations

## 2026-07-30 - Trip tracking plan execution complete

- All phases 1–8 built; tag trip-tracking/builds @ 4d54082a
- Open-only fuel tripType + vehicle tripTypesJson + TripTrackingScreen + sync columns
- Ready to test; no ReportsScreen edits

## 2026-07-30 - merge-trip-tracking-into-minor-fixes: execution start

- Magic-approved plan: dev-ai-interaction/plans/merge-trip-tracking-into-minor-fixes-20260730-plan.md
- Branch minor-fixes @ 4cecb088; merging trip-tracking @ f135917f
- Baseline builds tag: minor-fixes/builds

## 2026-07-30 - Trip tracking open-only fuel tripType execution start

- Approved plan: dev-ai-interaction/plans/trip-tracking-open-only-fuel-triptype-20260730-plan.md
- Branch: trip-tracking; agent-2 coder worktree
- First action eng-log; phases 1–8 schema→sync→UI→nav

## 2026-07-30 - Phase 1 schema tripType tripTypesJson

- FuelEntry.tripType; Vehicle.tripTypesJson; AppDatabase v17; MIGRATION_16_17 registered
- Building phase 1

## 2026-07-30 - Phase 2 TripTypes helper + inherit

- data/trip/TripTypes.kt pure parse/format/seed/reorder
- VehicleRepository.insertVehicle stamps tripTypesJson from inherit or seed

## 2026-07-30 - Phase 3 Tabular Trip Type columns

- FUEL_HEADERS Trip Type; VEHICLE_HEADERS Trip Types JSON; maps + GoogleSheetsClient delegate

## 2026-07-30 - Phase 4 TripTimeline helpers

- isTripStart, tripStartsForVehicle, currentOpenTrip, buildTripStart

## 2026-07-30 - Phase 5+6 TripTrackingScreen manual + manage types

- Manual vehicle/odo/type, Start + Close(Personal), datetime override
- ManageTripTypesDialog add/rename/reorder → vehicle.tripTypesJson

## 2026-07-30 - Phase 7 camera odo path

- TripTrackingScreen CameraPreview + OcrHarness.runAutoFillPipeline fills vehicle/odo

## 2026-07-30 - Phase 8 nav + docs

- MainActivity route triptracking + drawer after Quick Fill; NAVIGATION_MAP; project-facts Room v17 + trip locations

## 2026-07-30 - Trip tracking plan execution complete

- All phases 1–8 built; tag trip-tracking/builds @ 4d54082a
- Open-only fuel tripType + vehicle tripTypesJson + TripTrackingScreen + sync columns
- Ready to test; no ReportsScreen edits

## 2026-07-30 - merge-trip-tracking-into-minor-fixes: complete

- Plan: dev-ai-interaction/plans/merge-trip-tracking-into-minor-fixes-20260730-plan.md
- Merge: two-parent commit 4273b527 (trip-tracking → minor-fixes). Standard `git merge` blocked by chattr +a ENGINEERING_LOG; used trip-only checkout + manual MainActivity resolve + commit-tree.
- A2: MainActivity Trip Tracking after Quick Fill + Lab/Fuel History/Syncing retained; build SUCCESS (after java_res META-INF perm clean).
- A3: project-facts DB v17 + trip bullets; NAVIGATION_MAP Syncing/Fuel History/Lab; TODO phase-2 tax-mile line.
- B/C: UnitFormat.distanceUnitShortLabel + odometerReadingLabel; TripTrackingScreen wired (no bare mi); Fuel History trip line; Fuel Edit tripType field.
- Tip 8666c3dc; builds tag minor-fixes/builds.
- Residual: devices on DB v16 migrate once; multi-device font checklist separate; PR-minor-fixes.md stale until re-prepare-local-pr; tax reporting deferred.
- No deploy. Ready for user test / later PR prep.

## 2026-07-30 - reports-lab-trip-miles-and-exclude-trip-from-fuel: start

- Magic-approved plan: dev-ai-interaction/plans/reports-lab-trip-miles-and-exclude-trip-from-fuel-20260730-plan.md
- Branch minor-fixes @ 94b55314; baseline builds tag minor-fixes/builds
- Phases 1–6: trip predicate docs, production+Lab fill inventory exclude trips, trip segment helpers, Lab Trip miles screen, hub/nav/facts

## 2026-07-30 - reports-lab-trip-miles-and-exclude-trip-from-fuel: complete

- Phases 1–6 done; tip 8e0c6f5f; builds tag minor-fixes/builds
- Inventory: production + Lab fill counts/lists exclude TripTimeline.isTripStart
- Lab Trip miles: reports_lab/trips, TripSegments, UnitFormat distance labels, TEXT/CSV share
- Residual: implicit personal miles before first start out of scope; tax PDF n/a; re-prepare PR when ready

## 2026-07-30 - docs-rules-ui-compat-agent-guidance: start

- Magic-approved plan: dev-ai-interaction/plans/docs-rules-ui-compat-agent-guidance-20260730-plan.md
- Branch minor-fixes @ 6b27d49c; phases: trip-miles nits N1–N4 then UI_COMPATIBILITY + mandates/docs

## 2026-07-30 - docs-rules-ui-compat-agent-guidance: complete

- Plan: docs-rules-ui-compat-agent-guidance-20260730-plan.md
- N1–N4 fixed; UI_COMPATIBILITY.md + AGENT_MANDATES Compose UI section + CONTRIBUTING/facts/metrics/USER_GUIDE
- Tip 9174744d; builds tag minor-fixes/builds; no PR/history rewrite

## 2026-07-30 - ui-consistency-cards-density-theme: start

- Magic-approved plan: dev-ai-interaction/plans/ui-consistency-cards-density-theme-20260730-plan.md
- Branch minor-fixes; shared TappableCard/AdaptiveItemGrid/empty/date/cancel + apply in-scope UIs + theme accents + Material Save icons + UI_COMPAT docs

## 2026-07-30 - ui-consistency-cards-density-theme: complete

- Plan: ui-consistency-cards-density-theme-20260730-plan.md
- UiChrome primitives; Material Save/PhotoLibrary; theme accents; date/header/cancel unify
- Lists: Expense, Fuel History, Reports multi-col AdaptiveItemGrid, Lab hub/fills Cards, Syncing TappableCard
- UI_COMPATIBILITY.md sections 11–15 (Cards, density, theme, icons, shared controls)
- Residual: Import pending list not fully re-gridded; experiments untouched; camera chrome fixed contrast kept
- builds tag minor-fixes/builds

## 2026-07-30 - fix-adaptive-item-grid-measure: start

- Magic-approved plan: dev-ai-interaction/plans/fix-adaptive-item-grid-measure-20260730-plan.md
- Fix natural measure (Infinity wrap), remove 148.dp floor, TappableCard wrap, strip fillMaxWidth on grid children

## 2026-07-30 - fix-adaptive-item-grid-measure: complete

- Natural measure: Constraints(0, Infinity) + wrapContentWidth(unbounded); clamp natural to W; no 148.dp floor
- Layout pass: equal cellW fill; TappableCard fillMaxWidth fills cell only
- Reports VehicleSummaryBlock / Last5 wrapContentWidth; AdaptiveStatsText handles unbounded max
- UI_COMPATIBILITY §12 + project-facts helper contract
- Tip builds tag minor-fixes/builds; Phase 5 device check (5554 multi-col Lab hub) for user after install

## 2026-07-30 - minor-fixes: local PR prepared (history cleanup)

- Soft-reset cleanup: backup-minor-fixes @ 56b83864; 33 messy commits → 10 logical; TREE_MATCHES_BACKUP.
- Cleaned tip 3cc40826; post-cleanup ./build_app SUCCESS; minor-fixes/builds updated.
- ./generate_pr.sh → dev-ai-interaction/PRs/PR-minor-fixes.md (11 plans + Coder pre-submit).
- Ready for Master (run-grok-master) independent review + merge. Coder does not merge.

## 2026-07-30 - Master merge PR-minor-fixes

- Merged minor-fixes via merge-branch-into-master.sh (FF index path)
- POST-MERGE: 49 .kt paths staged (gate PASS)
- Special files: master TODO base + closed Trip recording future work; tax-mile phase-2 + i18n future kept; project-facts from branch orientation; CHANGELOG 2026-07-30 audit + minor-fixes section preserved
- Advanced reports left open (Lab experimental)


## 2026-08-01 - Master TODO review (code vs backlog)

- Closed: Advanced reports (Reports Lab shipped on master; multi-select remains separate deferred work)
- Kept open OnlyOffice/Collabora (real backends still wanted; stub/catalog only)
- Annotated: Location Lookup + EXIF/GPS (done on ui-followups, not master tip)
- Annotated: ConflictResolutionScreen exists but unwired to identification
- Future work: trip tax free-text reworded to remaining polish
- Still open (valid): LITE_BUILD_TAILOR, 16k pages, polarity, landmarks remove, BufferSet audit, multi-currency, expense multi-vehicle UI, email import/hook, MSAL, deep linking, GPS currency, ODB-II, Play Store, pump experiment UI removal, schema docs, NDK subproject, missed fill logging, i18n packs


## 2026-07-26 - reports-field-conditional-economy-chains-20260726

- Execution start for approved plan: reports-field-conditional-economy-chains-20260726-plan.md
- Branch: batch_load; no prior batch_load/builds tag (first successful ./build_app will create it)
- Scope: ReportsScreen.kt field-conditional MPG/$/mi chains + REPORTS_METRICS.md; phase 5 emulator probe after user deploy

## 2026-07-26 - reports-field-conditional-economy-chains-20260726 phases 1-4 done

- Phases 1-4 implemented and built on batch_load
- ReportsScreen.kt: full fill = odo+cost+vol+!partial; MPG breakers; $/mi segment sum
- docs/reference/REPORTS_METRICS.md rewritten to match
- Tag: batch_load/builds @ 3d29b986 (batch_load-start-4-g3d29b986)
- Phase 5 blocked on user APK deploy to emulator-5554 (device currently has versionName=instruction-start-8-g7d8aa877, not batch_load build)

## 2026-07-26 - reports-field-conditional-economy-chains phase 5 probe PASS

- Backup: test-data-backups/emulator-5554-20260726-095208
- App on emulator-5554: batch_load-start-5-ge76bbc73
- Probe cases odo_only / blank / cost_only / volume_only on Honda between full fills 2→3
- UI Honda: baseline last 15.8 avg 17.0 $/mi 0.259; odo_only same; blank avg 15.8 $/mi 0.260; cost_only avg 15.8 $/mi 0.276; volume_only avg 14.5 $/mi 0.260
- Evidence: dev-ai-interaction/research/reports-chain-probe-20260726/
- DB restored from pre-probe backup; no leftover PROBE rows

## 2026-07-26 - reports-field-conditional-economy-chains PR prepared

- Pre-submit review PASS vs plan; device probe PASS; DB restored
- Local PR: dev-ai-interaction/PRs/PR-batch_load.md
- Tag: batch_load/builds @ 1de13e02; backup-batch_load set
- Ready for Master independent review + merge

## 2026-07-26 - batch-load-ingest-merge-questions-20260726

- Execution start: Stage A (batch ingest) per approved plan batch-load-ingest-merge-questions-20260726-plan.md
- Baseline tag: batch_load/builds @ a02dc9bc; Stages B–C deferred to later turn if A fills the turn

## 2026-07-26 - batch-load Stage A complete (ingest)

- Plan: batch-load-ingest-merge-questions-20260726-plan.md Stage A only
- Added: PhotoExifMeta, FuelPhotoJson, BatchImportPending, BatchFuelImportCoordinator, hardDeleteFuelEntry
- Set I hybrid in PumpCostVolUtils.runSetICostVolExtraction + OcrHarness.runPumpCostVolPipelineSetI
- Import Old Pictures UI: Run batch import / Cancel / Review questions (list-only)
- Hilt ViewModel in ui.batch (not ui.import — Java keyword breaks KSP)
- Builds tag: batch_load/builds @ 6efd2743
- Stages B (merge) and C (apply answers) remain on same plan path — not in this turn

## 2026-07-26 - batch-dash-match-experiment-set-j-20260726

- Execution start: unify Set J (experiment + batch + Quick Fill) per plan
- Baseline: batch_load/builds @ 99d1043c

## 2026-07-26 - batch-setj approach: copy experiment logic into production

- User direction: do NOT extract shared dual-call API; copy experiment Set J into production
- Experiment remains free to tinker (future compile-out); production batch/Quick Fill own a frozen copy
- Continue plan batch-dash-match-experiment-set-j-20260726-plan.md with this approach

## 2026-07-26 - batch-dash-match-experiment-set-j production copy done

- OcrHarness.runSetJPipeline rewritten as independent copy of experiment Set J (Raw+Bin-Trials+connectSegmentsH+pickBestOdometer)
- ExperimentAlignmentScreen NOT shared — free to tinker
- Batch parseSetJOdometer: 4-7 pure digits only (no concat soup)
- Tag: batch_load/builds @ 38186950
- Device parity CSV pending user deploy + re-import after purging old batch dash rows
- Notes: dev-ai-interaction/research/batch-setj-parity-20260726/NOTES.md

## 2026-07-26 - production-setj-ref-geometry-parity-20260726

- Execution start: fix production Set J ref geometry (probe/4080, ICRS crop, Raw nestFilter)
- Baseline: batch_load/builds @ d51f30d9

## 2026-07-26 - production-setj-ref-geometry-parity done

- Probed ref dims for landmarks+align; fallback 4080×3072
- ICRS Float createCrop for odo window; Raw no nestFilter
- Experiment screen untouched (independent copy)
- Tag: batch_load/builds @ de113410
- Device compare pending user deploy; notes in research/setj-geometry-parity-20260726/

## 2026-07-26 - batch import limited button (first 20+20)

- Import UI: OutlinedButton "First 20 dash + first 20 pump" (name-sorted take)
- BatchFuelImportCoordinator.runIngest(maxDash, maxPump); LIMITED_IMPORT_COUNT=20

## 2026-07-26 - batch pump ingest without vehicle

- processPump always runs Set I; inserts with UNASSIGNED_VEHICLE_ID=0
- No ASSIGN_VEHICLE gate; merge later assigns vehicle via time/location pairing
- Unreadable pumps still pending only

## 2026-07-26 - batch-setj-reverify-and-pending-answers-20260726

- Execution start; device versionName=batch_load-start-21-gf43bb991 (geometry + unassigned pump)
- Part A first-20 re-verify then Part B clickable pending answers

## 2026-07-26 - batch-setj-reverify + pending answers

- Part A first-20 re-verify on f43bb991: 17 dash inserts + 20 pump vehicleId=0
- first20 score: see research/batch-setj-parity-20260726/first20-after-geometry.txt (gate FAIL — residual leading-digit/blank DIFFs)
- Part B: clickable pending UI + forcedVehicleId dash reprocess + skip/retry pump (build 06707a0a)
- Experiment untouched; deploy needed for Part B on device

## 2026-07-27 - batch-mirror-experiment-pipelines-setj-seti

- Execution start: batch must run experiment Set J / Set I pipelines, not OcrHarness reinvented front-end
- Baseline tag: batch_load/builds @ c112e80e

## 2026-07-27 - batch-mirror-experiment-pipelines-setj-seti done (code)

- AlignmentSetJRunner: experiment Set A ID lock + Set J runPaddleValleyIterative for batch dash
- PumpSetIRunner: experiment-equivalent Set I for batch pump
- BatchFuelImportCoordinator no longer uses runAutoFillPipeline / runPumpCostVolPipelineSetI
- Tag: batch_load/builds @ 7653d156
- Device parity first-20 still needs deploy of this APK + re-run (prior c112e80e re-verify failed)

## 2026-07-27 - Batch Stage A full-run parity PASS; Stage B plan ready

- Full batch finished (~02:35 PDT); app idle after last pump `PXL_20260411_201506380.jpg`.
- Selective purge: deleted 74 old `batch_import%` rows (`updatedAt < 1785142500000`); kept 12 non-batch + 290 this-run (ids 219–508); pending cleaned to 6 this-run items.
- Parity vs experiment: dash Set J 142 OK / 0 DIFF (4 no-vehicle pending); pump Set I 148 OK / 0 DIFF (2 unreadable both sides). Report: `dev-ai-interaction/research/batch-run-parity-20260727/`.
- Next coder plan: `dev-ai-interaction/plans/batch-stage-b-merge-engine-coder-20260727-plan.md` (merge engine + Run merge). Durable B+C: `batch-stage-b-merge-and-stage-c-questions-20260727-plan.md`.

## 2026-07-27 - batch-stage-b-merge-engine

- Execution start: Stage B merge engine per batch-stage-b-merge-engine-coder-20260727-plan.md
- Stage A parity PASS; baseline tag 83e36ca0

## 2026-07-27 - batch-stage-b-merge-engine B1–B4 code

- B1: FuelPhotoJson.unionPhotos / addPumpPhoto / addDashPhoto
- B2: FuelRowMergeEngine.planMerge (vehicle clusters ±45m, vehicleId=0 pump pair, sequence vs re-shot, CONFLICT_ODO + photo paths, hard-delete list)
- B3: BatchFuelImportCoordinator.applyMerge + Import **Run merge** button
- B4: Merge after import checkbox default **off**
- Fixtures notes: dev-ai-interaction/research/batch-stage-b-merge-fixtures-20260727.md
- Tag: batch_load/builds @ 13d7c06b
- Device Run merge still to verify after deploy

## 2026-07-27 - batch-stage-b-merge-engine device Run merge

- Deploy: adb install -r (./deploy re-exec loop as ai-coder; used direct install)
- Pre: 302 rows, v0=148, fulls=6, odo_only=140, pump_only=154
- applyMerge: updated=133 deleted=129 pending+=2 → tag code 13d7c06b
- Post: 173 rows, v0=14, fulls=130, odo_only=16, pump_only=25
- Non-batch: 9 kept (absorbed re-shot ids 6–8 same $18.40); full non-batch fills preserved
- CONFLICT_ODO pending: 9594 vs 9698; 198699 vs 98699 (photo paths attached)
- Summary: dev-ai-interaction/research/batch-stage-b-merge-20260727/merge-run-summary.txt
- Stage C deferred (image-first questions, assign→re-merge UI)

## 2026-07-27 - batch-stage-c-image-questions

- Execution start: Stage C image-first pending questions per batch-stage-c-image-questions-coder-20260727-plan.md
- Stage B done on device; tip batch_load/builds @ 0c93922c

## 2026-07-27 - batch-stage-c-image-questions done

- C1: pendingPhotoUris + image thumbs/zoom; DNG placeholder; filename secondary only
- C2: CONFLICT_ODO Keep odo / Keep both; pure wrong-odo hard-delete; pump data odo zeroed
- C3: successful answers auto applyMerge; skip does not re-merge
- Device: Keep odo 9594 → pending 8→7 + remerge updated=16 deleted=1; Skip → pending 6
- Tag: batch_load/builds @ f360ce24
- Notes: dev-ai-interaction/research/batch-stage-c-questions-20260727/smoke-notes.txt
- Residual: 1 CONFLICT_ODO, 3 no-vehicle dash, 2 unreadable pump; AMBIGUOUS_MULTI_PUMP unused

## 2026-07-27 - batch-stage-c-photo-ux-manual-entry

- Execution start: photo UX, manual entry, economyIgnored sync, tank heuristic, unknown vehicle, outliers
- Parent tip: batch_load/builds @ 27d92a09

## 2026-07-27 - batch-stage-c-photo-ux-manual-entry done

- Photo stem-dedupe; DNG OpenCV preview; fullscreen +/− + sticky actions
- Manual pump/dash/conflict odo; economyIgnored Room v14 + sync column Economy Ignored
- Reports: fills N(Mp); Unknown label; economy excludes ignored; avg filters 3× outliers
- Merge: tank maxFill+5 auto-assign; post-merge enqueue unknown + MPG_OUTLIER
- Device: merge pending+=52 (9 unknown, 42 outliers); tag batch_load/builds @ aa9b1e53
- Notes: research/batch-stage-c-photo-ux-20260727/smoke-notes.txt

## 2026-07-27 - batch-merge-window-odo-sanity

- Execution start: 15m merge window, tight dash/pump pairs, odo reverse/gap sanitizer, Flag partial, per-vehicle unknown context
- Parent tip: batch_load/builds @ 3f915150

## 2026-07-27 - batch-merge-window-odo-sanity done

- MERGE_WINDOW_MS=15m; splitTightDashPumpPairs for multi dash+pump
- FuelOdoSanitizer: gap (maxVol×mpg×3, no fallback) then reverse; bias demote later
- ODO_SUSPECT pending; FlagPartial action; per-vehicle nearest for unknown
- Device: sanitize=31, ODO_SUSPECT=31 (incl. 20119 gap); CONFLICT_ODO=1
- Tag: batch_load/builds @ 1fbe913e
- Notes: research/batch-merge-window-odo-sanity-20260727/smoke-notes.txt

## 2026-07-27 - batch-pending-gap-dedupe-timefmt

- Execution start: Mark as gap, pending rebuild/dedupe, formatTimeDelta, Clear & re-scan
- Parent tip: batch_load/builds @ 25df6455

## 2026-07-27 - batch-pending-gap-dedupe-timefmt done (code)

- Mark as gap (blank odo/cost/vol); pending full rebuild on merge; (kind,fuelEntryId) dedupe
- formatTimeDelta for neighbor/unknown deltas; Clear questions & re-scan button
- Sanitizer skips already-partial / blank; clear all pending kinds per answered fuelEntryId
- Tag: batch_load/builds @ e743f48a
- Verify: ./build_app only — no deploy/device smoke (coder not authorized to deploy for test)
- Notes: research/batch-pending-gap-dedupe-timefmt-20260727/smoke-notes.txt

## 2026-07-27 - batch-mpg-outlier-ui-clarity

- Execution start: MPG outlier UI clarity per batch-mpg-outlier-ui-clarity-20260727-plan.md
- Tip: batch_load/builds @ 47ddcf81
- Device verify only after human deploy (coder will not adb install/deploy)

## 2026-07-27 - batch-mpg-outlier-ui-clarity done (code)

- MPG_OUTLIER: end-only primary photos; leg start/end text; focus toggle prior/end
- Edit/flag/ignore/gap target focusEntryId; nearby badges LEG START / THIS FILL
- Tag: batch_load/builds @ 795c8e3b
- No device test (await human deploy)
- Notes: research/batch-mpg-outlier-ui-clarity-20260727/smoke-notes.txt

## 2026-07-27 - batch-mpg-outlier-ui-clarity (layout v2)

- Re-execute updated plan: Last/This photo blocks + 4-line context
- Prior UI (single strip + toggle) superseded by locked two-block layout

## 2026-07-27 - batch-mpg-outlier-ui-clarity layout v2 done (code)

- Two-block Last/This photos above each button; 4-line text context (before/last/this/after)
- Focus default This fill; edit/flag/ignore/gap target focus id
- toPending: thisPhotoPaths + lastPhotoPaths
- Tag: batch_load/builds @ c272c521
- No device test until human deploy
- Notes: research/batch-mpg-outlier-ui-clarity-20260727/smoke-notes.txt

## 2026-07-27 - batch-partial-flag-semantics + sanitizer-correctness

- Execution start: two plans
  - batch-partial-flag-semantics-20260727-plan.md
  - batch-partial-and-sanitizer-correctness-20260727-plan.md
- No device deploy (human deploys before test)

## 2026-07-27 - partial-flag + sanitizer-correctness done (code)

- isPartialFill explicit-only; inserts/merge/sanitizer never auto-true for incomplete
- FuelOdoSanitizer detect-only (reverse, digit_jump, gap with clean mpg); no demote updates
- Checkbox SetPartialFill; clearAutoPartialFlags repair button
- Reports display band 5–80 + formatMpg n/a outside 1–100
- Quick Fill: isPartialFill=false on save
- Tag: batch_load/builds @ ec44b782
- No device test until human deploy
- Notes: research/batch-partial-sanitizer-correctness-20260727/smoke-notes.txt

## 2026-07-27 - batch-odo-suspect-ui-per-fill

- Execution start: ODO_SUSPECT per-fill UI layout
- No device deploy until human deploys

## 2026-07-27 - batch-odo-suspect-ui-per-fill done (code)

- ODO_SUSPECT: ordered prev/cur/next dash-only photos + odo fields under each
- SaveOdoPeers multi-odo write; payload prevDashPaths/curDashPaths/nextDashPaths
- Tag: batch_load/builds @ 0da926d1
- No device test until human deploy; re-scan after deploy
- Notes: research/batch-odo-suspect-ui-per-fill-20260727/smoke-notes.txt

## 2026-07-27 - batch-sync-cross-device-partials-and-titlebar

- Execution start: soft-delete sync, origin-device partials, title bar logo/version
- No device deploy until human deploys

## 2026-07-27 - batch-sync-cross-device-partials-and-titlebar (in progress)

- Completing MainActivity yellow bar + remaining CTA; no device deploy


## 2026-07-27 - batch-sync-cross-device-partials-and-titlebar done (code)

- Merge: all live partials (sync/QF/batch); soft-delete published absorbs; lat/lon later-ts
- hasUnmatchedPartials + post-sync toast CTA (no auto-merge)
- Yellow title-bar ?N → import?review=1 expand Review questions; Help bullets
- Tag: batch_load/builds @ 5e7de408
- No device test until human deploy
- Notes: research/batch-sync-cross-device-partials-and-titlebar-20260727/smoke-notes.txt


## 2026-07-27 - batch-mpg-gap-button-and-window-fills done (code)

- MPG card: vertical Save / Missing data between last & this / Ignore (no clip)
- markAsGap MPG: insert mid-leg blank; anchors preserved; dismiss if breaker exists
- FuelEconomyChains + breaker-aware detectOutliers; windowSummary inventory
- Post-sync applyMerge after fuel LWW; SYNC_BEHAVIOR + REPORTS_METRICS
- Tag: batch_load/builds @ a50da18f
- No device test until human deploy
- Notes: research/batch-mpg-gap-button-and-window-fills-20260727/smoke-notes.txt


## 2026-07-27 - batch-no-durable-photo-copy done (code)

- processDash/processPump: source paths only; copyToDurable removed
- migrateDurablePhotoRefsToSource rewrite+delete unreferenced mirrors
- PendingPhotoUris prefer source dirs; Help note
- Tag: batch_load/builds @ a18099d1
- No device test until human deploy
- Notes: research/batch-no-durable-photo-copy-20260727/smoke-notes.txt


## 2026-07-28 - batch-stage-c-phased-questions done (code)

- StageCPhase 1–6 store + UI filter; skip/reset; clear-rescan → phase 1
- ODO chain merge + simple length-guess mode; BAD_PUMP_RATIO phase 3
- Gap from phase 3/5; post-sync remoteWins → phase 1 + rebuild
- Tag: batch_load/builds @ 8ad579ef
- No device test until human deploy
- Notes: research/batch-stage-c-phased-questions-20260728/smoke-notes.txt


## 2026-07-28 - batch-stage-c-ux-skip-photos-phase-scope done (code)

- Phase-scoped pending rebuild; Next phase regenerates only next kinds
- Skip ledger same-phase; answer journal jsonl + export (no replay)
- Unassigned vehicle id=0 fixed syncId + Fuel - Unassigned tab; picker exclusions
- Photo role dash/pump; Close z-order; CONFLICT keep-both completes
- Tag: batch_load/builds @ dab2e144
- No device test until human deploy
- Notes: research/batch-stage-c-ux-skip-photos-phase-scope-20260728/smoke-notes.txt


## 2026-07-28 - PR-batch_load prepared for Master review

- History cleaned: 57 commits → 5 logical; backup-batch_load @ ff33f355; cleaned HEAD @ 11a2c1ca
- PR: dev-ai-interaction/PRs/PR-batch_load.md (pre-submit review + plans)
- Not rebased onto master (simplify_experiments); Master merge expects eng-log special handling + possible ExperimentAlignmentScreen conflict
- Tree matches pre-cleanup tip; build_app SUCCESS after cleanup


## 2026-07-28 - Master merge PR-batch_load

- Independent review PASS; non-FF merge with simplify_experiments overlap
- merge-branch-into-master failed on +a eng-log (double/triple append side effect); completed via merge-tree + manual resolve
- ExperimentAlignmentScreen: keep Set-J-only simplify + internal helpers for AlignmentSetJRunner (dropped batch dead createScaledBase64/drawCropBoxes conflict side)
- project-facts: batch orientation + Room v14 from branch; TODO unchanged
- Commit 1bfab017; build SUCCESS after java_res permission retry; builds tag updated
- Worktree ENGINEERING_LOG.md may still have triple-appended tail (+a); committed blob is single third-version + this note — fix with sudo chattr -a when available
- No works tag. Cleanup: ./remove_worktree.sh batch_load when ready


## 2026-07-30 - Master merge PR-improve-merges

- Independent review APPROVE; audit_merge FF SUCCESS
- merge-branch-into-master.sh improve-merges (FF index path); ve-englog attempted dup tail → restored index to HEAD eng-log (no branch eng-log delta)
- Worktree ENGINEERING_LOG still has prior +a duplicate tail (skip-worktree for integrity); needs sudo chattr -a + restore when available
- project-facts: Room v16 + MergeAck locations
- Commit 4011fddd on tip after feature commits; builds tag @ 4011fddd
- BUILD SUCCESSFUL; no works tag
- Cleanup: ./remove_worktree.sh improve-merges from repo root when ready


## 2026-07-30 - EXEC START: minor-fixes-batch-20260730-plan.md

- Branch: minor-fixes
- Plan: dev-ai-interaction/plans/minor-fixes-batch-20260730-plan.md
- Scope: Waves A–I (settings, About feedback, debug UX, Syncing page, QF notes, photo sync refs-only, fuel download API, Fuel History/edit + on-demand fetch)
- Wave J host research not execution-build

## 2026-07-30 - EXEC DONE: minor-fixes-batch-20260730-plan.md (Waves A–I)

- Wave A: fuel photos label; pump Amazon album URL; max red boxes experiment-gated
- Wave B–C: About feedback email + DeviceFeedbackInfo; debug QF compact X/max Send Delete + info
- Wave D: SyncingScreen + drawer route; Settings loses sync block; red ! → syncing
- Wave E: Quick Fill Notes field + persist/clear
- Wave F: photo FULL/PENDING no expense bulk download; pending downloads = vehicle refs only
- Wave G: scrubUnreadable fuel/expense; downloadFuelPhoto; Fuel DAO getById + VM APIs
- Wave H: Fuel History tabs + FuelEdit; fetch-from-archive on history/edit/expense/batch pending
- Wave I: project-facts orientation for Syncing/Fuel History/on-demand photo policy
- Wave J host research: not in this execution (sandbox planner)
- builds tag tip: minor-fixes/builds @ 54f64e2f (pre–project-facts commit)

## 2026-07-30 - EXEC START: reports-lab-experimental-hub-20260730-plan.md

- Branch: minor-fixes
- Scope: Reports Lab hub + 6 report sets, filters, share TEXT/CSV, Vico charts; production ReportsScreen unchanged
- Waves A–E

## 2026-07-30 - EXEC DONE: reports-lab-experimental-hub-20260730-plan.md

- Reports Lab hub + 6 sets under ui/reports/lab/; drawer always visible
- Filters + prefs (vehicle/period/custom); teaser KPIs; empty states
- Share TEXT/CSV each set; Vehicle summary pack + VIN checkbox (default off)
- Vico 3.2.3 compose + compose-m3: MPG line, unit-price line, monthly bars, category bars
- Production ReportsScreen.kt UNCHANGED
- builds: minor-fixes/builds @ 2251a8f8 (+ project-facts phase next)

## 2026-07-30 - EXEC START: minor-fixes-gaps-nits-followup-20260730-plan.md

- Branch: minor-fixes
- Gaps G1–G5: expenseHasPendingWork; Fuel History fetch label/await; fuel pump_N roles; host photo-kind REPORT

## 2026-07-30 - EXEC DONE: minor-fixes-gaps-nits-followup-20260730-plan.md

- G2: removed dead expenseHasPendingWork + unused expenseNeedsDownload
- G3+G4: Fuel History Fetch image from archive + await Fetching…
- G5: fuelRoleForTag / fuelTagFromRole for pump_N; wired upload+pending+download
- G1: photo-kind-classify script + metrics.json + REPORT.md (5-NN ~88.7%, recommendation: needs human confirm)
- builds: minor-fixes/builds @ 7f9ab6c3

## 2026-07-30 - EXEC START: unit-i18n-consistency-20260730-plan.md

- Branch: minor-fixes
- Plan path: dev-ai-interaction/plans/unit-i18n-consistency-20260730-plan.md

## 2026-07-30 - EXEC DONE: unit-i18n-consistency-20260730-plan.md

- VolumeUnits.formatVolume (+ Context overload); space-before-unit
- UnitFormat: mpg / $/mi / distanceDeltaLabel façade
- Quick Fill convert → VolumeUnits.convert; Fuel History/Edit labels; batch neighbor currency+volume
- Lab formatVolume delegates; Reports + Lab high-traffic economy labels via UnitFormat
- project-facts + TODO i18n deferred pointer
- builds: minor-fixes/builds @ 04efeb68

## 2026-07-30 - EXEC START: form-icons-fontscale-startup-20260730-plan.md

- Branch: minor-fixes
- Plan: form-icons-fontscale-startup-20260730-plan.md

## 2026-07-30 - Phase B5 note: Quick Fill font-scale

- No QF code change: existing responsive A/B/C layout retained; multi-device large-font check deferred to 5554/5556 handoff

## 2026-07-30 - EXEC DONE: fix-icons-fontscale-startup-20260730-plan.md

- R1: material-icons-core + extended (BOM)
- R5: removed copyTessdataOnce
- R2: TopAppBar wrap, Settings debug stack, Fuel History fetch multi-line, Lab banner/title softWrap
- R3: removed dead volumeLabel
- R4: Lab UnitFormat for MPG chart/summary
- R2 B5: QF no change (responsive layout retained)
- builds: minor-fixes/builds @ bb63b72f

## 2026-07-30 - Startup smoke: drop Vico compose-m3

- compose-m3 pulled material3 1.4 → NoSuchMethodError ExposedDropdownMenuBox on QuickFill
- Keep vico:compose:3.2.3 only; cold start emulator-5554 OK (pid live, no Icons/tessdata FATAL)
- Final builds: minor-fixes/builds @ 26095264 (+ facts tip)

## 2026-07-30 - minor-fixes: local PR prepared (history cleanup)

- Soft-reset history cleanup: `git tag -f backup-minor-fixes` @ pre-cleanup tip `c4222079`; `git reset --soft master`; six logical commits; TREE_MATCHES_BACKUP (`HEAD^{tree}` == backup tree `b434f073`).
- Cleaned tip `d04fb7c7` (docs); post-cleanup `./build_app` SUCCESS; `minor-fixes/builds` updated to cleaned tip.
- Pre-submit review vs five plans: scope OK; residual risks = multi-device font checklist + Lab experimental + i18n deferred.
- `./generate_pr.sh` → `dev-ai-interaction/PRs/PR-minor-fixes.md` (plans embedded + Coder pre-submit section).
- Ready for Master (`run-grok-master`) independent review + merge. Coder does not merge.

## 2026-07-30 - Trip tracking open-only fuel tripType execution start

- Approved plan: dev-ai-interaction/plans/trip-tracking-open-only-fuel-triptype-20260730-plan.md
- Branch: trip-tracking; agent-2 coder worktree
- First action eng-log; phases 1–8 schema→sync→UI→nav

## 2026-07-30 - Phase 1 schema tripType tripTypesJson

- FuelEntry.tripType; Vehicle.tripTypesJson; AppDatabase v17; MIGRATION_16_17 registered
- Building phase 1

## 2026-07-30 - Phase 2 TripTypes helper + inherit

- data/trip/TripTypes.kt pure parse/format/seed/reorder
- VehicleRepository.insertVehicle stamps tripTypesJson from inherit or seed

## 2026-07-30 - Phase 3 Tabular Trip Type columns

- FUEL_HEADERS Trip Type; VEHICLE_HEADERS Trip Types JSON; maps + GoogleSheetsClient delegate

## 2026-07-30 - Phase 4 TripTimeline helpers

- isTripStart, tripStartsForVehicle, currentOpenTrip, buildTripStart

## 2026-07-30 - Phase 5+6 TripTrackingScreen manual + manage types

- Manual vehicle/odo/type, Start + Close(Personal), datetime override
- ManageTripTypesDialog add/rename/reorder → vehicle.tripTypesJson

## 2026-07-30 - Phase 7 camera odo path

- TripTrackingScreen CameraPreview + OcrHarness.runAutoFillPipeline fills vehicle/odo

## 2026-07-30 - Phase 8 nav + docs

- MainActivity route triptracking + drawer after Quick Fill; NAVIGATION_MAP; project-facts Room v17 + trip locations

## 2026-07-30 - Trip tracking plan execution complete

- All phases 1–8 built; tag trip-tracking/builds @ 4d54082a
- Open-only fuel tripType + vehicle tripTypesJson + TripTrackingScreen + sync columns
- Ready to test; no ReportsScreen edits

## 2026-07-30 - merge-trip-tracking-into-minor-fixes: execution start

- Magic-approved plan: dev-ai-interaction/plans/merge-trip-tracking-into-minor-fixes-20260730-plan.md
- Branch minor-fixes @ 4cecb088; merging trip-tracking @ f135917f
- Baseline builds tag: minor-fixes/builds

## 2026-07-30 - Trip tracking open-only fuel tripType execution start

- Approved plan: dev-ai-interaction/plans/trip-tracking-open-only-fuel-triptype-20260730-plan.md
- Branch: trip-tracking; agent-2 coder worktree
- First action eng-log; phases 1–8 schema→sync→UI→nav

## 2026-07-30 - Phase 1 schema tripType tripTypesJson

- FuelEntry.tripType; Vehicle.tripTypesJson; AppDatabase v17; MIGRATION_16_17 registered
- Building phase 1

## 2026-07-30 - Phase 2 TripTypes helper + inherit

- data/trip/TripTypes.kt pure parse/format/seed/reorder
- VehicleRepository.insertVehicle stamps tripTypesJson from inherit or seed

## 2026-07-30 - Phase 3 Tabular Trip Type columns

- FUEL_HEADERS Trip Type; VEHICLE_HEADERS Trip Types JSON; maps + GoogleSheetsClient delegate

## 2026-07-30 - Phase 4 TripTimeline helpers

- isTripStart, tripStartsForVehicle, currentOpenTrip, buildTripStart

## 2026-07-30 - Phase 5+6 TripTrackingScreen manual + manage types

- Manual vehicle/odo/type, Start + Close(Personal), datetime override
- ManageTripTypesDialog add/rename/reorder → vehicle.tripTypesJson

## 2026-07-30 - Phase 7 camera odo path

- TripTrackingScreen CameraPreview + OcrHarness.runAutoFillPipeline fills vehicle/odo

## 2026-07-30 - Phase 8 nav + docs

- MainActivity route triptracking + drawer after Quick Fill; NAVIGATION_MAP; project-facts Room v17 + trip locations

## 2026-07-30 - Trip tracking plan execution complete

- All phases 1–8 built; tag trip-tracking/builds @ 4d54082a
- Open-only fuel tripType + vehicle tripTypesJson + TripTrackingScreen + sync columns
- Ready to test; no ReportsScreen edits

## 2026-07-30 - merge-trip-tracking-into-minor-fixes: complete

- Plan: dev-ai-interaction/plans/merge-trip-tracking-into-minor-fixes-20260730-plan.md
- Merge: two-parent commit 4273b527 (trip-tracking → minor-fixes). Standard `git merge` blocked by chattr +a ENGINEERING_LOG; used trip-only checkout + manual MainActivity resolve + commit-tree.
- A2: MainActivity Trip Tracking after Quick Fill + Lab/Fuel History/Syncing retained; build SUCCESS (after java_res META-INF perm clean).
- A3: project-facts DB v17 + trip bullets; NAVIGATION_MAP Syncing/Fuel History/Lab; TODO phase-2 tax-mile line.
- B/C: UnitFormat.distanceUnitShortLabel + odometerReadingLabel; TripTrackingScreen wired (no bare mi); Fuel History trip line; Fuel Edit tripType field.
- Tip 8666c3dc; builds tag minor-fixes/builds.
- Residual: devices on DB v16 migrate once; multi-device font checklist separate; PR-minor-fixes.md stale until re-prepare-local-pr; tax reporting deferred.
- No deploy. Ready for user test / later PR prep.

## 2026-07-30 - reports-lab-trip-miles-and-exclude-trip-from-fuel: start

- Magic-approved plan: dev-ai-interaction/plans/reports-lab-trip-miles-and-exclude-trip-from-fuel-20260730-plan.md
- Branch minor-fixes @ 94b55314; baseline builds tag minor-fixes/builds
- Phases 1–6: trip predicate docs, production+Lab fill inventory exclude trips, trip segment helpers, Lab Trip miles screen, hub/nav/facts

## 2026-07-30 - reports-lab-trip-miles-and-exclude-trip-from-fuel: complete

- Phases 1–6 done; tip 8e0c6f5f; builds tag minor-fixes/builds
- Inventory: production + Lab fill counts/lists exclude TripTimeline.isTripStart
- Lab Trip miles: reports_lab/trips, TripSegments, UnitFormat distance labels, TEXT/CSV share
- Residual: implicit personal miles before first start out of scope; tax PDF n/a; re-prepare PR when ready

## 2026-07-30 - docs-rules-ui-compat-agent-guidance: start

- Magic-approved plan: dev-ai-interaction/plans/docs-rules-ui-compat-agent-guidance-20260730-plan.md
- Branch minor-fixes @ 6b27d49c; phases: trip-miles nits N1–N4 then UI_COMPATIBILITY + mandates/docs

## 2026-07-30 - docs-rules-ui-compat-agent-guidance: complete

- Plan: docs-rules-ui-compat-agent-guidance-20260730-plan.md
- N1–N4 fixed; UI_COMPATIBILITY.md + AGENT_MANDATES Compose UI section + CONTRIBUTING/facts/metrics/USER_GUIDE
- Tip 9174744d; builds tag minor-fixes/builds; no PR/history rewrite

## 2026-07-30 - ui-consistency-cards-density-theme: start

- Magic-approved plan: dev-ai-interaction/plans/ui-consistency-cards-density-theme-20260730-plan.md
- Branch minor-fixes; shared TappableCard/AdaptiveItemGrid/empty/date/cancel + apply in-scope UIs + theme accents + Material Save icons + UI_COMPAT docs

## 2026-07-30 - ui-consistency-cards-density-theme: complete

- Plan: ui-consistency-cards-density-theme-20260730-plan.md
- UiChrome primitives; Material Save/PhotoLibrary; theme accents; date/header/cancel unify
- Lists: Expense, Fuel History, Reports multi-col AdaptiveItemGrid, Lab hub/fills Cards, Syncing TappableCard
- UI_COMPATIBILITY.md sections 11–15 (Cards, density, theme, icons, shared controls)
- Residual: Import pending list not fully re-gridded; experiments untouched; camera chrome fixed contrast kept
- builds tag minor-fixes/builds

## 2026-07-30 - fix-adaptive-item-grid-measure: start

- Magic-approved plan: dev-ai-interaction/plans/fix-adaptive-item-grid-measure-20260730-plan.md
- Fix natural measure (Infinity wrap), remove 148.dp floor, TappableCard wrap, strip fillMaxWidth on grid children

## 2026-07-30 - fix-adaptive-item-grid-measure: complete

- Natural measure: Constraints(0, Infinity) + wrapContentWidth(unbounded); clamp natural to W; no 148.dp floor
- Layout pass: equal cellW fill; TappableCard fillMaxWidth fills cell only
- Reports VehicleSummaryBlock / Last5 wrapContentWidth; AdaptiveStatsText handles unbounded max
- UI_COMPATIBILITY §12 + project-facts helper contract
- Tip builds tag minor-fixes/builds; Phase 5 device check (5554 multi-col Lab hub) for user after install

## 2026-07-30 - minor-fixes: local PR prepared (history cleanup)

- Soft-reset cleanup: backup-minor-fixes @ 56b83864; 33 messy commits → 10 logical; TREE_MATCHES_BACKUP.
- Cleaned tip 3cc40826; post-cleanup ./build_app SUCCESS; minor-fixes/builds updated.
- ./generate_pr.sh → dev-ai-interaction/PRs/PR-minor-fixes.md (11 plans + Coder pre-submit).
- Ready for Master (run-grok-master) independent review + merge. Coder does not merge.

## 2026-07-30 - post-merge-verify-and-rehome-continue: start

- Magic-approved plan: dev-ai-interaction/plans/post-merge-verify-and-rehome-continue-20260730-plan.md
- agent-1 on minor-fixes; verify master merge then re-home in place to ui-followups from master tip

## 2026-07-30 - post-merge-verify-and-rehome-continue: verify + re-home complete

- Verify: master 36277aff Merge branch minor-fixes into master; minor-fixes 4fb0f104 is ancestor; master..minor-fixes count 0; minor-fixes..master count 1.
- App on master: UiChrome.kt (TappableCard/AdaptiveItemGrid), UI_COMPATIBILITY.md, ReportsLabHubScreen.kt present.
- Diff minor-fixes vs master: ENGINEERING_LOG/TODO/CHANGELOG special files only (not app loss).
- Re-home in place agent-1: branch ui-followups @ 36277aff (= master). minor-fixes left as historical tip (+ eng-log start commit 86a1271d only).
- AGENT_CONTEXT.md Current Branch ui-followups; Status IDLE. No new agent-N / setup_agent.
- ENGINEERING_LOG worktree uses skip-worktree (+a cannot replace with master blob); master worktree retains full merge eng-log.
- Ready for residual feature plans on ui-followups @ 36277aff (re-homed agent-1; no new agent-N).

## 2026-07-31 - ui-followups-batch-photos-reports-settings: start

- Magic-approved plan: dev-ai-interaction/plans/ui-followups-batch-photos-reports-settings-20260731-plan.md
- Branch ui-followups @ 36277aff; phases 1–8 batch photos, phase4 correlation, Reports chrome, Settings debug, QF Notes, Start trip

## 2026-07-31 - ui-followups-batch-photos-reports-settings: complete

- Plan implemented on ui-followups; tip after build ec492f73 (see builds tag).
- Photos: strict DASH/PUMP pendingPhotoUris; Stage C UI filters paths; no unfiltered candidates union.
- Phase4 suggest: time+location (place/geo 150m) before tank/time.
- Reports: drawer/hub rename; share after content; expenses list catalog; no drawer Fuel History; trip blurb short.
- Settings debug QF: compact Send/Delete icons; QF Notes landscape width; Start trip camera-first + manage types UX.
- Residual: full auto-merge partial/full still via existing merge when assign applied; device smoke recommended.

## 2026-07-31 - ui-followups-phase4-auto-merge-debug-layout: start

- Magic-approved plan: dev-ai-interaction/plans/ui-followups-phase4-auto-merge-debug-layout-20260731-plan.md
- Branch ui-followups; silent merge-time place/time assign + dual-pump partial/full; location backfill; debug QF layout

## 2026-07-31 - complete ui-followups-phase4-auto-merge-debug-layout

- FuelStopMatch shared place/geo 150m; FuelRowMergeEngine assignUnassigned place+time unique vehicle
- Same-stop dual-pump: earlier partial later full (mergeSequenceCluster markEarlierPartial)
- Location/lat/lon backfill via preferLocation + mergeFields when survivor empty
- Settings Debug QF: title·Info·count·Delete·Send·toggle; narrow line2 end-aligned
- build_app 289b01d6 success; criteria 1-6 plan path

## 2026-07-31 - integrate-location-fixes-into-ui-followups: start

- Plan: dev-ai-interaction/plans/integrate-location-fixes-into-ui-followups-20260731-plan.md
- ui-followups@289b01d6 merge location-fixes@90fceb54; blob v18 + union UX

## 2026-07-31 - complete integrate-location-fixes-into-ui-followups

- Merged location-fixes@90fceb54 into ui-followups (merge c1ad3551); tip b0462795 builds
- Room v18 location blob; FuelStopMatch/dual-pump/assign on blob coords; mergeFields mergeBlobs
- Trip/QF union: Start trip UX + GPS/EXIF/LocationConfirmBlock; non-blocking POI race
- Batch: Location enhance with import (default off) kickoff-before-OCR + Run location enhance
- Rate-limit pace ≥1s between lookup kickoffs; never await POI for OCR/save/import completion
- location-fixes branch left at 90fceb54 for further work

## 2026-07-31 - reports-hub-ux-implicit-personal-share: start

- Plan: dev-ai-interaction/plans/reports-hub-ux-implicit-personal-share-20260731-plan.md
- ui-followups@b0462795; implicit personal + hub summary + share icon

## 2026-07-31 - complete reports-hub-ux-implicit-personal-share

- TripSegments: leading Personal in period when no start before window (baseline odo → first start)
- Hub: overall + per-vehicle summary; no teasers/dual blurbs; Info icon; content-width filters
- Share: one icon → TEXT/CSV (PDF coming soon) on child report pages
- TODO: rejected continuous GPS / tax forms / end-trip; trip miles packaging note
- build 4440f5dd

## 2026-07-31 - ui-followups-residual-photos-caret-phase1-odo0: start

- Plan: dev-ai-interaction/plans/ui-followups-residual-photos-caret-phase1-odo0-20260731-plan.md
- Parts A–E: zoom photos, caret keys, hide Unassigned, odo=0 phase1, reports hub filters

## 2026-07-31 - complete ui-followups-residual-photos-caret-phase1-odo0

- A: ZoomablePhotoDialog/Thumb; Fuel Edit + History zoom +/−; Stage C still has +/−
- B: CaretEnabledOutlinedTextField (L/R cancel focus); Stage C simple odo, Fuel Edit, Trip odo, QF odo/notes
- C: forUserPicker/forManageList hide Unassigned on Expense/QF/Trip/Manage/Fuel Edit
- D: FuelOdoSanitizer missing odo=0 + dash → simple ODO_SUSPECT phase 1; Save requires odo>0
- E: Hub all-time no filters; child vehicle list data-bearing incl Unknown; content-width filters retained
- build 0c9e9211 + QF caret follow-up

## 2026-07-31 - ui-followups-residual-gaps-close: start

- Plan: dev-ai-interaction/plans/ui-followups-residual-gaps-close-20260731-plan.md
- Close photo/caret wiring + Unknown label + project-facts

## 2026-07-31 - complete ui-followups-residual-gaps-close

- Stage C: removed private FullscreenPhotoDialog → ZoomablePhotoDialog; thumbs 160/220.dp; all odo/cost/vol CaretEnabled + soft buttons
- Expense: tap photo → ZoomablePhotoDialog; amount/vendor/description/odo caret
- Manage Vehicles: View full photo zoom; QF cost+vol caret buttons
- Reports: vehicleId 0 always labeled Unknown (filter bar + LabReportData.vehicleName)
- project-facts residual helpers; build 8a8ef883

## 2026-07-31 - ui-followups-residual-nits-hygiene: start

- Plan: dev-ai-interaction/plans/ui-followups-residual-nits-hygiene-20260731-plan.md
- Dead import; Trip New name/Type caret; LocationConfirmBlock name/address caret

## 2026-07-31 - complete ui-followups-residual-nits-hygiene

- N1: removed unused OutlinedTextField import from ImportOldPicturesScreen
- N2: Trip manage-types New name / New Type → CaretEnabled (no soft buttons)
- N3: LocationConfirmBlock place name/address → CaretEnabled (Trip/QF/Expense)
- Read-only dropdown anchors unchanged; build 0dbc3ebc

## 2026-07-31 - vehicle-summary-last5-expense-categories-trip-delete: start

- Plan: dev-ai-interaction/plans/vehicle-summary-last5-expense-categories-trip-delete-20260731-plan.md
- Trip delete UI; ExpenseCategories v19; vehicle summary last-5 legs

## 2026-07-31 - complete vehicle-summary-last5-expense-categories-trip-delete

- A: LastFullFillLegsBlock on vehicle summary + TEXT/CSV full-fill legs
- B: ExpenseCategories seed; Vehicle.expenseCategoriesJson; Room v19; sheet Expense Categories JSON; Expense dropdown + Manage dialog
- C: Trip types Delete in ManageTripTypesDialog (keep ≥1)
- build bbe08969 (feature af3608ea + fix)

## 2026-07-31 - caret soft buttons under field + location row width

- Finished incomplete staged WIP: soft ◀▶ under numeric fields (not side-by-side; only when focused + number IME + no HW keyboard)
- LocationConfirmBlock: place name | address on one row (half width each)
- QF: location block shares Notes Panel C width in landscape
- build 8e4362c0; vehicle-summary plan remains complete at bbe08969

## 2026-07-31 - qf-caret-softkeys-layout-fix: start

- Plan: dev-ai-interaction/plans/qf-caret-softkeys-layout-fix-20260731-plan.md
- NumericKeypad 4x4 both orients; no QF field soft carets

## 2026-07-31 - qf-caret-softkeys-layout-fix start

- Plan: dev-ai-interaction/plans/qf-caret-softkeys-layout-fix-20260731-plan.md
- Resume mid-WIP: caret handlers present; finish NumericKeypad 4x4, both orients, readOnly, no soft carets
- No deploy; build_app only

## 2026-07-31 - qf-caret-softkeys-layout-fix complete

- Plan: dev-ai-interaction/plans/qf-caret-softkeys-layout-fix-20260731-plan.md
- QF odo/cost/vol: showCaretButtons=false, readOnly=true always; caretIndex wired to keypad
- NumericKeypad true 4x4: 0-9 . ⌫ ◀ ▶ OK(next) blank(dismiss); caret-aware insert/backspace
- Portrait + landscape both show keypad for numeric edit (replaces camera/A+B)
- CaretEnabled: optional caretIndex/onCaretIndexChange; soft carets remain under-field only
- Notes + LocationConfirm one-row place|address (prior + panelCTextWidth)
- build tag: be148a75 (ui-followups/builds); no deploy

## 2026-07-31 - photo-display-cloud-fetch-parity start

- Plan: dev-ai-interaction/plans/photo-display-cloud-fetch-parity-20260731-plan.md
- Fix Stage C OdoPeerBlock + any PendingPhotoRow missing archive fetch
- No deploy; build_app only

## 2026-07-31 - photo-display-cloud-fetch-parity complete

- Plan: dev-ai-interaction/plans/photo-display-cloud-fetch-parity-20260731-plan.md
- Forensic: PendingPhotoRow MPG/simple/generic already had canFetch; gap was OdoPeerBlock only
- OdoPeerBlock: canFetchArchive/isFetchingArchive/onFetchArchive; empty+no archive → No dash photo; else PendingPhotoRow
- Complex ODO prev/cur/next: peer FuelEntry state + fetchArchiveFor(peerId) → dash paths; refresh peer entry after download
- project-facts: on-demand archive fetch surfaces bullet
- build tag: 95d04ebd; no deploy

## 2026-07-31 - reports-pdf-export-all-lab start

- Plan: dev-ai-interaction/plans/reports-pdf-export-all-lab-20260731-plan.md
- Real PdfDocument PDF for all 7 Lab share screens; no deploy

## 2026-07-31 - reports-pdf-export-all-lab complete

- Plan: dev-ai-interaction/plans/reports-pdf-export-all-lab-20260731-plan.md
- ReportsLabPdf: PdfDocument multi-page text builder + fromPlainText (same facts as TEXT)
- ReportsLabShare.sharePdf → filesDir/reports_lab/*.pdf via FileProvider application/pdf
- All 7 Lab children: pdfBody wired; picker shows PDF when body present
- Hub info: TEXT/CSV/PDF; project-facts updated
- No chart bitmaps (PASS tabular/text); no deploy
- build tag: f161d1e0

## 2026-07-31 - caret-home-end-keys start

- Plan: dev-ai-interaction/plans/caret-home-end-keys-20260731-plan.md
- Home/End caret in CaretEnabled + QF via caretIndex; no deploy

## 2026-07-31 - caret-home-end-keys complete

- Plan: dev-ai-interaction/plans/caret-home-end-keys-20260731-plan.md
- CaretEnabled: setCaret + Home/MoveHome → 0, MoveEnd → length; consume key; onCaretIndexChange for QF
- Compose has no Key.End (use MoveEnd only); QF odo/cost/vol use same field path (no extra QF wiring)
- project-facts caret bullet updated; no soft Home/End on keypad
- build tag: 94c5f111; no deploy

## 2026-07-31 - batch-stage-c-empty-phase-nav-and-reorder-by-odo start

- Plan: dev-ai-interaction/plans/batch-stage-c-empty-phase-nav-and-reorder-by-odo-20260731-plan.md
- Part A: empty-phase Next phase always reachable; Part B: reorder-by-odo A/B/C
- No deploy

## 2026-07-31 - batch-stage-c-empty-phase-nav-and-reorder-by-odo complete

- Plan: dev-ai-interaction/plans/batch-stage-c-empty-phase-nav-and-reorder-by-odo-20260731-plan.md
- Part A: Next phase + Reset always on Import chrome; Review questions always enabled; empty-phase copy; stagePhase refreshed after advance
- Part B: FuelOdoReorder + dialog; A permute timestamps onto odo order; B economyIgnored on reverse later; C soft-delete reverse later; gate after odo phases (phase>2 or phase≥2 with no ODO pending)
- applyOdoReorder → applyMerge rescan; project-facts updated
- build tag: 57a269ad; no deploy

## 2026-07-31 - timestamp-filename-fallback + reports-lab-filter-dropdown start

- Plans: batch-import-timestamp-filename-fallback-20260731 + reports-lab-filter-dropdown-select-20260731
- EXIF datetime/offset/GPS accuracy; aggressive filename import; Lab filter Row fix
- No deploy

## 2026-07-31 - timestamp-filename-fallback + reports-lab-filter-dropdown complete

- Plans: batch-import-timestamp-filename-fallback-20260731 + reports-lab-filter-dropdown-select-20260731
- PhotoExifWriter: DateTimeOriginal/DateTime/Digitized + OffsetTime* + GPSHPositioningError when hasAccuracy; optional UserComment ve:tag
- PhotoExifMetaReader: generic filename heuristics (date+time, date-only midnight, ambiguous 8-digit, epoch 10/13) + mtime; source tags
- QF/Expense call sites pass captureTs; batch dash/pump log when falling back to now
- ReportsLabFilterBar: Row not AdaptiveItemGrid; onExpandedChange=it; filters outside verticalScroll; Log.i on select
- build tag: 9dd8800c; no deploy

## 2026-07-31 - stage-c-odo-suspect-looks-correct-ack start

- Plan: stage-c-odo-suspect-looks-correct-ack-20260731-plan.md
- Durable looks-correct for ODO_SUSPECT via merge_acks; UI + resolveMemberSyncIds
- No deploy

## 2026-07-31 - stage-c-odo-suspect-looks-correct-ack complete

- Plan: stage-c-odo-suspect-looks-correct-ack-20260731-plan.md
- Simple + complex ODO_SUSPECT: “These odometers look correct” → AcknowledgeLooksCorrect(ODO_SUSPECT)
- resolveMemberSyncIds adds curEntryId/nextEntryId; MergeAck.KIND_ODO_SUSPECT; no MERGE_EXEMPT for odo
- rebuild filterPending already drops same kind+member syncIds
- build tags: a45d0211 + b44c1cfe; no deploy

## 2026-07-31 - reports-efficiency-mpg-dpm-each-vehicle start

- Plan: reports-efficiency-mpg-dpm-each-vehicle-20260731-plan.md
- Multi-metric efficiency, EACH vehicle, date-X charts, trip Personal fix
- No deploy

## 2026-07-31 - reports-efficiency-mpg-dpm-each-vehicle complete

- Plan: reports-efficiency-mpg-dpm-each-vehicle-20260731-plan.md
- LabVehicleMode ALL/EACH/SINGLE + prefs; filter bar labels; no thrash on empty picker
- Charts: LabTimeSeriesLineChart date X (epoch-days) + rememberVicoScrollState(false) fit width
- Efficiency: mpg/gpm/dpmFuel/dpmIncl toggles (persisted); economy + money charts; export columns
- Metrics: dpmFuelOnly/dpmInclExpenses/gpm per leg; EACH multi-series by vehicle name
- Cost trends EACH + date series; monthly/category labels; vehicle summary EACH=stacked
- Trip: TR2 no-starts Personal baseline→last odo; vehicle mode; info text
- todo-append multi-select Sum/Average; project-facts updated
- Dual Y on one chart = two stacked charts (economy vs $) for scale separation
- build tags: 98a7c13d + ad5644f7; no deploy

## 2026-07-31 - qf-panel-a-min-half-c-scroll-help-info start

- Plan: qf-panel-a-min-half-c-scroll-help-info-20260731-plan.md
- A≥50% / landscape width; C scroll+cap; help→info dialog
- No deploy

## 2026-07-31 - qf-panel-a-min-half-c-scroll-help-info complete

- Plan: qf-panel-a-min-half-c-scroll-help-info-20260731-plan.md
- Portrait: A weight 0.55, B wrap, C weight 0.45 + verticalScroll (B not scrolled away)
- Landscape: A weight 1.2 + minWidth 200.dp; C weight 1 + widthIn(max=280.dp) + scroll
- Removed long shutter help from C; one-line status under B portrait; Info ⓘ dialog with shortcuts
- fieldsContent fillMaxWidth (no wrapContentWidth from help text)
- build tag: bc9789b0; no deploy

## 2026-07-31 - ui-chrome-cleanup-multiaxis-chart-retire-legacy-reports start

- Plan: ui-chrome-cleanup-multiaxis-chart-retire-legacy-reports-20260731-plan.md
- Multi-axis chart; trip/expense/vehicles chrome; retire ReportsScreen + drawer
- No deploy

## 2026-07-31 - ui-chrome-cleanup-multiaxis-chart-retire-legacy-reports complete

- Plan: ui-chrome-cleanup-multiaxis-chart-retire-legacy-reports-20260731-plan.md
- A: LabMultiAxisTimeSeriesChart — one chart, left Start (mpg/gpm share scale), right End ($/mi); dual left axes not used (eng-log)
- B: Trip — no hide camera; camera ~55% weight; no FeatureScreenHeader; Info; Time is now; location status no Resolved; confirm short
- C: Manage Vehicles help → Info dialog
- D: Expense bar New/Edit expense; no mid title/long help; Info; Time is now; Manage categories toast; no Resolved banner
- E: Drawer removed Expense List + Reports & Charts; deleted ReportsScreen; expenselist route kept via hub
- LocationConfirmBlock confirmLabel default “Confirm this location”
- build tag: b21bf0c4; no deploy

## 2026-07-31 - dropdown-overflow-affordance start

- Plan: dropdown-overflow-affordance-20260731-plan.md
- Pin Manage footer on expense category + trip type menus
- No deploy

## 2026-07-31 - dropdown-overflow-affordance complete

- Plan: dropdown-overflow-affordance-20260731-plan.md
- ExposedDropdownMenuBoxScope.ExposedDropdownMenuWithPinnedFooter / WithManageFooter
- Catalog scroll heightIn max 280.dp; “Scroll for more…” when ≥4 items; Manage row pinned below divider
- Wired Expense category + Trip type; project-facts one-liner
- build tag: df85f5a7; no deploy

## 2026-07-31 - trip-qf-controls-topbar-info-import-gate start

- Plan: trip-qf-controls-topbar-info-import-gate-20260731-plan.md
- Trip QF controls; top-bar PageHelp; Import experiment-gated; QF 45%
- No deploy

## 2026-07-31 - trip-qf-controls-topbar-info-import-gate implementing

- MainActivity: PageHelp CompositionLocal + TopAppBar Info; Import under experiment gate
- Manage Vehicles + Expense: RegisterPageHelp; strip mid-screen Info
- Trip/QF already mid-edit: 45/55, CaptureControls, side-by-side checkboxes
- Next: build_app

## 2026-07-31 - trip-qf-controls-topbar-info-import-gate complete

- Plan: trip-qf-controls-topbar-info-import-gate-20260731-plan.md
- A: Trip camera 45%/fields 55%; disk/shutter/Stop row; Confirm+Time is now side-by-side; CaptureControls shared with QF
- B: PageHelp registry (CompositionLocal); TopAppBar Info before ?N; RegisterPageHelp on QF/Trip/Manage/Expense; mid-screen Info removed
- C: Drawer Import only when show_experiment_screens (after Pump Experiment); ?N still opens import review
- D: QF portrait A 0.45 / C 0.55
- Mechanism: dynamic PageHelpController over static route map
- build tag: a8e9897f; no deploy

## 2026-07-31 - sync-now-in-dest-edit-and-failure-details start

- Plan: sync-now-in-dest-edit-and-failure-details-20260731-plan.md
- Failure store full messages + Details UI; Sync now on dest edit; rate-limit retry + pacing
- No deploy

## 2026-07-31 - sync-now-in-dest-edit-and-failure-details complete

- Plan: sync-now-in-dest-edit-and-failure-details-20260731-plan.md
- S: Spreadsheet/photo dest edit footer “Sync now (this destination)” via destId; hub Sync now kept
- E: Failure store full message (capped 6KB); Syncing + dest edit Details + Copy; hub short names + rate-limit title
- R: SyncRateLimit isRateLimitError; 15–45s random backoff; max 3 attempts; multi-dest pace 1.5–3s; spreadsheet mutex + photo mutex
- Mechanism: single-device serialize + pace; cross-device only detect/wait/retry
- build tag: 7b206e22; no deploy

## 2026-07-31 - sheets-rate-limit-write-level-retry-and-pacing start

- Plan: sheets-rate-limit-write-level-retry-and-pacing-20260731-plan.md
- Write-level retry + longer backoff (≥60s) + write pacing + longer inter-dest
- No deploy

## 2026-07-31 - sheets-rate-limit-write-level-retry-and-pacing complete

- Plan: sheets-rate-limit-write-level-retry-and-pacing-20260731-plan.md
- Write unit: SyncRateLimit.withSheetsWriteLimit on GoogleSheetsClient mutations (append/update/clear/batchUpdate/create)
- Backoff: first 60–120s, later 90–180s (cap 180s); MAX_WRITE_ATTEMPTS=8; UI “Rate limited — waiting Ns (try k/n)…”
- Write pace: MIN_WRITE_GAP_MS=1300 (~≤45 writes/min)
- Inter-dest pace: 10–20s; whole-dest rate-limit restart removed
- Exhausted: Details + cross-device hint
- build tag: f0246d9a; no deploy

## 2026-07-31 - sheets-rate-limit residual: wrap reads + writes

- Plan residual: dual-sheet fail was read quota on dest-2 fuel GET (unwrapped)
- withSheetsApiLimit on every GoogleSheetsClient.execute; postDestReadCooldown 15–30s
- No deploy

## 2026-07-31 - sheets-rate-limit residual complete (read+write)

- Plan residual: sheets-rate-limit-write-level-retry-and-pacing-20260731-plan.md
- Root cause: dest-2 fuel GET hit read_requests 429; only writes were wrapped
- withSheetsApiLimit on every GoogleSheetsClient.execute (GET values/meta + mutations)
- Shared MIN_API_GAP_MS=1300; inter-dest 10–20s + postDestReadCooldown 15–30s
- shortTitle: Rate limited (Sheets reads|writes)
- build tag: f17db051; no deploy

## 2026-07-31 - sheets-bulk-read-batchget-compare-pass start

- Plan: sheets-bulk-read-batchget-compare-pass-20260731-plan.md
- batchGet multi-tab compare prefetch; coordinator uses bulk cache for LWW
- No deploy

## 2026-07-31 - sheets-bulk-read-batchget-compare-pass complete

- Plan: sheets-bulk-read-batchget-compare-pass-20260731-plan.md
- batchReadTabs: values.batchGet via executeApi (≤40 ranges/chunk)
- Coordinator LWW: one listTabTitles + bulk prefetch; cache for vehicles/expenses/acks/fuel Pass 1
- ensureHeaders+single re-read only when tab missing or header incomplete
- Non-Google backends: batchReadTabs default loops readAllRows
- build tag: 22e8dd63; no deploy

## 2026-07-31 - sync-failure-orphan-prune start

- Plan: sync-failure-orphan-prune-20260731-plan.md
- Prune orphan destIds from failure store on dest save + sync start
- No deploy

## 2026-07-31 - sync-failure-orphan-prune complete

- Plan: sync-failure-orphan-prune-20260731-plan.md
- pruneToKnownDestinations on dest save + spreadsheet/photo syncNow
- Details orphan title + legacy name-only → “Sync failed (no detail)”
- Real present-dest failures kept until success; full API message still stored
- build tag: 0de03efb; no deploy
x
## 2026-07-31 - ui-followups local PR prepared (history cleanup)

- Soft-reset cleanup: backup-ui-followups @ 0de03efb; 47 messy commits → 7 logical; TREE_MATCHES_BACKUP (3326ab6b).
- Cleaned tip: see git rev-parse HEAD; post-cleanup ./build_app SUCCESS; ui-followups/builds updated.
- ./generate_pr.sh → dev-ai-interaction/PRs/PR-ui-followups.md (plans + Coder pre-submit).
- Ready for Master (run-grok-master) independent review + merge. Coder does not merge.

## 2026-07-31 - photo-backup-compose-scope-cancel-not-failure start

- Plan: photo-backup-compose-scope-cancel-not-failure-20260731-plan.md
- Safe progress; ViewModel-scoped Sync now; cancel ≠ dest failure
- No deploy

## 2026-07-31 - photo-backup-compose-scope-cancel-not-failure complete

- Plan: photo-backup-compose-scope-cancel-not-failure-20260731-plan.md
- Progress: Handler main post (no Compose rememberCoroutineScope throw)
- Sync now: PhotoBackup/Spreadsheet/Settings ViewModels viewModelScope + StateFlow status
- Coordinators: isNonFailureCancel → rethrow; never record rememberCoroutineScope text as dest failure
- build tag: 7e3dfded; no deploy

## 2026-07-31 - docs-refresh + reports Vico X precision start

- Plans: docs-refresh-and-topbar-info-narrow-20260731-plan.md + reports-lab-vico-x-precision-crash-20260731-plan.md
- Quantize chart X; leading Info + narrow title; RegisterPageHelp expand; docs refresh
- No deploy

## 2026-07-31 - docs-refresh + Vico X precision complete

- Plans: docs-refresh-and-topbar-info-narrow-20260731-plan.md; reports-lab-vico-x-precision-crash-20260731-plan.md
- tsToChartX: round to 4 decimals (Vico GCD crash)
- TopAppBar: Info leading next to ☰/←; narrow title <600dp; maxLines=1; trailing only ?N/!
- RegisterPageHelp: Lab via TitleRow; Syncing/Settings/FuelHistory/Spreadsheet/Photo lists
- Docs: NAVIGATION_MAP, USER_GUIDE, user-manual(+html/assets), API, REPORTS_METRICS, UI_COMPAT, SYNC_BEHAVIOR, CHANGELOG
- build tag: 38a142a2; no deploy

## 2026-07-31 - efficiency-gpm + reports-nav-each start

- Plans: efficiency-gpm-second-y-axis-colors-20260731-plan.md; reports-nav-each-monthly-experiments-20260731-plan.md
- Free toggles; gpm own axis; colors; Each monthly/category/trips; ☰+← reports; experiment First 10
- No deploy

## 2026-07-31 - efficiency-gpm + reports-nav-each complete

- Plans: efficiency-gpm-second-y-axis-colors-20260731-plan.md; reports-nav-each-monthly-experiments-20260731-plan.md
- Efficiency: free toggles (no ensureAtLeastOne); mpg/gpm/money series maps; gpm own axis; money-only Start-axis; A4 second money chart; family colors; all-off empty
- Charts: month X labels; LabMultiSeriesIndexChart; remount key without return@key (D8)
- Nav: ☰+← on reports_lab/* + expenselist; hub ☰ only
- Each: monthly multi-vehicle totals; category multi-series; trip miles per vehicle
- Experiments: drop Amazon/Golden/Failing/Problem; First 10 on Alignment+Pump
- build tag: c4f461c6; no deploy


## 2026-07-31 - efficiency-chart-line-and-axis-colors start

- Plan: efficiency-chart-line-and-axis-colors-20260731-plan.md
- C2 line strokes + C3 Y tick/axis colors via Vico 3.2.3 LineProvider / VerticalAxis style
- No deploy


## 2026-07-31 - efficiency-chart-line-and-axis-colors complete

- Plan: efficiency-chart-line-and-axis-colors-20260731-plan.md
- LineCartesianLayer LineProvider.series with family Fill colors; dual money fuel/incl; Each shade+dash
- VerticalAxis Start/End: rememberAxisLine/Label/TickComponent family color (Y1 real axis styling)
- Caption Text colors kept (Y2); bottom date axis neutral
- Non-efficiency charts unchanged (null familyDefault → Vico default)
- build tag: a590ab31; no deploy


## 2026-07-31 - unified-time-report-fill-edit-pagehelp start

- Plan: unified-time-report-fill-edit-pagehelp-20260731-plan.md
- H PageHelp token; F edit fill/fills-only/trips; R unified time report+bins+PDF; D docs
- No deploy


## 2026-07-31 - unified-time-report-fill-edit-pagehelp complete

- Plan: unified-time-report-fill-edit-pagehelp-20260731-plan.md
- H: PageHelp set returns owner; clearIf(id) only if current (Info stays)
- F: multi-dest archive identity; Edit fill currency-before-cost, multi-col, location expand, hide blank trip type; Fuel History fills-only; Trip miles trip list + tap edit
- R: reports_lab/time Fuel over time (metrics, smooth bins, axis policy, PDF combined+per-series); hub merge; legacy routes redirect
- D: NAVIGATION_MAP, REPORTS_METRICS, USER_GUIDE, project-facts
- No deploy


## 2026-07-31 - time-based-reports-ux-pdf-manual start

- Plan: time-based-reports-ux-pdf-manual-20260731-plan.md
- Rename Time based reports; UnitFormat labels; single chart; trip bins; PDF graphs; multi-dest fetch; docs/screenshots
- No deploy


## 2026-07-31 - time-based-reports-ux-pdf-manual complete

- Plan: time-based-reports-ux-pdf-manual-20260731-plan.md
- Rename Time based reports; UnitFormat volumePerDistance + unitPrice labels
- Single chart: economy left, money+trip right; color chips + multi-col metrics
- Trip miles/% from odo Δ bins (Personal included)
- PDF combined+per-series chart bitmaps + tables
- Multi-dest downloadFuel/Expense/VehicleIfNeeded
- Docs Help USER_GUIDE REPORTS_METRICS user-manual+html assets; screenshots deferred to post-deploy 5556 for hub/time/edit
- No deploy


## 2026-07-31 - time-report-fixed-axis-sides start

- Plan: time-report-fixed-axis-sides-20260731-plan.md
- Economy always Start; money/trip always End; no gpm-on-right dual mode
- No deploy


## 2026-07-31 - time-report-fixed-axis-sides complete

- Plan: time-report-fixed-axis-sides-20260731-plan.md
- Start = mpg∪gpm always; End = money∪trip only; dropped dual-axis gpm-on-right branch
- build via build_app; no deploy


## 2026-07-31 - time-report-multi-axis-trip-pct-by-type start

- Plan: time-report-multi-axis-trip-pct-by-type-20260731-plan.md (historical path; named for execution)
- Multi-family Y scales; trip % per type; custom Canvas chart; PDF parity
- No deploy


## 2026-07-31 - time-report-multi-axis-trip-pct-by-type complete

- Plan: time-report-multi-axis-trip-pct-by-type-20260731-plan.md
- tripMetricsFromOdo: milesTotal + pctByType; multi-family Canvas LabMultiFamilyTimeSeriesChart
- Left mpg + G/mi axes; right $ + trip mi + trip %; PDF renderMultiFamilyChartBitmap
- No deploy


## 2026-07-31 - onboarding-splash-tutorials start

- Plan: onboarding-splash-tutorials-20260731-plan.md
- First-run splash; tutorial_add_vehicle + tutorial_setup_sync; Help links
- No deploy


## 2026-07-31 - onboarding-splash-tutorials complete

- Plan: onboarding-splash-tutorials-20260731-plan.md
- Splash when forUserPicker empty; tutorials add_vehicle + setup_sync; Help/Settings CTAs
- Assets app/src/main/assets/tutorials/; manual composites from sandbox masked set + render
- No deploy


## 2026-07-31 - user-manual-screenshot-integration start

- Plan: user-manual-screenshot-integration-20260731-plan.md
- Promote 5556 masked shots; update md; render HTML/assets
- No deploy


## 2026-07-31 - user-manual-screenshot-integration complete

- Plan: user-manual-screenshot-integration-20260731-plan.md
- Promoted 5556 masked shots + re-captured spreadsheet list, photo backup list, settings scroll, syncing hub
- user-manual.md: Start trip, Time based reports, fill/fuel edit, trip miles, Syncing hub; composites noted
- render-user-manual.sh → html + assets; Help/About/USER_GUIDE verified
- No app code change required beyond prior onboarding


## 2026-08-01 - residual-finish-recent-plans start

- Plan: residual-finish-recent-plans-20260801-plan.md
- J join-existing sync tutorial; H Help multi-axis; D optional form shots
- No deploy


## 2026-08-01 - residual-finish-recent-plans complete

- Plan: residual-finish-recent-plans-20260801-plan.md
- J: SETUP_SYNC rewritten join-existing; splash Connect existing setup; Help/Settings labels
- H: Help Time based reports = independent Y scales + trip % by type; USER_GUIDE join-existing one-liner
- D: re-captured 09/10/12/13 (+10 email masked); r2 landmarks + r4 QF odo-result kept pre-refresh (chrome still accurate)
- render-user-manual.sh; build_app after Kotlin
- No deploy


## 2026-08-01 - Local PR prepared: ui-followups

- History cleanup: soft-reset onto master → **9 logical commits**; `backup-ui-followups` @ `72d52dac`; **TREE_MATCHES_BACKUP** (`HEAD^{tree}` == backup tree `1a408d5a`).
- Cleaned HEAD: `3037b9c5`.
- PR doc: `dev-ai-interaction/PRs/PR-ui-followups.md` (pre-submit review + plans + diffstat).
- Archived finished plans → `historical-plans/`: residual-finish-recent, onboarding-splash-tutorials, user-manual-screenshot-integration, sync-tutorial-join-existing-cluster, reports-efficiency-mpg-dpm-each-vehicle.
- Ready for Master (`run-grok-master`) independent review + merge. No deploy/merge by coder.

## 2026-08-01 - Master merge: ui-followups

- audit_merge SUCCESS (FF); merge-branch-into-master.sh FF index path; staged 95 .kt + assets/docs
- ve-englog third version (hash-object -w fix for missing blob before commit)
- TODO: closed Location Lookup Worker + Troubleshoot lat/long; deferred multi-candidate, post-save confirm, multi-select Sum/Avg, trip packaging; Advanced reports stays closed
- project-facts: branch orientation (Room v19, Lab Reports, onboarding, sync rate-limit)
- build_app: first attempt processDebugJavaRes mode 770 flake (cleaned intermediates); retry SUCCESS
- builds tag → 6889b142 (merge commit). No works tag.


## 2026-07-26 - reports-field-conditional-economy-chains-20260726

- Execution start for approved plan: reports-field-conditional-economy-chains-20260726-plan.md
- Branch: batch_load; no prior batch_load/builds tag (first successful ./build_app will create it)
- Scope: ReportsScreen.kt field-conditional MPG/$/mi chains + REPORTS_METRICS.md; phase 5 emulator probe after user deploy

## 2026-07-26 - reports-field-conditional-economy-chains-20260726 phases 1-4 done

- Phases 1-4 implemented and built on batch_load
- ReportsScreen.kt: full fill = odo+cost+vol+!partial; MPG breakers; $/mi segment sum
- docs/reference/REPORTS_METRICS.md rewritten to match
- Tag: batch_load/builds @ 3d29b986 (batch_load-start-4-g3d29b986)
- Phase 5 blocked on user APK deploy to emulator-5554 (device currently has versionName=instruction-start-8-g7d8aa877, not batch_load build)

## 2026-07-26 - reports-field-conditional-economy-chains phase 5 probe PASS

- Backup: test-data-backups/emulator-5554-20260726-095208
- App on emulator-5554: batch_load-start-5-ge76bbc73
- Probe cases odo_only / blank / cost_only / volume_only on Honda between full fills 2→3
- UI Honda: baseline last 15.8 avg 17.0 $/mi 0.259; odo_only same; blank avg 15.8 $/mi 0.260; cost_only avg 15.8 $/mi 0.276; volume_only avg 14.5 $/mi 0.260
- Evidence: dev-ai-interaction/research/reports-chain-probe-20260726/
- DB restored from pre-probe backup; no leftover PROBE rows

## 2026-07-26 - reports-field-conditional-economy-chains PR prepared

- Pre-submit review PASS vs plan; device probe PASS; DB restored
- Local PR: dev-ai-interaction/PRs/PR-batch_load.md
- Tag: batch_load/builds @ 1de13e02; backup-batch_load set
- Ready for Master independent review + merge

## 2026-07-26 - batch-load-ingest-merge-questions-20260726

- Execution start: Stage A (batch ingest) per approved plan batch-load-ingest-merge-questions-20260726-plan.md
- Baseline tag: batch_load/builds @ a02dc9bc; Stages B–C deferred to later turn if A fills the turn

## 2026-07-26 - batch-load Stage A complete (ingest)

- Plan: batch-load-ingest-merge-questions-20260726-plan.md Stage A only
- Added: PhotoExifMeta, FuelPhotoJson, BatchImportPending, BatchFuelImportCoordinator, hardDeleteFuelEntry
- Set I hybrid in PumpCostVolUtils.runSetICostVolExtraction + OcrHarness.runPumpCostVolPipelineSetI
- Import Old Pictures UI: Run batch import / Cancel / Review questions (list-only)
- Hilt ViewModel in ui.batch (not ui.import — Java keyword breaks KSP)
- Builds tag: batch_load/builds @ 6efd2743
- Stages B (merge) and C (apply answers) remain on same plan path — not in this turn

## 2026-07-26 - batch-dash-match-experiment-set-j-20260726

- Execution start: unify Set J (experiment + batch + Quick Fill) per plan
- Baseline: batch_load/builds @ 99d1043c

## 2026-07-26 - batch-setj approach: copy experiment logic into production

- User direction: do NOT extract shared dual-call API; copy experiment Set J into production
- Experiment remains free to tinker (future compile-out); production batch/Quick Fill own a frozen copy
- Continue plan batch-dash-match-experiment-set-j-20260726-plan.md with this approach

## 2026-07-26 - batch-dash-match-experiment-set-j production copy done

- OcrHarness.runSetJPipeline rewritten as independent copy of experiment Set J (Raw+Bin-Trials+connectSegmentsH+pickBestOdometer)
- ExperimentAlignmentScreen NOT shared — free to tinker
- Batch parseSetJOdometer: 4-7 pure digits only (no concat soup)
- Tag: batch_load/builds @ 38186950
- Device parity CSV pending user deploy + re-import after purging old batch dash rows
- Notes: dev-ai-interaction/research/batch-setj-parity-20260726/NOTES.md

## 2026-07-26 - production-setj-ref-geometry-parity-20260726

- Execution start: fix production Set J ref geometry (probe/4080, ICRS crop, Raw nestFilter)
- Baseline: batch_load/builds @ d51f30d9

## 2026-07-26 - production-setj-ref-geometry-parity done

- Probed ref dims for landmarks+align; fallback 4080×3072
- ICRS Float createCrop for odo window; Raw no nestFilter
- Experiment screen untouched (independent copy)
- Tag: batch_load/builds @ de113410
- Device compare pending user deploy; notes in research/setj-geometry-parity-20260726/

## 2026-07-26 - batch import limited button (first 20+20)

- Import UI: OutlinedButton "First 20 dash + first 20 pump" (name-sorted take)
- BatchFuelImportCoordinator.runIngest(maxDash, maxPump); LIMITED_IMPORT_COUNT=20

## 2026-07-26 - batch pump ingest without vehicle

- processPump always runs Set I; inserts with UNASSIGNED_VEHICLE_ID=0
- No ASSIGN_VEHICLE gate; merge later assigns vehicle via time/location pairing
- Unreadable pumps still pending only

## 2026-07-26 - batch-setj-reverify-and-pending-answers-20260726

- Execution start; device versionName=batch_load-start-21-gf43bb991 (geometry + unassigned pump)
- Part A first-20 re-verify then Part B clickable pending answers

## 2026-07-26 - batch-setj-reverify + pending answers

- Part A first-20 re-verify on f43bb991: 17 dash inserts + 20 pump vehicleId=0
- first20 score: see research/batch-setj-parity-20260726/first20-after-geometry.txt (gate FAIL — residual leading-digit/blank DIFFs)
- Part B: clickable pending UI + forcedVehicleId dash reprocess + skip/retry pump (build 06707a0a)
- Experiment untouched; deploy needed for Part B on device

## 2026-07-27 - batch-mirror-experiment-pipelines-setj-seti

- Execution start: batch must run experiment Set J / Set I pipelines, not OcrHarness reinvented front-end
- Baseline tag: batch_load/builds @ c112e80e

## 2026-07-27 - batch-mirror-experiment-pipelines-setj-seti done (code)

- AlignmentSetJRunner: experiment Set A ID lock + Set J runPaddleValleyIterative for batch dash
- PumpSetIRunner: experiment-equivalent Set I for batch pump
- BatchFuelImportCoordinator no longer uses runAutoFillPipeline / runPumpCostVolPipelineSetI
- Tag: batch_load/builds @ 7653d156
- Device parity first-20 still needs deploy of this APK + re-run (prior c112e80e re-verify failed)

## 2026-07-27 - Batch Stage A full-run parity PASS; Stage B plan ready

- Full batch finished (~02:35 PDT); app idle after last pump `PXL_20260411_201506380.jpg`.
- Selective purge: deleted 74 old `batch_import%` rows (`updatedAt < 1785142500000`); kept 12 non-batch + 290 this-run (ids 219–508); pending cleaned to 6 this-run items.
- Parity vs experiment: dash Set J 142 OK / 0 DIFF (4 no-vehicle pending); pump Set I 148 OK / 0 DIFF (2 unreadable both sides). Report: `dev-ai-interaction/research/batch-run-parity-20260727/`.
- Next coder plan: `dev-ai-interaction/plans/batch-stage-b-merge-engine-coder-20260727-plan.md` (merge engine + Run merge). Durable B+C: `batch-stage-b-merge-and-stage-c-questions-20260727-plan.md`.

## 2026-07-27 - batch-stage-b-merge-engine

- Execution start: Stage B merge engine per batch-stage-b-merge-engine-coder-20260727-plan.md
- Stage A parity PASS; baseline tag 83e36ca0

## 2026-07-27 - batch-stage-b-merge-engine B1–B4 code

- B1: FuelPhotoJson.unionPhotos / addPumpPhoto / addDashPhoto
- B2: FuelRowMergeEngine.planMerge (vehicle clusters ±45m, vehicleId=0 pump pair, sequence vs re-shot, CONFLICT_ODO + photo paths, hard-delete list)
- B3: BatchFuelImportCoordinator.applyMerge + Import **Run merge** button
- B4: Merge after import checkbox default **off**
- Fixtures notes: dev-ai-interaction/research/batch-stage-b-merge-fixtures-20260727.md
- Tag: batch_load/builds @ 13d7c06b
- Device Run merge still to verify after deploy

## 2026-07-27 - batch-stage-b-merge-engine device Run merge

- Deploy: adb install -r (./deploy re-exec loop as ai-coder; used direct install)
- Pre: 302 rows, v0=148, fulls=6, odo_only=140, pump_only=154
- applyMerge: updated=133 deleted=129 pending+=2 → tag code 13d7c06b
- Post: 173 rows, v0=14, fulls=130, odo_only=16, pump_only=25
- Non-batch: 9 kept (absorbed re-shot ids 6–8 same $18.40); full non-batch fills preserved
- CONFLICT_ODO pending: 9594 vs 9698; 198699 vs 98699 (photo paths attached)
- Summary: dev-ai-interaction/research/batch-stage-b-merge-20260727/merge-run-summary.txt
- Stage C deferred (image-first questions, assign→re-merge UI)

## 2026-07-27 - batch-stage-c-image-questions

- Execution start: Stage C image-first pending questions per batch-stage-c-image-questions-coder-20260727-plan.md
- Stage B done on device; tip batch_load/builds @ 0c93922c

## 2026-07-27 - batch-stage-c-image-questions done

- C1: pendingPhotoUris + image thumbs/zoom; DNG placeholder; filename secondary only
- C2: CONFLICT_ODO Keep odo / Keep both; pure wrong-odo hard-delete; pump data odo zeroed
- C3: successful answers auto applyMerge; skip does not re-merge
- Device: Keep odo 9594 → pending 8→7 + remerge updated=16 deleted=1; Skip → pending 6
- Tag: batch_load/builds @ f360ce24
- Notes: dev-ai-interaction/research/batch-stage-c-questions-20260727/smoke-notes.txt
- Residual: 1 CONFLICT_ODO, 3 no-vehicle dash, 2 unreadable pump; AMBIGUOUS_MULTI_PUMP unused

## 2026-07-27 - batch-stage-c-photo-ux-manual-entry

- Execution start: photo UX, manual entry, economyIgnored sync, tank heuristic, unknown vehicle, outliers
- Parent tip: batch_load/builds @ 27d92a09

## 2026-07-27 - batch-stage-c-photo-ux-manual-entry done

- Photo stem-dedupe; DNG OpenCV preview; fullscreen +/− + sticky actions
- Manual pump/dash/conflict odo; economyIgnored Room v14 + sync column Economy Ignored
- Reports: fills N(Mp); Unknown label; economy excludes ignored; avg filters 3× outliers
- Merge: tank maxFill+5 auto-assign; post-merge enqueue unknown + MPG_OUTLIER
- Device: merge pending+=52 (9 unknown, 42 outliers); tag batch_load/builds @ aa9b1e53
- Notes: research/batch-stage-c-photo-ux-20260727/smoke-notes.txt

## 2026-07-27 - batch-merge-window-odo-sanity

- Execution start: 15m merge window, tight dash/pump pairs, odo reverse/gap sanitizer, Flag partial, per-vehicle unknown context
- Parent tip: batch_load/builds @ 3f915150

## 2026-07-27 - batch-merge-window-odo-sanity done

- MERGE_WINDOW_MS=15m; splitTightDashPumpPairs for multi dash+pump
- FuelOdoSanitizer: gap (maxVol×mpg×3, no fallback) then reverse; bias demote later
- ODO_SUSPECT pending; FlagPartial action; per-vehicle nearest for unknown
- Device: sanitize=31, ODO_SUSPECT=31 (incl. 20119 gap); CONFLICT_ODO=1
- Tag: batch_load/builds @ 1fbe913e
- Notes: research/batch-merge-window-odo-sanity-20260727/smoke-notes.txt

## 2026-07-27 - batch-pending-gap-dedupe-timefmt

- Execution start: Mark as gap, pending rebuild/dedupe, formatTimeDelta, Clear & re-scan
- Parent tip: batch_load/builds @ 25df6455

## 2026-07-27 - batch-pending-gap-dedupe-timefmt done (code)

- Mark as gap (blank odo/cost/vol); pending full rebuild on merge; (kind,fuelEntryId) dedupe
- formatTimeDelta for neighbor/unknown deltas; Clear questions & re-scan button
- Sanitizer skips already-partial / blank; clear all pending kinds per answered fuelEntryId
- Tag: batch_load/builds @ e743f48a
- Verify: ./build_app only — no deploy/device smoke (coder not authorized to deploy for test)
- Notes: research/batch-pending-gap-dedupe-timefmt-20260727/smoke-notes.txt

## 2026-07-27 - batch-mpg-outlier-ui-clarity

- Execution start: MPG outlier UI clarity per batch-mpg-outlier-ui-clarity-20260727-plan.md
- Tip: batch_load/builds @ 47ddcf81
- Device verify only after human deploy (coder will not adb install/deploy)

## 2026-07-27 - batch-mpg-outlier-ui-clarity done (code)

- MPG_OUTLIER: end-only primary photos; leg start/end text; focus toggle prior/end
- Edit/flag/ignore/gap target focusEntryId; nearby badges LEG START / THIS FILL
- Tag: batch_load/builds @ 795c8e3b
- No device test (await human deploy)
- Notes: research/batch-mpg-outlier-ui-clarity-20260727/smoke-notes.txt

## 2026-07-27 - batch-mpg-outlier-ui-clarity (layout v2)

- Re-execute updated plan: Last/This photo blocks + 4-line context
- Prior UI (single strip + toggle) superseded by locked two-block layout

## 2026-07-27 - batch-mpg-outlier-ui-clarity layout v2 done (code)

- Two-block Last/This photos above each button; 4-line text context (before/last/this/after)
- Focus default This fill; edit/flag/ignore/gap target focus id
- toPending: thisPhotoPaths + lastPhotoPaths
- Tag: batch_load/builds @ c272c521
- No device test until human deploy
- Notes: research/batch-mpg-outlier-ui-clarity-20260727/smoke-notes.txt

## 2026-07-27 - batch-partial-flag-semantics + sanitizer-correctness

- Execution start: two plans
  - batch-partial-flag-semantics-20260727-plan.md
  - batch-partial-and-sanitizer-correctness-20260727-plan.md
- No device deploy (human deploys before test)

## 2026-07-27 - partial-flag + sanitizer-correctness done (code)

- isPartialFill explicit-only; inserts/merge/sanitizer never auto-true for incomplete
- FuelOdoSanitizer detect-only (reverse, digit_jump, gap with clean mpg); no demote updates
- Checkbox SetPartialFill; clearAutoPartialFlags repair button
- Reports display band 5–80 + formatMpg n/a outside 1–100
- Quick Fill: isPartialFill=false on save
- Tag: batch_load/builds @ ec44b782
- No device test until human deploy
- Notes: research/batch-partial-sanitizer-correctness-20260727/smoke-notes.txt

## 2026-07-27 - batch-odo-suspect-ui-per-fill

- Execution start: ODO_SUSPECT per-fill UI layout
- No device deploy until human deploys

## 2026-07-27 - batch-odo-suspect-ui-per-fill done (code)

- ODO_SUSPECT: ordered prev/cur/next dash-only photos + odo fields under each
- SaveOdoPeers multi-odo write; payload prevDashPaths/curDashPaths/nextDashPaths
- Tag: batch_load/builds @ 0da926d1
- No device test until human deploy; re-scan after deploy
- Notes: research/batch-odo-suspect-ui-per-fill-20260727/smoke-notes.txt

## 2026-07-27 - batch-sync-cross-device-partials-and-titlebar

- Execution start: soft-delete sync, origin-device partials, title bar logo/version
- No device deploy until human deploys

## 2026-07-27 - batch-sync-cross-device-partials-and-titlebar (in progress)

- Completing MainActivity yellow bar + remaining CTA; no device deploy


## 2026-07-27 - batch-sync-cross-device-partials-and-titlebar done (code)

- Merge: all live partials (sync/QF/batch); soft-delete published absorbs; lat/lon later-ts
- hasUnmatchedPartials + post-sync toast CTA (no auto-merge)
- Yellow title-bar ?N → import?review=1 expand Review questions; Help bullets
- Tag: batch_load/builds @ 5e7de408
- No device test until human deploy
- Notes: research/batch-sync-cross-device-partials-and-titlebar-20260727/smoke-notes.txt


## 2026-07-27 - batch-mpg-gap-button-and-window-fills done (code)

- MPG card: vertical Save / Missing data between last & this / Ignore (no clip)
- markAsGap MPG: insert mid-leg blank; anchors preserved; dismiss if breaker exists
- FuelEconomyChains + breaker-aware detectOutliers; windowSummary inventory
- Post-sync applyMerge after fuel LWW; SYNC_BEHAVIOR + REPORTS_METRICS
- Tag: batch_load/builds @ a50da18f
- No device test until human deploy
- Notes: research/batch-mpg-gap-button-and-window-fills-20260727/smoke-notes.txt


## 2026-07-27 - batch-no-durable-photo-copy done (code)

- processDash/processPump: source paths only; copyToDurable removed
- migrateDurablePhotoRefsToSource rewrite+delete unreferenced mirrors
- PendingPhotoUris prefer source dirs; Help note
- Tag: batch_load/builds @ a18099d1
- No device test until human deploy
- Notes: research/batch-no-durable-photo-copy-20260727/smoke-notes.txt


## 2026-07-28 - batch-stage-c-phased-questions done (code)

- StageCPhase 1–6 store + UI filter; skip/reset; clear-rescan → phase 1
- ODO chain merge + simple length-guess mode; BAD_PUMP_RATIO phase 3
- Gap from phase 3/5; post-sync remoteWins → phase 1 + rebuild
- Tag: batch_load/builds @ 8ad579ef
- No device test until human deploy
- Notes: research/batch-stage-c-phased-questions-20260728/smoke-notes.txt


## 2026-07-28 - batch-stage-c-ux-skip-photos-phase-scope done (code)

- Phase-scoped pending rebuild; Next phase regenerates only next kinds
- Skip ledger same-phase; answer journal jsonl + export (no replay)
- Unassigned vehicle id=0 fixed syncId + Fuel - Unassigned tab; picker exclusions
- Photo role dash/pump; Close z-order; CONFLICT keep-both completes
- Tag: batch_load/builds @ dab2e144
- No device test until human deploy
- Notes: research/batch-stage-c-ux-skip-photos-phase-scope-20260728/smoke-notes.txt


## 2026-07-28 - PR-batch_load prepared for Master review

- History cleaned: 57 commits → 5 logical; backup-batch_load @ ff33f355; cleaned HEAD @ 11a2c1ca
- PR: dev-ai-interaction/PRs/PR-batch_load.md (pre-submit review + plans)
- Not rebased onto master (simplify_experiments); Master merge expects eng-log special handling + possible ExperimentAlignmentScreen conflict
- Tree matches pre-cleanup tip; build_app SUCCESS after cleanup


## 2026-07-28 - Master merge PR-batch_load

- Independent review PASS; non-FF merge with simplify_experiments overlap
- merge-branch-into-master failed on +a eng-log (double/triple append side effect); completed via merge-tree + manual resolve
- ExperimentAlignmentScreen: keep Set-J-only simplify + internal helpers for AlignmentSetJRunner (dropped batch dead createScaledBase64/drawCropBoxes conflict side)
- project-facts: batch orientation + Room v14 from branch; TODO unchanged
- Commit 1bfab017; build SUCCESS after java_res permission retry; builds tag updated
- Worktree ENGINEERING_LOG.md may still have triple-appended tail (+a); committed blob is single third-version + this note — fix with sudo chattr -a when available
- No works tag. Cleanup: ./remove_worktree.sh batch_load when ready

## 2026-07-28 - Merge master into email-connection (pre Wave 0)

- Special-file protocol: index-first FF (chattr +a blocked plain git merge)
- App/docs = master tip 43bd3839 (batch_load, Unassigned, economyIgnored, FUEL_HEADERS)
- eng-log: ve-englog append of master-only tail
- TODO/project-facts: master base + todo-append preferred fuel grade
- Merge commit: e3693de3 (parents 91e94f53 + 43bd3839)
- Next: Wave 0 of email-loyalty-receipt plan after build verify


## 2026-07-28 - Execute email-loyalty-receipt plan (approved)

- Plan: dev-ai-interaction/plans/email-loyalty-receipt-fuel-fills-20260728-plan.md
- Branch: email-connection @ 0bc7af98
- Scope: full plan approval; start Wave 0 (Track A sandbox), then Wave 1
- First action: eng-log (this entry); no ritual TODO


## 2026-07-28 - email-loyalty Wave 0+1 implementation

- Wave 0 (sandbox): dev-ai-interaction/email-receipt/ parser+encoder+Apps Script+tests (43 pass)
- Wave 1 (app): data/email/* ShellReceiptParser, FuelReceiptIngest, Gmail client, WorkManager, Settings UI
- Contract: vehicleId=0, odo=0, partial/economyIgnored false, stable Sync ID, Fuel - Unassigned sheet path


## 2026-07-28 - email-loyalty plan ready to test

- Wave 0: sandbox email-receipt (node 43 pass); Wave 1: app data/email + Settings + builds
- Tag: email-connection/builds @ 7f610576
- Human test: Apps Script dry-run and/or Settings Email receipts poll with labeled Shell mail


## 2026-07-28 - Execute email-loyalty-pre-manual-test plan

- Plan: dev-ai-interaction/plans/email-loyalty-pre-manual-test-20260728-plan.md
- Branch: email-connection @ 930f7b76
- Scope: offline fixture assets + ingest, Settings last-run refresh, MANUAL_TEST.md


## 2026-07-28 - pre-manual-test phases 1-4 done

- Assets email-receipt fixtures; EmailReceiptFixtureIngest; Settings offline + last-run refresh
- MANUAL_TEST.md offline-first; project-facts pointer
- Building for human test handoff


## 2026-07-28 - track email-receipt assets (gitignore exception)

- .gitignore allow app/src/main/assets/email-receipt/** (was ignored by assets/*)
- Force-add shell-receipt1/2.html so offline ingest ships in APK


## 2026-07-29 - Local PR-email-connection prepared for Master

- Pre-submit review vs both email-loyalty plans: intent PASS; no merge-engine creep
- History: 2 logical commits; backup-email-connection @ d26c7960; cleaned HEAD @ 24e173ae
- PR: dev-ai-interaction/PRs/PR-email-connection.md
- Ask Master: Please review PR-email-connection


## 2026-08-01 - Recover agent-4 worktree after rebase

- git reset --hard email-connection @ 1dcc9e65 (stale disk after ref update without checkout)
- FuelReceiptIngest: drop obsolete latitude/longitude (FuelEntry location-only on master tip)
- NDK o+r fixed by human; ./build_app after recovery


## 2026-07-26 - reports-field-conditional-economy-chains-20260726

- Execution start for approved plan: reports-field-conditional-economy-chains-20260726-plan.md
- Branch: batch_load; no prior batch_load/builds tag (first successful ./build_app will create it)
- Scope: ReportsScreen.kt field-conditional MPG/$/mi chains + REPORTS_METRICS.md; phase 5 emulator probe after user deploy

## 2026-07-26 - reports-field-conditional-economy-chains-20260726 phases 1-4 done

- Phases 1-4 implemented and built on batch_load
- ReportsScreen.kt: full fill = odo+cost+vol+!partial; MPG breakers; $/mi segment sum
- docs/reference/REPORTS_METRICS.md rewritten to match
- Tag: batch_load/builds @ 3d29b986 (batch_load-start-4-g3d29b986)
- Phase 5 blocked on user APK deploy to emulator-5554 (device currently has versionName=instruction-start-8-g7d8aa877, not batch_load build)

## 2026-07-26 - reports-field-conditional-economy-chains phase 5 probe PASS

- Backup: test-data-backups/emulator-5554-20260726-095208
- App on emulator-5554: batch_load-start-5-ge76bbc73
- Probe cases odo_only / blank / cost_only / volume_only on Honda between full fills 2→3
- UI Honda: baseline last 15.8 avg 17.0 $/mi 0.259; odo_only same; blank avg 15.8 $/mi 0.260; cost_only avg 15.8 $/mi 0.276; volume_only avg 14.5 $/mi 0.260
- Evidence: dev-ai-interaction/research/reports-chain-probe-20260726/
- DB restored from pre-probe backup; no leftover PROBE rows

## 2026-07-26 - reports-field-conditional-economy-chains PR prepared

- Pre-submit review PASS vs plan; device probe PASS; DB restored
- Local PR: dev-ai-interaction/PRs/PR-batch_load.md
- Tag: batch_load/builds @ 1de13e02; backup-batch_load set
- Ready for Master independent review + merge

## 2026-07-26 - batch-load-ingest-merge-questions-20260726

- Execution start: Stage A (batch ingest) per approved plan batch-load-ingest-merge-questions-20260726-plan.md
- Baseline tag: batch_load/builds @ a02dc9bc; Stages B–C deferred to later turn if A fills the turn

## 2026-07-26 - batch-load Stage A complete (ingest)

- Plan: batch-load-ingest-merge-questions-20260726-plan.md Stage A only
- Added: PhotoExifMeta, FuelPhotoJson, BatchImportPending, BatchFuelImportCoordinator, hardDeleteFuelEntry
- Set I hybrid in PumpCostVolUtils.runSetICostVolExtraction + OcrHarness.runPumpCostVolPipelineSetI
- Import Old Pictures UI: Run batch import / Cancel / Review questions (list-only)
- Hilt ViewModel in ui.batch (not ui.import — Java keyword breaks KSP)
- Builds tag: batch_load/builds @ 6efd2743
- Stages B (merge) and C (apply answers) remain on same plan path — not in this turn

## 2026-07-26 - batch-dash-match-experiment-set-j-20260726

- Execution start: unify Set J (experiment + batch + Quick Fill) per plan
- Baseline: batch_load/builds @ 99d1043c

## 2026-07-26 - batch-setj approach: copy experiment logic into production

- User direction: do NOT extract shared dual-call API; copy experiment Set J into production
- Experiment remains free to tinker (future compile-out); production batch/Quick Fill own a frozen copy
- Continue plan batch-dash-match-experiment-set-j-20260726-plan.md with this approach

## 2026-07-26 - batch-dash-match-experiment-set-j production copy done

- OcrHarness.runSetJPipeline rewritten as independent copy of experiment Set J (Raw+Bin-Trials+connectSegmentsH+pickBestOdometer)
- ExperimentAlignmentScreen NOT shared — free to tinker
- Batch parseSetJOdometer: 4-7 pure digits only (no concat soup)
- Tag: batch_load/builds @ 38186950
- Device parity CSV pending user deploy + re-import after purging old batch dash rows
- Notes: dev-ai-interaction/research/batch-setj-parity-20260726/NOTES.md

## 2026-07-26 - production-setj-ref-geometry-parity-20260726

- Execution start: fix production Set J ref geometry (probe/4080, ICRS crop, Raw nestFilter)
- Baseline: batch_load/builds @ d51f30d9

## 2026-07-26 - production-setj-ref-geometry-parity done

- Probed ref dims for landmarks+align; fallback 4080×3072
- ICRS Float createCrop for odo window; Raw no nestFilter
- Experiment screen untouched (independent copy)
- Tag: batch_load/builds @ de113410
- Device compare pending user deploy; notes in research/setj-geometry-parity-20260726/

## 2026-07-26 - batch import limited button (first 20+20)

- Import UI: OutlinedButton "First 20 dash + first 20 pump" (name-sorted take)
- BatchFuelImportCoordinator.runIngest(maxDash, maxPump); LIMITED_IMPORT_COUNT=20

## 2026-07-26 - batch pump ingest without vehicle

- processPump always runs Set I; inserts with UNASSIGNED_VEHICLE_ID=0
- No ASSIGN_VEHICLE gate; merge later assigns vehicle via time/location pairing
- Unreadable pumps still pending only

## 2026-07-26 - batch-setj-reverify-and-pending-answers-20260726

- Execution start; device versionName=batch_load-start-21-gf43bb991 (geometry + unassigned pump)
- Part A first-20 re-verify then Part B clickable pending answers

## 2026-07-26 - batch-setj-reverify + pending answers

- Part A first-20 re-verify on f43bb991: 17 dash inserts + 20 pump vehicleId=0
- first20 score: see research/batch-setj-parity-20260726/first20-after-geometry.txt (gate FAIL — residual leading-digit/blank DIFFs)
- Part B: clickable pending UI + forcedVehicleId dash reprocess + skip/retry pump (build 06707a0a)
- Experiment untouched; deploy needed for Part B on device

## 2026-07-27 - batch-mirror-experiment-pipelines-setj-seti

- Execution start: batch must run experiment Set J / Set I pipelines, not OcrHarness reinvented front-end
- Baseline tag: batch_load/builds @ c112e80e

## 2026-07-27 - batch-mirror-experiment-pipelines-setj-seti done (code)

- AlignmentSetJRunner: experiment Set A ID lock + Set J runPaddleValleyIterative for batch dash
- PumpSetIRunner: experiment-equivalent Set I for batch pump
- BatchFuelImportCoordinator no longer uses runAutoFillPipeline / runPumpCostVolPipelineSetI
- Tag: batch_load/builds @ 7653d156
- Device parity first-20 still needs deploy of this APK + re-run (prior c112e80e re-verify failed)

## 2026-07-27 - Batch Stage A full-run parity PASS; Stage B plan ready

- Full batch finished (~02:35 PDT); app idle after last pump `PXL_20260411_201506380.jpg`.
- Selective purge: deleted 74 old `batch_import%` rows (`updatedAt < 1785142500000`); kept 12 non-batch + 290 this-run (ids 219–508); pending cleaned to 6 this-run items.
- Parity vs experiment: dash Set J 142 OK / 0 DIFF (4 no-vehicle pending); pump Set I 148 OK / 0 DIFF (2 unreadable both sides). Report: `dev-ai-interaction/research/batch-run-parity-20260727/`.
- Next coder plan: `dev-ai-interaction/plans/batch-stage-b-merge-engine-coder-20260727-plan.md` (merge engine + Run merge). Durable B+C: `batch-stage-b-merge-and-stage-c-questions-20260727-plan.md`.

## 2026-07-27 - batch-stage-b-merge-engine

- Execution start: Stage B merge engine per batch-stage-b-merge-engine-coder-20260727-plan.md
- Stage A parity PASS; baseline tag 83e36ca0

## 2026-07-27 - batch-stage-b-merge-engine B1–B4 code

- B1: FuelPhotoJson.unionPhotos / addPumpPhoto / addDashPhoto
- B2: FuelRowMergeEngine.planMerge (vehicle clusters ±45m, vehicleId=0 pump pair, sequence vs re-shot, CONFLICT_ODO + photo paths, hard-delete list)
- B3: BatchFuelImportCoordinator.applyMerge + Import **Run merge** button
- B4: Merge after import checkbox default **off**
- Fixtures notes: dev-ai-interaction/research/batch-stage-b-merge-fixtures-20260727.md
- Tag: batch_load/builds @ 13d7c06b
- Device Run merge still to verify after deploy

## 2026-07-27 - batch-stage-b-merge-engine device Run merge

- Deploy: adb install -r (./deploy re-exec loop as ai-coder; used direct install)
- Pre: 302 rows, v0=148, fulls=6, odo_only=140, pump_only=154
- applyMerge: updated=133 deleted=129 pending+=2 → tag code 13d7c06b
- Post: 173 rows, v0=14, fulls=130, odo_only=16, pump_only=25
- Non-batch: 9 kept (absorbed re-shot ids 6–8 same $18.40); full non-batch fills preserved
- CONFLICT_ODO pending: 9594 vs 9698; 198699 vs 98699 (photo paths attached)
- Summary: dev-ai-interaction/research/batch-stage-b-merge-20260727/merge-run-summary.txt
- Stage C deferred (image-first questions, assign→re-merge UI)

## 2026-07-27 - batch-stage-c-image-questions

- Execution start: Stage C image-first pending questions per batch-stage-c-image-questions-coder-20260727-plan.md
- Stage B done on device; tip batch_load/builds @ 0c93922c

## 2026-07-27 - batch-stage-c-image-questions done

- C1: pendingPhotoUris + image thumbs/zoom; DNG placeholder; filename secondary only
- C2: CONFLICT_ODO Keep odo / Keep both; pure wrong-odo hard-delete; pump data odo zeroed
- C3: successful answers auto applyMerge; skip does not re-merge
- Device: Keep odo 9594 → pending 8→7 + remerge updated=16 deleted=1; Skip → pending 6
- Tag: batch_load/builds @ f360ce24
- Notes: dev-ai-interaction/research/batch-stage-c-questions-20260727/smoke-notes.txt
- Residual: 1 CONFLICT_ODO, 3 no-vehicle dash, 2 unreadable pump; AMBIGUOUS_MULTI_PUMP unused

## 2026-07-27 - batch-stage-c-photo-ux-manual-entry

- Execution start: photo UX, manual entry, economyIgnored sync, tank heuristic, unknown vehicle, outliers
- Parent tip: batch_load/builds @ 27d92a09

## 2026-07-27 - batch-stage-c-photo-ux-manual-entry done

- Photo stem-dedupe; DNG OpenCV preview; fullscreen +/− + sticky actions
- Manual pump/dash/conflict odo; economyIgnored Room v14 + sync column Economy Ignored
- Reports: fills N(Mp); Unknown label; economy excludes ignored; avg filters 3× outliers
- Merge: tank maxFill+5 auto-assign; post-merge enqueue unknown + MPG_OUTLIER
- Device: merge pending+=52 (9 unknown, 42 outliers); tag batch_load/builds @ aa9b1e53
- Notes: research/batch-stage-c-photo-ux-20260727/smoke-notes.txt

## 2026-07-27 - batch-merge-window-odo-sanity

- Execution start: 15m merge window, tight dash/pump pairs, odo reverse/gap sanitizer, Flag partial, per-vehicle unknown context
- Parent tip: batch_load/builds @ 3f915150

## 2026-07-27 - batch-merge-window-odo-sanity done

- MERGE_WINDOW_MS=15m; splitTightDashPumpPairs for multi dash+pump
- FuelOdoSanitizer: gap (maxVol×mpg×3, no fallback) then reverse; bias demote later
- ODO_SUSPECT pending; FlagPartial action; per-vehicle nearest for unknown
- Device: sanitize=31, ODO_SUSPECT=31 (incl. 20119 gap); CONFLICT_ODO=1
- Tag: batch_load/builds @ 1fbe913e
- Notes: research/batch-merge-window-odo-sanity-20260727/smoke-notes.txt

## 2026-07-27 - batch-pending-gap-dedupe-timefmt

- Execution start: Mark as gap, pending rebuild/dedupe, formatTimeDelta, Clear & re-scan
- Parent tip: batch_load/builds @ 25df6455

## 2026-07-27 - batch-pending-gap-dedupe-timefmt done (code)

- Mark as gap (blank odo/cost/vol); pending full rebuild on merge; (kind,fuelEntryId) dedupe
- formatTimeDelta for neighbor/unknown deltas; Clear questions & re-scan button
- Sanitizer skips already-partial / blank; clear all pending kinds per answered fuelEntryId
- Tag: batch_load/builds @ e743f48a
- Verify: ./build_app only — no deploy/device smoke (coder not authorized to deploy for test)
- Notes: research/batch-pending-gap-dedupe-timefmt-20260727/smoke-notes.txt

## 2026-07-27 - batch-mpg-outlier-ui-clarity

- Execution start: MPG outlier UI clarity per batch-mpg-outlier-ui-clarity-20260727-plan.md
- Tip: batch_load/builds @ 47ddcf81
- Device verify only after human deploy (coder will not adb install/deploy)

## 2026-07-27 - batch-mpg-outlier-ui-clarity done (code)

- MPG_OUTLIER: end-only primary photos; leg start/end text; focus toggle prior/end
- Edit/flag/ignore/gap target focusEntryId; nearby badges LEG START / THIS FILL
- Tag: batch_load/builds @ 795c8e3b
- No device test (await human deploy)
- Notes: research/batch-mpg-outlier-ui-clarity-20260727/smoke-notes.txt

## 2026-07-27 - batch-mpg-outlier-ui-clarity (layout v2)

- Re-execute updated plan: Last/This photo blocks + 4-line context
- Prior UI (single strip + toggle) superseded by locked two-block layout

## 2026-07-27 - batch-mpg-outlier-ui-clarity layout v2 done (code)

- Two-block Last/This photos above each button; 4-line text context (before/last/this/after)
- Focus default This fill; edit/flag/ignore/gap target focus id
- toPending: thisPhotoPaths + lastPhotoPaths
- Tag: batch_load/builds @ c272c521
- No device test until human deploy
- Notes: research/batch-mpg-outlier-ui-clarity-20260727/smoke-notes.txt

## 2026-07-27 - batch-partial-flag-semantics + sanitizer-correctness

- Execution start: two plans
  - batch-partial-flag-semantics-20260727-plan.md
  - batch-partial-and-sanitizer-correctness-20260727-plan.md
- No device deploy (human deploys before test)

## 2026-07-27 - partial-flag + sanitizer-correctness done (code)

- isPartialFill explicit-only; inserts/merge/sanitizer never auto-true for incomplete
- FuelOdoSanitizer detect-only (reverse, digit_jump, gap with clean mpg); no demote updates
- Checkbox SetPartialFill; clearAutoPartialFlags repair button
- Reports display band 5–80 + formatMpg n/a outside 1–100
- Quick Fill: isPartialFill=false on save
- Tag: batch_load/builds @ ec44b782
- No device test until human deploy
- Notes: research/batch-partial-sanitizer-correctness-20260727/smoke-notes.txt

## 2026-07-27 - batch-odo-suspect-ui-per-fill

- Execution start: ODO_SUSPECT per-fill UI layout
- No device deploy until human deploys

## 2026-07-27 - batch-odo-suspect-ui-per-fill done (code)

- ODO_SUSPECT: ordered prev/cur/next dash-only photos + odo fields under each
- SaveOdoPeers multi-odo write; payload prevDashPaths/curDashPaths/nextDashPaths
- Tag: batch_load/builds @ 0da926d1
- No device test until human deploy; re-scan after deploy
- Notes: research/batch-odo-suspect-ui-per-fill-20260727/smoke-notes.txt

## 2026-07-27 - batch-sync-cross-device-partials-and-titlebar

- Execution start: soft-delete sync, origin-device partials, title bar logo/version
- No device deploy until human deploys

## 2026-07-27 - batch-sync-cross-device-partials-and-titlebar (in progress)

- Completing MainActivity yellow bar + remaining CTA; no device deploy


## 2026-07-27 - batch-sync-cross-device-partials-and-titlebar done (code)

- Merge: all live partials (sync/QF/batch); soft-delete published absorbs; lat/lon later-ts
- hasUnmatchedPartials + post-sync toast CTA (no auto-merge)
- Yellow title-bar ?N → import?review=1 expand Review questions; Help bullets
- Tag: batch_load/builds @ 5e7de408
- No device test until human deploy
- Notes: research/batch-sync-cross-device-partials-and-titlebar-20260727/smoke-notes.txt


## 2026-07-27 - batch-mpg-gap-button-and-window-fills done (code)

- MPG card: vertical Save / Missing data between last & this / Ignore (no clip)
- markAsGap MPG: insert mid-leg blank; anchors preserved; dismiss if breaker exists
- FuelEconomyChains + breaker-aware detectOutliers; windowSummary inventory
- Post-sync applyMerge after fuel LWW; SYNC_BEHAVIOR + REPORTS_METRICS
- Tag: batch_load/builds @ a50da18f
- No device test until human deploy
- Notes: research/batch-mpg-gap-button-and-window-fills-20260727/smoke-notes.txt


## 2026-07-27 - batch-no-durable-photo-copy done (code)

- processDash/processPump: source paths only; copyToDurable removed
- migrateDurablePhotoRefsToSource rewrite+delete unreferenced mirrors
- PendingPhotoUris prefer source dirs; Help note
- Tag: batch_load/builds @ a18099d1
- No device test until human deploy
- Notes: research/batch-no-durable-photo-copy-20260727/smoke-notes.txt


## 2026-07-28 - batch-stage-c-phased-questions done (code)

- StageCPhase 1–6 store + UI filter; skip/reset; clear-rescan → phase 1
- ODO chain merge + simple length-guess mode; BAD_PUMP_RATIO phase 3
- Gap from phase 3/5; post-sync remoteWins → phase 1 + rebuild
- Tag: batch_load/builds @ 8ad579ef
- No device test until human deploy
- Notes: research/batch-stage-c-phased-questions-20260728/smoke-notes.txt


## 2026-07-28 - batch-stage-c-ux-skip-photos-phase-scope done (code)

- Phase-scoped pending rebuild; Next phase regenerates only next kinds
- Skip ledger same-phase; answer journal jsonl + export (no replay)
- Unassigned vehicle id=0 fixed syncId + Fuel - Unassigned tab; picker exclusions
- Photo role dash/pump; Close z-order; CONFLICT keep-both completes
- Tag: batch_load/builds @ dab2e144
- No device test until human deploy
- Notes: research/batch-stage-c-ux-skip-photos-phase-scope-20260728/smoke-notes.txt


## 2026-07-28 - PR-batch_load prepared for Master review

- History cleaned: 57 commits → 5 logical; backup-batch_load @ ff33f355; cleaned HEAD @ 11a2c1ca
- PR: dev-ai-interaction/PRs/PR-batch_load.md (pre-submit review + plans)
- Not rebased onto master (simplify_experiments); Master merge expects eng-log special handling + possible ExperimentAlignmentScreen conflict
- Tree matches pre-cleanup tip; build_app SUCCESS after cleanup

## 2026-07-26 - reports-field-conditional-economy-chains-20260726

- Execution start for approved plan: reports-field-conditional-economy-chains-20260726-plan.md
- Branch: batch_load; no prior batch_load/builds tag (first successful ./build_app will create it)
- Scope: ReportsScreen.kt field-conditional MPG/$/mi chains + REPORTS_METRICS.md; phase 5 emulator probe after user deploy

## 2026-07-26 - reports-field-conditional-economy-chains-20260726 phases 1-4 done

- Phases 1-4 implemented and built on batch_load
- ReportsScreen.kt: full fill = odo+cost+vol+!partial; MPG breakers; $/mi segment sum
- docs/reference/REPORTS_METRICS.md rewritten to match
- Tag: batch_load/builds @ 3d29b986 (batch_load-start-4-g3d29b986)
- Phase 5 blocked on user APK deploy to emulator-5554 (device currently has versionName=instruction-start-8-g7d8aa877, not batch_load build)

## 2026-07-26 - reports-field-conditional-economy-chains phase 5 probe PASS

- Backup: test-data-backups/emulator-5554-20260726-095208
- App on emulator-5554: batch_load-start-5-ge76bbc73
- Probe cases odo_only / blank / cost_only / volume_only on Honda between full fills 2→3
- UI Honda: baseline last 15.8 avg 17.0 $/mi 0.259; odo_only same; blank avg 15.8 $/mi 0.260; cost_only avg 15.8 $/mi 0.276; volume_only avg 14.5 $/mi 0.260
- Evidence: dev-ai-interaction/research/reports-chain-probe-20260726/
- DB restored from pre-probe backup; no leftover PROBE rows

## 2026-07-26 - reports-field-conditional-economy-chains PR prepared

- Pre-submit review PASS vs plan; device probe PASS; DB restored
- Local PR: dev-ai-interaction/PRs/PR-batch_load.md
- Tag: batch_load/builds @ 1de13e02; backup-batch_load set
- Ready for Master independent review + merge

## 2026-07-26 - batch-load-ingest-merge-questions-20260726

- Execution start: Stage A (batch ingest) per approved plan batch-load-ingest-merge-questions-20260726-plan.md
- Baseline tag: batch_load/builds @ a02dc9bc; Stages B–C deferred to later turn if A fills the turn

## 2026-07-26 - batch-load Stage A complete (ingest)

- Plan: batch-load-ingest-merge-questions-20260726-plan.md Stage A only
- Added: PhotoExifMeta, FuelPhotoJson, BatchImportPending, BatchFuelImportCoordinator, hardDeleteFuelEntry
- Set I hybrid in PumpCostVolUtils.runSetICostVolExtraction + OcrHarness.runPumpCostVolPipelineSetI
- Import Old Pictures UI: Run batch import / Cancel / Review questions (list-only)
- Hilt ViewModel in ui.batch (not ui.import — Java keyword breaks KSP)
- Builds tag: batch_load/builds @ 6efd2743
- Stages B (merge) and C (apply answers) remain on same plan path — not in this turn

## 2026-07-26 - batch-dash-match-experiment-set-j-20260726

- Execution start: unify Set J (experiment + batch + Quick Fill) per plan
- Baseline: batch_load/builds @ 99d1043c

## 2026-07-26 - batch-setj approach: copy experiment logic into production

- User direction: do NOT extract shared dual-call API; copy experiment Set J into production
- Experiment remains free to tinker (future compile-out); production batch/Quick Fill own a frozen copy
- Continue plan batch-dash-match-experiment-set-j-20260726-plan.md with this approach

## 2026-07-26 - batch-dash-match-experiment-set-j production copy done

- OcrHarness.runSetJPipeline rewritten as independent copy of experiment Set J (Raw+Bin-Trials+connectSegmentsH+pickBestOdometer)
- ExperimentAlignmentScreen NOT shared — free to tinker
- Batch parseSetJOdometer: 4-7 pure digits only (no concat soup)
- Tag: batch_load/builds @ 38186950
- Device parity CSV pending user deploy + re-import after purging old batch dash rows
- Notes: dev-ai-interaction/research/batch-setj-parity-20260726/NOTES.md

## 2026-07-26 - production-setj-ref-geometry-parity-20260726

- Execution start: fix production Set J ref geometry (probe/4080, ICRS crop, Raw nestFilter)
- Baseline: batch_load/builds @ d51f30d9

## 2026-07-26 - production-setj-ref-geometry-parity done

- Probed ref dims for landmarks+align; fallback 4080×3072
- ICRS Float createCrop for odo window; Raw no nestFilter
- Experiment screen untouched (independent copy)
- Tag: batch_load/builds @ de113410
- Device compare pending user deploy; notes in research/setj-geometry-parity-20260726/

## 2026-07-26 - batch import limited button (first 20+20)

- Import UI: OutlinedButton "First 20 dash + first 20 pump" (name-sorted take)
- BatchFuelImportCoordinator.runIngest(maxDash, maxPump); LIMITED_IMPORT_COUNT=20

## 2026-07-26 - batch pump ingest without vehicle

- processPump always runs Set I; inserts with UNASSIGNED_VEHICLE_ID=0
- No ASSIGN_VEHICLE gate; merge later assigns vehicle via time/location pairing
- Unreadable pumps still pending only

## 2026-07-26 - batch-setj-reverify-and-pending-answers-20260726

- Execution start; device versionName=batch_load-start-21-gf43bb991 (geometry + unassigned pump)
- Part A first-20 re-verify then Part B clickable pending answers

## 2026-07-26 - batch-setj-reverify + pending answers

- Part A first-20 re-verify on f43bb991: 17 dash inserts + 20 pump vehicleId=0
- first20 score: see research/batch-setj-parity-20260726/first20-after-geometry.txt (gate FAIL — residual leading-digit/blank DIFFs)
- Part B: clickable pending UI + forcedVehicleId dash reprocess + skip/retry pump (build 06707a0a)
- Experiment untouched; deploy needed for Part B on device

## 2026-07-27 - batch-mirror-experiment-pipelines-setj-seti

- Execution start: batch must run experiment Set J / Set I pipelines, not OcrHarness reinvented front-end
- Baseline tag: batch_load/builds @ c112e80e

## 2026-07-27 - batch-mirror-experiment-pipelines-setj-seti done (code)

- AlignmentSetJRunner: experiment Set A ID lock + Set J runPaddleValleyIterative for batch dash
- PumpSetIRunner: experiment-equivalent Set I for batch pump
- BatchFuelImportCoordinator no longer uses runAutoFillPipeline / runPumpCostVolPipelineSetI
- Tag: batch_load/builds @ 7653d156
- Device parity first-20 still needs deploy of this APK + re-run (prior c112e80e re-verify failed)

## 2026-07-27 - Batch Stage A full-run parity PASS; Stage B plan ready

- Full batch finished (~02:35 PDT); app idle after last pump `PXL_20260411_201506380.jpg`.
- Selective purge: deleted 74 old `batch_import%` rows (`updatedAt < 1785142500000`); kept 12 non-batch + 290 this-run (ids 219–508); pending cleaned to 6 this-run items.
- Parity vs experiment: dash Set J 142 OK / 0 DIFF (4 no-vehicle pending); pump Set I 148 OK / 0 DIFF (2 unreadable both sides). Report: `dev-ai-interaction/research/batch-run-parity-20260727/`.
- Next coder plan: `dev-ai-interaction/plans/batch-stage-b-merge-engine-coder-20260727-plan.md` (merge engine + Run merge). Durable B+C: `batch-stage-b-merge-and-stage-c-questions-20260727-plan.md`.

## 2026-07-27 - batch-stage-b-merge-engine

- Execution start: Stage B merge engine per batch-stage-b-merge-engine-coder-20260727-plan.md
- Stage A parity PASS; baseline tag 83e36ca0

## 2026-07-27 - batch-stage-b-merge-engine B1–B4 code

- B1: FuelPhotoJson.unionPhotos / addPumpPhoto / addDashPhoto
- B2: FuelRowMergeEngine.planMerge (vehicle clusters ±45m, vehicleId=0 pump pair, sequence vs re-shot, CONFLICT_ODO + photo paths, hard-delete list)
- B3: BatchFuelImportCoordinator.applyMerge + Import **Run merge** button
- B4: Merge after import checkbox default **off**
- Fixtures notes: dev-ai-interaction/research/batch-stage-b-merge-fixtures-20260727.md
- Tag: batch_load/builds @ 13d7c06b
- Device Run merge still to verify after deploy

## 2026-07-27 - batch-stage-b-merge-engine device Run merge

- Deploy: adb install -r (./deploy re-exec loop as ai-coder; used direct install)
- Pre: 302 rows, v0=148, fulls=6, odo_only=140, pump_only=154
- applyMerge: updated=133 deleted=129 pending+=2 → tag code 13d7c06b
- Post: 173 rows, v0=14, fulls=130, odo_only=16, pump_only=25
- Non-batch: 9 kept (absorbed re-shot ids 6–8 same $18.40); full non-batch fills preserved
- CONFLICT_ODO pending: 9594 vs 9698; 198699 vs 98699 (photo paths attached)
- Summary: dev-ai-interaction/research/batch-stage-b-merge-20260727/merge-run-summary.txt
- Stage C deferred (image-first questions, assign→re-merge UI)

## 2026-07-27 - batch-stage-c-image-questions

- Execution start: Stage C image-first pending questions per batch-stage-c-image-questions-coder-20260727-plan.md
- Stage B done on device; tip batch_load/builds @ 0c93922c

## 2026-07-27 - batch-stage-c-image-questions done

- C1: pendingPhotoUris + image thumbs/zoom; DNG placeholder; filename secondary only
- C2: CONFLICT_ODO Keep odo / Keep both; pure wrong-odo hard-delete; pump data odo zeroed
- C3: successful answers auto applyMerge; skip does not re-merge
- Device: Keep odo 9594 → pending 8→7 + remerge updated=16 deleted=1; Skip → pending 6
- Tag: batch_load/builds @ f360ce24
- Notes: dev-ai-interaction/research/batch-stage-c-questions-20260727/smoke-notes.txt
- Residual: 1 CONFLICT_ODO, 3 no-vehicle dash, 2 unreadable pump; AMBIGUOUS_MULTI_PUMP unused

## 2026-07-27 - batch-stage-c-photo-ux-manual-entry

- Execution start: photo UX, manual entry, economyIgnored sync, tank heuristic, unknown vehicle, outliers
- Parent tip: batch_load/builds @ 27d92a09

## 2026-07-27 - batch-stage-c-photo-ux-manual-entry done

- Photo stem-dedupe; DNG OpenCV preview; fullscreen +/− + sticky actions
- Manual pump/dash/conflict odo; economyIgnored Room v14 + sync column Economy Ignored
- Reports: fills N(Mp); Unknown label; economy excludes ignored; avg filters 3× outliers
- Merge: tank maxFill+5 auto-assign; post-merge enqueue unknown + MPG_OUTLIER
- Device: merge pending+=52 (9 unknown, 42 outliers); tag batch_load/builds @ aa9b1e53
- Notes: research/batch-stage-c-photo-ux-20260727/smoke-notes.txt

## 2026-07-27 - batch-merge-window-odo-sanity

- Execution start: 15m merge window, tight dash/pump pairs, odo reverse/gap sanitizer, Flag partial, per-vehicle unknown context
- Parent tip: batch_load/builds @ 3f915150

## 2026-07-27 - batch-merge-window-odo-sanity done

- MERGE_WINDOW_MS=15m; splitTightDashPumpPairs for multi dash+pump
- FuelOdoSanitizer: gap (maxVol×mpg×3, no fallback) then reverse; bias demote later
- ODO_SUSPECT pending; FlagPartial action; per-vehicle nearest for unknown
- Device: sanitize=31, ODO_SUSPECT=31 (incl. 20119 gap); CONFLICT_ODO=1
- Tag: batch_load/builds @ 1fbe913e
- Notes: research/batch-merge-window-odo-sanity-20260727/smoke-notes.txt

## 2026-07-27 - batch-pending-gap-dedupe-timefmt

- Execution start: Mark as gap, pending rebuild/dedupe, formatTimeDelta, Clear & re-scan
- Parent tip: batch_load/builds @ 25df6455

## 2026-07-27 - batch-pending-gap-dedupe-timefmt done (code)

- Mark as gap (blank odo/cost/vol); pending full rebuild on merge; (kind,fuelEntryId) dedupe
- formatTimeDelta for neighbor/unknown deltas; Clear questions & re-scan button
- Sanitizer skips already-partial / blank; clear all pending kinds per answered fuelEntryId
- Tag: batch_load/builds @ e743f48a
- Verify: ./build_app only — no deploy/device smoke (coder not authorized to deploy for test)
- Notes: research/batch-pending-gap-dedupe-timefmt-20260727/smoke-notes.txt

## 2026-07-27 - batch-mpg-outlier-ui-clarity

- Execution start: MPG outlier UI clarity per batch-mpg-outlier-ui-clarity-20260727-plan.md
- Tip: batch_load/builds @ 47ddcf81
- Device verify only after human deploy (coder will not adb install/deploy)

## 2026-07-27 - batch-mpg-outlier-ui-clarity done (code)

- MPG_OUTLIER: end-only primary photos; leg start/end text; focus toggle prior/end
- Edit/flag/ignore/gap target focusEntryId; nearby badges LEG START / THIS FILL
- Tag: batch_load/builds @ 795c8e3b
- No device test (await human deploy)
- Notes: research/batch-mpg-outlier-ui-clarity-20260727/smoke-notes.txt

## 2026-07-27 - batch-mpg-outlier-ui-clarity (layout v2)

- Re-execute updated plan: Last/This photo blocks + 4-line context
- Prior UI (single strip + toggle) superseded by locked two-block layout

## 2026-07-27 - batch-mpg-outlier-ui-clarity layout v2 done (code)

- Two-block Last/This photos above each button; 4-line text context (before/last/this/after)
- Focus default This fill; edit/flag/ignore/gap target focus id
- toPending: thisPhotoPaths + lastPhotoPaths
- Tag: batch_load/builds @ c272c521
- No device test until human deploy
- Notes: research/batch-mpg-outlier-ui-clarity-20260727/smoke-notes.txt

## 2026-07-27 - batch-partial-flag-semantics + sanitizer-correctness

- Execution start: two plans
  - batch-partial-flag-semantics-20260727-plan.md
  - batch-partial-and-sanitizer-correctness-20260727-plan.md
- No device deploy (human deploys before test)

## 2026-07-27 - partial-flag + sanitizer-correctness done (code)

- isPartialFill explicit-only; inserts/merge/sanitizer never auto-true for incomplete
- FuelOdoSanitizer detect-only (reverse, digit_jump, gap with clean mpg); no demote updates
- Checkbox SetPartialFill; clearAutoPartialFlags repair button
- Reports display band 5–80 + formatMpg n/a outside 1–100
- Quick Fill: isPartialFill=false on save
- Tag: batch_load/builds @ ec44b782
- No device test until human deploy
- Notes: research/batch-partial-sanitizer-correctness-20260727/smoke-notes.txt

## 2026-07-27 - batch-odo-suspect-ui-per-fill

- Execution start: ODO_SUSPECT per-fill UI layout
- No device deploy until human deploys

## 2026-07-27 - batch-odo-suspect-ui-per-fill done (code)

- ODO_SUSPECT: ordered prev/cur/next dash-only photos + odo fields under each
- SaveOdoPeers multi-odo write; payload prevDashPaths/curDashPaths/nextDashPaths
- Tag: batch_load/builds @ 0da926d1
- No device test until human deploy; re-scan after deploy
- Notes: research/batch-odo-suspect-ui-per-fill-20260727/smoke-notes.txt

## 2026-07-27 - batch-sync-cross-device-partials-and-titlebar

- Execution start: soft-delete sync, origin-device partials, title bar logo/version
- No device deploy until human deploys

## 2026-07-27 - batch-sync-cross-device-partials-and-titlebar (in progress)

- Completing MainActivity yellow bar + remaining CTA; no device deploy


## 2026-07-27 - batch-sync-cross-device-partials-and-titlebar done (code)

- Merge: all live partials (sync/QF/batch); soft-delete published absorbs; lat/lon later-ts
- hasUnmatchedPartials + post-sync toast CTA (no auto-merge)
- Yellow title-bar ?N → import?review=1 expand Review questions; Help bullets
- Tag: batch_load/builds @ 5e7de408
- No device test until human deploy
- Notes: research/batch-sync-cross-device-partials-and-titlebar-20260727/smoke-notes.txt


## 2026-07-27 - batch-mpg-gap-button-and-window-fills done (code)

- MPG card: vertical Save / Missing data between last & this / Ignore (no clip)
- markAsGap MPG: insert mid-leg blank; anchors preserved; dismiss if breaker exists
- FuelEconomyChains + breaker-aware detectOutliers; windowSummary inventory
- Post-sync applyMerge after fuel LWW; SYNC_BEHAVIOR + REPORTS_METRICS
- Tag: batch_load/builds @ a50da18f
- No device test until human deploy
- Notes: research/batch-mpg-gap-button-and-window-fills-20260727/smoke-notes.txt


## 2026-07-27 - batch-no-durable-photo-copy done (code)

- processDash/processPump: source paths only; copyToDurable removed
- migrateDurablePhotoRefsToSource rewrite+delete unreferenced mirrors
- PendingPhotoUris prefer source dirs; Help note
- Tag: batch_load/builds @ a18099d1
- No device test until human deploy
- Notes: research/batch-no-durable-photo-copy-20260727/smoke-notes.txt


## 2026-07-28 - batch-stage-c-phased-questions done (code)

- StageCPhase 1–6 store + UI filter; skip/reset; clear-rescan → phase 1
- ODO chain merge + simple length-guess mode; BAD_PUMP_RATIO phase 3
- Gap from phase 3/5; post-sync remoteWins → phase 1 + rebuild
- Tag: batch_load/builds @ 8ad579ef
- No device test until human deploy
- Notes: research/batch-stage-c-phased-questions-20260728/smoke-notes.txt


## 2026-07-28 - batch-stage-c-ux-skip-photos-phase-scope done (code)

- Phase-scoped pending rebuild; Next phase regenerates only next kinds
- Skip ledger same-phase; answer journal jsonl + export (no replay)
- Unassigned vehicle id=0 fixed syncId + Fuel - Unassigned tab; picker exclusions
- Photo role dash/pump; Close z-order; CONFLICT keep-both completes
- Tag: batch_load/builds @ dab2e144
- No device test until human deploy
- Notes: research/batch-stage-c-ux-skip-photos-phase-scope-20260728/smoke-notes.txt


## 2026-07-28 - PR-batch_load prepared for Master review

- History cleaned: 57 commits → 5 logical; backup-batch_load @ ff33f355; cleaned HEAD @ 11a2c1ca
- PR: dev-ai-interaction/PRs/PR-batch_load.md (pre-submit review + plans)
- Not rebased onto master (simplify_experiments); Master merge expects eng-log special handling + possible ExperimentAlignmentScreen conflict
- Tree matches pre-cleanup tip; build_app SUCCESS after cleanup

## 2026-07-26 - reports-field-conditional-economy-chains-20260726

- Execution start for approved plan: reports-field-conditional-economy-chains-20260726-plan.md
- Branch: batch_load; no prior batch_load/builds tag (first successful ./build_app will create it)
- Scope: ReportsScreen.kt field-conditional MPG/$/mi chains + REPORTS_METRICS.md; phase 5 emulator probe after user deploy

## 2026-07-26 - reports-field-conditional-economy-chains-20260726 phases 1-4 done

- Phases 1-4 implemented and built on batch_load
- ReportsScreen.kt: full fill = odo+cost+vol+!partial; MPG breakers; $/mi segment sum
- docs/reference/REPORTS_METRICS.md rewritten to match
- Tag: batch_load/builds @ 3d29b986 (batch_load-start-4-g3d29b986)
- Phase 5 blocked on user APK deploy to emulator-5554 (device currently has versionName=instruction-start-8-g7d8aa877, not batch_load build)

## 2026-07-26 - reports-field-conditional-economy-chains phase 5 probe PASS

- Backup: test-data-backups/emulator-5554-20260726-095208
- App on emulator-5554: batch_load-start-5-ge76bbc73
- Probe cases odo_only / blank / cost_only / volume_only on Honda between full fills 2→3
- UI Honda: baseline last 15.8 avg 17.0 $/mi 0.259; odo_only same; blank avg 15.8 $/mi 0.260; cost_only avg 15.8 $/mi 0.276; volume_only avg 14.5 $/mi 0.260
- Evidence: dev-ai-interaction/research/reports-chain-probe-20260726/
- DB restored from pre-probe backup; no leftover PROBE rows

## 2026-07-26 - reports-field-conditional-economy-chains PR prepared

- Pre-submit review PASS vs plan; device probe PASS; DB restored
- Local PR: dev-ai-interaction/PRs/PR-batch_load.md
- Tag: batch_load/builds @ 1de13e02; backup-batch_load set
- Ready for Master independent review + merge

## 2026-07-26 - batch-load-ingest-merge-questions-20260726

- Execution start: Stage A (batch ingest) per approved plan batch-load-ingest-merge-questions-20260726-plan.md
- Baseline tag: batch_load/builds @ a02dc9bc; Stages B–C deferred to later turn if A fills the turn

## 2026-07-26 - batch-load Stage A complete (ingest)

- Plan: batch-load-ingest-merge-questions-20260726-plan.md Stage A only
- Added: PhotoExifMeta, FuelPhotoJson, BatchImportPending, BatchFuelImportCoordinator, hardDeleteFuelEntry
- Set I hybrid in PumpCostVolUtils.runSetICostVolExtraction + OcrHarness.runPumpCostVolPipelineSetI
- Import Old Pictures UI: Run batch import / Cancel / Review questions (list-only)
- Hilt ViewModel in ui.batch (not ui.import — Java keyword breaks KSP)
- Builds tag: batch_load/builds @ 6efd2743
- Stages B (merge) and C (apply answers) remain on same plan path — not in this turn

## 2026-07-26 - batch-dash-match-experiment-set-j-20260726

- Execution start: unify Set J (experiment + batch + Quick Fill) per plan
- Baseline: batch_load/builds @ 99d1043c

## 2026-07-26 - batch-setj approach: copy experiment logic into production

- User direction: do NOT extract shared dual-call API; copy experiment Set J into production
- Experiment remains free to tinker (future compile-out); production batch/Quick Fill own a frozen copy
- Continue plan batch-dash-match-experiment-set-j-20260726-plan.md with this approach

## 2026-07-26 - batch-dash-match-experiment-set-j production copy done

- OcrHarness.runSetJPipeline rewritten as independent copy of experiment Set J (Raw+Bin-Trials+connectSegmentsH+pickBestOdometer)
- ExperimentAlignmentScreen NOT shared — free to tinker
- Batch parseSetJOdometer: 4-7 pure digits only (no concat soup)
- Tag: batch_load/builds @ 38186950
- Device parity CSV pending user deploy + re-import after purging old batch dash rows
- Notes: dev-ai-interaction/research/batch-setj-parity-20260726/NOTES.md

## 2026-07-26 - production-setj-ref-geometry-parity-20260726

- Execution start: fix production Set J ref geometry (probe/4080, ICRS crop, Raw nestFilter)
- Baseline: batch_load/builds @ d51f30d9

## 2026-07-26 - production-setj-ref-geometry-parity done

- Probed ref dims for landmarks+align; fallback 4080×3072
- ICRS Float createCrop for odo window; Raw no nestFilter
- Experiment screen untouched (independent copy)
- Tag: batch_load/builds @ de113410
- Device compare pending user deploy; notes in research/setj-geometry-parity-20260726/

## 2026-07-26 - batch import limited button (first 20+20)

- Import UI: OutlinedButton "First 20 dash + first 20 pump" (name-sorted take)
- BatchFuelImportCoordinator.runIngest(maxDash, maxPump); LIMITED_IMPORT_COUNT=20

## 2026-07-26 - batch pump ingest without vehicle

- processPump always runs Set I; inserts with UNASSIGNED_VEHICLE_ID=0
- No ASSIGN_VEHICLE gate; merge later assigns vehicle via time/location pairing
- Unreadable pumps still pending only

## 2026-07-26 - batch-setj-reverify-and-pending-answers-20260726

- Execution start; device versionName=batch_load-start-21-gf43bb991 (geometry + unassigned pump)
- Part A first-20 re-verify then Part B clickable pending answers

## 2026-07-26 - batch-setj-reverify + pending answers

- Part A first-20 re-verify on f43bb991: 17 dash inserts + 20 pump vehicleId=0
- first20 score: see research/batch-setj-parity-20260726/first20-after-geometry.txt (gate FAIL — residual leading-digit/blank DIFFs)
- Part B: clickable pending UI + forcedVehicleId dash reprocess + skip/retry pump (build 06707a0a)
- Experiment untouched; deploy needed for Part B on device

## 2026-07-27 - batch-mirror-experiment-pipelines-setj-seti

- Execution start: batch must run experiment Set J / Set I pipelines, not OcrHarness reinvented front-end
- Baseline tag: batch_load/builds @ c112e80e

## 2026-07-27 - batch-mirror-experiment-pipelines-setj-seti done (code)

- AlignmentSetJRunner: experiment Set A ID lock + Set J runPaddleValleyIterative for batch dash
- PumpSetIRunner: experiment-equivalent Set I for batch pump
- BatchFuelImportCoordinator no longer uses runAutoFillPipeline / runPumpCostVolPipelineSetI
- Tag: batch_load/builds @ 7653d156
- Device parity first-20 still needs deploy of this APK + re-run (prior c112e80e re-verify failed)

## 2026-07-27 - Batch Stage A full-run parity PASS; Stage B plan ready

- Full batch finished (~02:35 PDT); app idle after last pump `PXL_20260411_201506380.jpg`.
- Selective purge: deleted 74 old `batch_import%` rows (`updatedAt < 1785142500000`); kept 12 non-batch + 290 this-run (ids 219–508); pending cleaned to 6 this-run items.
- Parity vs experiment: dash Set J 142 OK / 0 DIFF (4 no-vehicle pending); pump Set I 148 OK / 0 DIFF (2 unreadable both sides). Report: `dev-ai-interaction/research/batch-run-parity-20260727/`.
- Next coder plan: `dev-ai-interaction/plans/batch-stage-b-merge-engine-coder-20260727-plan.md` (merge engine + Run merge). Durable B+C: `batch-stage-b-merge-and-stage-c-questions-20260727-plan.md`.

## 2026-07-27 - batch-stage-b-merge-engine

- Execution start: Stage B merge engine per batch-stage-b-merge-engine-coder-20260727-plan.md
- Stage A parity PASS; baseline tag 83e36ca0

## 2026-07-27 - batch-stage-b-merge-engine B1–B4 code

- B1: FuelPhotoJson.unionPhotos / addPumpPhoto / addDashPhoto
- B2: FuelRowMergeEngine.planMerge (vehicle clusters ±45m, vehicleId=0 pump pair, sequence vs re-shot, CONFLICT_ODO + photo paths, hard-delete list)
- B3: BatchFuelImportCoordinator.applyMerge + Import **Run merge** button
- B4: Merge after import checkbox default **off**
- Fixtures notes: dev-ai-interaction/research/batch-stage-b-merge-fixtures-20260727.md
- Tag: batch_load/builds @ 13d7c06b
- Device Run merge still to verify after deploy

## 2026-07-27 - batch-stage-b-merge-engine device Run merge

- Deploy: adb install -r (./deploy re-exec loop as ai-coder; used direct install)
- Pre: 302 rows, v0=148, fulls=6, odo_only=140, pump_only=154
- applyMerge: updated=133 deleted=129 pending+=2 → tag code 13d7c06b
- Post: 173 rows, v0=14, fulls=130, odo_only=16, pump_only=25
- Non-batch: 9 kept (absorbed re-shot ids 6–8 same $18.40); full non-batch fills preserved
- CONFLICT_ODO pending: 9594 vs 9698; 198699 vs 98699 (photo paths attached)
- Summary: dev-ai-interaction/research/batch-stage-b-merge-20260727/merge-run-summary.txt
- Stage C deferred (image-first questions, assign→re-merge UI)

## 2026-07-27 - batch-stage-c-image-questions

- Execution start: Stage C image-first pending questions per batch-stage-c-image-questions-coder-20260727-plan.md
- Stage B done on device; tip batch_load/builds @ 0c93922c

## 2026-07-27 - batch-stage-c-image-questions done

- C1: pendingPhotoUris + image thumbs/zoom; DNG placeholder; filename secondary only
- C2: CONFLICT_ODO Keep odo / Keep both; pure wrong-odo hard-delete; pump data odo zeroed
- C3: successful answers auto applyMerge; skip does not re-merge
- Device: Keep odo 9594 → pending 8→7 + remerge updated=16 deleted=1; Skip → pending 6
- Tag: batch_load/builds @ f360ce24
- Notes: dev-ai-interaction/research/batch-stage-c-questions-20260727/smoke-notes.txt
- Residual: 1 CONFLICT_ODO, 3 no-vehicle dash, 2 unreadable pump; AMBIGUOUS_MULTI_PUMP unused

## 2026-07-27 - batch-stage-c-photo-ux-manual-entry

- Execution start: photo UX, manual entry, economyIgnored sync, tank heuristic, unknown vehicle, outliers
- Parent tip: batch_load/builds @ 27d92a09

## 2026-07-27 - batch-stage-c-photo-ux-manual-entry done

- Photo stem-dedupe; DNG OpenCV preview; fullscreen +/− + sticky actions
- Manual pump/dash/conflict odo; economyIgnored Room v14 + sync column Economy Ignored
- Reports: fills N(Mp); Unknown label; economy excludes ignored; avg filters 3× outliers
- Merge: tank maxFill+5 auto-assign; post-merge enqueue unknown + MPG_OUTLIER
- Device: merge pending+=52 (9 unknown, 42 outliers); tag batch_load/builds @ aa9b1e53
- Notes: research/batch-stage-c-photo-ux-20260727/smoke-notes.txt

## 2026-07-27 - batch-merge-window-odo-sanity

- Execution start: 15m merge window, tight dash/pump pairs, odo reverse/gap sanitizer, Flag partial, per-vehicle unknown context
- Parent tip: batch_load/builds @ 3f915150

## 2026-07-27 - batch-merge-window-odo-sanity done

- MERGE_WINDOW_MS=15m; splitTightDashPumpPairs for multi dash+pump
- FuelOdoSanitizer: gap (maxVol×mpg×3, no fallback) then reverse; bias demote later
- ODO_SUSPECT pending; FlagPartial action; per-vehicle nearest for unknown
- Device: sanitize=31, ODO_SUSPECT=31 (incl. 20119 gap); CONFLICT_ODO=1
- Tag: batch_load/builds @ 1fbe913e
- Notes: research/batch-merge-window-odo-sanity-20260727/smoke-notes.txt

## 2026-07-27 - batch-pending-gap-dedupe-timefmt

- Execution start: Mark as gap, pending rebuild/dedupe, formatTimeDelta, Clear & re-scan
- Parent tip: batch_load/builds @ 25df6455

## 2026-07-27 - batch-pending-gap-dedupe-timefmt done (code)

- Mark as gap (blank odo/cost/vol); pending full rebuild on merge; (kind,fuelEntryId) dedupe
- formatTimeDelta for neighbor/unknown deltas; Clear questions & re-scan button
- Sanitizer skips already-partial / blank; clear all pending kinds per answered fuelEntryId
- Tag: batch_load/builds @ e743f48a
- Verify: ./build_app only — no deploy/device smoke (coder not authorized to deploy for test)
- Notes: research/batch-pending-gap-dedupe-timefmt-20260727/smoke-notes.txt

## 2026-07-27 - batch-mpg-outlier-ui-clarity

- Execution start: MPG outlier UI clarity per batch-mpg-outlier-ui-clarity-20260727-plan.md
- Tip: batch_load/builds @ 47ddcf81
- Device verify only after human deploy (coder will not adb install/deploy)

## 2026-07-27 - batch-mpg-outlier-ui-clarity done (code)

- MPG_OUTLIER: end-only primary photos; leg start/end text; focus toggle prior/end
- Edit/flag/ignore/gap target focusEntryId; nearby badges LEG START / THIS FILL
- Tag: batch_load/builds @ 795c8e3b
- No device test (await human deploy)
- Notes: research/batch-mpg-outlier-ui-clarity-20260727/smoke-notes.txt

## 2026-07-27 - batch-mpg-outlier-ui-clarity (layout v2)

- Re-execute updated plan: Last/This photo blocks + 4-line context
- Prior UI (single strip + toggle) superseded by locked two-block layout

## 2026-07-27 - batch-mpg-outlier-ui-clarity layout v2 done (code)

- Two-block Last/This photos above each button; 4-line text context (before/last/this/after)
- Focus default This fill; edit/flag/ignore/gap target focus id
- toPending: thisPhotoPaths + lastPhotoPaths
- Tag: batch_load/builds @ c272c521
- No device test until human deploy
- Notes: research/batch-mpg-outlier-ui-clarity-20260727/smoke-notes.txt

## 2026-07-27 - batch-partial-flag-semantics + sanitizer-correctness

- Execution start: two plans
  - batch-partial-flag-semantics-20260727-plan.md
  - batch-partial-and-sanitizer-correctness-20260727-plan.md
- No device deploy (human deploys before test)

## 2026-07-27 - partial-flag + sanitizer-correctness done (code)

- isPartialFill explicit-only; inserts/merge/sanitizer never auto-true for incomplete
- FuelOdoSanitizer detect-only (reverse, digit_jump, gap with clean mpg); no demote updates
- Checkbox SetPartialFill; clearAutoPartialFlags repair button
- Reports display band 5–80 + formatMpg n/a outside 1–100
- Quick Fill: isPartialFill=false on save
- Tag: batch_load/builds @ ec44b782
- No device test until human deploy
- Notes: research/batch-partial-sanitizer-correctness-20260727/smoke-notes.txt

## 2026-07-27 - batch-odo-suspect-ui-per-fill

- Execution start: ODO_SUSPECT per-fill UI layout
- No device deploy until human deploys

## 2026-07-27 - batch-odo-suspect-ui-per-fill done (code)

- ODO_SUSPECT: ordered prev/cur/next dash-only photos + odo fields under each
- SaveOdoPeers multi-odo write; payload prevDashPaths/curDashPaths/nextDashPaths
- Tag: batch_load/builds @ 0da926d1
- No device test until human deploy; re-scan after deploy
- Notes: research/batch-odo-suspect-ui-per-fill-20260727/smoke-notes.txt

## 2026-07-27 - batch-sync-cross-device-partials-and-titlebar

- Execution start: soft-delete sync, origin-device partials, title bar logo/version
- No device deploy until human deploys

## 2026-07-27 - batch-sync-cross-device-partials-and-titlebar (in progress)

- Completing MainActivity yellow bar + remaining CTA; no device deploy


## 2026-07-27 - batch-sync-cross-device-partials-and-titlebar done (code)

- Merge: all live partials (sync/QF/batch); soft-delete published absorbs; lat/lon later-ts
- hasUnmatchedPartials + post-sync toast CTA (no auto-merge)
- Yellow title-bar ?N → import?review=1 expand Review questions; Help bullets
- Tag: batch_load/builds @ 5e7de408
- No device test until human deploy
- Notes: research/batch-sync-cross-device-partials-and-titlebar-20260727/smoke-notes.txt


## 2026-07-27 - batch-mpg-gap-button-and-window-fills done (code)

- MPG card: vertical Save / Missing data between last & this / Ignore (no clip)
- markAsGap MPG: insert mid-leg blank; anchors preserved; dismiss if breaker exists
- FuelEconomyChains + breaker-aware detectOutliers; windowSummary inventory
- Post-sync applyMerge after fuel LWW; SYNC_BEHAVIOR + REPORTS_METRICS
- Tag: batch_load/builds @ a50da18f
- No device test until human deploy
- Notes: research/batch-mpg-gap-button-and-window-fills-20260727/smoke-notes.txt


## 2026-07-27 - batch-no-durable-photo-copy done (code)

- processDash/processPump: source paths only; copyToDurable removed
- migrateDurablePhotoRefsToSource rewrite+delete unreferenced mirrors
- PendingPhotoUris prefer source dirs; Help note
- Tag: batch_load/builds @ a18099d1
- No device test until human deploy
- Notes: research/batch-no-durable-photo-copy-20260727/smoke-notes.txt


## 2026-07-28 - batch-stage-c-phased-questions done (code)

- StageCPhase 1–6 store + UI filter; skip/reset; clear-rescan → phase 1
- ODO chain merge + simple length-guess mode; BAD_PUMP_RATIO phase 3
- Gap from phase 3/5; post-sync remoteWins → phase 1 + rebuild
- Tag: batch_load/builds @ 8ad579ef
- No device test until human deploy
- Notes: research/batch-stage-c-phased-questions-20260728/smoke-notes.txt


## 2026-07-28 - batch-stage-c-ux-skip-photos-phase-scope done (code)

- Phase-scoped pending rebuild; Next phase regenerates only next kinds
- Skip ledger same-phase; answer journal jsonl + export (no replay)
- Unassigned vehicle id=0 fixed syncId + Fuel - Unassigned tab; picker exclusions
- Photo role dash/pump; Close z-order; CONFLICT keep-both completes
- Tag: batch_load/builds @ dab2e144
- No device test until human deploy
- Notes: research/batch-stage-c-ux-skip-photos-phase-scope-20260728/smoke-notes.txt


## 2026-07-28 - PR-batch_load prepared for Master review

- History cleaned: 57 commits → 5 logical; backup-batch_load @ ff33f355; cleaned HEAD @ 11a2c1ca
- PR: dev-ai-interaction/PRs/PR-batch_load.md (pre-submit review + plans)
- Not rebased onto master (simplify_experiments); Master merge expects eng-log special handling + possible ExperimentAlignmentScreen conflict
- Tree matches pre-cleanup tip; build_app SUCCESS after cleanup

## 2026-07-30 - Master merge PR-improve-merges

- Independent review APPROVE; audit_merge FF SUCCESS
- merge-branch-into-master.sh improve-merges (FF index path); ve-englog attempted dup tail → restored index to HEAD eng-log (no branch eng-log delta)
- Worktree ENGINEERING_LOG still has prior +a duplicate tail (skip-worktree for integrity); needs sudo chattr -a + restore when available
- project-facts: Room v16 + MergeAck locations
- Commit 4011fddd on tip after feature commits; builds tag @ 4011fddd
- BUILD SUCCESSFUL; no works tag
- Cleanup: ./remove_worktree.sh improve-merges from repo root when ready


## 2026-07-30 - Paddle-Lite PR email rewrite

- Approved plan: dev-ai-interaction/plans/paddle-upstream-fix-author-email-20260730-plan.md
- Scope: rewrite david@example.com → david@lang.hm on pr-upstream-cleanup, pr-x86-android-mobile-gap, pr-calib-safe-uint8-dequant in Paddle-Lite-upstream; force-push to push remote (davidelang/Paddle-Lite)
- No VehicleExpenses app source changes

## 2026-07-30 - Paddle-Lite PR email rewrite complete (local)

- Local identity: user.email → david@lang.hm in Paddle-Lite-upstream
- filter-branch rewrite: pr-upstream-cleanup, pr-x86-android-mobile-gap, pr-calib-safe-uint8-dequant (+ WIP hygiene heads)
- Trees identical to backup/*-pre-email; author+committer now david@lang.hm
- New tips: 2ca962497 / c6a9b9ada / d718308c5
- Force-push to davidelang/Paddle-Lite FAILED: ai-coder SSH publickey denied — user must push with credentials
- Note: dev-ai-interaction/paddle-pr-email-rewrite-backup-20260730.txt

## 2026-07-30 - TODO hygiene (master audit)

- Closed two backlog items after code/PR review (user-approved):
  - Generate UI manual / in-app guide (PR-instruction delivered)
  - Quick Fill Settings currency/volume "use system" (already on master)
- CHANGELOG § Backlog completed (2026-07-30) updated
- Email/import TODOs left open (email-connection not merged)


## 2026-07-30 - EXEC START: minor-fixes-batch-20260730-plan.md

- Branch: minor-fixes
- Plan: dev-ai-interaction/plans/minor-fixes-batch-20260730-plan.md
- Scope: Waves A–I (settings, About feedback, debug UX, Syncing page, QF notes, photo sync refs-only, fuel download API, Fuel History/edit + on-demand fetch)
- Wave J host research not execution-build

## 2026-07-30 - EXEC DONE: minor-fixes-batch-20260730-plan.md (Waves A–I)

- Wave A: fuel photos label; pump Amazon album URL; max red boxes experiment-gated
- Wave B–C: About feedback email + DeviceFeedbackInfo; debug QF compact X/max Send Delete + info
- Wave D: SyncingScreen + drawer route; Settings loses sync block; red ! → syncing
- Wave E: Quick Fill Notes field + persist/clear
- Wave F: photo FULL/PENDING no expense bulk download; pending downloads = vehicle refs only
- Wave G: scrubUnreadable fuel/expense; downloadFuelPhoto; Fuel DAO getById + VM APIs
- Wave H: Fuel History tabs + FuelEdit; fetch-from-archive on history/edit/expense/batch pending
- Wave I: project-facts orientation for Syncing/Fuel History/on-demand photo policy
- Wave J host research: not in this execution (sandbox planner)
- builds tag tip: minor-fixes/builds @ 54f64e2f (pre–project-facts commit)

## 2026-07-30 - EXEC START: reports-lab-experimental-hub-20260730-plan.md

- Branch: minor-fixes
- Scope: Reports Lab hub + 6 report sets, filters, share TEXT/CSV, Vico charts; production ReportsScreen unchanged
- Waves A–E

## 2026-07-30 - EXEC DONE: reports-lab-experimental-hub-20260730-plan.md

- Reports Lab hub + 6 sets under ui/reports/lab/; drawer always visible
- Filters + prefs (vehicle/period/custom); teaser KPIs; empty states
- Share TEXT/CSV each set; Vehicle summary pack + VIN checkbox (default off)
- Vico 3.2.3 compose + compose-m3: MPG line, unit-price line, monthly bars, category bars
- Production ReportsScreen.kt UNCHANGED
- builds: minor-fixes/builds @ 2251a8f8 (+ project-facts phase next)

## 2026-07-30 - EXEC START: minor-fixes-gaps-nits-followup-20260730-plan.md

- Branch: minor-fixes
- Gaps G1–G5: expenseHasPendingWork; Fuel History fetch label/await; fuel pump_N roles; host photo-kind REPORT

## 2026-07-30 - EXEC DONE: minor-fixes-gaps-nits-followup-20260730-plan.md

- G2: removed dead expenseHasPendingWork + unused expenseNeedsDownload
- G3+G4: Fuel History Fetch image from archive + await Fetching…
- G5: fuelRoleForTag / fuelTagFromRole for pump_N; wired upload+pending+download
- G1: photo-kind-classify script + metrics.json + REPORT.md (5-NN ~88.7%, recommendation: needs human confirm)
- builds: minor-fixes/builds @ 7f9ab6c3

## 2026-07-30 - EXEC START: unit-i18n-consistency-20260730-plan.md

- Branch: minor-fixes
- Plan path: dev-ai-interaction/plans/unit-i18n-consistency-20260730-plan.md

## 2026-07-30 - EXEC DONE: unit-i18n-consistency-20260730-plan.md

- VolumeUnits.formatVolume (+ Context overload); space-before-unit
- UnitFormat: mpg / $/mi / distanceDeltaLabel façade
- Quick Fill convert → VolumeUnits.convert; Fuel History/Edit labels; batch neighbor currency+volume
- Lab formatVolume delegates; Reports + Lab high-traffic economy labels via UnitFormat
- project-facts + TODO i18n deferred pointer
- builds: minor-fixes/builds @ 04efeb68

## 2026-07-30 - EXEC START: form-icons-fontscale-startup-20260730-plan.md

- Branch: minor-fixes
- Plan: form-icons-fontscale-startup-20260730-plan.md

## 2026-07-30 - Phase B5 note: Quick Fill font-scale

- No QF code change: existing responsive A/B/C layout retained; multi-device large-font check deferred to 5554/5556 handoff

## 2026-07-30 - EXEC DONE: fix-icons-fontscale-startup-20260730-plan.md

- R1: material-icons-core + extended (BOM)
- R5: removed copyTessdataOnce
- R2: TopAppBar wrap, Settings debug stack, Fuel History fetch multi-line, Lab banner/title softWrap
- R3: removed dead volumeLabel
- R4: Lab UnitFormat for MPG chart/summary
- R2 B5: QF no change (responsive layout retained)
- builds: minor-fixes/builds @ bb63b72f

## 2026-07-30 - Startup smoke: drop Vico compose-m3

- compose-m3 pulled material3 1.4 → NoSuchMethodError ExposedDropdownMenuBox on QuickFill
- Keep vico:compose:3.2.3 only; cold start emulator-5554 OK (pid live, no Icons/tessdata FATAL)
- Final builds: minor-fixes/builds @ 26095264 (+ facts tip)

## 2026-07-30 - minor-fixes: local PR prepared (history cleanup)

- Soft-reset history cleanup: `git tag -f backup-minor-fixes` @ pre-cleanup tip `c4222079`; `git reset --soft master`; six logical commits; TREE_MATCHES_BACKUP (`HEAD^{tree}` == backup tree `b434f073`).
- Cleaned tip `d04fb7c7` (docs); post-cleanup `./build_app` SUCCESS; `minor-fixes/builds` updated to cleaned tip.
- Pre-submit review vs five plans: scope OK; residual risks = multi-device font checklist + Lab experimental + i18n deferred.
- `./generate_pr.sh` → `dev-ai-interaction/PRs/PR-minor-fixes.md` (plans embedded + Coder pre-submit section).
- Ready for Master (`run-grok-master`) independent review + merge. Coder does not merge.

## 2026-07-30 - Trip tracking open-only fuel tripType execution start

- Approved plan: dev-ai-interaction/plans/trip-tracking-open-only-fuel-triptype-20260730-plan.md
- Branch: trip-tracking; agent-2 coder worktree
- First action eng-log; phases 1–8 schema→sync→UI→nav

## 2026-07-30 - Phase 1 schema tripType tripTypesJson

- FuelEntry.tripType; Vehicle.tripTypesJson; AppDatabase v17; MIGRATION_16_17 registered
- Building phase 1

## 2026-07-30 - Phase 2 TripTypes helper + inherit

- data/trip/TripTypes.kt pure parse/format/seed/reorder
- VehicleRepository.insertVehicle stamps tripTypesJson from inherit or seed

## 2026-07-30 - Phase 3 Tabular Trip Type columns

- FUEL_HEADERS Trip Type; VEHICLE_HEADERS Trip Types JSON; maps + GoogleSheetsClient delegate

## 2026-07-30 - Phase 4 TripTimeline helpers

- isTripStart, tripStartsForVehicle, currentOpenTrip, buildTripStart

## 2026-07-30 - Phase 5+6 TripTrackingScreen manual + manage types

- Manual vehicle/odo/type, Start + Close(Personal), datetime override
- ManageTripTypesDialog add/rename/reorder → vehicle.tripTypesJson

## 2026-07-30 - Phase 7 camera odo path

- TripTrackingScreen CameraPreview + OcrHarness.runAutoFillPipeline fills vehicle/odo

## 2026-07-30 - Phase 8 nav + docs

- MainActivity route triptracking + drawer after Quick Fill; NAVIGATION_MAP; project-facts Room v17 + trip locations

## 2026-07-30 - Trip tracking plan execution complete

- All phases 1–8 built; tag trip-tracking/builds @ 4d54082a
- Open-only fuel tripType + vehicle tripTypesJson + TripTrackingScreen + sync columns
- Ready to test; no ReportsScreen edits

## 2026-07-30 - merge-trip-tracking-into-minor-fixes: execution start

- Magic-approved plan: dev-ai-interaction/plans/merge-trip-tracking-into-minor-fixes-20260730-plan.md
- Branch minor-fixes @ 4cecb088; merging trip-tracking @ f135917f
- Baseline builds tag: minor-fixes/builds

## 2026-07-30 - Trip tracking open-only fuel tripType execution start

- Approved plan: dev-ai-interaction/plans/trip-tracking-open-only-fuel-triptype-20260730-plan.md
- Branch: trip-tracking; agent-2 coder worktree
- First action eng-log; phases 1–8 schema→sync→UI→nav

## 2026-07-30 - Phase 1 schema tripType tripTypesJson

- FuelEntry.tripType; Vehicle.tripTypesJson; AppDatabase v17; MIGRATION_16_17 registered
- Building phase 1

## 2026-07-30 - Phase 2 TripTypes helper + inherit

- data/trip/TripTypes.kt pure parse/format/seed/reorder
- VehicleRepository.insertVehicle stamps tripTypesJson from inherit or seed

## 2026-07-30 - Phase 3 Tabular Trip Type columns

- FUEL_HEADERS Trip Type; VEHICLE_HEADERS Trip Types JSON; maps + GoogleSheetsClient delegate

## 2026-07-30 - Phase 4 TripTimeline helpers

- isTripStart, tripStartsForVehicle, currentOpenTrip, buildTripStart

## 2026-07-30 - Phase 5+6 TripTrackingScreen manual + manage types

- Manual vehicle/odo/type, Start + Close(Personal), datetime override
- ManageTripTypesDialog add/rename/reorder → vehicle.tripTypesJson

## 2026-07-30 - Phase 7 camera odo path

- TripTrackingScreen CameraPreview + OcrHarness.runAutoFillPipeline fills vehicle/odo

## 2026-07-30 - Phase 8 nav + docs

- MainActivity route triptracking + drawer after Quick Fill; NAVIGATION_MAP; project-facts Room v17 + trip locations

## 2026-07-30 - Trip tracking plan execution complete

- All phases 1–8 built; tag trip-tracking/builds @ 4d54082a
- Open-only fuel tripType + vehicle tripTypesJson + TripTrackingScreen + sync columns
- Ready to test; no ReportsScreen edits

## 2026-07-30 - merge-trip-tracking-into-minor-fixes: complete

- Plan: dev-ai-interaction/plans/merge-trip-tracking-into-minor-fixes-20260730-plan.md
- Merge: two-parent commit 4273b527 (trip-tracking → minor-fixes). Standard `git merge` blocked by chattr +a ENGINEERING_LOG; used trip-only checkout + manual MainActivity resolve + commit-tree.
- A2: MainActivity Trip Tracking after Quick Fill + Lab/Fuel History/Syncing retained; build SUCCESS (after java_res META-INF perm clean).
- A3: project-facts DB v17 + trip bullets; NAVIGATION_MAP Syncing/Fuel History/Lab; TODO phase-2 tax-mile line.
- B/C: UnitFormat.distanceUnitShortLabel + odometerReadingLabel; TripTrackingScreen wired (no bare mi); Fuel History trip line; Fuel Edit tripType field.
- Tip 8666c3dc; builds tag minor-fixes/builds.
- Residual: devices on DB v16 migrate once; multi-device font checklist separate; PR-minor-fixes.md stale until re-prepare-local-pr; tax reporting deferred.
- No deploy. Ready for user test / later PR prep.

## 2026-07-30 - reports-lab-trip-miles-and-exclude-trip-from-fuel: start

- Magic-approved plan: dev-ai-interaction/plans/reports-lab-trip-miles-and-exclude-trip-from-fuel-20260730-plan.md
- Branch minor-fixes @ 94b55314; baseline builds tag minor-fixes/builds
- Phases 1–6: trip predicate docs, production+Lab fill inventory exclude trips, trip segment helpers, Lab Trip miles screen, hub/nav/facts

## 2026-07-30 - reports-lab-trip-miles-and-exclude-trip-from-fuel: complete

- Phases 1–6 done; tip 8e0c6f5f; builds tag minor-fixes/builds
- Inventory: production + Lab fill counts/lists exclude TripTimeline.isTripStart
- Lab Trip miles: reports_lab/trips, TripSegments, UnitFormat distance labels, TEXT/CSV share
- Residual: implicit personal miles before first start out of scope; tax PDF n/a; re-prepare PR when ready

## 2026-07-30 - docs-rules-ui-compat-agent-guidance: start

- Magic-approved plan: dev-ai-interaction/plans/docs-rules-ui-compat-agent-guidance-20260730-plan.md
- Branch minor-fixes @ 6b27d49c; phases: trip-miles nits N1–N4 then UI_COMPATIBILITY + mandates/docs

## 2026-07-30 - docs-rules-ui-compat-agent-guidance: complete

- Plan: docs-rules-ui-compat-agent-guidance-20260730-plan.md
- N1–N4 fixed; UI_COMPATIBILITY.md + AGENT_MANDATES Compose UI section + CONTRIBUTING/facts/metrics/USER_GUIDE
- Tip 9174744d; builds tag minor-fixes/builds; no PR/history rewrite

## 2026-07-30 - ui-consistency-cards-density-theme: start

- Magic-approved plan: dev-ai-interaction/plans/ui-consistency-cards-density-theme-20260730-plan.md
- Branch minor-fixes; shared TappableCard/AdaptiveItemGrid/empty/date/cancel + apply in-scope UIs + theme accents + Material Save icons + UI_COMPAT docs

## 2026-07-30 - ui-consistency-cards-density-theme: complete

- Plan: ui-consistency-cards-density-theme-20260730-plan.md
- UiChrome primitives; Material Save/PhotoLibrary; theme accents; date/header/cancel unify
- Lists: Expense, Fuel History, Reports multi-col AdaptiveItemGrid, Lab hub/fills Cards, Syncing TappableCard
- UI_COMPATIBILITY.md sections 11–15 (Cards, density, theme, icons, shared controls)
- Residual: Import pending list not fully re-gridded; experiments untouched; camera chrome fixed contrast kept
- builds tag minor-fixes/builds

## 2026-07-30 - fix-adaptive-item-grid-measure: start

- Magic-approved plan: dev-ai-interaction/plans/fix-adaptive-item-grid-measure-20260730-plan.md
- Fix natural measure (Infinity wrap), remove 148.dp floor, TappableCard wrap, strip fillMaxWidth on grid children

## 2026-07-30 - fix-adaptive-item-grid-measure: complete

- Natural measure: Constraints(0, Infinity) + wrapContentWidth(unbounded); clamp natural to W; no 148.dp floor
- Layout pass: equal cellW fill; TappableCard fillMaxWidth fills cell only
- Reports VehicleSummaryBlock / Last5 wrapContentWidth; AdaptiveStatsText handles unbounded max
- UI_COMPATIBILITY §12 + project-facts helper contract
- Tip builds tag minor-fixes/builds; Phase 5 device check (5554 multi-col Lab hub) for user after install

## 2026-07-30 - minor-fixes: local PR prepared (history cleanup)

- Soft-reset cleanup: backup-minor-fixes @ 56b83864; 33 messy commits → 10 logical; TREE_MATCHES_BACKUP.
- Cleaned tip 3cc40826; post-cleanup ./build_app SUCCESS; minor-fixes/builds updated.
- ./generate_pr.sh → dev-ai-interaction/PRs/PR-minor-fixes.md (11 plans + Coder pre-submit).
- Ready for Master (run-grok-master) independent review + merge. Coder does not merge.

## 2026-07-30 - Master merge PR-minor-fixes

- Merged minor-fixes via merge-branch-into-master.sh (FF index path)
- POST-MERGE: 49 .kt paths staged (gate PASS)
- Special files: master TODO base + closed Trip recording future work; tax-mile phase-2 + i18n future kept; project-facts from branch orientation; CHANGELOG 2026-07-30 audit + minor-fixes section preserved
- Advanced reports left open (Lab experimental)


## 2026-08-01 - Master TODO review (code vs backlog)

- Closed: Advanced reports (Reports Lab shipped on master; multi-select remains separate deferred work)
- Kept open OnlyOffice/Collabora (real backends still wanted; stub/catalog only)
- Annotated: Location Lookup + EXIF/GPS (done on ui-followups, not master tip)
- Annotated: ConflictResolutionScreen exists but unwired to identification
- Future work: trip tax free-text reworded to remaining polish
- Still open (valid): LITE_BUILD_TAILOR, 16k pages, polarity, landmarks remove, BufferSet audit, multi-currency, expense multi-vehicle UI, email import/hook, MSAL, deep linking, GPS currency, ODB-II, Play Store, pump experiment UI removal, schema docs, NDK subproject, missed fill logging, i18n packs


## 2026-07-26 - reports-field-conditional-economy-chains-20260726

- Execution start for approved plan: reports-field-conditional-economy-chains-20260726-plan.md
- Branch: batch_load; no prior batch_load/builds tag (first successful ./build_app will create it)
- Scope: ReportsScreen.kt field-conditional MPG/$/mi chains + REPORTS_METRICS.md; phase 5 emulator probe after user deploy

## 2026-07-26 - reports-field-conditional-economy-chains-20260726 phases 1-4 done

- Phases 1-4 implemented and built on batch_load
- ReportsScreen.kt: full fill = odo+cost+vol+!partial; MPG breakers; $/mi segment sum
- docs/reference/REPORTS_METRICS.md rewritten to match
- Tag: batch_load/builds @ 3d29b986 (batch_load-start-4-g3d29b986)
- Phase 5 blocked on user APK deploy to emulator-5554 (device currently has versionName=instruction-start-8-g7d8aa877, not batch_load build)

## 2026-07-26 - reports-field-conditional-economy-chains phase 5 probe PASS

- Backup: test-data-backups/emulator-5554-20260726-095208
- App on emulator-5554: batch_load-start-5-ge76bbc73
- Probe cases odo_only / blank / cost_only / volume_only on Honda between full fills 2→3
- UI Honda: baseline last 15.8 avg 17.0 $/mi 0.259; odo_only same; blank avg 15.8 $/mi 0.260; cost_only avg 15.8 $/mi 0.276; volume_only avg 14.5 $/mi 0.260
- Evidence: dev-ai-interaction/research/reports-chain-probe-20260726/
- DB restored from pre-probe backup; no leftover PROBE rows

## 2026-07-26 - reports-field-conditional-economy-chains PR prepared

- Pre-submit review PASS vs plan; device probe PASS; DB restored
- Local PR: dev-ai-interaction/PRs/PR-batch_load.md
- Tag: batch_load/builds @ 1de13e02; backup-batch_load set
- Ready for Master independent review + merge

## 2026-07-26 - batch-load-ingest-merge-questions-20260726

- Execution start: Stage A (batch ingest) per approved plan batch-load-ingest-merge-questions-20260726-plan.md
- Baseline tag: batch_load/builds @ a02dc9bc; Stages B–C deferred to later turn if A fills the turn

## 2026-07-26 - batch-load Stage A complete (ingest)

- Plan: batch-load-ingest-merge-questions-20260726-plan.md Stage A only
- Added: PhotoExifMeta, FuelPhotoJson, BatchImportPending, BatchFuelImportCoordinator, hardDeleteFuelEntry
- Set I hybrid in PumpCostVolUtils.runSetICostVolExtraction + OcrHarness.runPumpCostVolPipelineSetI
- Import Old Pictures UI: Run batch import / Cancel / Review questions (list-only)
- Hilt ViewModel in ui.batch (not ui.import — Java keyword breaks KSP)
- Builds tag: batch_load/builds @ 6efd2743
- Stages B (merge) and C (apply answers) remain on same plan path — not in this turn

## 2026-07-26 - batch-dash-match-experiment-set-j-20260726

- Execution start: unify Set J (experiment + batch + Quick Fill) per plan
- Baseline: batch_load/builds @ 99d1043c

## 2026-07-26 - batch-setj approach: copy experiment logic into production

- User direction: do NOT extract shared dual-call API; copy experiment Set J into production
- Experiment remains free to tinker (future compile-out); production batch/Quick Fill own a frozen copy
- Continue plan batch-dash-match-experiment-set-j-20260726-plan.md with this approach

## 2026-07-26 - batch-dash-match-experiment-set-j production copy done

- OcrHarness.runSetJPipeline rewritten as independent copy of experiment Set J (Raw+Bin-Trials+connectSegmentsH+pickBestOdometer)
- ExperimentAlignmentScreen NOT shared — free to tinker
- Batch parseSetJOdometer: 4-7 pure digits only (no concat soup)
- Tag: batch_load/builds @ 38186950
- Device parity CSV pending user deploy + re-import after purging old batch dash rows
- Notes: dev-ai-interaction/research/batch-setj-parity-20260726/NOTES.md

## 2026-07-26 - production-setj-ref-geometry-parity-20260726

- Execution start: fix production Set J ref geometry (probe/4080, ICRS crop, Raw nestFilter)
- Baseline: batch_load/builds @ d51f30d9

## 2026-07-26 - production-setj-ref-geometry-parity done

- Probed ref dims for landmarks+align; fallback 4080×3072
- ICRS Float createCrop for odo window; Raw no nestFilter
- Experiment screen untouched (independent copy)
- Tag: batch_load/builds @ de113410
- Device compare pending user deploy; notes in research/setj-geometry-parity-20260726/

## 2026-07-26 - batch import limited button (first 20+20)

- Import UI: OutlinedButton "First 20 dash + first 20 pump" (name-sorted take)
- BatchFuelImportCoordinator.runIngest(maxDash, maxPump); LIMITED_IMPORT_COUNT=20

## 2026-07-26 - batch pump ingest without vehicle

- processPump always runs Set I; inserts with UNASSIGNED_VEHICLE_ID=0
- No ASSIGN_VEHICLE gate; merge later assigns vehicle via time/location pairing
- Unreadable pumps still pending only

## 2026-07-26 - batch-setj-reverify-and-pending-answers-20260726

- Execution start; device versionName=batch_load-start-21-gf43bb991 (geometry + unassigned pump)
- Part A first-20 re-verify then Part B clickable pending answers

## 2026-07-26 - batch-setj-reverify + pending answers

- Part A first-20 re-verify on f43bb991: 17 dash inserts + 20 pump vehicleId=0
- first20 score: see research/batch-setj-parity-20260726/first20-after-geometry.txt (gate FAIL — residual leading-digit/blank DIFFs)
- Part B: clickable pending UI + forcedVehicleId dash reprocess + skip/retry pump (build 06707a0a)
- Experiment untouched; deploy needed for Part B on device

## 2026-07-27 - batch-mirror-experiment-pipelines-setj-seti

- Execution start: batch must run experiment Set J / Set I pipelines, not OcrHarness reinvented front-end
- Baseline tag: batch_load/builds @ c112e80e

## 2026-07-27 - batch-mirror-experiment-pipelines-setj-seti done (code)

- AlignmentSetJRunner: experiment Set A ID lock + Set J runPaddleValleyIterative for batch dash
- PumpSetIRunner: experiment-equivalent Set I for batch pump
- BatchFuelImportCoordinator no longer uses runAutoFillPipeline / runPumpCostVolPipelineSetI
- Tag: batch_load/builds @ 7653d156
- Device parity first-20 still needs deploy of this APK + re-run (prior c112e80e re-verify failed)

## 2026-07-27 - Batch Stage A full-run parity PASS; Stage B plan ready

- Full batch finished (~02:35 PDT); app idle after last pump `PXL_20260411_201506380.jpg`.
- Selective purge: deleted 74 old `batch_import%` rows (`updatedAt < 1785142500000`); kept 12 non-batch + 290 this-run (ids 219–508); pending cleaned to 6 this-run items.
- Parity vs experiment: dash Set J 142 OK / 0 DIFF (4 no-vehicle pending); pump Set I 148 OK / 0 DIFF (2 unreadable both sides). Report: `dev-ai-interaction/research/batch-run-parity-20260727/`.
- Next coder plan: `dev-ai-interaction/plans/batch-stage-b-merge-engine-coder-20260727-plan.md` (merge engine + Run merge). Durable B+C: `batch-stage-b-merge-and-stage-c-questions-20260727-plan.md`.

## 2026-07-27 - batch-stage-b-merge-engine

- Execution start: Stage B merge engine per batch-stage-b-merge-engine-coder-20260727-plan.md
- Stage A parity PASS; baseline tag 83e36ca0

## 2026-07-27 - batch-stage-b-merge-engine B1–B4 code

- B1: FuelPhotoJson.unionPhotos / addPumpPhoto / addDashPhoto
- B2: FuelRowMergeEngine.planMerge (vehicle clusters ±45m, vehicleId=0 pump pair, sequence vs re-shot, CONFLICT_ODO + photo paths, hard-delete list)
- B3: BatchFuelImportCoordinator.applyMerge + Import **Run merge** button
- B4: Merge after import checkbox default **off**
- Fixtures notes: dev-ai-interaction/research/batch-stage-b-merge-fixtures-20260727.md
- Tag: batch_load/builds @ 13d7c06b
- Device Run merge still to verify after deploy

## 2026-07-27 - batch-stage-b-merge-engine device Run merge

- Deploy: adb install -r (./deploy re-exec loop as ai-coder; used direct install)
- Pre: 302 rows, v0=148, fulls=6, odo_only=140, pump_only=154
- applyMerge: updated=133 deleted=129 pending+=2 → tag code 13d7c06b
- Post: 173 rows, v0=14, fulls=130, odo_only=16, pump_only=25
- Non-batch: 9 kept (absorbed re-shot ids 6–8 same $18.40); full non-batch fills preserved
- CONFLICT_ODO pending: 9594 vs 9698; 198699 vs 98699 (photo paths attached)
- Summary: dev-ai-interaction/research/batch-stage-b-merge-20260727/merge-run-summary.txt
- Stage C deferred (image-first questions, assign→re-merge UI)

## 2026-07-27 - batch-stage-c-image-questions

- Execution start: Stage C image-first pending questions per batch-stage-c-image-questions-coder-20260727-plan.md
- Stage B done on device; tip batch_load/builds @ 0c93922c

## 2026-07-27 - batch-stage-c-image-questions done

- C1: pendingPhotoUris + image thumbs/zoom; DNG placeholder; filename secondary only
- C2: CONFLICT_ODO Keep odo / Keep both; pure wrong-odo hard-delete; pump data odo zeroed
- C3: successful answers auto applyMerge; skip does not re-merge
- Device: Keep odo 9594 → pending 8→7 + remerge updated=16 deleted=1; Skip → pending 6
- Tag: batch_load/builds @ f360ce24
- Notes: dev-ai-interaction/research/batch-stage-c-questions-20260727/smoke-notes.txt
- Residual: 1 CONFLICT_ODO, 3 no-vehicle dash, 2 unreadable pump; AMBIGUOUS_MULTI_PUMP unused

## 2026-07-27 - batch-stage-c-photo-ux-manual-entry

- Execution start: photo UX, manual entry, economyIgnored sync, tank heuristic, unknown vehicle, outliers
- Parent tip: batch_load/builds @ 27d92a09

## 2026-07-27 - batch-stage-c-photo-ux-manual-entry done

- Photo stem-dedupe; DNG OpenCV preview; fullscreen +/− + sticky actions
- Manual pump/dash/conflict odo; economyIgnored Room v14 + sync column Economy Ignored
- Reports: fills N(Mp); Unknown label; economy excludes ignored; avg filters 3× outliers
- Merge: tank maxFill+5 auto-assign; post-merge enqueue unknown + MPG_OUTLIER
- Device: merge pending+=52 (9 unknown, 42 outliers); tag batch_load/builds @ aa9b1e53
- Notes: research/batch-stage-c-photo-ux-20260727/smoke-notes.txt

## 2026-07-27 - batch-merge-window-odo-sanity

- Execution start: 15m merge window, tight dash/pump pairs, odo reverse/gap sanitizer, Flag partial, per-vehicle unknown context
- Parent tip: batch_load/builds @ 3f915150

## 2026-07-27 - batch-merge-window-odo-sanity done

- MERGE_WINDOW_MS=15m; splitTightDashPumpPairs for multi dash+pump
- FuelOdoSanitizer: gap (maxVol×mpg×3, no fallback) then reverse; bias demote later
- ODO_SUSPECT pending; FlagPartial action; per-vehicle nearest for unknown
- Device: sanitize=31, ODO_SUSPECT=31 (incl. 20119 gap); CONFLICT_ODO=1
- Tag: batch_load/builds @ 1fbe913e
- Notes: research/batch-merge-window-odo-sanity-20260727/smoke-notes.txt

## 2026-07-27 - batch-pending-gap-dedupe-timefmt

- Execution start: Mark as gap, pending rebuild/dedupe, formatTimeDelta, Clear & re-scan
- Parent tip: batch_load/builds @ 25df6455

## 2026-07-27 - batch-pending-gap-dedupe-timefmt done (code)

- Mark as gap (blank odo/cost/vol); pending full rebuild on merge; (kind,fuelEntryId) dedupe
- formatTimeDelta for neighbor/unknown deltas; Clear questions & re-scan button
- Sanitizer skips already-partial / blank; clear all pending kinds per answered fuelEntryId
- Tag: batch_load/builds @ e743f48a
- Verify: ./build_app only — no deploy/device smoke (coder not authorized to deploy for test)
- Notes: research/batch-pending-gap-dedupe-timefmt-20260727/smoke-notes.txt

## 2026-07-27 - batch-mpg-outlier-ui-clarity

- Execution start: MPG outlier UI clarity per batch-mpg-outlier-ui-clarity-20260727-plan.md
- Tip: batch_load/builds @ 47ddcf81
- Device verify only after human deploy (coder will not adb install/deploy)

## 2026-07-27 - batch-mpg-outlier-ui-clarity done (code)

- MPG_OUTLIER: end-only primary photos; leg start/end text; focus toggle prior/end
- Edit/flag/ignore/gap target focusEntryId; nearby badges LEG START / THIS FILL
- Tag: batch_load/builds @ 795c8e3b
- No device test (await human deploy)
- Notes: research/batch-mpg-outlier-ui-clarity-20260727/smoke-notes.txt

## 2026-07-27 - batch-mpg-outlier-ui-clarity (layout v2)

- Re-execute updated plan: Last/This photo blocks + 4-line context
- Prior UI (single strip + toggle) superseded by locked two-block layout

## 2026-07-27 - batch-mpg-outlier-ui-clarity layout v2 done (code)

- Two-block Last/This photos above each button; 4-line text context (before/last/this/after)
- Focus default This fill; edit/flag/ignore/gap target focus id
- toPending: thisPhotoPaths + lastPhotoPaths
- Tag: batch_load/builds @ c272c521
- No device test until human deploy
- Notes: research/batch-mpg-outlier-ui-clarity-20260727/smoke-notes.txt

## 2026-07-27 - batch-partial-flag-semantics + sanitizer-correctness

- Execution start: two plans
  - batch-partial-flag-semantics-20260727-plan.md
  - batch-partial-and-sanitizer-correctness-20260727-plan.md
- No device deploy (human deploys before test)

## 2026-07-27 - partial-flag + sanitizer-correctness done (code)

- isPartialFill explicit-only; inserts/merge/sanitizer never auto-true for incomplete
- FuelOdoSanitizer detect-only (reverse, digit_jump, gap with clean mpg); no demote updates
- Checkbox SetPartialFill; clearAutoPartialFlags repair button
- Reports display band 5–80 + formatMpg n/a outside 1–100
- Quick Fill: isPartialFill=false on save
- Tag: batch_load/builds @ ec44b782
- No device test until human deploy
- Notes: research/batch-partial-sanitizer-correctness-20260727/smoke-notes.txt

## 2026-07-27 - batch-odo-suspect-ui-per-fill

- Execution start: ODO_SUSPECT per-fill UI layout
- No device deploy until human deploys

## 2026-07-27 - batch-odo-suspect-ui-per-fill done (code)

- ODO_SUSPECT: ordered prev/cur/next dash-only photos + odo fields under each
- SaveOdoPeers multi-odo write; payload prevDashPaths/curDashPaths/nextDashPaths
- Tag: batch_load/builds @ 0da926d1
- No device test until human deploy; re-scan after deploy
- Notes: research/batch-odo-suspect-ui-per-fill-20260727/smoke-notes.txt

## 2026-07-27 - batch-sync-cross-device-partials-and-titlebar

- Execution start: soft-delete sync, origin-device partials, title bar logo/version
- No device deploy until human deploys

## 2026-07-27 - batch-sync-cross-device-partials-and-titlebar (in progress)

- Completing MainActivity yellow bar + remaining CTA; no device deploy


## 2026-07-27 - batch-sync-cross-device-partials-and-titlebar done (code)

- Merge: all live partials (sync/QF/batch); soft-delete published absorbs; lat/lon later-ts
- hasUnmatchedPartials + post-sync toast CTA (no auto-merge)
- Yellow title-bar ?N → import?review=1 expand Review questions; Help bullets
- Tag: batch_load/builds @ 5e7de408
- No device test until human deploy
- Notes: research/batch-sync-cross-device-partials-and-titlebar-20260727/smoke-notes.txt


## 2026-07-27 - batch-mpg-gap-button-and-window-fills done (code)

- MPG card: vertical Save / Missing data between last & this / Ignore (no clip)
- markAsGap MPG: insert mid-leg blank; anchors preserved; dismiss if breaker exists
- FuelEconomyChains + breaker-aware detectOutliers; windowSummary inventory
- Post-sync applyMerge after fuel LWW; SYNC_BEHAVIOR + REPORTS_METRICS
- Tag: batch_load/builds @ a50da18f
- No device test until human deploy
- Notes: research/batch-mpg-gap-button-and-window-fills-20260727/smoke-notes.txt


## 2026-07-27 - batch-no-durable-photo-copy done (code)

- processDash/processPump: source paths only; copyToDurable removed
- migrateDurablePhotoRefsToSource rewrite+delete unreferenced mirrors
- PendingPhotoUris prefer source dirs; Help note
- Tag: batch_load/builds @ a18099d1
- No device test until human deploy
- Notes: research/batch-no-durable-photo-copy-20260727/smoke-notes.txt


## 2026-07-28 - batch-stage-c-phased-questions done (code)

- StageCPhase 1–6 store + UI filter; skip/reset; clear-rescan → phase 1
- ODO chain merge + simple length-guess mode; BAD_PUMP_RATIO phase 3
- Gap from phase 3/5; post-sync remoteWins → phase 1 + rebuild
- Tag: batch_load/builds @ 8ad579ef
- No device test until human deploy
- Notes: research/batch-stage-c-phased-questions-20260728/smoke-notes.txt


## 2026-07-28 - batch-stage-c-ux-skip-photos-phase-scope done (code)

- Phase-scoped pending rebuild; Next phase regenerates only next kinds
- Skip ledger same-phase; answer journal jsonl + export (no replay)
- Unassigned vehicle id=0 fixed syncId + Fuel - Unassigned tab; picker exclusions
- Photo role dash/pump; Close z-order; CONFLICT keep-both completes
- Tag: batch_load/builds @ dab2e144
- No device test until human deploy
- Notes: research/batch-stage-c-ux-skip-photos-phase-scope-20260728/smoke-notes.txt


## 2026-07-28 - PR-batch_load prepared for Master review

- History cleaned: 57 commits → 5 logical; backup-batch_load @ ff33f355; cleaned HEAD @ 11a2c1ca
- PR: dev-ai-interaction/PRs/PR-batch_load.md (pre-submit review + plans)
- Not rebased onto master (simplify_experiments); Master merge expects eng-log special handling + possible ExperimentAlignmentScreen conflict
- Tree matches pre-cleanup tip; build_app SUCCESS after cleanup


## 2026-07-28 - Master merge PR-batch_load

- Independent review PASS; non-FF merge with simplify_experiments overlap
- merge-branch-into-master failed on +a eng-log (double/triple append side effect); completed via merge-tree + manual resolve
- ExperimentAlignmentScreen: keep Set-J-only simplify + internal helpers for AlignmentSetJRunner (dropped batch dead createScaledBase64/drawCropBoxes conflict side)
- project-facts: batch orientation + Room v14 from branch; TODO unchanged
- Commit 1bfab017; build SUCCESS after java_res permission retry; builds tag updated
- Worktree ENGINEERING_LOG.md may still have triple-appended tail (+a); committed blob is single third-version + this note — fix with sudo chattr -a when available
- No works tag. Cleanup: ./remove_worktree.sh batch_load when ready


## 2026-07-30 - Master merge PR-improve-merges

- Independent review APPROVE; audit_merge FF SUCCESS
- merge-branch-into-master.sh improve-merges (FF index path); ve-englog attempted dup tail → restored index to HEAD eng-log (no branch eng-log delta)
- Worktree ENGINEERING_LOG still has prior +a duplicate tail (skip-worktree for integrity); needs sudo chattr -a + restore when available
- project-facts: Room v16 + MergeAck locations
- Commit 4011fddd on tip after feature commits; builds tag @ 4011fddd
- BUILD SUCCESSFUL; no works tag
- Cleanup: ./remove_worktree.sh improve-merges from repo root when ready


## 2026-07-30 - EXEC START: minor-fixes-batch-20260730-plan.md

- Branch: minor-fixes
- Plan: dev-ai-interaction/plans/minor-fixes-batch-20260730-plan.md
- Scope: Waves A–I (settings, About feedback, debug UX, Syncing page, QF notes, photo sync refs-only, fuel download API, Fuel History/edit + on-demand fetch)
- Wave J host research not execution-build

## 2026-07-30 - EXEC DONE: minor-fixes-batch-20260730-plan.md (Waves A–I)

- Wave A: fuel photos label; pump Amazon album URL; max red boxes experiment-gated
- Wave B–C: About feedback email + DeviceFeedbackInfo; debug QF compact X/max Send Delete + info
- Wave D: SyncingScreen + drawer route; Settings loses sync block; red ! → syncing
- Wave E: Quick Fill Notes field + persist/clear
- Wave F: photo FULL/PENDING no expense bulk download; pending downloads = vehicle refs only
- Wave G: scrubUnreadable fuel/expense; downloadFuelPhoto; Fuel DAO getById + VM APIs
- Wave H: Fuel History tabs + FuelEdit; fetch-from-archive on history/edit/expense/batch pending
- Wave I: project-facts orientation for Syncing/Fuel History/on-demand photo policy
- Wave J host research: not in this execution (sandbox planner)
- builds tag tip: minor-fixes/builds @ 54f64e2f (pre–project-facts commit)

## 2026-07-30 - EXEC START: reports-lab-experimental-hub-20260730-plan.md

- Branch: minor-fixes
- Scope: Reports Lab hub + 6 report sets, filters, share TEXT/CSV, Vico charts; production ReportsScreen unchanged
- Waves A–E

## 2026-07-30 - EXEC DONE: reports-lab-experimental-hub-20260730-plan.md

- Reports Lab hub + 6 sets under ui/reports/lab/; drawer always visible
- Filters + prefs (vehicle/period/custom); teaser KPIs; empty states
- Share TEXT/CSV each set; Vehicle summary pack + VIN checkbox (default off)
- Vico 3.2.3 compose + compose-m3: MPG line, unit-price line, monthly bars, category bars
- Production ReportsScreen.kt UNCHANGED
- builds: minor-fixes/builds @ 2251a8f8 (+ project-facts phase next)

## 2026-07-30 - EXEC START: minor-fixes-gaps-nits-followup-20260730-plan.md

- Branch: minor-fixes
- Gaps G1–G5: expenseHasPendingWork; Fuel History fetch label/await; fuel pump_N roles; host photo-kind REPORT

## 2026-07-30 - EXEC DONE: minor-fixes-gaps-nits-followup-20260730-plan.md

- G2: removed dead expenseHasPendingWork + unused expenseNeedsDownload
- G3+G4: Fuel History Fetch image from archive + await Fetching…
- G5: fuelRoleForTag / fuelTagFromRole for pump_N; wired upload+pending+download
- G1: photo-kind-classify script + metrics.json + REPORT.md (5-NN ~88.7%, recommendation: needs human confirm)
- builds: minor-fixes/builds @ 7f9ab6c3

## 2026-07-30 - EXEC START: unit-i18n-consistency-20260730-plan.md

- Branch: minor-fixes
- Plan path: dev-ai-interaction/plans/unit-i18n-consistency-20260730-plan.md

## 2026-07-30 - EXEC DONE: unit-i18n-consistency-20260730-plan.md

- VolumeUnits.formatVolume (+ Context overload); space-before-unit
- UnitFormat: mpg / $/mi / distanceDeltaLabel façade
- Quick Fill convert → VolumeUnits.convert; Fuel History/Edit labels; batch neighbor currency+volume
- Lab formatVolume delegates; Reports + Lab high-traffic economy labels via UnitFormat
- project-facts + TODO i18n deferred pointer
- builds: minor-fixes/builds @ 04efeb68

## 2026-07-30 - EXEC START: form-icons-fontscale-startup-20260730-plan.md

- Branch: minor-fixes
- Plan: form-icons-fontscale-startup-20260730-plan.md

## 2026-07-30 - Phase B5 note: Quick Fill font-scale

- No QF code change: existing responsive A/B/C layout retained; multi-device large-font check deferred to 5554/5556 handoff

## 2026-07-30 - EXEC DONE: fix-icons-fontscale-startup-20260730-plan.md

- R1: material-icons-core + extended (BOM)
- R5: removed copyTessdataOnce
- R2: TopAppBar wrap, Settings debug stack, Fuel History fetch multi-line, Lab banner/title softWrap
- R3: removed dead volumeLabel
- R4: Lab UnitFormat for MPG chart/summary
- R2 B5: QF no change (responsive layout retained)
- builds: minor-fixes/builds @ bb63b72f

## 2026-07-30 - Startup smoke: drop Vico compose-m3

- compose-m3 pulled material3 1.4 → NoSuchMethodError ExposedDropdownMenuBox on QuickFill
- Keep vico:compose:3.2.3 only; cold start emulator-5554 OK (pid live, no Icons/tessdata FATAL)
- Final builds: minor-fixes/builds @ 26095264 (+ facts tip)

## 2026-07-30 - minor-fixes: local PR prepared (history cleanup)

- Soft-reset history cleanup: `git tag -f backup-minor-fixes` @ pre-cleanup tip `c4222079`; `git reset --soft master`; six logical commits; TREE_MATCHES_BACKUP (`HEAD^{tree}` == backup tree `b434f073`).
- Cleaned tip `d04fb7c7` (docs); post-cleanup `./build_app` SUCCESS; `minor-fixes/builds` updated to cleaned tip.
- Pre-submit review vs five plans: scope OK; residual risks = multi-device font checklist + Lab experimental + i18n deferred.
- `./generate_pr.sh` → `dev-ai-interaction/PRs/PR-minor-fixes.md` (plans embedded + Coder pre-submit section).
- Ready for Master (`run-grok-master`) independent review + merge. Coder does not merge.

## 2026-07-30 - Trip tracking open-only fuel tripType execution start

- Approved plan: dev-ai-interaction/plans/trip-tracking-open-only-fuel-triptype-20260730-plan.md
- Branch: trip-tracking; agent-2 coder worktree
- First action eng-log; phases 1–8 schema→sync→UI→nav

## 2026-07-30 - Phase 1 schema tripType tripTypesJson

- FuelEntry.tripType; Vehicle.tripTypesJson; AppDatabase v17; MIGRATION_16_17 registered
- Building phase 1

## 2026-07-30 - Phase 2 TripTypes helper + inherit

- data/trip/TripTypes.kt pure parse/format/seed/reorder
- VehicleRepository.insertVehicle stamps tripTypesJson from inherit or seed

## 2026-07-30 - Phase 3 Tabular Trip Type columns

- FUEL_HEADERS Trip Type; VEHICLE_HEADERS Trip Types JSON; maps + GoogleSheetsClient delegate

## 2026-07-30 - Phase 4 TripTimeline helpers

- isTripStart, tripStartsForVehicle, currentOpenTrip, buildTripStart

## 2026-07-30 - Phase 5+6 TripTrackingScreen manual + manage types

- Manual vehicle/odo/type, Start + Close(Personal), datetime override
- ManageTripTypesDialog add/rename/reorder → vehicle.tripTypesJson

## 2026-07-30 - Phase 7 camera odo path

- TripTrackingScreen CameraPreview + OcrHarness.runAutoFillPipeline fills vehicle/odo

## 2026-07-30 - Phase 8 nav + docs

- MainActivity route triptracking + drawer after Quick Fill; NAVIGATION_MAP; project-facts Room v17 + trip locations

## 2026-07-30 - Trip tracking plan execution complete

- All phases 1–8 built; tag trip-tracking/builds @ 4d54082a
- Open-only fuel tripType + vehicle tripTypesJson + TripTrackingScreen + sync columns
- Ready to test; no ReportsScreen edits

## 2026-07-30 - merge-trip-tracking-into-minor-fixes: execution start

- Magic-approved plan: dev-ai-interaction/plans/merge-trip-tracking-into-minor-fixes-20260730-plan.md
- Branch minor-fixes @ 4cecb088; merging trip-tracking @ f135917f
- Baseline builds tag: minor-fixes/builds

## 2026-07-30 - Trip tracking open-only fuel tripType execution start

- Approved plan: dev-ai-interaction/plans/trip-tracking-open-only-fuel-triptype-20260730-plan.md
- Branch: trip-tracking; agent-2 coder worktree
- First action eng-log; phases 1–8 schema→sync→UI→nav

## 2026-07-30 - Phase 1 schema tripType tripTypesJson

- FuelEntry.tripType; Vehicle.tripTypesJson; AppDatabase v17; MIGRATION_16_17 registered
- Building phase 1

## 2026-07-30 - Phase 2 TripTypes helper + inherit

- data/trip/TripTypes.kt pure parse/format/seed/reorder
- VehicleRepository.insertVehicle stamps tripTypesJson from inherit or seed

## 2026-07-30 - Phase 3 Tabular Trip Type columns

- FUEL_HEADERS Trip Type; VEHICLE_HEADERS Trip Types JSON; maps + GoogleSheetsClient delegate

## 2026-07-30 - Phase 4 TripTimeline helpers

- isTripStart, tripStartsForVehicle, currentOpenTrip, buildTripStart

## 2026-07-30 - Phase 5+6 TripTrackingScreen manual + manage types

- Manual vehicle/odo/type, Start + Close(Personal), datetime override
- ManageTripTypesDialog add/rename/reorder → vehicle.tripTypesJson

## 2026-07-30 - Phase 7 camera odo path

- TripTrackingScreen CameraPreview + OcrHarness.runAutoFillPipeline fills vehicle/odo

## 2026-07-30 - Phase 8 nav + docs

- MainActivity route triptracking + drawer after Quick Fill; NAVIGATION_MAP; project-facts Room v17 + trip locations

## 2026-07-30 - Trip tracking plan execution complete

- All phases 1–8 built; tag trip-tracking/builds @ 4d54082a
- Open-only fuel tripType + vehicle tripTypesJson + TripTrackingScreen + sync columns
- Ready to test; no ReportsScreen edits

## 2026-07-30 - merge-trip-tracking-into-minor-fixes: complete

- Plan: dev-ai-interaction/plans/merge-trip-tracking-into-minor-fixes-20260730-plan.md
- Merge: two-parent commit 4273b527 (trip-tracking → minor-fixes). Standard `git merge` blocked by chattr +a ENGINEERING_LOG; used trip-only checkout + manual MainActivity resolve + commit-tree.
- A2: MainActivity Trip Tracking after Quick Fill + Lab/Fuel History/Syncing retained; build SUCCESS (after java_res META-INF perm clean).
- A3: project-facts DB v17 + trip bullets; NAVIGATION_MAP Syncing/Fuel History/Lab; TODO phase-2 tax-mile line.
- B/C: UnitFormat.distanceUnitShortLabel + odometerReadingLabel; TripTrackingScreen wired (no bare mi); Fuel History trip line; Fuel Edit tripType field.
- Tip 8666c3dc; builds tag minor-fixes/builds.
- Residual: devices on DB v16 migrate once; multi-device font checklist separate; PR-minor-fixes.md stale until re-prepare-local-pr; tax reporting deferred.
- No deploy. Ready for user test / later PR prep.

## 2026-07-30 - reports-lab-trip-miles-and-exclude-trip-from-fuel: start

- Magic-approved plan: dev-ai-interaction/plans/reports-lab-trip-miles-and-exclude-trip-from-fuel-20260730-plan.md
- Branch minor-fixes @ 94b55314; baseline builds tag minor-fixes/builds
- Phases 1–6: trip predicate docs, production+Lab fill inventory exclude trips, trip segment helpers, Lab Trip miles screen, hub/nav/facts

## 2026-07-30 - reports-lab-trip-miles-and-exclude-trip-from-fuel: complete

- Phases 1–6 done; tip 8e0c6f5f; builds tag minor-fixes/builds
- Inventory: production + Lab fill counts/lists exclude TripTimeline.isTripStart
- Lab Trip miles: reports_lab/trips, TripSegments, UnitFormat distance labels, TEXT/CSV share
- Residual: implicit personal miles before first start out of scope; tax PDF n/a; re-prepare PR when ready

## 2026-07-30 - docs-rules-ui-compat-agent-guidance: start

- Magic-approved plan: dev-ai-interaction/plans/docs-rules-ui-compat-agent-guidance-20260730-plan.md
- Branch minor-fixes @ 6b27d49c; phases: trip-miles nits N1–N4 then UI_COMPATIBILITY + mandates/docs

## 2026-07-30 - docs-rules-ui-compat-agent-guidance: complete

- Plan: docs-rules-ui-compat-agent-guidance-20260730-plan.md
- N1–N4 fixed; UI_COMPATIBILITY.md + AGENT_MANDATES Compose UI section + CONTRIBUTING/facts/metrics/USER_GUIDE
- Tip 9174744d; builds tag minor-fixes/builds; no PR/history rewrite

## 2026-07-30 - ui-consistency-cards-density-theme: start

- Magic-approved plan: dev-ai-interaction/plans/ui-consistency-cards-density-theme-20260730-plan.md
- Branch minor-fixes; shared TappableCard/AdaptiveItemGrid/empty/date/cancel + apply in-scope UIs + theme accents + Material Save icons + UI_COMPAT docs

## 2026-07-30 - ui-consistency-cards-density-theme: complete

- Plan: ui-consistency-cards-density-theme-20260730-plan.md
- UiChrome primitives; Material Save/PhotoLibrary; theme accents; date/header/cancel unify
- Lists: Expense, Fuel History, Reports multi-col AdaptiveItemGrid, Lab hub/fills Cards, Syncing TappableCard
- UI_COMPATIBILITY.md sections 11–15 (Cards, density, theme, icons, shared controls)
- Residual: Import pending list not fully re-gridded; experiments untouched; camera chrome fixed contrast kept
- builds tag minor-fixes/builds

## 2026-07-30 - fix-adaptive-item-grid-measure: start

- Magic-approved plan: dev-ai-interaction/plans/fix-adaptive-item-grid-measure-20260730-plan.md
- Fix natural measure (Infinity wrap), remove 148.dp floor, TappableCard wrap, strip fillMaxWidth on grid children

## 2026-07-30 - fix-adaptive-item-grid-measure: complete

- Natural measure: Constraints(0, Infinity) + wrapContentWidth(unbounded); clamp natural to W; no 148.dp floor
- Layout pass: equal cellW fill; TappableCard fillMaxWidth fills cell only
- Reports VehicleSummaryBlock / Last5 wrapContentWidth; AdaptiveStatsText handles unbounded max
- UI_COMPATIBILITY §12 + project-facts helper contract
- Tip builds tag minor-fixes/builds; Phase 5 device check (5554 multi-col Lab hub) for user after install

## 2026-07-30 - minor-fixes: local PR prepared (history cleanup)

- Soft-reset cleanup: backup-minor-fixes @ 56b83864; 33 messy commits → 10 logical; TREE_MATCHES_BACKUP.
- Cleaned tip 3cc40826; post-cleanup ./build_app SUCCESS; minor-fixes/builds updated.
- ./generate_pr.sh → dev-ai-interaction/PRs/PR-minor-fixes.md (11 plans + Coder pre-submit).
- Ready for Master (run-grok-master) independent review + merge. Coder does not merge.

## 2026-07-30 - post-merge-verify-and-rehome-continue: start

- Magic-approved plan: dev-ai-interaction/plans/post-merge-verify-and-rehome-continue-20260730-plan.md
- agent-1 on minor-fixes; verify master merge then re-home in place to ui-followups from master tip

## 2026-07-30 - post-merge-verify-and-rehome-continue: verify + re-home complete

- Verify: master 36277aff Merge branch minor-fixes into master; minor-fixes 4fb0f104 is ancestor; master..minor-fixes count 0; minor-fixes..master count 1.
- App on master: UiChrome.kt (TappableCard/AdaptiveItemGrid), UI_COMPATIBILITY.md, ReportsLabHubScreen.kt present.
- Diff minor-fixes vs master: ENGINEERING_LOG/TODO/CHANGELOG special files only (not app loss).
- Re-home in place agent-1: branch ui-followups @ 36277aff (= master). minor-fixes left as historical tip (+ eng-log start commit 86a1271d only).
- AGENT_CONTEXT.md Current Branch ui-followups; Status IDLE. No new agent-N / setup_agent.
- ENGINEERING_LOG worktree uses skip-worktree (+a cannot replace with master blob); master worktree retains full merge eng-log.
- Ready for residual feature plans on ui-followups @ 36277aff (re-homed agent-1; no new agent-N).

## 2026-07-31 - ui-followups-batch-photos-reports-settings: start

- Magic-approved plan: dev-ai-interaction/plans/ui-followups-batch-photos-reports-settings-20260731-plan.md
- Branch ui-followups @ 36277aff; phases 1–8 batch photos, phase4 correlation, Reports chrome, Settings debug, QF Notes, Start trip

## 2026-07-31 - ui-followups-batch-photos-reports-settings: complete

- Plan implemented on ui-followups; tip after build ec492f73 (see builds tag).
- Photos: strict DASH/PUMP pendingPhotoUris; Stage C UI filters paths; no unfiltered candidates union.
- Phase4 suggest: time+location (place/geo 150m) before tank/time.
- Reports: drawer/hub rename; share after content; expenses list catalog; no drawer Fuel History; trip blurb short.
- Settings debug QF: compact Send/Delete icons; QF Notes landscape width; Start trip camera-first + manage types UX.
- Residual: full auto-merge partial/full still via existing merge when assign applied; device smoke recommended.

## 2026-07-31 - ui-followups-phase4-auto-merge-debug-layout: start

- Magic-approved plan: dev-ai-interaction/plans/ui-followups-phase4-auto-merge-debug-layout-20260731-plan.md
- Branch ui-followups; silent merge-time place/time assign + dual-pump partial/full; location backfill; debug QF layout

## 2026-07-31 - complete ui-followups-phase4-auto-merge-debug-layout

- FuelStopMatch shared place/geo 150m; FuelRowMergeEngine assignUnassigned place+time unique vehicle
- Same-stop dual-pump: earlier partial later full (mergeSequenceCluster markEarlierPartial)
- Location/lat/lon backfill via preferLocation + mergeFields when survivor empty
- Settings Debug QF: title·Info·count·Delete·Send·toggle; narrow line2 end-aligned
- build_app 289b01d6 success; criteria 1-6 plan path

## 2026-07-31 - integrate-location-fixes-into-ui-followups: start

- Plan: dev-ai-interaction/plans/integrate-location-fixes-into-ui-followups-20260731-plan.md
- ui-followups@289b01d6 merge location-fixes@90fceb54; blob v18 + union UX

## 2026-07-31 - complete integrate-location-fixes-into-ui-followups

- Merged location-fixes@90fceb54 into ui-followups (merge c1ad3551); tip b0462795 builds
- Room v18 location blob; FuelStopMatch/dual-pump/assign on blob coords; mergeFields mergeBlobs
- Trip/QF union: Start trip UX + GPS/EXIF/LocationConfirmBlock; non-blocking POI race
- Batch: Location enhance with import (default off) kickoff-before-OCR + Run location enhance
- Rate-limit pace ≥1s between lookup kickoffs; never await POI for OCR/save/import completion
- location-fixes branch left at 90fceb54 for further work

## 2026-07-31 - reports-hub-ux-implicit-personal-share: start

- Plan: dev-ai-interaction/plans/reports-hub-ux-implicit-personal-share-20260731-plan.md
- ui-followups@b0462795; implicit personal + hub summary + share icon

## 2026-07-31 - complete reports-hub-ux-implicit-personal-share

- TripSegments: leading Personal in period when no start before window (baseline odo → first start)
- Hub: overall + per-vehicle summary; no teasers/dual blurbs; Info icon; content-width filters
- Share: one icon → TEXT/CSV (PDF coming soon) on child report pages
- TODO: rejected continuous GPS / tax forms / end-trip; trip miles packaging note
- build 4440f5dd

## 2026-07-31 - ui-followups-residual-photos-caret-phase1-odo0: start

- Plan: dev-ai-interaction/plans/ui-followups-residual-photos-caret-phase1-odo0-20260731-plan.md
- Parts A–E: zoom photos, caret keys, hide Unassigned, odo=0 phase1, reports hub filters

## 2026-07-31 - complete ui-followups-residual-photos-caret-phase1-odo0

- A: ZoomablePhotoDialog/Thumb; Fuel Edit + History zoom +/−; Stage C still has +/−
- B: CaretEnabledOutlinedTextField (L/R cancel focus); Stage C simple odo, Fuel Edit, Trip odo, QF odo/notes
- C: forUserPicker/forManageList hide Unassigned on Expense/QF/Trip/Manage/Fuel Edit
- D: FuelOdoSanitizer missing odo=0 + dash → simple ODO_SUSPECT phase 1; Save requires odo>0
- E: Hub all-time no filters; child vehicle list data-bearing incl Unknown; content-width filters retained
- build 0c9e9211 + QF caret follow-up

## 2026-07-31 - ui-followups-residual-gaps-close: start

- Plan: dev-ai-interaction/plans/ui-followups-residual-gaps-close-20260731-plan.md
- Close photo/caret wiring + Unknown label + project-facts

## 2026-07-31 - complete ui-followups-residual-gaps-close

- Stage C: removed private FullscreenPhotoDialog → ZoomablePhotoDialog; thumbs 160/220.dp; all odo/cost/vol CaretEnabled + soft buttons
- Expense: tap photo → ZoomablePhotoDialog; amount/vendor/description/odo caret
- Manage Vehicles: View full photo zoom; QF cost+vol caret buttons
- Reports: vehicleId 0 always labeled Unknown (filter bar + LabReportData.vehicleName)
- project-facts residual helpers; build 8a8ef883

## 2026-07-31 - ui-followups-residual-nits-hygiene: start

- Plan: dev-ai-interaction/plans/ui-followups-residual-nits-hygiene-20260731-plan.md
- Dead import; Trip New name/Type caret; LocationConfirmBlock name/address caret

## 2026-07-31 - complete ui-followups-residual-nits-hygiene

- N1: removed unused OutlinedTextField import from ImportOldPicturesScreen
- N2: Trip manage-types New name / New Type → CaretEnabled (no soft buttons)
- N3: LocationConfirmBlock place name/address → CaretEnabled (Trip/QF/Expense)
- Read-only dropdown anchors unchanged; build 0dbc3ebc

## 2026-07-31 - vehicle-summary-last5-expense-categories-trip-delete: start

- Plan: dev-ai-interaction/plans/vehicle-summary-last5-expense-categories-trip-delete-20260731-plan.md
- Trip delete UI; ExpenseCategories v19; vehicle summary last-5 legs

## 2026-07-31 - complete vehicle-summary-last5-expense-categories-trip-delete

- A: LastFullFillLegsBlock on vehicle summary + TEXT/CSV full-fill legs
- B: ExpenseCategories seed; Vehicle.expenseCategoriesJson; Room v19; sheet Expense Categories JSON; Expense dropdown + Manage dialog
- C: Trip types Delete in ManageTripTypesDialog (keep ≥1)
- build bbe08969 (feature af3608ea + fix)

## 2026-07-31 - caret soft buttons under field + location row width

- Finished incomplete staged WIP: soft ◀▶ under numeric fields (not side-by-side; only when focused + number IME + no HW keyboard)
- LocationConfirmBlock: place name | address on one row (half width each)
- QF: location block shares Notes Panel C width in landscape
- build 8e4362c0; vehicle-summary plan remains complete at bbe08969

## 2026-07-31 - qf-caret-softkeys-layout-fix: start

- Plan: dev-ai-interaction/plans/qf-caret-softkeys-layout-fix-20260731-plan.md
- NumericKeypad 4x4 both orients; no QF field soft carets

## 2026-07-31 - qf-caret-softkeys-layout-fix start

- Plan: dev-ai-interaction/plans/qf-caret-softkeys-layout-fix-20260731-plan.md
- Resume mid-WIP: caret handlers present; finish NumericKeypad 4x4, both orients, readOnly, no soft carets
- No deploy; build_app only

## 2026-07-31 - qf-caret-softkeys-layout-fix complete

- Plan: dev-ai-interaction/plans/qf-caret-softkeys-layout-fix-20260731-plan.md
- QF odo/cost/vol: showCaretButtons=false, readOnly=true always; caretIndex wired to keypad
- NumericKeypad true 4x4: 0-9 . ⌫ ◀ ▶ OK(next) blank(dismiss); caret-aware insert/backspace
- Portrait + landscape both show keypad for numeric edit (replaces camera/A+B)
- CaretEnabled: optional caretIndex/onCaretIndexChange; soft carets remain under-field only
- Notes + LocationConfirm one-row place|address (prior + panelCTextWidth)
- build tag: be148a75 (ui-followups/builds); no deploy

## 2026-07-31 - photo-display-cloud-fetch-parity start

- Plan: dev-ai-interaction/plans/photo-display-cloud-fetch-parity-20260731-plan.md
- Fix Stage C OdoPeerBlock + any PendingPhotoRow missing archive fetch
- No deploy; build_app only

## 2026-07-31 - photo-display-cloud-fetch-parity complete

- Plan: dev-ai-interaction/plans/photo-display-cloud-fetch-parity-20260731-plan.md
- Forensic: PendingPhotoRow MPG/simple/generic already had canFetch; gap was OdoPeerBlock only
- OdoPeerBlock: canFetchArchive/isFetchingArchive/onFetchArchive; empty+no archive → No dash photo; else PendingPhotoRow
- Complex ODO prev/cur/next: peer FuelEntry state + fetchArchiveFor(peerId) → dash paths; refresh peer entry after download
- project-facts: on-demand archive fetch surfaces bullet
- build tag: 95d04ebd; no deploy

## 2026-07-31 - reports-pdf-export-all-lab start

- Plan: dev-ai-interaction/plans/reports-pdf-export-all-lab-20260731-plan.md
- Real PdfDocument PDF for all 7 Lab share screens; no deploy

## 2026-07-31 - reports-pdf-export-all-lab complete

- Plan: dev-ai-interaction/plans/reports-pdf-export-all-lab-20260731-plan.md
- ReportsLabPdf: PdfDocument multi-page text builder + fromPlainText (same facts as TEXT)
- ReportsLabShare.sharePdf → filesDir/reports_lab/*.pdf via FileProvider application/pdf
- All 7 Lab children: pdfBody wired; picker shows PDF when body present
- Hub info: TEXT/CSV/PDF; project-facts updated
- No chart bitmaps (PASS tabular/text); no deploy
- build tag: f161d1e0

## 2026-07-31 - caret-home-end-keys start

- Plan: dev-ai-interaction/plans/caret-home-end-keys-20260731-plan.md
- Home/End caret in CaretEnabled + QF via caretIndex; no deploy

## 2026-07-31 - caret-home-end-keys complete

- Plan: dev-ai-interaction/plans/caret-home-end-keys-20260731-plan.md
- CaretEnabled: setCaret + Home/MoveHome → 0, MoveEnd → length; consume key; onCaretIndexChange for QF
- Compose has no Key.End (use MoveEnd only); QF odo/cost/vol use same field path (no extra QF wiring)
- project-facts caret bullet updated; no soft Home/End on keypad
- build tag: 94c5f111; no deploy

## 2026-07-31 - batch-stage-c-empty-phase-nav-and-reorder-by-odo start

- Plan: dev-ai-interaction/plans/batch-stage-c-empty-phase-nav-and-reorder-by-odo-20260731-plan.md
- Part A: empty-phase Next phase always reachable; Part B: reorder-by-odo A/B/C
- No deploy

## 2026-07-31 - batch-stage-c-empty-phase-nav-and-reorder-by-odo complete

- Plan: dev-ai-interaction/plans/batch-stage-c-empty-phase-nav-and-reorder-by-odo-20260731-plan.md
- Part A: Next phase + Reset always on Import chrome; Review questions always enabled; empty-phase copy; stagePhase refreshed after advance
- Part B: FuelOdoReorder + dialog; A permute timestamps onto odo order; B economyIgnored on reverse later; C soft-delete reverse later; gate after odo phases (phase>2 or phase≥2 with no ODO pending)
- applyOdoReorder → applyMerge rescan; project-facts updated
- build tag: 57a269ad; no deploy

## 2026-07-31 - timestamp-filename-fallback + reports-lab-filter-dropdown start

- Plans: batch-import-timestamp-filename-fallback-20260731 + reports-lab-filter-dropdown-select-20260731
- EXIF datetime/offset/GPS accuracy; aggressive filename import; Lab filter Row fix
- No deploy

## 2026-07-31 - timestamp-filename-fallback + reports-lab-filter-dropdown complete

- Plans: batch-import-timestamp-filename-fallback-20260731 + reports-lab-filter-dropdown-select-20260731
- PhotoExifWriter: DateTimeOriginal/DateTime/Digitized + OffsetTime* + GPSHPositioningError when hasAccuracy; optional UserComment ve:tag
- PhotoExifMetaReader: generic filename heuristics (date+time, date-only midnight, ambiguous 8-digit, epoch 10/13) + mtime; source tags
- QF/Expense call sites pass captureTs; batch dash/pump log when falling back to now
- ReportsLabFilterBar: Row not AdaptiveItemGrid; onExpandedChange=it; filters outside verticalScroll; Log.i on select
- build tag: 9dd8800c; no deploy

## 2026-07-31 - stage-c-odo-suspect-looks-correct-ack start

- Plan: stage-c-odo-suspect-looks-correct-ack-20260731-plan.md
- Durable looks-correct for ODO_SUSPECT via merge_acks; UI + resolveMemberSyncIds
- No deploy

## 2026-07-31 - stage-c-odo-suspect-looks-correct-ack complete

- Plan: stage-c-odo-suspect-looks-correct-ack-20260731-plan.md
- Simple + complex ODO_SUSPECT: “These odometers look correct” → AcknowledgeLooksCorrect(ODO_SUSPECT)
- resolveMemberSyncIds adds curEntryId/nextEntryId; MergeAck.KIND_ODO_SUSPECT; no MERGE_EXEMPT for odo
- rebuild filterPending already drops same kind+member syncIds
- build tags: a45d0211 + b44c1cfe; no deploy

## 2026-07-31 - reports-efficiency-mpg-dpm-each-vehicle start

- Plan: reports-efficiency-mpg-dpm-each-vehicle-20260731-plan.md
- Multi-metric efficiency, EACH vehicle, date-X charts, trip Personal fix
- No deploy

## 2026-07-31 - reports-efficiency-mpg-dpm-each-vehicle complete

- Plan: reports-efficiency-mpg-dpm-each-vehicle-20260731-plan.md
- LabVehicleMode ALL/EACH/SINGLE + prefs; filter bar labels; no thrash on empty picker
- Charts: LabTimeSeriesLineChart date X (epoch-days) + rememberVicoScrollState(false) fit width
- Efficiency: mpg/gpm/dpmFuel/dpmIncl toggles (persisted); economy + money charts; export columns
- Metrics: dpmFuelOnly/dpmInclExpenses/gpm per leg; EACH multi-series by vehicle name
- Cost trends EACH + date series; monthly/category labels; vehicle summary EACH=stacked
- Trip: TR2 no-starts Personal baseline→last odo; vehicle mode; info text
- todo-append multi-select Sum/Average; project-facts updated
- Dual Y on one chart = two stacked charts (economy vs $) for scale separation
- build tags: 98a7c13d + ad5644f7; no deploy

## 2026-07-31 - qf-panel-a-min-half-c-scroll-help-info start

- Plan: qf-panel-a-min-half-c-scroll-help-info-20260731-plan.md
- A≥50% / landscape width; C scroll+cap; help→info dialog
- No deploy

## 2026-07-31 - qf-panel-a-min-half-c-scroll-help-info complete

- Plan: qf-panel-a-min-half-c-scroll-help-info-20260731-plan.md
- Portrait: A weight 0.55, B wrap, C weight 0.45 + verticalScroll (B not scrolled away)
- Landscape: A weight 1.2 + minWidth 200.dp; C weight 1 + widthIn(max=280.dp) + scroll
- Removed long shutter help from C; one-line status under B portrait; Info ⓘ dialog with shortcuts
- fieldsContent fillMaxWidth (no wrapContentWidth from help text)
- build tag: bc9789b0; no deploy

## 2026-07-31 - ui-chrome-cleanup-multiaxis-chart-retire-legacy-reports start

- Plan: ui-chrome-cleanup-multiaxis-chart-retire-legacy-reports-20260731-plan.md
- Multi-axis chart; trip/expense/vehicles chrome; retire ReportsScreen + drawer
- No deploy

## 2026-07-31 - ui-chrome-cleanup-multiaxis-chart-retire-legacy-reports complete

- Plan: ui-chrome-cleanup-multiaxis-chart-retire-legacy-reports-20260731-plan.md
- A: LabMultiAxisTimeSeriesChart — one chart, left Start (mpg/gpm share scale), right End ($/mi); dual left axes not used (eng-log)
- B: Trip — no hide camera; camera ~55% weight; no FeatureScreenHeader; Info; Time is now; location status no Resolved; confirm short
- C: Manage Vehicles help → Info dialog
- D: Expense bar New/Edit expense; no mid title/long help; Info; Time is now; Manage categories toast; no Resolved banner
- E: Drawer removed Expense List + Reports & Charts; deleted ReportsScreen; expenselist route kept via hub
- LocationConfirmBlock confirmLabel default “Confirm this location”
- build tag: b21bf0c4; no deploy

## 2026-07-31 - dropdown-overflow-affordance start

- Plan: dropdown-overflow-affordance-20260731-plan.md
- Pin Manage footer on expense category + trip type menus
- No deploy

## 2026-07-31 - dropdown-overflow-affordance complete

- Plan: dropdown-overflow-affordance-20260731-plan.md
- ExposedDropdownMenuBoxScope.ExposedDropdownMenuWithPinnedFooter / WithManageFooter
- Catalog scroll heightIn max 280.dp; “Scroll for more…” when ≥4 items; Manage row pinned below divider
- Wired Expense category + Trip type; project-facts one-liner
- build tag: df85f5a7; no deploy

## 2026-07-31 - trip-qf-controls-topbar-info-import-gate start

- Plan: trip-qf-controls-topbar-info-import-gate-20260731-plan.md
- Trip QF controls; top-bar PageHelp; Import experiment-gated; QF 45%
- No deploy

## 2026-07-31 - trip-qf-controls-topbar-info-import-gate implementing

- MainActivity: PageHelp CompositionLocal + TopAppBar Info; Import under experiment gate
- Manage Vehicles + Expense: RegisterPageHelp; strip mid-screen Info
- Trip/QF already mid-edit: 45/55, CaptureControls, side-by-side checkboxes
- Next: build_app

## 2026-07-31 - trip-qf-controls-topbar-info-import-gate complete

- Plan: trip-qf-controls-topbar-info-import-gate-20260731-plan.md
- A: Trip camera 45%/fields 55%; disk/shutter/Stop row; Confirm+Time is now side-by-side; CaptureControls shared with QF
- B: PageHelp registry (CompositionLocal); TopAppBar Info before ?N; RegisterPageHelp on QF/Trip/Manage/Expense; mid-screen Info removed
- C: Drawer Import only when show_experiment_screens (after Pump Experiment); ?N still opens import review
- D: QF portrait A 0.45 / C 0.55
- Mechanism: dynamic PageHelpController over static route map
- build tag: a8e9897f; no deploy

## 2026-07-31 - sync-now-in-dest-edit-and-failure-details start

- Plan: sync-now-in-dest-edit-and-failure-details-20260731-plan.md
- Failure store full messages + Details UI; Sync now on dest edit; rate-limit retry + pacing
- No deploy

## 2026-07-31 - sync-now-in-dest-edit-and-failure-details complete

- Plan: sync-now-in-dest-edit-and-failure-details-20260731-plan.md
- S: Spreadsheet/photo dest edit footer “Sync now (this destination)” via destId; hub Sync now kept
- E: Failure store full message (capped 6KB); Syncing + dest edit Details + Copy; hub short names + rate-limit title
- R: SyncRateLimit isRateLimitError; 15–45s random backoff; max 3 attempts; multi-dest pace 1.5–3s; spreadsheet mutex + photo mutex
- Mechanism: single-device serialize + pace; cross-device only detect/wait/retry
- build tag: 7b206e22; no deploy

## 2026-07-31 - sheets-rate-limit-write-level-retry-and-pacing start

- Plan: sheets-rate-limit-write-level-retry-and-pacing-20260731-plan.md
- Write-level retry + longer backoff (≥60s) + write pacing + longer inter-dest
- No deploy

## 2026-07-31 - sheets-rate-limit-write-level-retry-and-pacing complete

- Plan: sheets-rate-limit-write-level-retry-and-pacing-20260731-plan.md
- Write unit: SyncRateLimit.withSheetsWriteLimit on GoogleSheetsClient mutations (append/update/clear/batchUpdate/create)
- Backoff: first 60–120s, later 90–180s (cap 180s); MAX_WRITE_ATTEMPTS=8; UI “Rate limited — waiting Ns (try k/n)…”
- Write pace: MIN_WRITE_GAP_MS=1300 (~≤45 writes/min)
- Inter-dest pace: 10–20s; whole-dest rate-limit restart removed
- Exhausted: Details + cross-device hint
- build tag: f0246d9a; no deploy

## 2026-07-31 - sheets-rate-limit residual: wrap reads + writes

- Plan residual: dual-sheet fail was read quota on dest-2 fuel GET (unwrapped)
- withSheetsApiLimit on every GoogleSheetsClient.execute; postDestReadCooldown 15–30s
- No deploy

## 2026-07-31 - sheets-rate-limit residual complete (read+write)

- Plan residual: sheets-rate-limit-write-level-retry-and-pacing-20260731-plan.md
- Root cause: dest-2 fuel GET hit read_requests 429; only writes were wrapped
- withSheetsApiLimit on every GoogleSheetsClient.execute (GET values/meta + mutations)
- Shared MIN_API_GAP_MS=1300; inter-dest 10–20s + postDestReadCooldown 15–30s
- shortTitle: Rate limited (Sheets reads|writes)
- build tag: f17db051; no deploy

## 2026-07-31 - sheets-bulk-read-batchget-compare-pass start

- Plan: sheets-bulk-read-batchget-compare-pass-20260731-plan.md
- batchGet multi-tab compare prefetch; coordinator uses bulk cache for LWW
- No deploy

## 2026-07-31 - sheets-bulk-read-batchget-compare-pass complete

- Plan: sheets-bulk-read-batchget-compare-pass-20260731-plan.md
- batchReadTabs: values.batchGet via executeApi (≤40 ranges/chunk)
- Coordinator LWW: one listTabTitles + bulk prefetch; cache for vehicles/expenses/acks/fuel Pass 1
- ensureHeaders+single re-read only when tab missing or header incomplete
- Non-Google backends: batchReadTabs default loops readAllRows
- build tag: 22e8dd63; no deploy

## 2026-07-31 - sync-failure-orphan-prune start

- Plan: sync-failure-orphan-prune-20260731-plan.md
- Prune orphan destIds from failure store on dest save + sync start
- No deploy

## 2026-07-31 - sync-failure-orphan-prune complete

- Plan: sync-failure-orphan-prune-20260731-plan.md
- pruneToKnownDestinations on dest save + spreadsheet/photo syncNow
- Details orphan title + legacy name-only → “Sync failed (no detail)”
- Real present-dest failures kept until success; full API message still stored
- build tag: 0de03efb; no deploy
x
## 2026-07-31 - ui-followups local PR prepared (history cleanup)

- Soft-reset cleanup: backup-ui-followups @ 0de03efb; 47 messy commits → 7 logical; TREE_MATCHES_BACKUP (3326ab6b).
- Cleaned tip: see git rev-parse HEAD; post-cleanup ./build_app SUCCESS; ui-followups/builds updated.
- ./generate_pr.sh → dev-ai-interaction/PRs/PR-ui-followups.md (plans + Coder pre-submit).
- Ready for Master (run-grok-master) independent review + merge. Coder does not merge.

## 2026-07-31 - photo-backup-compose-scope-cancel-not-failure start

- Plan: photo-backup-compose-scope-cancel-not-failure-20260731-plan.md
- Safe progress; ViewModel-scoped Sync now; cancel ≠ dest failure
- No deploy

## 2026-07-31 - photo-backup-compose-scope-cancel-not-failure complete

- Plan: photo-backup-compose-scope-cancel-not-failure-20260731-plan.md
- Progress: Handler main post (no Compose rememberCoroutineScope throw)
- Sync now: PhotoBackup/Spreadsheet/Settings ViewModels viewModelScope + StateFlow status
- Coordinators: isNonFailureCancel → rethrow; never record rememberCoroutineScope text as dest failure
- build tag: 7e3dfded; no deploy

## 2026-07-31 - docs-refresh + reports Vico X precision start

- Plans: docs-refresh-and-topbar-info-narrow-20260731-plan.md + reports-lab-vico-x-precision-crash-20260731-plan.md
- Quantize chart X; leading Info + narrow title; RegisterPageHelp expand; docs refresh
- No deploy

## 2026-07-31 - docs-refresh + Vico X precision complete

- Plans: docs-refresh-and-topbar-info-narrow-20260731-plan.md; reports-lab-vico-x-precision-crash-20260731-plan.md
- tsToChartX: round to 4 decimals (Vico GCD crash)
- TopAppBar: Info leading next to ☰/←; narrow title <600dp; maxLines=1; trailing only ?N/!
- RegisterPageHelp: Lab via TitleRow; Syncing/Settings/FuelHistory/Spreadsheet/Photo lists
- Docs: NAVIGATION_MAP, USER_GUIDE, user-manual(+html/assets), API, REPORTS_METRICS, UI_COMPAT, SYNC_BEHAVIOR, CHANGELOG
- build tag: 38a142a2; no deploy

## 2026-07-31 - efficiency-gpm + reports-nav-each start

- Plans: efficiency-gpm-second-y-axis-colors-20260731-plan.md; reports-nav-each-monthly-experiments-20260731-plan.md
- Free toggles; gpm own axis; colors; Each monthly/category/trips; ☰+← reports; experiment First 10
- No deploy

## 2026-07-31 - efficiency-gpm + reports-nav-each complete

- Plans: efficiency-gpm-second-y-axis-colors-20260731-plan.md; reports-nav-each-monthly-experiments-20260731-plan.md
- Efficiency: free toggles (no ensureAtLeastOne); mpg/gpm/money series maps; gpm own axis; money-only Start-axis; A4 second money chart; family colors; all-off empty
- Charts: month X labels; LabMultiSeriesIndexChart; remount key without return@key (D8)
- Nav: ☰+← on reports_lab/* + expenselist; hub ☰ only
- Each: monthly multi-vehicle totals; category multi-series; trip miles per vehicle
- Experiments: drop Amazon/Golden/Failing/Problem; First 10 on Alignment+Pump
- build tag: c4f461c6; no deploy


## 2026-07-31 - efficiency-chart-line-and-axis-colors start

- Plan: efficiency-chart-line-and-axis-colors-20260731-plan.md
- C2 line strokes + C3 Y tick/axis colors via Vico 3.2.3 LineProvider / VerticalAxis style
- No deploy


## 2026-07-31 - efficiency-chart-line-and-axis-colors complete

- Plan: efficiency-chart-line-and-axis-colors-20260731-plan.md
- LineCartesianLayer LineProvider.series with family Fill colors; dual money fuel/incl; Each shade+dash
- VerticalAxis Start/End: rememberAxisLine/Label/TickComponent family color (Y1 real axis styling)
- Caption Text colors kept (Y2); bottom date axis neutral
- Non-efficiency charts unchanged (null familyDefault → Vico default)
- build tag: a590ab31; no deploy


## 2026-07-31 - unified-time-report-fill-edit-pagehelp start

- Plan: unified-time-report-fill-edit-pagehelp-20260731-plan.md
- H PageHelp token; F edit fill/fills-only/trips; R unified time report+bins+PDF; D docs
- No deploy


## 2026-07-31 - unified-time-report-fill-edit-pagehelp complete

- Plan: unified-time-report-fill-edit-pagehelp-20260731-plan.md
- H: PageHelp set returns owner; clearIf(id) only if current (Info stays)
- F: multi-dest archive identity; Edit fill currency-before-cost, multi-col, location expand, hide blank trip type; Fuel History fills-only; Trip miles trip list + tap edit
- R: reports_lab/time Fuel over time (metrics, smooth bins, axis policy, PDF combined+per-series); hub merge; legacy routes redirect
- D: NAVIGATION_MAP, REPORTS_METRICS, USER_GUIDE, project-facts
- No deploy


## 2026-07-31 - time-based-reports-ux-pdf-manual start

- Plan: time-based-reports-ux-pdf-manual-20260731-plan.md
- Rename Time based reports; UnitFormat labels; single chart; trip bins; PDF graphs; multi-dest fetch; docs/screenshots
- No deploy


## 2026-07-31 - time-based-reports-ux-pdf-manual complete

- Plan: time-based-reports-ux-pdf-manual-20260731-plan.md
- Rename Time based reports; UnitFormat volumePerDistance + unitPrice labels
- Single chart: economy left, money+trip right; color chips + multi-col metrics
- Trip miles/% from odo Δ bins (Personal included)
- PDF combined+per-series chart bitmaps + tables
- Multi-dest downloadFuel/Expense/VehicleIfNeeded
- Docs Help USER_GUIDE REPORTS_METRICS user-manual+html assets; screenshots deferred to post-deploy 5556 for hub/time/edit
- No deploy


## 2026-07-31 - time-report-fixed-axis-sides start

- Plan: time-report-fixed-axis-sides-20260731-plan.md
- Economy always Start; money/trip always End; no gpm-on-right dual mode
- No deploy


## 2026-07-31 - time-report-fixed-axis-sides complete

- Plan: time-report-fixed-axis-sides-20260731-plan.md
- Start = mpg∪gpm always; End = money∪trip only; dropped dual-axis gpm-on-right branch
- build via build_app; no deploy


## 2026-07-31 - time-report-multi-axis-trip-pct-by-type start

- Plan: time-report-multi-axis-trip-pct-by-type-20260731-plan.md (historical path; named for execution)
- Multi-family Y scales; trip % per type; custom Canvas chart; PDF parity
- No deploy


## 2026-07-31 - time-report-multi-axis-trip-pct-by-type complete

- Plan: time-report-multi-axis-trip-pct-by-type-20260731-plan.md
- tripMetricsFromOdo: milesTotal + pctByType; multi-family Canvas LabMultiFamilyTimeSeriesChart
- Left mpg + G/mi axes; right $ + trip mi + trip %; PDF renderMultiFamilyChartBitmap
- No deploy


## 2026-07-31 - onboarding-splash-tutorials start

- Plan: onboarding-splash-tutorials-20260731-plan.md
- First-run splash; tutorial_add_vehicle + tutorial_setup_sync; Help links
- No deploy


## 2026-07-31 - onboarding-splash-tutorials complete

- Plan: onboarding-splash-tutorials-20260731-plan.md
- Splash when forUserPicker empty; tutorials add_vehicle + setup_sync; Help/Settings CTAs
- Assets app/src/main/assets/tutorials/; manual composites from sandbox masked set + render
- No deploy


## 2026-07-31 - user-manual-screenshot-integration start

- Plan: user-manual-screenshot-integration-20260731-plan.md
- Promote 5556 masked shots; update md; render HTML/assets
- No deploy


## 2026-07-31 - user-manual-screenshot-integration complete

- Plan: user-manual-screenshot-integration-20260731-plan.md
- Promoted 5556 masked shots + re-captured spreadsheet list, photo backup list, settings scroll, syncing hub
- user-manual.md: Start trip, Time based reports, fill/fuel edit, trip miles, Syncing hub; composites noted
- render-user-manual.sh → html + assets; Help/About/USER_GUIDE verified
- No app code change required beyond prior onboarding


## 2026-08-01 - residual-finish-recent-plans start

- Plan: residual-finish-recent-plans-20260801-plan.md
- J join-existing sync tutorial; H Help multi-axis; D optional form shots
- No deploy


## 2026-08-01 - residual-finish-recent-plans complete

- Plan: residual-finish-recent-plans-20260801-plan.md
- J: SETUP_SYNC rewritten join-existing; splash Connect existing setup; Help/Settings labels
- H: Help Time based reports = independent Y scales + trip % by type; USER_GUIDE join-existing one-liner
- D: re-captured 09/10/12/13 (+10 email masked); r2 landmarks + r4 QF odo-result kept pre-refresh (chrome still accurate)
- render-user-manual.sh; build_app after Kotlin
- No deploy


## 2026-08-01 - Local PR prepared: ui-followups

- History cleanup: soft-reset onto master → **9 logical commits**; `backup-ui-followups` @ `72d52dac`; **TREE_MATCHES_BACKUP** (`HEAD^{tree}` == backup tree `1a408d5a`).
- Cleaned HEAD: `3037b9c5`.
- PR doc: `dev-ai-interaction/PRs/PR-ui-followups.md` (pre-submit review + plans + diffstat).
- Archived finished plans → `historical-plans/`: residual-finish-recent, onboarding-splash-tutorials, user-manual-screenshot-integration, sync-tutorial-join-existing-cluster, reports-efficiency-mpg-dpm-each-vehicle.
- Ready for Master (`run-grok-master`) independent review + merge. No deploy/merge by coder.

## 2026-08-01 - Master merge: ui-followups

- audit_merge SUCCESS (FF); merge-branch-into-master.sh FF index path; staged 95 .kt + assets/docs
- ve-englog third version (hash-object -w fix for missing blob before commit)
- TODO: closed Location Lookup Worker + Troubleshoot lat/long; deferred multi-candidate, post-save confirm, multi-select Sum/Avg, trip packaging; Advanced reports stays closed
- project-facts: branch orientation (Room v19, Lab Reports, onboarding, sync rate-limit)
- build_app: first attempt processDebugJavaRes mode 770 flake (cleaned intermediates); retry SUCCESS
- builds tag → 6889b142 (merge commit). No works tag.


## 2026-07-26 - reports-field-conditional-economy-chains-20260726

- Execution start for approved plan: reports-field-conditional-economy-chains-20260726-plan.md
- Branch: batch_load; no prior batch_load/builds tag (first successful ./build_app will create it)
- Scope: ReportsScreen.kt field-conditional MPG/$/mi chains + REPORTS_METRICS.md; phase 5 emulator probe after user deploy

## 2026-07-26 - reports-field-conditional-economy-chains-20260726 phases 1-4 done

- Phases 1-4 implemented and built on batch_load
- ReportsScreen.kt: full fill = odo+cost+vol+!partial; MPG breakers; $/mi segment sum
- docs/reference/REPORTS_METRICS.md rewritten to match
- Tag: batch_load/builds @ 3d29b986 (batch_load-start-4-g3d29b986)
- Phase 5 blocked on user APK deploy to emulator-5554 (device currently has versionName=instruction-start-8-g7d8aa877, not batch_load build)

## 2026-07-26 - reports-field-conditional-economy-chains phase 5 probe PASS

- Backup: test-data-backups/emulator-5554-20260726-095208
- App on emulator-5554: batch_load-start-5-ge76bbc73
- Probe cases odo_only / blank / cost_only / volume_only on Honda between full fills 2→3
- UI Honda: baseline last 15.8 avg 17.0 $/mi 0.259; odo_only same; blank avg 15.8 $/mi 0.260; cost_only avg 15.8 $/mi 0.276; volume_only avg 14.5 $/mi 0.260
- Evidence: dev-ai-interaction/research/reports-chain-probe-20260726/
- DB restored from pre-probe backup; no leftover PROBE rows

## 2026-07-26 - reports-field-conditional-economy-chains PR prepared

- Pre-submit review PASS vs plan; device probe PASS; DB restored
- Local PR: dev-ai-interaction/PRs/PR-batch_load.md
- Tag: batch_load/builds @ 1de13e02; backup-batch_load set
- Ready for Master independent review + merge

## 2026-07-26 - batch-load-ingest-merge-questions-20260726

- Execution start: Stage A (batch ingest) per approved plan batch-load-ingest-merge-questions-20260726-plan.md
- Baseline tag: batch_load/builds @ a02dc9bc; Stages B–C deferred to later turn if A fills the turn

## 2026-07-26 - batch-load Stage A complete (ingest)

- Plan: batch-load-ingest-merge-questions-20260726-plan.md Stage A only
- Added: PhotoExifMeta, FuelPhotoJson, BatchImportPending, BatchFuelImportCoordinator, hardDeleteFuelEntry
- Set I hybrid in PumpCostVolUtils.runSetICostVolExtraction + OcrHarness.runPumpCostVolPipelineSetI
- Import Old Pictures UI: Run batch import / Cancel / Review questions (list-only)
- Hilt ViewModel in ui.batch (not ui.import — Java keyword breaks KSP)
- Builds tag: batch_load/builds @ 6efd2743
- Stages B (merge) and C (apply answers) remain on same plan path — not in this turn

## 2026-07-26 - batch-dash-match-experiment-set-j-20260726

- Execution start: unify Set J (experiment + batch + Quick Fill) per plan
- Baseline: batch_load/builds @ 99d1043c

## 2026-07-26 - batch-setj approach: copy experiment logic into production

- User direction: do NOT extract shared dual-call API; copy experiment Set J into production
- Experiment remains free to tinker (future compile-out); production batch/Quick Fill own a frozen copy
- Continue plan batch-dash-match-experiment-set-j-20260726-plan.md with this approach

## 2026-07-26 - batch-dash-match-experiment-set-j production copy done

- OcrHarness.runSetJPipeline rewritten as independent copy of experiment Set J (Raw+Bin-Trials+connectSegmentsH+pickBestOdometer)
- ExperimentAlignmentScreen NOT shared — free to tinker
- Batch parseSetJOdometer: 4-7 pure digits only (no concat soup)
- Tag: batch_load/builds @ 38186950
- Device parity CSV pending user deploy + re-import after purging old batch dash rows
- Notes: dev-ai-interaction/research/batch-setj-parity-20260726/NOTES.md

## 2026-07-26 - production-setj-ref-geometry-parity-20260726

- Execution start: fix production Set J ref geometry (probe/4080, ICRS crop, Raw nestFilter)
- Baseline: batch_load/builds @ d51f30d9

## 2026-07-26 - production-setj-ref-geometry-parity done

- Probed ref dims for landmarks+align; fallback 4080×3072
- ICRS Float createCrop for odo window; Raw no nestFilter
- Experiment screen untouched (independent copy)
- Tag: batch_load/builds @ de113410
- Device compare pending user deploy; notes in research/setj-geometry-parity-20260726/

## 2026-07-26 - batch import limited button (first 20+20)

- Import UI: OutlinedButton "First 20 dash + first 20 pump" (name-sorted take)
- BatchFuelImportCoordinator.runIngest(maxDash, maxPump); LIMITED_IMPORT_COUNT=20

## 2026-07-26 - batch pump ingest without vehicle

- processPump always runs Set I; inserts with UNASSIGNED_VEHICLE_ID=0
- No ASSIGN_VEHICLE gate; merge later assigns vehicle via time/location pairing
- Unreadable pumps still pending only

## 2026-07-26 - batch-setj-reverify-and-pending-answers-20260726

- Execution start; device versionName=batch_load-start-21-gf43bb991 (geometry + unassigned pump)
- Part A first-20 re-verify then Part B clickable pending answers

## 2026-07-26 - batch-setj-reverify + pending answers

- Part A first-20 re-verify on f43bb991: 17 dash inserts + 20 pump vehicleId=0
- first20 score: see research/batch-setj-parity-20260726/first20-after-geometry.txt (gate FAIL — residual leading-digit/blank DIFFs)
- Part B: clickable pending UI + forcedVehicleId dash reprocess + skip/retry pump (build 06707a0a)
- Experiment untouched; deploy needed for Part B on device

## 2026-07-27 - batch-mirror-experiment-pipelines-setj-seti

- Execution start: batch must run experiment Set J / Set I pipelines, not OcrHarness reinvented front-end
- Baseline tag: batch_load/builds @ c112e80e

## 2026-07-27 - batch-mirror-experiment-pipelines-setj-seti done (code)

- AlignmentSetJRunner: experiment Set A ID lock + Set J runPaddleValleyIterative for batch dash
- PumpSetIRunner: experiment-equivalent Set I for batch pump
- BatchFuelImportCoordinator no longer uses runAutoFillPipeline / runPumpCostVolPipelineSetI
- Tag: batch_load/builds @ 7653d156
- Device parity first-20 still needs deploy of this APK + re-run (prior c112e80e re-verify failed)

## 2026-07-27 - Batch Stage A full-run parity PASS; Stage B plan ready

- Full batch finished (~02:35 PDT); app idle after last pump `PXL_20260411_201506380.jpg`.
- Selective purge: deleted 74 old `batch_import%` rows (`updatedAt < 1785142500000`); kept 12 non-batch + 290 this-run (ids 219–508); pending cleaned to 6 this-run items.
- Parity vs experiment: dash Set J 142 OK / 0 DIFF (4 no-vehicle pending); pump Set I 148 OK / 0 DIFF (2 unreadable both sides). Report: `dev-ai-interaction/research/batch-run-parity-20260727/`.
- Next coder plan: `dev-ai-interaction/plans/batch-stage-b-merge-engine-coder-20260727-plan.md` (merge engine + Run merge). Durable B+C: `batch-stage-b-merge-and-stage-c-questions-20260727-plan.md`.

## 2026-07-27 - batch-stage-b-merge-engine

- Execution start: Stage B merge engine per batch-stage-b-merge-engine-coder-20260727-plan.md
- Stage A parity PASS; baseline tag 83e36ca0

## 2026-07-27 - batch-stage-b-merge-engine B1–B4 code

- B1: FuelPhotoJson.unionPhotos / addPumpPhoto / addDashPhoto
- B2: FuelRowMergeEngine.planMerge (vehicle clusters ±45m, vehicleId=0 pump pair, sequence vs re-shot, CONFLICT_ODO + photo paths, hard-delete list)
- B3: BatchFuelImportCoordinator.applyMerge + Import **Run merge** button
- B4: Merge after import checkbox default **off**
- Fixtures notes: dev-ai-interaction/research/batch-stage-b-merge-fixtures-20260727.md
- Tag: batch_load/builds @ 13d7c06b
- Device Run merge still to verify after deploy

## 2026-07-27 - batch-stage-b-merge-engine device Run merge

- Deploy: adb install -r (./deploy re-exec loop as ai-coder; used direct install)
- Pre: 302 rows, v0=148, fulls=6, odo_only=140, pump_only=154
- applyMerge: updated=133 deleted=129 pending+=2 → tag code 13d7c06b
- Post: 173 rows, v0=14, fulls=130, odo_only=16, pump_only=25
- Non-batch: 9 kept (absorbed re-shot ids 6–8 same $18.40); full non-batch fills preserved
- CONFLICT_ODO pending: 9594 vs 9698; 198699 vs 98699 (photo paths attached)
- Summary: dev-ai-interaction/research/batch-stage-b-merge-20260727/merge-run-summary.txt
- Stage C deferred (image-first questions, assign→re-merge UI)

## 2026-07-27 - batch-stage-c-image-questions

- Execution start: Stage C image-first pending questions per batch-stage-c-image-questions-coder-20260727-plan.md
- Stage B done on device; tip batch_load/builds @ 0c93922c

## 2026-07-27 - batch-stage-c-image-questions done

- C1: pendingPhotoUris + image thumbs/zoom; DNG placeholder; filename secondary only
- C2: CONFLICT_ODO Keep odo / Keep both; pure wrong-odo hard-delete; pump data odo zeroed
- C3: successful answers auto applyMerge; skip does not re-merge
- Device: Keep odo 9594 → pending 8→7 + remerge updated=16 deleted=1; Skip → pending 6
- Tag: batch_load/builds @ f360ce24
- Notes: dev-ai-interaction/research/batch-stage-c-questions-20260727/smoke-notes.txt
- Residual: 1 CONFLICT_ODO, 3 no-vehicle dash, 2 unreadable pump; AMBIGUOUS_MULTI_PUMP unused

## 2026-07-27 - batch-stage-c-photo-ux-manual-entry

- Execution start: photo UX, manual entry, economyIgnored sync, tank heuristic, unknown vehicle, outliers
- Parent tip: batch_load/builds @ 27d92a09

## 2026-07-27 - batch-stage-c-photo-ux-manual-entry done

- Photo stem-dedupe; DNG OpenCV preview; fullscreen +/− + sticky actions
- Manual pump/dash/conflict odo; economyIgnored Room v14 + sync column Economy Ignored
- Reports: fills N(Mp); Unknown label; economy excludes ignored; avg filters 3× outliers
- Merge: tank maxFill+5 auto-assign; post-merge enqueue unknown + MPG_OUTLIER
- Device: merge pending+=52 (9 unknown, 42 outliers); tag batch_load/builds @ aa9b1e53
- Notes: research/batch-stage-c-photo-ux-20260727/smoke-notes.txt

## 2026-07-27 - batch-merge-window-odo-sanity

- Execution start: 15m merge window, tight dash/pump pairs, odo reverse/gap sanitizer, Flag partial, per-vehicle unknown context
- Parent tip: batch_load/builds @ 3f915150

## 2026-07-27 - batch-merge-window-odo-sanity done

- MERGE_WINDOW_MS=15m; splitTightDashPumpPairs for multi dash+pump
- FuelOdoSanitizer: gap (maxVol×mpg×3, no fallback) then reverse; bias demote later
- ODO_SUSPECT pending; FlagPartial action; per-vehicle nearest for unknown
- Device: sanitize=31, ODO_SUSPECT=31 (incl. 20119 gap); CONFLICT_ODO=1
- Tag: batch_load/builds @ 1fbe913e
- Notes: research/batch-merge-window-odo-sanity-20260727/smoke-notes.txt

## 2026-07-27 - batch-pending-gap-dedupe-timefmt

- Execution start: Mark as gap, pending rebuild/dedupe, formatTimeDelta, Clear & re-scan
- Parent tip: batch_load/builds @ 25df6455

## 2026-07-27 - batch-pending-gap-dedupe-timefmt done (code)

- Mark as gap (blank odo/cost/vol); pending full rebuild on merge; (kind,fuelEntryId) dedupe
- formatTimeDelta for neighbor/unknown deltas; Clear questions & re-scan button
- Sanitizer skips already-partial / blank; clear all pending kinds per answered fuelEntryId
- Tag: batch_load/builds @ e743f48a
- Verify: ./build_app only — no deploy/device smoke (coder not authorized to deploy for test)
- Notes: research/batch-pending-gap-dedupe-timefmt-20260727/smoke-notes.txt

## 2026-07-27 - batch-mpg-outlier-ui-clarity

- Execution start: MPG outlier UI clarity per batch-mpg-outlier-ui-clarity-20260727-plan.md
- Tip: batch_load/builds @ 47ddcf81
- Device verify only after human deploy (coder will not adb install/deploy)

## 2026-07-27 - batch-mpg-outlier-ui-clarity done (code)

- MPG_OUTLIER: end-only primary photos; leg start/end text; focus toggle prior/end
- Edit/flag/ignore/gap target focusEntryId; nearby badges LEG START / THIS FILL
- Tag: batch_load/builds @ 795c8e3b
- No device test (await human deploy)
- Notes: research/batch-mpg-outlier-ui-clarity-20260727/smoke-notes.txt

## 2026-07-27 - batch-mpg-outlier-ui-clarity (layout v2)

- Re-execute updated plan: Last/This photo blocks + 4-line context
- Prior UI (single strip + toggle) superseded by locked two-block layout

## 2026-07-27 - batch-mpg-outlier-ui-clarity layout v2 done (code)

- Two-block Last/This photos above each button; 4-line text context (before/last/this/after)
- Focus default This fill; edit/flag/ignore/gap target focus id
- toPending: thisPhotoPaths + lastPhotoPaths
- Tag: batch_load/builds @ c272c521
- No device test until human deploy
- Notes: research/batch-mpg-outlier-ui-clarity-20260727/smoke-notes.txt

## 2026-07-27 - batch-partial-flag-semantics + sanitizer-correctness

- Execution start: two plans
  - batch-partial-flag-semantics-20260727-plan.md
  - batch-partial-and-sanitizer-correctness-20260727-plan.md
- No device deploy (human deploys before test)

## 2026-07-27 - partial-flag + sanitizer-correctness done (code)

- isPartialFill explicit-only; inserts/merge/sanitizer never auto-true for incomplete
- FuelOdoSanitizer detect-only (reverse, digit_jump, gap with clean mpg); no demote updates
- Checkbox SetPartialFill; clearAutoPartialFlags repair button
- Reports display band 5–80 + formatMpg n/a outside 1–100
- Quick Fill: isPartialFill=false on save
- Tag: batch_load/builds @ ec44b782
- No device test until human deploy
- Notes: research/batch-partial-sanitizer-correctness-20260727/smoke-notes.txt

## 2026-07-27 - batch-odo-suspect-ui-per-fill

- Execution start: ODO_SUSPECT per-fill UI layout
- No device deploy until human deploys

## 2026-07-27 - batch-odo-suspect-ui-per-fill done (code)

- ODO_SUSPECT: ordered prev/cur/next dash-only photos + odo fields under each
- SaveOdoPeers multi-odo write; payload prevDashPaths/curDashPaths/nextDashPaths
- Tag: batch_load/builds @ 0da926d1
- No device test until human deploy; re-scan after deploy
- Notes: research/batch-odo-suspect-ui-per-fill-20260727/smoke-notes.txt

## 2026-07-27 - batch-sync-cross-device-partials-and-titlebar

- Execution start: soft-delete sync, origin-device partials, title bar logo/version
- No device deploy until human deploys

## 2026-07-27 - batch-sync-cross-device-partials-and-titlebar (in progress)

- Completing MainActivity yellow bar + remaining CTA; no device deploy


## 2026-07-27 - batch-sync-cross-device-partials-and-titlebar done (code)

- Merge: all live partials (sync/QF/batch); soft-delete published absorbs; lat/lon later-ts
- hasUnmatchedPartials + post-sync toast CTA (no auto-merge)
- Yellow title-bar ?N → import?review=1 expand Review questions; Help bullets
- Tag: batch_load/builds @ 5e7de408
- No device test until human deploy
- Notes: research/batch-sync-cross-device-partials-and-titlebar-20260727/smoke-notes.txt


## 2026-07-27 - batch-mpg-gap-button-and-window-fills done (code)

- MPG card: vertical Save / Missing data between last & this / Ignore (no clip)
- markAsGap MPG: insert mid-leg blank; anchors preserved; dismiss if breaker exists
- FuelEconomyChains + breaker-aware detectOutliers; windowSummary inventory
- Post-sync applyMerge after fuel LWW; SYNC_BEHAVIOR + REPORTS_METRICS
- Tag: batch_load/builds @ a50da18f
- No device test until human deploy
- Notes: research/batch-mpg-gap-button-and-window-fills-20260727/smoke-notes.txt


## 2026-07-27 - batch-no-durable-photo-copy done (code)

- processDash/processPump: source paths only; copyToDurable removed
- migrateDurablePhotoRefsToSource rewrite+delete unreferenced mirrors
- PendingPhotoUris prefer source dirs; Help note
- Tag: batch_load/builds @ a18099d1
- No device test until human deploy
- Notes: research/batch-no-durable-photo-copy-20260727/smoke-notes.txt


## 2026-07-28 - batch-stage-c-phased-questions done (code)

- StageCPhase 1–6 store + UI filter; skip/reset; clear-rescan → phase 1
- ODO chain merge + simple length-guess mode; BAD_PUMP_RATIO phase 3
- Gap from phase 3/5; post-sync remoteWins → phase 1 + rebuild
- Tag: batch_load/builds @ 8ad579ef
- No device test until human deploy
- Notes: research/batch-stage-c-phased-questions-20260728/smoke-notes.txt


## 2026-07-28 - batch-stage-c-ux-skip-photos-phase-scope done (code)

- Phase-scoped pending rebuild; Next phase regenerates only next kinds
- Skip ledger same-phase; answer journal jsonl + export (no replay)
- Unassigned vehicle id=0 fixed syncId + Fuel - Unassigned tab; picker exclusions
- Photo role dash/pump; Close z-order; CONFLICT keep-both completes
- Tag: batch_load/builds @ dab2e144
- No device test until human deploy
- Notes: research/batch-stage-c-ux-skip-photos-phase-scope-20260728/smoke-notes.txt


## 2026-07-28 - PR-batch_load prepared for Master review

- History cleaned: 57 commits → 5 logical; backup-batch_load @ ff33f355; cleaned HEAD @ 11a2c1ca
- PR: dev-ai-interaction/PRs/PR-batch_load.md (pre-submit review + plans)
- Not rebased onto master (simplify_experiments); Master merge expects eng-log special handling + possible ExperimentAlignmentScreen conflict
- Tree matches pre-cleanup tip; build_app SUCCESS after cleanup


## 2026-07-28 - Master merge PR-batch_load

- Independent review PASS; non-FF merge with simplify_experiments overlap
- merge-branch-into-master failed on +a eng-log (double/triple append side effect); completed via merge-tree + manual resolve
- ExperimentAlignmentScreen: keep Set-J-only simplify + internal helpers for AlignmentSetJRunner (dropped batch dead createScaledBase64/drawCropBoxes conflict side)
- project-facts: batch orientation + Room v14 from branch; TODO unchanged
- Commit 1bfab017; build SUCCESS after java_res permission retry; builds tag updated
- Worktree ENGINEERING_LOG.md may still have triple-appended tail (+a); committed blob is single third-version + this note — fix with sudo chattr -a when available
- No works tag. Cleanup: ./remove_worktree.sh batch_load when ready

## 2026-07-28 - Merge master into email-connection (pre Wave 0)

- Special-file protocol: index-first FF (chattr +a blocked plain git merge)
- App/docs = master tip 43bd3839 (batch_load, Unassigned, economyIgnored, FUEL_HEADERS)
- eng-log: ve-englog append of master-only tail
- TODO/project-facts: master base + todo-append preferred fuel grade
- Merge commit: e3693de3 (parents 91e94f53 + 43bd3839)
- Next: Wave 0 of email-loyalty-receipt plan after build verify


## 2026-07-28 - Execute email-loyalty-receipt plan (approved)

- Plan: dev-ai-interaction/plans/email-loyalty-receipt-fuel-fills-20260728-plan.md
- Branch: email-connection @ 0bc7af98
- Scope: full plan approval; start Wave 0 (Track A sandbox), then Wave 1
- First action: eng-log (this entry); no ritual TODO


## 2026-07-28 - email-loyalty Wave 0+1 implementation

- Wave 0 (sandbox): dev-ai-interaction/email-receipt/ parser+encoder+Apps Script+tests (43 pass)
- Wave 1 (app): data/email/* ShellReceiptParser, FuelReceiptIngest, Gmail client, WorkManager, Settings UI
- Contract: vehicleId=0, odo=0, partial/economyIgnored false, stable Sync ID, Fuel - Unassigned sheet path


## 2026-07-28 - email-loyalty plan ready to test

- Wave 0: sandbox email-receipt (node 43 pass); Wave 1: app data/email + Settings + builds
- Tag: email-connection/builds @ 7f610576
- Human test: Apps Script dry-run and/or Settings Email receipts poll with labeled Shell mail


## 2026-07-28 - Execute email-loyalty-pre-manual-test plan

- Plan: dev-ai-interaction/plans/email-loyalty-pre-manual-test-20260728-plan.md
- Branch: email-connection @ 930f7b76
- Scope: offline fixture assets + ingest, Settings last-run refresh, MANUAL_TEST.md


## 2026-07-28 - pre-manual-test phases 1-4 done

- Assets email-receipt fixtures; EmailReceiptFixtureIngest; Settings offline + last-run refresh
- MANUAL_TEST.md offline-first; project-facts pointer
- Building for human test handoff


## 2026-07-28 - track email-receipt assets (gitignore exception)

- .gitignore allow app/src/main/assets/email-receipt/** (was ignored by assets/*)
- Force-add shell-receipt1/2.html so offline ingest ships in APK

## 2026-08-01 - Execute email-receipt Sam's Club + Shell autodetect

- Plan: dev-ai-interaction/plans/email-receipt-sams-club-plus-shell-autodetect-20260801-plan.md
- Branch: email-connection @ c73f1a68
- Scope: Sam's parser, label-only Gmail list, ReceiptParsers facade, offline fixtures


## 2026-08-01 - Sam's Club + Shell autodetect (code)

- SamsClubReceiptParser + ReceiptParsers facade; Gmail label-only list
- Offline fixtures: 2 Shell + 1 Sam's; node tests 60 pass
- Apps Script ReceiptParsers/SamsParser; Settings copy multi-vendor


## 2026-08-01 - Execute generic IMAP folder fetch plan

- Plan: dev-ai-interaction/plans/email-receipt-generic-imap-folder-fetch-20260801-0231-plan.md
- Branch: email-connection @ 7e5368c7
- First action: eng-log; Status APPROVED on plan


## 2026-08-01 - IMAP folder fetch phases done

- ImapReceiptClient (IMAPS 993), prefs + encrypted password, worker gmail|imap|both
- Settings IMAP fields; ReceiptParsers for parse; Sync ID email|imap|{id}


## 2026-08-01 - IMAP build gate

## 2026-08-02 - Execute remotetable M1 + extractmail M1 plans

- Plans: remotetable-m1-lib-conformance-ve-pin-20260802-0348 + extractmail-m1-extract-stdin-external-ve-pin-20260802-0348
- Library hosts: /home/dlang/git/{remotetable,extractmail}
- Note: ai-coder can write product dirs (ai-sandbox group) but not .git (dlang:dlang) — commits need human unless perms fixed
- VE third_party pins still TBD until lib commits land


## 2026-08-02 - remotetable + extractmail M1 progress (partial)

- Library hosts: product trees updated (ai-sandbox dirs); .git not writable by ai-coder
- remotetable: Python mock conformance PASS; backend stubs; Kotlin/Go sketches
- extractmail: YAML types; stdin CLI; goldens PASS (Shell×2 + Sam's)
- VE third_party: SOURCE/build recipes; lock still git_sha TBD — pin deferred
- Status: BLOCKED on library git commit permissions for full CODE LANDED pin


## 2026-08-02 - Execute remotetable M1 then extractmail M1 (revised fetch-deps)

- Plans: remotetable-m1-…0348 + extractmail-m1-…0348 (revised)
- Work under third_party/*/src after fetch-deps rw


## 2026-08-02 - M1 libs blocked on git object dir perms

- remotetable/src: product tree + AAR built (9457 bytes) uncommitted
- extractmail/src: goldens PASS uncommitted  
- Blocker: /home/dlang/git/{remotetable,extractmail}/.git/objects/* many dirs dlang:dlang not ai-code
- Fix: chgrp -R ai-code .git/objects; chmod -R g+rwX .git/objects (both hosts)
- Then: git commit in third_party/*/src; fetch-deps upgrade; pin artifact on VE


## 2026-08-02 - remotetable + extractmail M1 VE pin and thin adapters

- Pin remotetable @ d718ff3 + artifact/remotetable.aar; extractmail @ b18461f + artifact/extractmail.aar
- Gradle files() deps on both AARs; FirstPartyLibsProbe compile-link surface
- SpreadsheetProvider.EXCEL → EXCEL_GRAPH("excel-graph"); legacy wire "excel" / route "new:excel"
- extractmail pin build → scripts/build-aar.sh; library commits android scaffold + build-aar
- Full tabular stack cutover to remotetable AAR deferred (in-tree backends remain for Sheets/Excel/EtherCalc)

## 2026-08-02 - first-party libs M2: tests CLI VE cutover

- remotetable @ 4fd9013: CLI, harness, live AAR backends; pin AAR sha fdeae58a…
- extractmail @ 5547073: YAML type registry, scripts/extractmail, external 0/1/2; AAR v2
- VE cutover: GoogleSheets/ExcelGraph/EtherCalc TabularShareBackend → remotetable AAR
- Auth remains in-app (GoogleAuthUtil / MSAL); tokens passed into Backends.*
- Phase 6: offline email still in-app parsers; probe uses extractmail VERSION/types (Gmail/IMAP workers unchanged)

## 2026-08-02 - M2.5 start: offline email + CLI harden + dead HTTP cleanup

- Plan: first-party-libs-m2.5-offline-email-harden-cleanup-20260802-1735-plan.md
- Baseline remotetable 70d57bb / extractmail 8155a5d; offline harness/goldens PASS

## 2026-08-02 - M2.5 offline email goldens + CLI harden + dead HTTP cleanup

- remotetable @ cb4c2e4: CLI flags before/after subcommand; live smoke docs
- extractmail @ 9c22953: YAML module/export dispatch; export_offline_goldens.sh
- VE offline Settings: expected-*.json assets (extractmail goldens) → FuelReceiptIngest; Extractmail.TYPE_*
- Deleted GraphExcelClient; EtherCalcClient slim to config/room only
- Gmail/IMAP workers unchanged (still ReceiptParsers HTML)

## 2026-08-02 - M3 remotetable residual backends + CLI expansion

- Start goal autopilot: port row-DB backends into remotetable; expand Go/Python CLIs; EtherCalc docker smoke.
- Google live CLI deferred (needs human token). Local git only.


## 2026-08-02 - M3 remotetable row-db cutover landed

- remotetable lib @ 472eab9: row-db backends (Kotlin+Python), Go CLI mock/ethercalc, AAR rebuilt (JDK 17).
- VE pin + thin RemoteTableRowDbTabularBackend for Baserow/NocoDB/PocketBase/Supabase/Airtable; removed in-app clients.
- ./build_app green; tag email-connection/builds → email-connection-start-76-g79072b28.
- EtherCalc one-shot: ~/git/ethercalc/start.sh (docker run). Google live CLI still needs human token.
- Still open: Firebase, Zoho, OnlyOffice/Collabora, then extractmail full stack.


## 2026-08-02 - M3 continue Firebase/Zoho + extractmail

- Resume: port Firebase + Zoho into remotetable; OnlyOffice/Collabora stubs; then extractmail stack.


## 2026-08-02 - M3 progress batch (remotetable+extractmail+rclone host)

- remotetable @ 5eca9dd: Firebase+Zoho backends; OnlyOffice/Collabora DeferredBackend; VE cutover; dead RowDb stack removed; build green.
- extractmail @ 0806a42: xpath/css configs, EXTERNAL.md, IMAP fetch_mail.py, Go CLI; pin + AAR.
- rclone: ~/git/rclone email-connection + ve-build recipes; third_party lock + photo AAR artifact; Docker build script.
- opencv: ~/git/opencv 4.10.0 clone + ve-build notes; third_party bootstrap lock (TBD pin).
- Still open: full extractmail Apps Script parity in lib, VE live Gmail worker thin, OpenCV 16k rebuild, paddle third_party Docker wire-up, CsvZip, real OnlyOffice/Collabora.


## 2026-08-02 - Continue migration (email thin + opencv/paddle)

- Thin VE live HTML parse dispatch via extractmail AAR type detect contract.
- OpenCV 16k build script scaffolding; paddle host wire notes.


## 2026-08-02 - Email thin + OpenCV cmake progress

- extractmail AAR v3 @ 327ebf0: detectType + fuel contract; VE ReceiptParsers/Worker/FuelReceiptIngest thinned; build email-connection-start-83-gaa49da01.
- Apps Script README Shell+Sam's. OpenCV arm64 cmake configure OK (SDK cmake); full compile running; ant missing so java wrappers off — may still get native libs.
- paddle-ve-meta host pointer under ~/git/paddle-ve-meta.


## 2026-08-02 - OpenCV arm64 16k native libs staged

- Built core/imgproc/imgcodecs .so with Align 0x4000 (16KB) via NDK 28 + max-page-size=16384.
- Staged under third_party/opencv/artifact/arm64-v8a/. java4 wrapper blocked (android_sdk cmake path).
- VE email thin + extractmail v3 already green (start-83).


## 2026-08-03 - third_party pin docs + get-artifacts + OpenCV happy path

- Docs: third_party/README.md, docs/reference/THIRD_PARTY_PIN_BUILDS.md; layout handoff thinned.
- get-artifacts + fetch-deps build collect; example pin with glob/pick.
- OpenCV pin 71d3237: fetch-deps ro (src worktree) + patch skip android_sdk; fat libopencv_java4.so arm64 (~8.5MB) + x86_64 (~11.6MB), LOAD Align 0x4000; artifact/jni/.
- libpin name free on GitHub/PyPI/npm; stay in-tree for now. Copied tooling into extractmail/third_party.
- TODO already covers remotetable/extractmail ro-build hygiene later.


## 2026-08-03 - libpin.toml rename + docs cleanup

- Config file is libpin.toml (TOML, [[artifact]] singular); tools parse via tomllib.
- Removed docs/reference/FIRST_PARTY_LIBS.md and THIRD_PARTY_LAYOUT_FOR_AGENTS.md.
- Example + opencv get-artifacts verified with libpin.toml.


## 2026-08-03 - OpenCV pin wired + 5554 First 10 before/after match

- Built/used third_party/opencv pin artifacts (71d3237 / 4.10.0 fat libopencv_java4.so, 16KB, arm64+x86_64).
- Wired app: opencv-java-4.10.0.jar in app/libs; jniLibs arm64+x86_64 from artifact; dropped Maven OpenCV AAR natives; UPX skip for libopencv_java4.so.
- Commit 80d75c65; deployed to emulator-5554.
- Baseline + after First 10 alignment and pump short reports.
- Semantic JSON match (strip timings/thumbs/version/ts): alignment OK, pump OK.
- Scratch: dev-ai-interaction/scratch/opencv-5554-before-after/

## 2026-08-03 - rclone pin: Docker wrapper + photo AAR rebuild (16KB)

- fetch-deps: fix lock/pin unbound variable (ro/rw/status/build/resolve).
- rclone pin @ pure upstream 3f9d583 (not local ve-build commit); scripts/Dockerfile + build-photo-aar.sh (curated backends, CGO 16KB).
- Product path src/build/out/ (not upstream src/bin/ tools tree).
- Built artifact/librclone.aar ~82MB; ABIs arm/arm64/x86_64; Align 0x4000.
- App wires third_party/rclone/artifact/librclone.aar; removed app/libs/librclone.aar; UPX skip libgojni.so.
- Upstream already has gomobile; VE carries post-clone curation forever (not fork PR for backend list).

## 2026-08-03 - rclone libpin migration finished (host + docs)

- ~/git/rclone: pure upstream master @ 3f9d583; email-connection retargeted (dropped ve-build commit).
- Pin scripts/build remain SoT; sandbox rclone-build marked SUPERSEDED.md.
- project-facts + ENVIRONMENT_SETUP + 16k notes point at third_party/rclone artifact.
- fetch-deps ro rclone: clean @ pin, dirty=n.

## 2026-08-03 - remotetable + extractmail libpin RO builds + reproducible

- Both pins: fetch-deps ro + build OK; status dirty=n after build.
- Pin ./build unlocks only android/.gradle + module tree for Gradle; requires_writable_src=false.
- fetch-deps post-build leaves android scratch dirs writable for iterate.
- Double clean rebuild: remotetable + extractmail AAR bit-identical (reproducible=true in libpin).
- SOURCE.md rewritten; sha256 unchanged (751d70a1… / fcac2d7c…).

## 2026-08-03 - bubblewrap optional sandbox for libpin tooling

- third_party/libpin-bwrap: confine writes (lib/src/artifact modes); no-op if bwrap missing or LIBPIN_NO_BWRAP=1.
- fetch-deps/get-artifacts wire single-level sandbox; nested userns avoided.
- Docs: README.md, ENVIRONMENT_SETUP §2.4, third_party/README, THIRD_PARTY_PIN_BUILDS.

## 2026-08-03 - libpin Landlock write confinement

- libpin-landlock (Python): mutation-only Landlock; RW under mode paths + /tmp + essential /dev nodes.
- libpin-sandbox: bwrap outer + landlock inner; either optional.
- fetch-deps / get-artifacts use libpin-sandbox; --no-landlock flag.
- Docs: README, ENVIRONMENT_SETUP, third_party/README, THIRD_PARTY_PIN_BUILDS.

## 2026-08-03 - paddle libpin scaffold + pin build docs

- libpin.toml pin c6a9b9ad (pr-x86-android-mobile-gap = cleanup+x86 gap).
- Validated: pr-calib-safe-uint8-dequant is NOT git-stacked on x86-gap (develop tip +1).
- patches-int8/ vendored; Docker+run-android-slim build path; docs/reference/PADDLE_PIN_BUILDS.md.
- Models remain separate host opt pipeline (scripts under assets/paddle/scripts).

## 2026-08-03 - libpin sandboxed rebuild testing

- RO materialize: worktree→standalone + HTTPS origin; checkout_pin_clean so patches re-apply (opencv).
- Builds OK under sandbox: example, remotetable AAR, extractmail AAR, opencv fat jni (arm64+x86_64), rclone librclone.aar (sha e4f26a7d…).
- Paddle: arm64 slim + patches-int8 OK (uint8 calib stamps; jni ~188MB unstripped); x86_64 still fails (DENSE_TENSOR C++ vs LOD_TENSOR flatbuffers on pr-x86 pin).
- Tooling fixes: paddle RO script copy-before-chmod, third-party tarball when stub dirs, get-artifacts chmod dest, kernel check via grep -aF, patches-int8 LOD→DENSE in light_api.cc.
- Logs: /tmp/libpin-rebuild-round2.log /tmp/libpin-paddle-rebuild.log


## 2026-08-03 - paddle pin slim arm64+x86_64 build success

- Fixed x86: preserve pin flatbuffers pre-build (DENSE) over third-party tarball; patches-x86-openblas mklml LITE_WITH_MKL gate (no mkl.h on NDK).
- Fixed earlier: LOD→DENSE in patches-int8 light_api.cc; third-party stub re-fetch; RO script chmod.
- Built arm64-v8a + x86_64 slim JNI/light with int8/uint8 calib stamps; strip-unneeded → ~5.1MB arm64 jni, ~8.8MB x86 jni + light.
- Artifacts under third_party/paddle/artifact/; libpin.toml sha256 updated.
- Note: still larger than app tailored arm64 (~1.6MB) / thin x86 jni (~0.7MB); not auto-promoted to jniLibs.


## 2026-08-03 - 5554 First 10 after libpin rebuild (semantic JSON)

- build_app → email-connection-start-100-g80d85d52; installed emulator-5554.
- Ran Alignment + Pump First 10; reports in scratch/libpin-5554-first10-20260803-1524/.
- Semantic strip (version/ts/device/images/t_*/time/best_post): last-round OpenCV pre/post still MATCH; this-run vs last-after alignment+pump MATCH.


## 2026-08-03 - Paddle PR restack + local CI smoke

- Restacked on upstream/develop: cleanup ⊂ x86-gap ⊂ uint8 (local branches *-restack).
- All commits include test=develop (Paddle docs: required to trigger CI). Stalled #10712/#10713/#10714 only had CLA.
- Folded mklml LITE_WITH_MKL + OpenBLAS strip into x86 PR; dropped local PR markdown from stack.
- Local smokes on tip: Android x86_64 JNI OK; armv8 JNI with uint8_to_fp16/int8 stamps OK (docker ve-paddle-int8).
- Notes: ~/git/paddle/UPSTREAM_RESTACK_NOTES.md. Push requires user SSH (agent HTTPS no auth).


## 2026-08-03 - Paddle upstream PR messages + durable notes + pre-commit

- PR bodies (template-complete) under third_party/paddle/docs/upstream/PR_*.md; restack notes same dir (not only ~/git/paddle).
- SOURCE.md points at docs/upstream/.
- Travis-like pre-commit: non-format hooks pass; clang-format requires 3.8 (docker xenial); reformatted stack with 3.8; cpplint nits on im2col match develop style.
- Restack tips local: cleanup 81b6abea0, x86 44b78dc75, uint8 4563e88bd. No agent push.


## 2026-08-03 - Pins + paddle pin JNI + libpin linked-worktree cleanup

- extractmail pin → 0dc3f80 (master with third_party/remotetable)
- remotetable pin → 65366fc
- App jniLibs paddle: replace tailored/old with pin slim strip arm64+x86_64; drop old armv7 paddle until pin armv7 build lands
- libpin: prefer host linked worktree; sandbox allow only $GIT_HOME/<lib>/.git


## 2026-08-03 - Pin paddle strip-debug arm64/x86 + armv7 plan

- Rebuilt slim arm64+x86 unstripped then llvm-strip --strip-debug; wired app jniLibs (no armv7 paddle until separate plan).
- extractmail pin 0dc3f80; remotetable 65366fc; libpin linked worktree + host .git only.
- armv7 plan: dev-ai-interaction/plans/paddle-armv7-fp16-and-functional-calib-20260803-plan.md
- Human: deploy to emulator-5554 + Pixel; First 10 align/pump; compare to libpin-5554-first10 baseline.


## 2026-08-03 - App paddle hybrid for NDK28 link

- x86_64 jniLibs: pin slim strip-debug (fresh rebuild) for emulator-5554
- arm64 jniLibs: keep model-tailored SO (3ad8acd5) — slim arm64 from NDK r20 has LOCAL ABS symbols lld rejects
- Plan armv7+NDK bump: paddle-armv7-fp16-and-functional-calib-20260803-plan.md


## 2026-08-03 - Restore interim armv7 paddle SO for multi-ABI link

- Keep fat interim armv7 until pin armv7 plan; arm64 tailored; x86 pin slim for 5554.



## 2026-08-03 - Pin device First 10 (start-107) both devices PASS

- Pulled reports → `dev-ai-interaction/scratch/pin-device-test-20260803/` (emu5554 + pixel6pro); MD5 match device.
- APK: email-connection-start-107-g00a1c331. Baseline: libpin-5554-first10-20260803-1524 (start-100).
- EMU vs baseline: PASS — alignment winners/angles 10/10; pump Set G-- & Set I cost/vol 10/10. Residual float/hist/timing only.
- Pixel: smoke PASS — winners 10/10 same as EMU; cost/vol ABI diffs (armv8) documented; no degraded/errors.
- Full write-up: scratch/pin-device-test-20260803/REPORT.md.


## 2026-08-03 - Note: EMU vs Pixel First 10 not bit-identical

- Same APK start-107: emulator-5554 and Pixel 6 Pro produce slightly different First 10 results (deskew angles on some photos, ML Kit box counts, several pump cost/vol).
- Alignment winners still 10/10 same; no degraded/errors. Primary gate remains EMU vs same-device baseline (PASS). Pixel is arm64 smoke only.
- Documented in scratch/pin-device-test-20260803/REPORT.md.


## 2026-08-03 - Pixel prior (Jul27) vs First10 start-107

- Prior on device: align n=160 / pump n=167 `batch_load-start-53-g1ba4c0d2` (2026-07-27).
- Same First10 filenames: winners+angles 10/10 MATCH; Set G-- & Set I cost/vol 10/10 MATCH (extra D/E/G/G- only on prior).
- Slim subsets: scratch/pin-device-test-20260803/pixel6pro-prior-20260727/.
- Merge readiness: NOT ready — see chat (history not cleaned, hybrid/interim jni, dirty tree, pin sha drift).


## 2026-08-04 - Restore historical paddle build under third_party/paddle

- Ported Jul working recipe into third_party/paddle: patches/ (full), apply_patches.sh, run-android-historical.sh, patch_x86_thin_jni, tailor_models/armv8.
- ./third_party/paddle/build: Docker NDK r20, strip-unneeded, NDK28 link gate. Default ABIs arm64+x86_64.
- Rebuilt: arm64 tailor jni ~1.65MB LINK_OK; x86 thin jni ~31KB + light ~9.9MB LINK_OK (all four SOs).
- get-artifacts + libpin.toml SHAs updated; jniLibs wired; abiFilters drop armv7.
- Is-vs-should report: dev-ai-interaction/scratch/paddle-pin-is-vs-should-20260804.md (follow-up after merge).
- Deprecated run-android-slim.sh (int8-only + strip-debug path).


## 2026-08-04 - First 10 after historical paddle ship (b8449343)

- Pulled start-109 reports → scratch/pin-device-test-20260804-b8449343/{emu5554,pixel6pro}/
- EMU vs baseline and vs start-107: PASS 10/10 align+pump outcomes
- Pixel vs start-107: PASS 10/10; Pixel vs EMU still differs (ABI) as before
- REPORT.md in that scratch dir


## 2026-08-04 - get-artifacts promotes to app paths (libpin)

- get-artifacts: path app/… → repo root; optional sha256; no landlock by default (opt-in LIBPIN_GET_ARTIFACTS_SANDBOX=1).
- paddle/opencv libpin.toml: independent [[artifact]] rows for artifact/ and app jniLibs (and jar).
- ./third_party/get-artifacts paddle opencv verified.


## 2026-08-04 - libpin promote + 5554 First 10 (start-111)

- get-artifacts: app/ paths, sha256, no default landlock; paddle/opencv toml dual artifact+jniLibs rows.
- Built/deployed email-connection-start-111-g1f95c129 to emulator-5554.
- First 10 align+pump: PASS vs baseline and vs b8449343 (10/10). Reports: scratch/pin-device-test-20260804-libpin-promote/emu5554/

## 2026-08-04 - Merge email-connection into master
- Non-FF merge 61ded60d; build_app SUCCESS; builds tag updated; specials reconciled; armv7 deferred

## 2026-08-04 - remotetable-contract-strategy foundation — execution start

- Approved plan: dev-ai-interaction/plans/remotetable-contract-strategy-and-foundation-20260804-2024-plan.md
- Library-first: materialize, CONTRACT, L0 rate limits + Sheets efficiency, L1/L2 many-ops, pin + thin VE adapter, optional directional push MVP
- Phase 6 device deferred

## 2026-08-04 - remotetable-contract-strategy foundation — CODE LANDED

- Plan: dev-ai-interaction/plans/remotetable-contract-strategy-and-foundation-20260804-2024-plan.md
- Library commit 37c61a9 (fix-syncing): CONTRACT schema_v1, RateLimiter, Sheets batch/range/pace, L1/L2 ops, PolicySync push; harness PASS
- VE pin + AAR promoted; GoogleSheetsTabularBackend thin (readMany/updateRangeRows); SyncRateLimit.notifyProgress; SYNC_BEHAVIOR docs
- ./build_app OK; tag fix-syncing/builds → 7dfe1523
- Phase 6 device (5554) deferred by plan

## 2026-08-04 - remotetable-gaps-hygiene-and-cli-test — execution start

- Approved plan: dev-ai-interaction/plans/remotetable-gaps-hygiene-and-cli-test-20260804-2119-plan.md
- Phases: VE docs hygiene; appendRows no double-read; rate-limit all HTTP backends; type coerce; CLI/harness test surface; pin promote + build_app

## 2026-08-04 - remotetable-gaps-hygiene-and-cli-test — pre-build

- Library 17cf482: all HTTP backends RateLimiter; CellTypes+PolicySync coerce; appendDataRows; CLI conformance/push; harness PASS
- VE: project-facts+API hygiene; GoogleSheetsTabularBackend.appendRows→appendDataRows; pin+AAR promote

## 2026-08-04 - remotetable-gaps-hygiene-and-cli-test — CODE LANDED

- Plan complete: docs hygiene, appendDataRows, all HTTP backends paced, type coerce, CLI/harness test surface
- Library pin 17cf482; VE build 463181fb; tag fix-syncing/builds
- Optional 5554 not required by plan

## 2026-08-04 - remotetable-l3-ab-merge — execution start

- Plan: dev-ai-interaction/plans/remotetable-l3-ab-merge-union-lww-fieldfill-20260804-2209-plan.md
- Library L3 merge modes union/lww_row/field_fill; harness+CLI; pin promote; no VE coordinator rewrite

## 2026-08-04 - remotetable-l3-ab-merge — pin promote

- Library 570237b: MergeSync union/lww_row/field_fill; harness+CLI merge; CONTRACT L3
- Pin+AAR promoted; VE build link check (no coordinator rewrite)

## 2026-08-04 - remotetable-l3-ab-merge — CODE LANDED

- Plan complete: L3 merge union/lww_row/field_fill; harness+CLI; pin 570237b; VE build 0825f08b
- No VE coordinator rewrite; push regression PASS

## 2026-08-04 - remotetable-local-file-backends — execution start

- Plan: dev-ai-interaction/plans/remotetable-local-file-backends-and-offline-copy-20260804-2225-plan.md
- local/json-book/csv-dir L0 backends; offline push/merge; Kotlin local+json; pin if AAR changes

## 2026-08-04 - remotetable-local-file-backends — CODE LANDED prep

- Library b4baf3b: local/json-book (Kotlin+Python), csv-dir Python host-only; harness offline PASS
- Pin+AAR promoted; project-facts offline endpoints note

## 2026-08-04 - remotetable-local-file-backends — CODE LANDED

- Plan complete: local/json-book/csv-dir; harness offline PASS; pin b4baf3b; VE ac135520
- No coordinator rewrite

## 2026-08-04 - ve-sheets-sync-device-acceptance-5554 — execution start

- Plan: dev-ai-interaction/plans/ve-sheets-sync-device-acceptance-5554-20260804-2236-plan.md
- Preflight pin/build; dump logcat after human Sync now; residual fix only if needed

## 2026-08-04 - ve-sheets-sync-device-acceptance-5554 — BLOCKED (wrong APK)

- emulator-5554 online; pin on tree b4baf3b; builds tag fix-syncing/builds → 0faad378 (v0.98-24-g0faad378)
- Device installed versionName=email-connection-start-111-g1f95c129 (2026-08-04 09:01) — pre-rate-limit AAR
- Log dump: dev-ai-interaction/device-logcat-emulator-5554-sync-accept-20260804-preflight.log
- Evidence 14:57:26 hard fail HTTP 429 Read requests on GoogleSheetsBackend.readRows via OLD updateRows (line 130 stack) — no RateLimiter wait
- WorkManager RETRY then Sync complete 14:58:06 Honda/Ford writes OK (old APK worker retry, not lib pace acceptance)
- Human must ./deploy or install app/build/outputs/apk/debug/app-debug.apk on 5554 then Sync now; reply to resume Phase 2 on fixed build
- No residual code change this turn (stack proves stale binary)

## 2026-08-04 - ve-sheets-sync-device-acceptance-resume — execution start

- Plan: dev-ai-interaction/plans/ve-sheets-sync-device-acceptance-resume-20260804-2309-plan.md
- Phase 1 deploy gate: verify versionName on emulator-5554

## 2026-08-04 - ve-sheets-sync-device-acceptance-resume — BLOCKED (Phase 1 deploy gate)

- Plan: ve-sheets-sync-device-acceptance-resume-20260804-2309-plan.md
- adb 5554 versionName still email-connection-start-111-g1f95c129 (unchanged since 09:01)
- STOP per plan: no Sync acceptance / no postdeploy log as PASS
- Worktree ready: pin b4baf3b, HEAD 47e629a6, APK app/build/outputs/apk/debug/app-debug.apk
- Human: install that APK on 5554, confirm versionName ≠ email-connection-start-111…, Sync now, re-approve resume or say continue

## 2026-08-04 - ve-sheets-sync human-deploy plan — BLOCKED Phase 1

- Plan: dev-ai-interaction/plans/ve-sheets-sync-device-acceptance-human-deploy-20260804-2324-plan.md
- adb 5554: versionName still email-connection-start-111-g1f95c129 lastUpdate=2026-08-04 09:01:25
- STOP: no Sync acceptance / no postdeploy log as PASS
- Ready APK: app/build/outputs/apk/debug/app-debug.apk pin b4baf3b HEAD 1fa0af0b
- Human must install then Sync now; agent will not adb install

## 2026-08-04 - fix-sheets-tab-states-headers-and-rewrite — execution start

- Plan: dev-ai-interaction/plans/fix-sheets-tab-states-headers-and-rewrite-20260804-2344-plan.md
- Three cases: no tab / empty / data-without-headers; valid Sync ID; force writeAllRows

## 2026-08-04 - fix-sheets-tab-states — pre-build

- TabularSchema.isValidHeaderRow (Sync ID); mergeHeaderOrder invalid→canonical
- Lib ensureHeaders clear+rewrite on invalid; pin 04ee2d2
- Coordinator: resolve poison→empty LWW + forceFullRewrite writeAllRows; empty remote no appendDataRows

## 2026-08-04 - fix-sheets-tab-states-headers-and-rewrite — CODE LANDED

- Plan complete: three-case headers; pin 04ee2d2; VE 3623d9ea; tag fix-syncing/builds
- Human: install APK, clear junk tabs if needed, Sync now — confirm row 1 has Sync ID + data below

## 2026-08-05 - fix-sheets-missing-columns-report-not-silent — execution start

- Plan: dev-ai-interaction/plans/fix-sheets-missing-columns-report-not-silent-20260805-0000-plan.md
- Case 3: fail with named missing columns; no silent rewrite/poison LWW

## 2026-08-05 - fix-sheets-missing-columns-report-not-silent — pre-build

- missingRequiredHeaders + isCompletelyBlankGrid
- Case 3: SpreadsheetMissingColumnsException aborts dest; message names tab + columns
- No silent ensureHeaders merge/rewrite or poison LWW; cases 1–2 still ensure+fullRewrite

## 2026-08-05 - fix-sheets-missing-columns-report-not-silent — CODE LANDED

- Case 3 fails with named missing columns; no silent rewrite/poison LWW
- Cases 1–2 keep ensureHeaders; build 04e5bfc2 / fix-syncing/builds
- Human: install; corrupt fuel row1 → expect failure in Syncing Details

## 2026-08-05 - ethercalc harness + Room pilot — execution start

- Plans: remotetable-local-ethercalc-validation-harness-20260805-0011-plan.md; ve-room-tabular-backend-pilot-20260805-0011-plan.md
- Local EtherCalc compose+smoke; read-only Room Vehicles Backend + export path

## 2026-08-05 - ethercalc harness + Room pilot — pre-build

- remotetable 8f4901b: local EtherCalc up/down + live smoke PASS
- RoomVehiclesBackend read-only pilot + exportJsonBook; project-facts pointer

## 2026-08-05 - ethercalc harness + Room pilot — CODE LANDED

- EtherCalc: up/down + smoke PASS; lib commit 8f4901b (python/docs; AAR pin unchanged)
- RoomVehiclesBackend pilot + exportJsonBook; VE 8775c03d
- Production Sheets path unchanged

## 2026-08-05 - ve-policysync-merge-acks-pilot — execution start

- Plan: dev-ai-interaction/plans/ve-policysync-merge-acks-pilot-20260805-0025-plan.md
- PolicySync bridge for Merge acks; flag use_policy_sync_merge_acks default false

## 2026-08-05 - ve-policysync-merge-acks-pilot — CODE LANDED

- Plan: dev-ai-interaction/plans/ve-policysync-merge-acks-pilot-20260805-0025-plan.md
- PolicySyncBridge + syncMergeAcksTab gate; prefs use_policy_sync_merge_acks default false
- Merge acks only via MergeSync lww_row when flag on; fuel/vehicles unchanged
- build_app green; VE 03dca84e
- Human optional: set vehicle_settings boolean use_policy_sync_merge_acks=true then Sync now; log line Merge acks via PolicySync/MergeSync; set false after


## 2026-08-05 - remotetable-richer-filters-in-isempty — execution start

- Plan: dev-ai-interaction/plans/remotetable-richer-filters-in-isempty-20260805-0033-plan.md
- Library: IN + is_empty filter ops on remotetable


## 2026-08-05 - remotetable-richer-filters-in-isempty — CODE LANDED

- Plan: dev-ai-interaction/plans/remotetable-richer-filters-in-isempty-20260805-0033-plan.md
- Filter v1.1: in: / is_empty: / empty: / not_empty: + equality; Kotlin RowOps + Python parity
- Harness PASS offline; CONTRACT updated; pin 14b97be AAR sha 59a07946…
- VE coordinator not switched (out of scope)


## 2026-08-05 - ve-room-to-ethercalc-e2e-export-validation — execution start

- Plan: dev-ai-interaction/plans/ve-room-to-ethercalc-e2e-export-validation-20260805-0044-plan.md
- E2E: Room export → EtherCalc path validation


## 2026-08-05 - ve-room-to-ethercalc-e2e-export-validation — CODE LANDED

- Plan: dev-ai-interaction/plans/ve-room-to-ethercalc-e2e-export-validation-20260805-0044-plan.md
- Offline: room_export_to_ethercalc_smoke + fixture Vehicles/Sync ID; harness PASS
- EtherCalc e2e PASS with up.sh (unique room); SKIP without env
- scripts/room-export-ethercalc-smoke.sh pointer; pin dbb7068 (python/docs only; AAR sha unchanged)
- Sheets production path untouched


## 2026-08-05 - ve-room-fuel-tabs-backend-pilot — execution start

- Plan: dev-ai-interaction/plans/ve-room-fuel-tabs-backend-pilot-20260805-0121-plan.md
- Room Fuel tabs Backend pilot (read-only default)


## 2026-08-05 - ve-room-fuel-tabs-backend-pilot — CODE LANDED

- Plan: dev-ai-interaction/plans/ve-room-fuel-tabs-backend-pilot-20260805-0121-plan.md
- RoomFuelBackend read-only multi-tab + exportJsonBook; FUEL_HEADERS/Sync ID
- Golden fixture + room_fuel_export_smoke offline PASS; EtherCalc one-tab PASS with up.sh
- Sheets/coordinator fuel LWW untouched; build via build_app next


## 2026-08-05 - ve-policysync-expenses-pilot — execution start

- Plan: dev-ai-interaction/plans/ve-policysync-expenses-pilot-20260805-0128-plan.md
- PolicySync/MergeSync pilot for Expenses tab; flag default off


## 2026-08-05 - ve-policysync-expenses-pilot — CODE LANDED

- Plan: dev-ai-interaction/plans/ve-policysync-expenses-pilot-20260805-0128-plan.md
- PolicySyncBridge expenses LWW + pref use_policy_sync_expenses default false
- syncExpensesTab gated; flag-off legacy LWW; build green VE 541fad1c
- Human optional: vehicle_settings use_policy_sync_expenses=true then Sync now; log Expenses via PolicySync/MergeSync; set false after


## 2026-08-05 - ve-policysync-vehicles-pilot — execution start

- Plan: dev-ai-interaction/plans/ve-policysync-vehicles-pilot-20260805-0137-plan.md
- PolicySync/MergeSync pilot for Vehicles tab; flag default off


## 2026-08-05 - ve-policysync-vehicles-pilot — CODE LANDED

- Plan: dev-ai-interaction/plans/ve-policysync-vehicles-pilot-20260805-0137-plan.md
- PolicySyncBridge mergeVehiclesViaLwwRow; pref use_policy_sync_vehicles default false
- syncVehiclesTab gated; flag-off keeps mergeVehicleLww+overlay; flag-on full-row only
- build green VE a4dc7d9c
- Human optional: vehicle_settings use_policy_sync_vehicles=true; Sync now; log Vehicles via PolicySync/MergeSync; set false after


## 2026-08-05 - ve-policysync-fuel-tabs-pilot — execution start

- Plan: dev-ai-interaction/plans/ve-policysync-fuel-tabs-pilot-20260805-0634-plan.md
- PolicySync/MergeSync pilot for Fuel tabs; flag default off


## 2026-08-05 - ve-policysync-fuel-tabs-pilot — CODE LANDED

- Plan: dev-ai-interaction/plans/ve-policysync-fuel-tabs-pilot-20260805-0634-plan.md
- PolicySyncBridge mergeFuelViaLwwRow; pref use_policy_sync_fuel default false
- syncFuelTabs Pass 1 gated; Pass 2 field-merge + Pass 3 write always app-side
- build green VE 130a5d28
- Human optional: vehicle_settings use_policy_sync_fuel=true; Sync now; log Fuel LWW via PolicySync/MergeSync; set false after


## 2026-08-05 - ve-room-fuel-multi-tab-ethercalc-export — execution start

- Plan: dev-ai-interaction/plans/ve-room-fuel-multi-tab-ethercalc-export-20260805-0713-plan.md
- Multi-tab Room fuel → EtherCalc export validation


## 2026-08-05 - ve-room-fuel-multi-tab-ethercalc-export — CODE LANDED

- Plan: dev-ai-interaction/plans/ve-room-fuel-multi-tab-ethercalc-export-20260805-0713-plan.md
- room_fuel_export_smoke: multi-room EtherCalc (each Fuel tab); offline still PASS
- pin 883cda7; Sheets/production untouched


## 2026-08-05 - ve-policysync-vehicles-definition-overlay — execution start

- Plan: dev-ai-interaction/plans/ve-policysync-vehicles-definition-overlay-20260805-0731-plan.md
- PolicySync vehicles path: port definition overlay after library LWW


## 2026-08-05 - ve-policysync-vehicles-definition-overlay — CODE LANDED

- Plan: dev-ai-interaction/plans/ve-policysync-vehicles-definition-overlay-20260805-0731-plan.md
- VehicleDefinitionOverlay shared helper; flag-on PolicySync LWW + same overlay as legacy
- build green VE ae05af7a


## 2026-08-05 - ve-policysync-pilots-default-on-evaluation — execution start

- Plan: dev-ai-interaction/plans/ve-policysync-pilots-default-on-evaluation-20260805-0815-plan.md


## 2026-08-05 - ve-policysync-pilots-default-on-evaluation — PHASE1_SOAK (no default flip)

- Plan: dev-ai-interaction/plans/ve-policysync-pilots-default-on-evaluation-20260805-0815-plan.md
- Checklist: docs/reference/POLICY_SYNC_PILOT_SOAK.md; project-facts defaults table
- Defaults still all false; STOP for human soak / go on which prefs to default true
- Proposed tranche (not applied): merge_acks+expenses+vehicles true; fuel false


## 2026-08-05 - policysync-scenario-tests-local-ethercalc — execution start

- Plan: dev-ai-interaction/plans/policysync-scenario-tests-local-ethercalc-20260805-0826-plan.md


## 2026-08-05 - policysync-scenario-tests-local-ethercalc — CODE LANDED

- Plan: dev-ai-interaction/plans/policysync-scenario-tests-local-ethercalc-20260805-0826-plan.md
- S1–S7 offline PASS in harness; S8 multi-entity + multi-tab fuel EtherCalc PASS
- Defaults still false; soak doc updated for automated evidence
- pin 188b328


## 2026-08-05 - PolicySync cutover: drop pilot prefs, library LWW is the path

- Human: no production phase-in flags; implement + test; rollback = master APK
- Remove use_policy_sync_* gates; always MergeSync lww_row for acks/expenses/vehicles/fuel Pass1


## 2026-08-05 - PolicySync cutover CODE LANDED (no pilot prefs)

- Always library MergeSync LWW for acks/expenses/vehicles/fuel Pass1; vehicle overlay after
- Fuel field-merge still app; removed use_policy_sync_* prefs and dual paths
- Scenarios S1–S7 PASS; S8 when docker available


## 2026-08-05 - ve-location-blob-merge-after-library-lww — execution start

- Plan: dev-ai-interaction/plans/ve-location-blob-merge-after-library-lww-20260805-0631-plan.md
- Restore FuelLocationJson.mergeBlobs after library LWW for fuel + expenses


## 2026-08-05 - ve-location-blob-merge-after-library-lww — CODE LANDED

- LocationBlobOverlay + wire fuel Pass1 and expenses after library LWW
- FuelLocationJson.mergeBlobs unchanged; build d45524e2; scenarios S1–S7 PASS


## 2026-08-05 - location-blob-overlay-scenario-tests — execution start

- Plan: dev-ai-interaction/plans/location-blob-overlay-scenario-tests-20260805-0830-plan.md


## 2026-08-05 - location-blob-overlay-scenario-tests — CODE LANDED

- LocationBlobOverlayTest 6 cases green (Robolectric); build_app -- testDebugUnitTest
- Plan: location-blob-overlay-scenario-tests-20260805-0830-plan.md
- S1–S7 still PASS


## 2026-08-05 - vehicle-definition-overlay-unit-tests — execution start

- Plan: dev-ai-interaction/plans/vehicle-definition-overlay-unit-tests-20260805-1359-plan.md


## 2026-08-05 - vehicle-definition-overlay-unit-tests — CODE LANDED

- VehicleDefinitionOverlayTest 8 cases green; LocationBlobOverlayTest still green
- Plan: vehicle-definition-overlay-unit-tests-20260805-1359-plan.md


## 2026-08-05 - fuel-field-merge-unit-tests-after-library-lww — execution start

- Plan: dev-ai-interaction/plans/fuel-field-merge-unit-tests-after-library-lww-20260805-1405-plan.md


## 2026-08-05 - fuel-field-merge-unit-tests-after-library-lww — CODE LANDED

- FuelRowMergeEngineFieldMergeTest 8 cases green via build_app -- testDebugUnitTest
- Plan: fuel-field-merge-unit-tests-after-library-lww-20260805-1405-plan.md


## 2026-08-05 - sync-architecture-hygiene-archive-obsolete-plans — execution start

- Plan: dev-ai-interaction/plans/sync-architecture-hygiene-archive-obsolete-plans-20260805-1440-plan.md


## 2026-08-05 - sync-architecture-hygiene-archive-obsolete-plans — CODE LANDED

- Plan: sync-architecture-hygiene-archive-obsolete-plans-20260805-1440-plan.md
- Architecture map: dev-ai-interaction/research/sync-remotetable-architecture-map-20260805.md
- Archived 29 obsolete sync/remotetable/policysync plans → historical-plans/
- Renamed POLICY_SYNC_PILOT_SOAK.md → docs/reference/SYNC_TAB_LWW_AND_TESTS.md
- No app behavior change


## 2026-08-05 - sync-always-on-device-confidence — execution start

- Plan: dev-ai-interaction/plans/sync-always-on-device-confidence-20260805-1622-plan.md


## 2026-08-05 - sync-always-on-device-confidence — preflight PASS (awaiting human Sync)

- Plan: sync-always-on-device-confidence-20260805-1622-plan.md
- Harness PASS; EC S8 PASS; unit tests 3 classes PASS
- Installed APK on emulator-5554 versionName=fix-syncing-start-48-gcc2ed4dd
- STOP for human: Sync now on good re-seeded sheet; report result


## 2026-08-05 - sync-always-on-device-confidence CODE LANDED

- Plan: sync-always-on-device-confidence-20260805-1622-plan.md
- Device Sync complete ~10:04-10:10; Honda update=214 paced; Ford Van update=81; no Sync failed
- Log: device-logcat-emulator-5554-confidence-sync-done-20260805.log


## 2026-08-05 - local PR-fix-syncing prepared (two-stage: remotetable then VE)

- **Stage 1 remotetable:** nested `third_party/remotetable/src` rebased onto origin/master; 11 logical commits (+ eng-log); `backup-fix-syncing` @ ad97c08; cleaned product tip 8f7b67b; TREE_MATCHES_BACKUP YES; PR `/home/dlang/git/remotetable/sandbox/PRs/PR-fix-syncing.md`
- **Stage 2 VE:** soft-reset cleanup ~50→8 logical commits; `backup-fix-syncing` @ bf02effd; cleaned HEAD 8da29f16; TREE_MATCHES_BACKUP YES (52e2a463); PR `dev-ai-interaction/PRs/PR-fix-syncing.md`
- **Merge order:** remotetable first → pin+AAR to merged library master tip → then VE. Current VE pin still 188b328 (pre-Stage-1); do not merge VE until pin updated.
- Archived to historical-plans: device-confidence + architecture-hygiene plans (CODE LANDED).
- Ready for Master independent review; no merge/push by coder.

## 2026-08-05 - VE pin promote 50b376a + Stage 2 PR ready

- Remotetable Stage 1 merged master tip: `50b376ad4ac817c3cd0541eaa20cad91e3e78692`
- Updated `third_party/remotetable/libpin.toml` + SOURCE.md; `fetch-deps --git-home /home/dlang/git ro|build remotetable`
- Conformance PASS (S1–S7 offline); assembleRelease SUCCESS; AAR sha256 `59a07946…` bit-identical to prior co-dev product
- Commit: `d266c6a6` chore: pin remotetable to master 50b376a
- Stage 2 PR refreshed: `dev-ai-interaction/PRs/PR-fix-syncing.md` (pin gate CLEARED; ready for Master merge)
- HEAD: `d266c6a6e0b2d703a665bc33f8c4eda72443a13a`

## 2026-08-05 - Merge fix-syncing into master
- Stage 2 VE merged (FF lineage via fix-syncing tip 4eb46e4c + cleanup b82ef2dd). Pin remotetable 50b376a. Kept master set-*-perms @@ tokens. PolicySync always-on LWW + overlays + Room pilots + fail-loud. ./build_app SUCCESS; builds @ HEAD. No works tag.

## 2026-08-13 - Merge location-fixes into master (specials corrected)
- Product FF lineage at 7a274012 + project-facts reconcile from branch tip (Stations/v20/Wrong-station orientation). TODO unchanged. Eng-log via wrapper. Continuing ./build_app for builds tag.

## 2026-08-16 - Merge libpin-paddle-cleanup into master
- FF merge tip a0874aeb: multi-ABI First-10-good paddle SOs, QF G4, Set V odo, Room v21, experiment JobRunner, JNI expand. Master build-home infra retained. Specials: project-facts fold; TODO unchanged. ./build_app next.

## 2026-08-16 - libpin-paddle-cleanup build gate

- ./build_app assembleDebug SUCCESS; builds @ d318a322 (v0.98-52-gd318a322). First-10-good SO pin hashes unchanged. No works tag.
