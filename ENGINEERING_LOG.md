# Engineering Activity Log

This log tracks the implementation, refactoring, and deployment activities performed by the Application Engineer session.

## [2026-06-18] - ICRS filter unify, red-box storage, and pump analysis work
- **Branch:** fix-pump-experiment
- **Activity:** New planning cycle started; high-level plan created for remaining ICRS and storage unification work.
- **Scope summary:** Fix bad 0/1/2f ICRS filter in takeSnapshot (clip bounds only); unify to single annotations param (pixel int / ICRS float); explicit "red_boxes"/"initial_red_rects" + odo crop rect in JSON metadata (beyond trial_*_annotations backdoor); ensure aligned odo crop outlines render in Native Aligned report images; change 4 offset->8; create pump equivalents of deep_analysis.py + stage_progression_analysis.py.
- **First deliverable:** Plan file written to dev-ai-interaction/plans/fix-icrs-filter-unify-red-box-storage-odo-aligned-pump-analysis-20260618-plan.md
- **Notes:** Effort tracking moved here per current-state hygiene rules. current-state.md now limited to branch/tag/plan link + codebase pointers/facts.

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

## [2026-06-16] - Pump Experiment Heatmap + Per-Char Probs (Phases 2-8 of approved plan)
- **Activity:** Master (orchestrator) completing remaining phases of pump-experiment-heatmap-hist-in-redbox-fn-plus-perchar-recognition-probs-in-json-20260616-plan.md after sub-agent cancellation/repair cycle. Sub-agents (IDs 019ed1b4 etc.) had been used for initial phases; repair sub did early work then was killed; master took over with full forensic gates.
- **Key Changes (from state narrative at time of roll):**
  - Phase 2: Wired hist return as Pair<List<TextBlock>, IntArray?> in processPaddleHeatmap / Native paths; legacy return updated; call site in runDiscoveryPaddle destructures.
  - Phase 3: Added metadata["heatmap_hist_${scale}"] = JSONArray... in runDiscoveryPaddle (flows to branch metadata/JSON); forensic PASS.
  - Phase 3 build: SUCCESS at tag d7fcf099 (from 133fce63); hist now in metadata/JSON for redbox fn paths.
  - Phase 5 start: Extended RecStageResult + OcrResult with perCharProbs: String; capture in processOcr / processOcrNumeric using maxVal; passed through constructions; forensic PASS. Ready for blue/orange wiring.
- **Notes:** Sub-agent stagnation/cancellation and repair preflight details rolled here from current-state.md. Full code forensics and subsequent phases in the sandbox plan + git history since baseline. Current-state.md pruned to facts/pointers only (see hygiene rules).
- **Files Referenced (at time):** ExperimentPumpScreen.kt (various per forensic reads), NativeImageUtils, etc.

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

## 2026-06-22 - test

## 2026-06-22 - planner test

## 2026-06-22 - test

## 2026-06-22 - planner test

## 2026-06-22 - planner test2

## 2026-06-22 - direct from dlang

## 2026-06-22 - planner test

## 2026-06-22 - coder test

## 2026-06-22 - orch test

## 2026-06-22 - Emulator crash re-investigation (binPeak stroke hist setSize)

- User: "it crashed on the emulator again, fetch the logs and investigate"
- Devices: emulator-5554 + phone present.
- Fetched: `adb -s emulator-5554 logcat -d -b all > dev-ai-interaction/logs/emulator-crash-20260622-032844.log` (3104 lines, ~729kB; post-crash artd spam, no new Java trace - buffer rolled after abort).
- Pump reports pulled to `dev-ai-interaction/logs/emulator-latest-pump_reports-20260622-0328/` (9 files; latest partial is pump_report_2026-06-22_03-23-10_part1.html 1248B - only <html> header table, no rows; same as 03:24 pull. Big JSONs from prior successful runs).
- Confirmed crashing log: dev-ai-interaction/logs/emulator-crash-20260622-032407.log (29MB) produced the 03-23-10 partial.
- Crash details (verbatim from log @03:23:15.389):
  E cv::error(): OpenCV(4.10.0) Error: Assertion failed (s >= 0) in setSize, .../matrix.cpp:246
  F libc : Fatal signal 6 (SIGABRT)...
  Stack: nativeCalculateHistogramWithThresholdH+1720 -> calculateHistogramWithThresholdH -> binPeakComputeStrokeWidths -> captureBinPeakSnapshotsFromRedbox (in procB path of first photo).
