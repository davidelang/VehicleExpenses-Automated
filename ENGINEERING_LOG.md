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

## [2026-06-21] - Paddle INT8 Rebuild & App Migration (plan 20260620)
- **Activity:** Isolated INT8 patches (`patches-int8/`, `apply_int8_patches.sh`, `Dockerfile.int8`); app migration to XOR int8 input (`q=(b^128)`), BufferSet p→s, tier int8 buffers, ShareExternalMemory bind.
- **Sandbox:** P1 `keep_quantized_weights`, P2 `analytic_input_quant_pass` on branch `pr-int8-activation-input`; `optimize_mono_int8_models.sh`; call-site audit at `dev-ai-interaction/research/paddle-int8-callsite-audit.md`.
- **App:** `nativeQuantizeMonoToInt8`, `nativeBindInputInt8`, `BufferSet.quantizeMonoInputToScratch`, `sharedTierInt8Buffers`, `*_int8_*.nb` assets; float det/rec staging buffers removed.
- **Build tag:** `int8-paddle-processing-start-5-gf277eb38`
- **Pending:** Part B Docker rebuild (`paddle-build-int8-20.04`) for true INT8 kernels + jar `setKeepQuantizedWeights`; C2 full opt conversion when INT8 image available.

## [2026-06-21] - Paddle INT8 Retry (Docker restored)
- **Docker:** `paddle-build-int8-20.04` rebuilt; B3–B5 PASSED (linux opt, arm64, armv7); B6 x86_64 blocked (libc++/protobuf linker ABI).
- **Patches-int8 fixes:** `LOD_TENSOR` in `light_api.cc`; analytic `ResetOp` copy; x86 SSE `elementwise_common_broadcast_config.h`; `prepare_thirdparty` via mounted build scripts.
- **C2:** True INT8 `.nb` via `opt_linux_x86_int8` (models no longer float copies).
- **D7:** Deployed arm64-v8a + armeabi-v7a `libpaddle_lite_jni.so`, `PaddlePredictor.jar` (`setKeepQuantizedWeights`), direct Kotlin call (no reflection).
- **Build tag:** `int8-paddle-processing-start-8-g23d8e306` (D7 phase).

## [2026-06-21] - Paddle INT8 Recovery & Completion
- **Recovery plan:** `paddle-int8-recovery-and-completion-20260621-plan.md`; supersedes 20260620 migration plan execution.
- **Artifacts:** All ABIs jni + INT8 `.nb` deployed; x86_64 build DONE (`ce3fcf60…`); `verify_int8_deployment.sh` PASS (sandbox forensic gate).
- **Crash fix:** `nativeBindInputInt8` now derefs `cppTensorPointer` as `unique_ptr<Tensor>*` (emulator SIGSEGV on Alignment/Pump `detect()`).
- **Docs:** `project-facts.md` documentation layers table; device validation steps in sandbox; float `.nb` retained on disk (not loaded).
- **Build tag:** `int8-paddle-processing-start-16-ge09a3268`

## [Legacy Completion Migrated from TODO.md]
- [b860046] Fix: Correct HTML report file size rotation logic to accurately count bytes
- [2f1a582] Deep Trace Phase 2d: Finalize report to include missing global discovery images, method scores/times, and tier reached
- [9a07669] Deep Trace Phase 2c: Implement 5-step OCR trace (Raw, Gray, Bile, CLAHE, OTSU) across 3 engines with timings

## [2026-06-21] - INT8 Tier Buffer Restore (purge pump mixes)
- **Plan:** `restore-int8-tier-buffers-identical-to-original-float-logic-purge-extra-mixes-20260621-plan.md`
- **Change:** Restored `sharedTierBuffers` tier selection + get/clear/quantize/bind flow identical to branch-start float logic; removed x86-608 bump, PumpCheckpoint logs, detSet special-casing, tier-4000 scale, and PumpScreen dedupe extras. Kept only JSON 256MB/512MB ceiling + `android:largeHeap=true`.
- **Build tag:** `int8-paddle-processing/builds` → `0e353cd8`

## 2026-06-21 - test

