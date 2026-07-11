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

## 2026-07-10 - Quick Fill / Reports research (planner, plan mode)

- Plan (sandbox): `dev-ai-interaction/plans/quickfill-photos-partial-camera-reliability-20260710-plan.md` (F1–F5: media permission, photo save+photoUrl, auto partial+clear fields, portrait camera parity, reports per-vehicle summary).
- Phone artifacts: portrait/landscape Quick Fill screenshots; Reports screenshot; DB pull under `dev-ai-interaction/research/quickfill-db-*` and `reports-screenshot-*`.
- Photos: intended path DCIM/Camera/fuel_*; only one historical fuel_* (2026-06-26); July fills have null photoUrl; media permission never requested (CAMERA only).
- Reports UI: totals OK ($383.94 / 95.8 gal / 11 fills); Avg MPG 583.8 is cross-vehicle nonsense; takeLast(5) on DESC list shows oldest not newest.
- Landscape camera framing accepted as OK; portrait has large 3-side black bars — F4 is portrait-only parity.
- Deferred (not this plan): fail-email package; Paddle native abort on dash retry (tombstone 2026-07-09 17:52).
- Note: current-cycle narrative belongs in ENGINEERING_LOG via this wrapper; backlog-only items via `./todo-append` (not direct TODO.md edits for live work).

## 2026-07-10 - Execution start: quickfill-photos-partial-camera-reliability

- Plan: dev-ai-interaction/plans/quickfill-photos-partial-camera-reliability-20260710-plan.md
- Features F1-F5: media permission, photo save+photoUrl, auto partial+clear fields, portrait camera, reports per-vehicle MPG
- Baseline: no operational-improvements/builds tag yet (first successful build_app will create it)

## 2026-07-10 - Execution complete: quickfill-photos-partial-camera-reliability

- Plan: dev-ai-interaction/plans/quickfill-photos-partial-camera-reliability-20260710-plan.md
- F1: media permission MainActivity + Settings (commit c624e424 + prior MainActivity)
- F2: photo MediaStore errors/Toasts/photoUrl/status/fallback (1773500c)
- F3: auto isPartialFill + clear fields, no pop (a077e886)
- F4: portrait panel A fillMaxSize single FIT letterbox (53cd7cd6)
- F5: per-vehicle MPG, take(5) newest, usable fields, single scroll (ab889a33 + 5d4f3d6b fix)
- Builds tag: operational-improvements/builds @ 5d4f3d6b
- Note: TODO.md not updated for live work (backlog only per user/mandates)

## 2026-07-10 - Post-test DB check + remove Quick Fill test rows

- User validated: multi-retry no crash; portrait camera size OK; partial odo-only + pump-only saves; fuel photos saving.
- DB before cleanup: id 12 odo-only partial=1 photoUrl set; id 13 pump-only (cost+gal) partial=1 photoUrl null; ids 1-11 historical.
- Partial auto-flag correct for new rows (isPartialFill=1 when any of odo/cost/gal blank).
- Removed test rows id 12 and 13 from device `vehicle_expenses.db` (force-stop, rewrite DB via run-as, clear WAL). Verified 11 rows remain, max id 11.
- Historical rows 5-8 still partial=0 with odo=0 (pre-fix data; not rewritten).

## 2026-07-10 - DB cleanup after second Quick Fill photo test

- Fetched post-test DB: id 14 full fill (odo 199397, gal 13.12, cost 52.34) partial=0 with single photoUrl media/1000002795.
- Two fuel_ files on disk at 18:18: fuel_1783732682156 (media/1000002794) and fuel_1783732691966 (media/1000002795). Entry only stores one photoUrl (schema single field; last shutter wins).
- Deleted id 14 from device DB.
- Marked prior partials isPartialFill=1: ids 5-8 (odo was 0); id 1 and 9 (bogus odo 28 and 122 cleared to 0, partial=1).
- Left full fills 2,3,4,10,11 partial=0. Device verified 11 rows, no id 14.

## 2026-07-10 - Execution start: quickfill-multi-photo-json-and-reports-redesign

- Plan: dev-ai-interaction/plans/quickfill-multi-photo-json-and-reports-redesign-20260710-plan.md
- Feature A: session multi-photo JSON on Save (dash/pump)
- Feature B: reports redesign last-5 full fills + per-vehicle summary
- Baseline: operational-improvements/builds @ 40caac5e

## 2026-07-10 - Execution complete: quickfill-multi-photo-json-and-reports-redesign

- Plan: dev-ai-interaction/plans/quickfill-multi-photo-json-and-reports-redesign-20260710-plan.md
- A: sessionPhotos map dash/pump; JSON on Save only; clear after save (33ac2b24)
- B: reports overall + per-vehicle last-5 full fills (interim gallons MPG), $/mi, no $/gal bars (2a3e5b53)
- Builds tag: operational-improvements/builds @ 2a3e5b53

## 2026-07-10 - Verify multi-photo JSON test rows then delete