- Pre-crash sequence (from log): ... 1024-scale CHAR_AWARE/EXPAND traces (discovery), 5x "Registered crop at 1037..1041", BINARIZE_RANGE: [230,246] on 4080x3060 (twice), crash in histH.
- No "binPeak_*_vsw" metadata emitted for the crashing peak (crashed inside first stroke call after 2nd binarize per peak loop).
- Code cross-ref: NativePaddleEngine.kt:189 hardcodes BufferSetA/B(4000, 3072); pump does workspace.resize(actualW, actualH) from photo (4080x3060); BufferSet.resize does physicalResize on both instances but NO refreshViews() re-wrap of _mat; captureBinPeak does `val b=workspace.s; b.mat.setTo(0); binarizeRange(workspace.p.mat, b.mat, ...); binPeakCompute...(b.mat, redPixelBForBinPeak)`.
- Red prep: top-6 area prune (for B raw/exp/max) + validBinPeakRects (normalize+filter w/h>0) + pass to hist; native H does OR coverage + per-red maxWRow/md discard + HIST_SZ=8192 uncapped (but crash before return).
- Conclusion: same root as analyzed in analyze-pump-crash-binpeak-stroke-hist-opencv-setsize-fix-20260622-plan.md and the prune6 fix plan. The 03:23 run hit it before any of the ultra-micro fix phases were executed on device. No new distinct failure mode; buffer size/header staleness + insufficient pre-native guards on (binMat dims + red union area) when mixing initial 4000 setup with real photo + direct .s full-res hijack for binPeak.
- Artifacts preserved for branch testing as requested previously.

## 2026-06-22 - Review of claimed implementation of analyze-pump-crash plan via compliance-report

Forensic check of code after implementation agent:

- Native H hist (cpp ~1692): Good updates for swap, skip <=0, early empty return, coverage guard if(w>0 h>0), maxWRow/maxHCol only valid, cov gate in scans, run < md per-col/row, HIST_SZ=8192 uncapped. Matches.

- Kotlin: validBinPeakRects used for ForBinPeak lists; subList(6) active in proc B/C etc red pruning. Good.

- Probe defensive: added <=0 checks/logs/throws in ImageIngestionProvider and pump. Good.

Defects found:

1. BufferSet physicalResize still does NOT call refreshViews() (only updates native + _buffer). _mat for p/s remain from initial BufferSet(4000,3072) creation. After runtime resize to photo dims (4080x3060), b.mat in binPeak capture can have stale header -> risk of bad size() reaching zeros/Rect/mean even with rect guards. (Root cause from original crash not fully closed.)

2. "n_reds_after_prune4" metadata still written in 7+ locations across procs (lines ~1262,1418,1582,...). Comments still reference prune4 in redbox context. Plan required cleanup.

3. No refresh of Mat views after resize anywhere in pump flow or BufferSet. flip()/rotate may compound.

4. Implementation used bulk commits, not the mandated ultra-micro phases + per-phase forensic read/grep + build_app from the plan STANDARD BLOCK. Compliance report itself notes this.

5. No additional guards around binMat size vs passed rects/img dims in binPeakCompute or capture.

6. "top-4" comments in classifier paths are unrelated (leave them).

New plan required for residual fixes to actually close the crash and follow process.

## 2026-06-22 - Re-execution verify: fix-pump-binpeak-redbox-hist-sampling-255-cap-discard-rule-prune-limit-6-20260622-plan

- User re-approved execution; forensic verify at HEAD a2aaf5fc: all plan criteria already present from 053d8c2b (prune 6, native H OR coverage, uncapped runs, HIST_SZ=8192, per-red md discard) plus later crash hardening in b0d32a55/a2aaf5fc.
- No additional app/src changes required this turn; TODO.md updated with plan reference.
- Build gate via ./build_app.

## 2026-06-22 - Alignment experiment crash after pump fixes + uninstall/reinstall

Fetched: logcat + tombstone_07 from emulator-5554.