## 2026-06-21 - dlang sudo test2

## 2026-06-21 - dlang sudo test2

## 2026-06-21 - dlang sudo test2

## 2026-06-21 - dlang sudo test2

## 2026-06-22 - Golden pump photos deploy helper

- Created `dev-ai-interaction/scripts/deploy-golden-pump-photos.sh` to push the exact 10 GOLDEN_SUBSET pump photos to `getExternalFilesDir(null)/pump_photos` on device after `./deploy`.
- Verified on Pixel wireless adb: all 10 golden files present with positive size via `run-as com.davidlang.vehicleexpensesautomated ls files/pump_photos/`.
- No changes to `./deploy` or logcat handling.

## 2026-06-22 - INT8 arm JNI SONAME Docker build fix (in progress)

Executing plan fix-int8-paddle-arm-jni-soname-in-docker-build-20260622: added patchelf + set_jni_soname.sh to paddle-build Dockerfile.int8/apply_int8_patches.sh; rebuilt paddle-build-int8-20.04 image; arm64 int8 rebuild running.

## 2026-06-22 - INT8 arm64 JNI SONAME Docker build fix (complete)

Rebuilt paddle-build-int8-20.04 with patchelf; arm64 int8 full_publish + set_jni_soname.sh.
Verified output + jniLibs SONAME [libpaddle_lite_jni.so]; libnative_ocr.so DT_NEEDED now bare soname.
Tag: int8-paddle-processing-start-55-g120eb3a0. armv7 jniLibs not rebuilt this turn.

## 2026-06-23 - int8 crash diagnosis after SONAME version (emulator x86_64 SIGSEGV in DefaultDispatch after crop register)

- User reported: version from soname fix (fix-armeabi-v7a-paddle-jni-soname-and-validate-all-abis-20260623-plan.md + compliance at tag 3a7e566d) still crashes; also tracked jni .so not committed.
- Uncommitted tracked: M app/src/main/jniLibs/{arm64,armeabi-v7a,x86_64}/libpaddle_lite_jni.so (the copies from Docker output during phases 5-7; last git commit is unrelated gradle chore).
- get-builds-tag still requires a ./build_app to seed int8-paddle-processing/builds.
- Fetched logs via adb (2 devices: wireless + emulator-5554; used -s to target).
- Crash: on emulator (x86_64). Process uptime 7s. SIGSEGV (code 1 SEGV_MAPERR) fault addr 0x4a251000 in tid 16323 (DefaultDispatch). Tombstone_18 written. Backtrace only 1 frame (pc 0x123 in anon mapping; stripped libs).
- Log sequence immediately prior (05:17:33-39):
  - Paddle global rigid buffers + multiple BufferSet nativeSetup (4000x3072 globals from recent default-buffer change, tiers 224/608/..., small 320x48 512x128).
  - libpaddle_lite_jni.so and libnative_ocr.so loaded successfully (no dlopen/UnsatisfiedLink — SONAME fix passed load).
  - "Registered crop at 1", "2" then later "Registered crop at 1000", "1001".
  - Then F libc fatal in DefaultDispatch.
- "Registered crop" site: BufferSet.kt:153 in registerCrop (called after createCrop for odo/other or alignment crops; high cids 1000/1001 appear in pump/odo paths).
- Loads succeeded, crash is deeper in int8 execution (quantizeMonoHandleToInt8 / nativeBindInputInt8 / predictor use on dispatch thread after crop setup).
- x86_64 libpaddle date remains older (Jun 21) vs armv7 Jun 23.
- No pump JSON/reports pulled (run-as/perms on device).
- Physical arm64 device log dump did not contain matching crash (may not have been triggered or different behavior).
- Likely why: latent bug exposed by buffer default to 4080x3072 + tier/crop registration + int8 tensor bind/quantize path (size/pointer mismatch or use of disarmed crop handle on certain inputs). Not the original SONAME load error.
- Compliance for soname plan claimed PASS on build verification (readelf bare NEEDED), but no device logcat in that turn.
- Recorded per mandate (used append wrapper only).