- Fetched DB: ids 15 pump-only partial JSON [pump]; 16 dash-only partial JSON [dash]; 17 full both JSON [dash,pump] with distinct media URIs.
- Verified tags/uris/ts present; removed ids 15–17 from device DB (11 historical rows remain).

## 2026-07-10 - Execution start: reports-summary-compact-and-by-vehicle-last5-only

- Plan: dev-ai-interaction/plans/reports-summary-compact-and-by-vehicle-last5-only-20260710-plan.md
- Compact Summary (overall + vehicle stats); By vehicle = last-5 full fills only
- Baseline: operational-improvements/builds @ b942548d

## 2026-07-10 - Execution complete: reports-summary-compact-and-by-vehicle-last5-only

- Plan: dev-ai-interaction/plans/reports-summary-compact-and-by-vehicle-last5-only-20260710-plan.md
- Summary card: overall 1 dense line + per-vehicle 1-line stats (width-aware)
- Last 5 full fills section: name + rows only (no Fuel/Gal/MPG/$/mi in cards)
- MPG helpers unchanged (interim gallons)
- Commit 22ff13b3; builds tag operational-improvements/builds

## 2026-07-10 - Execution start: reports-summary name-line + last-5 legs rollup

- Plan: dev-ai-interaction/plans/reports-summary-compact-and-by-vehicle-last5-only-20260710-plan.md (updated)
- Vehicle summary: name alone then stats 1–2 lines
- Last-5: newest legs only with valid mpg + rolled cost/vol
- Baseline: operational-improvements/builds @ 947e3783

## 2026-07-10 - Execution complete: reports name-line summary + last-5 leg rollup

- Plan: reports-summary-compact-and-by-vehicle-last5-only-20260710-plan.md
- Summary: vehicle name line + adaptive stats 1–2 lines (middot split)
- Last-5: newest valid legs only; rolled sumCost/sumVol; always mpg+bar; no first-full n/a
- Commit 4d7fb9ad

## 2026-07-10 - Remove duplicate Honda full fill

- Deleted fuel_entries id 4 (duplicate of id 3: same odo 202071, gal 5.372, cost 25.03, ~8s later). Kept id 3.
- Honda fulls remaining: 201973, 202071, 202589 (still 2 valid last-5 legs).

## 2026-07-10 - Execution start: expense-entry-vehicle-dropdown-and-quickfill-camera

- Plan: dev-ai-interaction/plans/expense-entry-vehicle-dropdown-and-quickfill-camera-20260710-plan.md
- A: vehicle dropdown; B: camera/zoom/shutter/save/gallery icons
- Baseline: operational-improvements/builds @ 8cb3260a

## 2026-07-10 - Execution complete: expense-entry-vehicle-dropdown-and-quickfill-camera

- Plan: expense-entry-vehicle-dropdown-and-quickfill-camera-20260710-plan.md
- A: vehicle ExposedDropdownMenu (658de92a)
- B: CameraPreview + zoom + MediaStore shutter + Save/Gallery icons, no OCR (2e21041c)
- Builds: operational-improvements/builds @ 2e21041c

## 2026-07-10 - Clear photo on expense id 1

- expense_entries id 1 (Honda, \$800 repair starter): set photoUrl=NULL, receiptImagePath=NULL. Gallery media 1000002802 left on disk unless user deletes separately.

## 2026-07-10 - Execution: expense plan delta (optional photo on save)

- Plan: expense-entry-vehicle-dropdown-and-quickfill-camera-20260710-plan.md (updated: photoUrl optional)
- A/B already landed 658de92a/2e21041c; delta: allow save without photo

## 2026-07-10 - App icon lightened master → Android densities (export ready)

- Source: dev-ai-interaction/research/imagine-icon-candidate/app-icon-master-1024.png (pale yellow car, pale blue van, white glass).
- Generated full density set + round masks + adaptive foregrounds + 512 playstore PNGs + #FAFAFA adaptive background into android-export/.
- Could not write into agent-7/app (ai-planner lacks write on dlang:ai-code res files). Install: bash android-export/install-into-app.sh <worktree> as a writable user, or execution agent after perms.

## 2026-07-10 - Execution: install lightened app icon

- Plan: install-lightened-app-icon-from-master-20260710-plan.md
- install-into-app.sh already applied (user); verify sizes + build_app commit
- Baseline: operational-improvements/builds @ 104bfd08

## 2026-07-10 - Keep icon Imagine link and masters for reference

- Retained: Imagine post https://grok.com/imagine/post/c2fefde0-449d-4795-b48a-8f4d96cd0b8c
- Masters + export: dev-ai-interaction/research/imagine-icon-candidate/ (README.md documents link, masters, install).
- project-facts.md pointer added to that research path.

## 2026-07-10 - Docs file for launcher icon (draft + plan; docs/ not writable)

- Draft full doc: dev-ai-interaction/research/imagine-icon-candidate/docs-APP_LAUNCHER_ICON.md
- Plan for execution agent to copy to docs/reference/APP_LAUNCHER_ICON.md: dev-ai-interaction/plans/add-docs-app-launcher-icon-20260710-plan.md
- Planner cannot write worktree docs/ (permission denied).

