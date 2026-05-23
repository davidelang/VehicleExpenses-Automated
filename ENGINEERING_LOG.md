# Engineering Activity Log

This log tracks the implementation, refactoring, and deployment activities performed by the Application Engineer session.

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