## 2026-06-23 - Answers to user: JNI must be committed + crashing crop was ID 1001

1. The new (SONAME-fixed) versions of libpaddle_lite_jni.so are in the working tree (post-fix mtimes/sizes) but show as M (modified, not committed) in git status. git diff shows binary changes vs index. They are required for builds to pick up the fixed JNI. Last relevant commits were the plan phases. To get them into the repo, they must be committed via an approved plan execution using ./build_app on the jni files + TODO.md.

2. Crashing crop: nextCropId=1000 (auto when no explicit id=). In the exact repro (alignment exp on 2-item FAILING_SUBSET):
   - Registered 1 and 2 (explicit ref vehicle.id using DB crop rects loaded with ?:0f/?:1f).
   - Then (after 1088x358 etc buffer setups + native_ocr load on DefaultDispatch): Registered 1000, then 1001.
   - Crash immediately after.
   The crop requested that caused the crash was the auto-ID **1001**.
   Coordinates: not logged at creation (only cid logged on register). The createCrop calls that consumed auto-IDs 1000/1001 in the FAILING_SUBSET alignment query-photo processing path (ExperimentAlignmentScreen + related Ocr paths) were ones like:
   - experimentDetSet512x128.createCrop(0, 0, fw, fh) or experimentRecSet...createCrop(0/4,4, ew, eh)  -- full small buffers
   - odoBuffer.createCrop(bbox left/top/w/h) from redbox detections
   - odoBuffer.createCrop(sL, sT, sR-sL, sB-sT) from source sub-rects
   The numeric values came from either fixed small rects or from the per-photo odo region (sized from the DB vehicle crop rects for the 2 photos, using the ?:0/1f loading + icrsToPixel) or detection bboxes on it.
   See BufferSet nextCropId=1000, registerCrop, the alignment ref+query crop setup code (repeated l=Left?:0f, r=Right?:1f patterns), and the calls in ExperimentAlignmentScreen around lines 1025+, 1056+, 1103+, 1264+, 1922+, 1945+ etc.



## 2026-06-29 - Execution Start: Paddle OCR Pipeline Optimizations (Stage 1)

- Starting execution of Stage 1 optimizations described in dev-ai-interaction/plans/ocr-optimization-plan.md.
- Objectives: Implement native C++ CTC greedy decoding and zero-copy INT8 thresholding.

## 2026-06-29 - Revised Stage 1 OCR Execution Plan Approved

- Starting implementation of revised JNI direct buffer scanning & split detect methods.

## 2026-06-29 - Execute INT8 Heatmap Copying Crash Fix

- Starting execution for plan `int8-heatmap-crash-fix-20260629`.
- Baseline tag: `int8-paddle-processing-start-893-g716623f5`.
- Objective: Default copyHeatmap to false in NativePaddleEngine.detect and return null to prevent native getFloatData() SIGSEGV on INT8 quantized tensors.

## 2026-06-29 - Execute JNI IntArray Transport & 256-Bucket Histogram

- Starting execution for plan `int8-heatmap-ints-histogram-20260629`.
- Baseline tag: `int8-paddle-processing-start-894-gb70f5f3c`.
- Objective: Convert nativeProcessHeatmap JNI output to IntArray, pack integer coordinates/scaled confidence, and implement a 256-bucket histogram.

## 2026-06-29 - Execute Memory Padding & System Device Tags

- Starting execution for plan `int8-remediation-padding-and-system-tag-20260629`.
- Baseline tag: `int8-paddle-processing-start-896-g013e76a3`.
- Objective: Implement 64-element trailing padding to JNI intArrays for safe SIMD vectorization, and add device system identification tags to JSON/HTML reports.

## 2026-06-29 - Execute tempBox Elimination for SIMD Safety

- Starting execution for plan `int8-loop-tempbox-elimination-20260629`.
- Baseline tag: `int8-paddle-processing-start-897-gb0f7cc89`.
- Objective: Eliminate intermediate tempBox allocation/copy in NativePaddleEngine.kt and loop directly over padded nativeRes to leverage memory safety cushion against JIT vectorization crashes.