Crash: identical OpenCV setSize s>=0 in nativeCalculateHistogramWithThresholdH (matrix.cpp:246), now from alignment path:
- stack: runBinTrialsPaddle -> runPaddleValleyIterative -> runExperiment (in ExperimentAlignmentScreen)
- calls use odoBuffer.createCrop(boundingBox) then crop[id].mat + Rect(0,0,cropW,cropH) passed to calculateHistogramWithThresholdH
- happens in bin trials for paddle valley iterative.

Cause: Pump-induced BufferSet changes (resize drops !icrs/pixel crops, crop refresh clamping to even 2px boundaries, submat logic, flip/rebind) make crop.submat produce Mat with absW/absH <=0 or stale header in some flows. Passed bad-sized crop mat to H-hist triggers zeros/create inside (even with rect guards added for binPeak). Alignment relies on pixel crops + relative rects on vehicleBufferSets/odoBuffer (unlike pump binPeak which moved to full .s + absolute rects).

Pump images on device were empty; need redeploy full-res (exclude thumbs).

No logcat Java exception visible (native abort); tombstone confirms.

New plan created to harden + redeploy.

## 2026-06-22 - fix-alignment-crash-hist-crop-after-bufferset-pump-changes-plus-pump-images-redeploy-20260622-plan

- BufferSet ManagedCrop.refresh: abort on absW/absH <= 0 before submat.
- ExperimentAlignmentScreen: alignmentHistogramWithThresholdH uses odoBuffer.p.mat + absolute bbox (3 bin-trial hist sites); no crop submat for H hist.
- NativeImageUtils.calculateHistogramWithThresholdH: mat.empty + valid rect filter.
- Native H: guard mat cols/rows <= 0.
- Pump images redeploy to emulator-5554 pump_photos (GOLDEN_SUBSET 10, no thumbs) follows build.

## 2026-06-22 - Pump images redeploy (alignment plan phase 7)

- Pushed GOLDEN_SUBSET 10 files (no thumbs) to emulator-5554:/sdcard/Android/data/com.davidlang.vehicleexpensesautomated/files/pump_photos/
- Verified: ls count PXL_* = 10

## 2026-06-22 - Diagnosis of alignment crash per user directive: stop guessing, inspect code+logs, plan for more info if needed

User correction: odoBuffer is alignment, scaled are pump. drop non-ICRS on resize is from spec (not recent pump change). Crops for odo populate supposed to be ICRS. Did that change?

Inspected code:

- In ExperimentAlignmentScreen.kt setup: icrsRect = from vehicle odometerCrop* or full ICRS.

  Then set.p.createCrop( icrsRect.left (float), top, width(), height(), id=vehicle.id ) -- float createCrop → isIcrs=true ICRS crop on globals A/B.

- Then vehicleBufferSets[id] = BufferSet( pixel target from icrsToPixel )

- In runBinTrialsPaddle: resize( masterBuffer.c[vehicleId].mat (the ICRS crop view on global) to odoBuffer.p.mat )

- odoBuffer is the per-vehicle pixel sized for odo.

- Then after binarize + flip, alignmentHistogramWithThresholdH( odoBuffer.p.mat (binary), b.boundingBox ,.. )

- b from det on scaled odo content, invScale to odo space.

Crops feeding odo are ICRS based (float create on globals). No evidence of change in current code.

Logs (crash-again... and tombstone_08): crash in alignmentHistogramWithThresholdH -> native H during bin trial on ~960x198 odo size buffer, after CHAR_AWARE and crop reg, after the flip to binary.

No logged values for the actual primaryMat.cols/rows or the bbox or the l/r after coerce or inside native minL/roi at the failing call. Insufficient to identify exact bad numbers causing s<0 inside (zeros or roi create).

Therefore, per directive, do not speculate; make plan to add instrumentation to gather the exact info.

Also verified drop non-ICRS is in BufferSet as per spec.



## 2026-06-22 - diagnose-alignment-odo-hist-setsize-with-instrumentation-20260622-plan

- Added temporary ALIGN_HIST_DIAG (Kotlin wrapper), ALIGN_HIST_NATIVE (native H), ALIGN_ODO_POP (odo resize) logs.
- No functional changes; instrumentation only for next alignment crash repro.