## 2026-07-10 - Execution: add docs APP_LAUNCHER_ICON

- Plan: add-docs-app-launcher-icon-20260710-plan.md
- Copy draft → docs/reference/APP_LAUNCHER_ICON.md + build_app
- Baseline: operational-improvements/builds @ c8357785

## 2026-07-10 - Launcher icon crop too tight on device

- Screenshot: home/search shows VehicleExpenses adaptive icon over-cropped (mostly yellow car; van/wheel/$ cut off).
- Cause: full-bleed master used as adaptive FG; system mask shows ~center only.
- Rebuilt android-export with ~70% safe-zone scale + padding. Re-install via install-into-app.sh when writable; rebuild APK to verify.

## 2026-07-10 - Execution start: install lightened app icon safe-zone

- Plan: dev-ai-interaction/plans/install-lightened-app-icon-safe-zone-20260710-plan.md
- Intent: install adaptive safe-zone padded lightened launcher icon export into app/
- Baseline: operational-improvements/builds @ 2b25b1b6f300cea95c04f2aa3b600ec15f7650eb

## 2026-07-10 - Execution end: install lightened app icon safe-zone

- Safe-zone padded lightened launcher mipmaps installed from android-export (match export SHA)
- Full/round/foreground densities mdpi–xxxhdpi; sizes FG 432 / full 192 OK
- Playstore/background already matched prior lightened install (no further delta)
- Commit c6dc894d; builds tag: operational-improvements/builds @ c6dc894dbdaee551c0631025e22eacef20b3afad
- User: reinstall APK / clear launcher cache to verify van, wheel, $ inside circular mask

## 2026-07-10 - Execution start: expense-edit-date-photo-viewer

- Plan: dev-ai-interaction/plans/expense-edit-date-photo-viewer-20260710-plan.md
- Schema vendor+odometer; list/edit nav; date UI; zoom-pan; reports Exp/categories
- Baseline: operational-improvements/builds @ 62fa444d

## 2026-07-10 - Execution end: expense-edit-date-photo-viewer

- Plan: expense-edit-date-photo-viewer-20260710-plan.md
- DB v8: vendor + odometer; getById (b17dc5d8)
- List vehicle/date/vendor + nav expense/{id} (b1735d24)
- Entry create/edit, date, vendor/description, zoom-pan photo (05293155)
- Reports per-vehicle Exp + categories (270d1b7f/d855c76e)
- Builds tag: operational-improvements/builds @ d855c76e2bdd059d59c11875dc7784a51935e3db

## 2026-07-10 - History cleanup + PR for operational-improvements

- Backup tag: backup-operational-improvements @ d681633d (pre-squash messy history).
- Squashed ~29 commits → 5 logical: quickfill; reports; expense; assets+docs; eng log.
- Cleaned HEAD: 8cbcc645. PR doc: dev-ai-interaction/PRs/PR-operational-improvements.md
- User: master agent "Please review PR-operational-improvements". Dirty jniLibs/gradlew left unstaged.

## 2026-07-11 - PR review three bugs: plan written (app/ not writable by planner)

- Reviewer bugs: (1) expense edit REPLACE wipes metadata (2) CSV unquoted photoUrl JSON (3) missing expense silent fail on edit.
- Plan: dev-ai-interaction/plans/fix-pr-review-three-bugs-expense-csv-20260711-plan.md
- Planner cannot write app/src (ai-planner vs dlang:ai-code); execution agent must implement.

## 2026-07-11 - Execution start: fix-pr-review-three-bugs-expense-csv

- Plan: fix-pr-review-three-bugs-expense-csv-20260711-plan.md
- Bugs: edit preserve metadata; CSV quote/parse; missing-edit toast
- Baseline: operational-improvements/builds @ 26538437d3b703459f5b7370789dc3dec3795828

## 2026-07-11 - Execution end: fix-pr-review-three-bugs-expense-csv

- Plan: fix-pr-review-three-bugs-expense-csv-20260711-plan.md
- Bug1/3: loadedExpense copy on save; Toast+back if missing; Save disabled until load (c97bdbb2)
- Bug2: csvEscape + parseCsvLine; fuel/expense/vehicle export; expense adds vendor/odo/photoUrl (74ecb25e)
- Builds tag: operational-improvements/builds @ 74ecb25ea8a425e1325380013501992623ca3d1e

## 2026-07-11 - PR updated after three-bug fix

- Squashed review-fix commits into: 95462332 fix: expense edit preserve metadata; CSV quote photoUrl; missing-edit toast
- Logical history: 6 commits on merge-base..HEAD. backup-operational-improvements still points at pre-first-squash d681633d.
- PR doc refreshed: dev-ai-interaction/PRs/PR-operational-improvements.md (includes review-fix section).
- Note: master (b581a173) is ahead of merge-base 9aac4d0d — rebase onto master before GitHub merge if needed.