## 2026-06-29 - Execute Array Copy & Unrolled Coordinates Unpacker

- Starting execution for plan `int8-arraycopy-unrolled-remediation-20260629`.
- Baseline tag: `int8-paddle-processing-start-898-g23d9b11f`.
- Objective: Implement boundary-safe System.arraycopy and unrolled float initialization in NativePaddleEngine.kt to completely bypass JIT compiler loop vectorization page-fault crashes.

## 2026-06-29 - Execute Zero-Waste Multi-Exit Loop Guard

- Starting execution for plan `int8-multi-exit-remediation-20260629`.
- Baseline tag: `int8-paddle-processing-start-899-gfc6563ce`.
- Objective: Revert JNI padding back to exactly results.size() and implement Kotlin multi-exit loop guards in NativePaddleEngine.kt to prevent JIT speculative vectorization page-fault crashes.

## 2026-06-29 - Execute IntBuffer Wrapping for Safe Unpacking

- Starting execution for plan `int8-intbuffer-remediation-20260629`.
- Baseline tag: `int8-paddle-processing-start-900-ga50d06e0`.
- Objective: Implement IntBuffer wrapping in NativePaddleEngine.kt to force scalar sequential execution and guarantee bounds-safety.

## 2026-06-29 - Execute Sequential while Loop + 64-Byte Guard Padding

- Starting execution for plan `int8-final-remediation-20260629`.
- Baseline tag: `int8-paddle-processing-start-901-g2c2ade86`.
- Objective: Implement 16-element (64-byte) JNI array padding and sequential while loop unpacking in NativePaddleEngine.kt to guarantee safe scalar execution.

## 2026-06-29 - Execute Diagnostic Logging Plan

- Starting execution for plan `int8-diagnostic-logging-20260629`.
- Baseline tag: `int8-paddle-processing-start-902-gb3794767`.
- Objective: Instrument C++ processHeatmap and Kotlin NativePaddleEngine with logging to find the exact crash location, array size, and division remainder.

## 2026-06-29 - Execute Scalar-Only Histogram Copy

- Starting execution for plan `int8-scalar-copy-remediation-20260629`.
- Baseline tag: `int8-paddle-processing-start-905-g6ef1ad66`.
- Objective: Replace System.arraycopy with a scalar sequential while loop for the histogram in NativePaddleEngine.kt to prevent JIT pre-fetching page faults.

## 2026-06-29 - Execute Explicit Bounds Diagnostics Plan

- Starting execution for plan `int8-explicit-bounds-diagnostics-20260629`.
- Baseline tag: `int8-paddle-processing-start-906-g16e84c5d`.
- Objective: Implement exhaustive boundary logging inside JNI processHeatmap (C++) and NativePaddleEngine unpack/copy loops (Kotlin) to capture raw sizes and index operations at runtime.

## 2026-06-30 - Execute Explicit Diagnostics with Log Flushing Delays Plan

- Starting execution for plan `int8-diagnostics-sleep-20260629`.
- Baseline tag: `int8-paddle-processing-start-907-g0563bbd5`.
- Objective: Implement explicit Thread.sleep delays after log statements in Kotlin to guarantee logcat flushing before process termination.

## 2026-06-30 - Execute JNI Split Array Transport with VarHandle Fences Plan

- Starting execution for plan `int8-jni-split-remediation-20260630`.
- Baseline tag: `int8-paddle-processing-start-908-g8dee0d60`.
- Objective: Implement nativeProcessHeatmapSplit returning a split Object array (jfloatArray coords, jfloatArray confs, jintArray hist) from C++ JNI, and consume it in Kotlin using VarHandle.fullFence() barriers to flush all diagnostics.

## 2026-06-30 - Execute JNI C++ Diagnostics and POSIX sleeps

- Starting execution for JNI C++ logs in `nativeProcessHeatmapSplit`.
- Objective: Implement __android_log_print and POSIX usleep inside NativeImageUtils.cpp to capture exact data sizes and coordinates directly from JNI and flush them to logcat.

## 2026-06-30 - Execute Kotlin-side Line-by-Line Diagnostics Plan

- Starting execution for plan `int8-jni-split-remediation-20260630`.
- Objective: Implement detailed Kotlin-side log prints, VarHandle.fullFence() barriers, and Thread.sleep(200) calls before and after every execution step in detect and detectInference to pinpoint the exact crash location and print returned array sizes.

## 2026-06-30 - Execute x86 float→long-lived int8 copy (no paddle touch)

- Starting execution for plan fix-x86-float-output-copy-to-longlived-int8-no-paddle-involvement-20260630-102641-plan.md
- Baseline tag: f6b6ea8a
- Objective: Remove bindOutputInt8 on x86; post-process from long-lived int8 via processHeatmapFromInt8Buffer

## 2026-06-30 - Complete x86 long-lived int8 copy (no paddle touch)

- Completed plan fix-x86-float-output-copy-to-longlived-int8-no-paddle-involvement-20260630-102641-plan.md
- Final tag pending phase 5 build
- Changes: removed bindOutputInt8 from x86 wrapper; detect/detectMat use processHeatmapFromInt8Buffer; C++ direct int8 consumer

## 2026-06-30 - Execution start: fix-x86-detect-helper-only-populates-int8-then-uniform-processing-20260630-113237-plan.md

## 2026-06-30 - Uniform int8 detect plan resume (phases 2-5)

- Resume after Phase 1 commit 1890ed19 (helper side-effect only)
- JNI copyTensorInt8ToBuffer + dequantHeatmapInt8ToFloat ready in working tree
- Next: ARM output copy, uniform int8 processing, dequant copyHeatmap

## 2026-06-30 - Phase 2 complete (tag 1f0429a8)

- ARM kInt8 output copy JNI + detect/detectMat population
- x86 helper unchanged; builds tag updated

## 2026-06-30 - Uniform int8 detect plan phases 3-5 complete

- Phase 3 (c66c07f4): uniform processHeatmapFromInt8Buffer both arches
- Phase 4 (1835bd76): DET_HEATMAP_INT8_U_THRESHOLD=4; copyHeatmap via dequant
- Phase 5: audit — no floatData/processHeatmap arch branch in detect paths; rec unchanged

## 2026-06-30 - Execute ARM direct int8 output bind plan (baseline 68c9b094)

- Plan: fix-arm-direct-int8-write-no-copy-plus-proper-arm-emulator-20260630-130648
- Goal: ARM bindOutputInt8 before run (no post-run copy); x86 helper unchanged; emulator-5556 arm64 AVD notes

## 2026-06-30 - Phase 1 complete (tag 96a9855c)

- ARM: bindOutputInt8 before run on sharedTierBuffers/sharedMaxInt8Buffer
- Removed copyTensorInt8ToBuffer from detect paths entirely
- Phases 2-3 forensic: x86 helper Unit-only; uniform processHeatmapFromInt8Buffer confirmed

## 2026-06-30 - Phase 3 verified (uniform int8 processing)

- detect/detectMat always call processHeatmapFromInt8Buffer with DET_HEATMAP_INT8_FLOAT_THRESHOLD
- No arch branch on post-processing call sites

## 2026-06-30 - Phase 4: path diags + emulator-5556 arm64 setup doc

- detect/detectMat log arm direct-bind vs x86 float+helper paths
- Sandbox doc: dev-ai-interaction/emulator-5556-armv8-setup.md

## 2026-06-30 - Execute ARM direct-bind crash fix plan (baseline a38ae6e3)

- Plan: fix-arm-phone-crash-after-direct-bind-plus-x86-zero-20260630-150000
- Root cause hypothesis: tier detect() reuses same buf for input+output bind on ARM; x86 zero heatmap SEGV in CC/downstream

## 2026-06-30 - ARM phone crash + x86 zero fix complete (phases 1-3)

- Phase 1 (e4a47fdb): tier detect() uses temp input buf + long-lived output bind on ARM
- Phase 2 (d450f2a7): int u_threshold CC path; safe empty result when uMax < threshold
- Phase 3 (16d616ec): quantize u_val min/max diags; pump zero-box guards
- Phone runtime verification pending device connection

## 2026-06-30 - Execute uint8 long-lived buffer plan (baseline 9eb991dc)

- Plan: fix-uint8-conversion-for-int8-buffer-20260630-151214
- Store uint8 0-255 in long-lived buf; ARM signed→uint8 after bind write; read without ^128

## 2026-06-30 - Phase 1 commit 57e02e83; rebuild after Java 17 toolchain fix

## 2026-06-30 - uint8 long-lived buffer plan complete (phases 1-3)

- Phase 1 (c5665db0): quantize stores uint8 q directly
- Phase 2 (2eea0dbe): ARM convertSignedInt8BufToUint8 after direct bind
- Phase 3 (c362e216): process/dequant read uint8 without ^128

## 2026-06-30 - Execute full tensor minmax diags + ARM output bind fix (baseline c9dc2f99)

- Plan: add-full-tensor-minmax-diagnostics-and-fix-arm-output-bind-crash-20260630-175835
- Phase 1: FLOAT_TENSOR_FULL + INT8_TENSOR_FULL logging
- Phase 2: remove bindOutputInt8 on detector output; copyTensorInt8ToBuffer post-run on ARM

## 2026-07-01 - Full tensor minmax + ARM output bind fix (phases 1-3)

- Phase 1 (c836b9ad): FLOAT_TENSOR_FULL + INT8_TENSOR_FULL diagnostics
- Phase 2 (6ce783cf): remove detector bindOutputInt8; ARM copyTensorInt8ToBuffer + convert
- Stale direct-bind comments removed; convert log updated

## 2026-06-30 - EXEC: fix-uniform-128-on-arm-post-copy-plus-low-float-range-emu-segv

- Phase 1 start: enhance ARM int8 copy/convert diagnostics; Phase 2: uniform/degenerate heatmap guards

## 2026-06-30 - fix-uniform-128 ARM kFloat output + degenerate heatmap guards

- copyTensorInt8ToBuffer: handle kFloat (prec=1) via quantize; kInt8 xor-in-place; return boolean
- processHeatmapFromInt8UData: treat uniform uMin==uMax as empty
- NativePaddleEngine ARM: precision-aware copy, skip separate convert step
- PumpCostVolUtils + ExperimentPumpScreen: zero-box, invalid-point, full-image degenerate guards

## 2026-06-30 - EXEC: no-fallbacks ARM force kInt8 + x86 uint8*255 + 256 hist

- Phase 1 start: forceOutputTensorInt8Precision, strip kFloat fallbacks in copy/resolve

## 2026-06-30 - no-fallbacks ARM force kInt8 + x86 uint8*255 + 256 hist

- Phase 1-3: forceOutputTensorInt8Precision before ARM run; loud errors on kFloat in copy/resolve
- x86 populateUint8FromFloat (f*255 no clamp); processHeatmap hist 256 buckets from uint8 u_val

## 2026-06-30 - no-fallbacks build commit 5e07c6bd

- Remaining cpp: loud kFloat errors in resolve/copy; forceOutputTensorInt8Precision JNI

## 2026-06-30 - EXEC: set-detect-filter-to-nonzero-no-low-bucket-ramp

- Phase 1 start: change detector uThreshold filter to nonzero (u_val > 0)

## 2026-06-30 - set-detect-filter nonzero (uThreshold=1)

- DET_HEATMAP_INT8_U_THRESHOLD 4→1; comment documents no-ramp in buckets 1-20
- Temp diag logLowBucketHistDiag for hist[1..20] in detect/detectMat

## 2026-06-30 - EXEC: measure CC internals to diagnose x86 SEGV with uThreshold=1

- Phase 1 start: on_pixels, numLabels, comp bbox, OOB diags in processHeatmapFromInt8UData

## 2026-06-30 - CC measurement diags for x86 SEGV diagnosis

- processHeatmapFromInt8UData: on_pixels, on_u_1_20/21_255, numLabels, comp l=1..5 bbox, OOB_BBOX/OOB_ACCESS logs

## 2026-07-01 - EXEC: minimal-1024-rec-processocr-plan

- Phase 1 start: read plan and locate rec/processOcr paths for 1024 tier

## 2026-07-01 - minimal 1024 rec processOcr pump redbox

- processOcr/processOcrNumeric: recBindDimensions(bindW/H from recSet when useRecSetPs)
- Pump redbox: crop(4,4,40h,aspect,no-320-cap), release crop, recognize(.p,recSet)
- ExperimentPumpScreen + PumpCostVolUtils + ocrBinPeak + performHunkRecognition updated
- Temp diag: PaddleDiag processOcr(useRecSetPs) bind=WxH

## 2026-07-01 - EXEC: simplify-rec-to-1024x48-everywhere-plus-256-hardcode-test

- Phase 1 start: unify global rec backing to 1024x48 + model init

## 2026-07-01 - Phase 1: unify global rec to 1024x48

- _recBufferSet, sharedRecInt8Buffer, recBindDimensions fallback, model resize 48x1024

## 2026-07-01 - Phase 2: remove pump dual 320 recset

- experimentRecSet320x48 removed; getFinal paths use experimentRecSet1024x48

## 2026-07-01 - Phase 3: 256 hardcode test in useRecSetPs

- quantizeMonoInputToScratch(256,48) + bindInputInt8(...,256,48) literal in processOcr/Numeric useRecSetPs branches only

## 2026-07-01 - EXEC: 256-lie-test-rec-int8-stride-consistent

- Phase 1 start: read plan and locate rec int8 stride sites

## 2026-07-01 - 256-lie rec int8 stride-consistent diagnostic

- quantizeMonoInputToScratch(1024,48) preserves 1024-byte row stride in backing
- bindInputInt8(...,256,48) declares narrow width (LIE test for float32-read hypothesis)
- Log: LIE declare=256x48 stride=1024 backing=WxH in processOcr + processOcrNumeric

## 2026-07-01 - EXEC: switch-rec-to-uint8-zero-copy-input

- Phase 1 start: add JNI uint8 bind + switch rec useRecSetPs to zero-copy

## 2026-07-01 - switch rec to uint8 zero-copy input

- nativeBindInputUInt8: kUInt8 + ShareExternalMemory JNI
- processOcr/Numeric useRecSetPs: recSet.p.raw + bindInputUInt8(bindW,bindH), no quantize
- BufferSet.Slice crop path: input.raw uint8 zero-copy; Mat keeps int8 fallback

## 2026-07-01 - build retry uint8 zero-copy

- retry after kotlin cache permission repair

## 2026-07-01 - uint8 zero-copy rec build gate

- nativeBindInputUInt8 + recSet.p.raw bind at full bindW x bindH

## 2026-07-01 - verify switch-rec-to-uint8-zero-copy plan complete

- Already at ef825df7: nativeBindInputUInt8 (kUInt8 ShareExternal)
- useRecSetPs: recSet.p.raw + bindInputUInt8(bindW,bindH), no quantize
- crop Slice: input.raw uint8 zero-copy; Mat path int8 fallback unchanged
- Re-execution: no further source changes required

## 2026-07-01 - Host paddle precision verification plan (phase 2)

- Executing `dev-ai-interaction/plans/host-paddle-precision-verification-and-smoke-tests-20260701-plan.md`.
- Phase 1–2: `research/opt-flags-for-precision.md` documents opt binary path, quant flags, conversion head findings (float32 heads; uint8 runtime only).
- Baseline tag: `int8-paddle-processing/builds` at acbdc9c7; builds require `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` (Java 25 lacks JAVA_COMPILER for Hilt).

## 2026-07-01 - Host paddle precision verification (phase 3)

- `host_precision_smoke.py --skip-lite`: OVERALL PASS on 3 images × 3 models (det, rec_v3, rec_numeric) × 3 input modes.
- Discrepancy signal: det float_x86 nz ~1.5–4.2% vs int8_xor ~1.0–2.7%; rec_v3 int8_xor collapses on one pump JPG (28% nz).
- Log: `dev-ai-interaction/research/smoke_phase3.log`.
