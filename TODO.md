## Protocol & AI Alignment
- [x] **DONE:** **Mandate Guardrail:** Explicitly anchor corrected mandates in TODO.md to prevent AI memory conflicts.
- **Rule 1:** Strict linear history (NO `git commit --amend`).
- **Rule 2:** 3-3-3 Strike System for build failures.
- **Rule 3:** Mandatory User Approval for resets past `builds`.
- **Rule 4:** Exclusive use of `./build_app` for commits/builds.

## Infrastructure & Protocol
- [x] **Documentation Restructuring & Policy Enforcement**
- [x] **Deskew Angle Normalization:** Implement normalization to [-45, 45] range in `OdometerOcrUtils` to unify ML Kit and Paddle outputs.
- [x] **BufferSet Rotation Logic Switch:** Switch the BufferSet pipeline to use Paddle Mono deskew results for rotation in `ExperimentAlignmentScreen`.
- [ ] **Deskew Forensic Logging:** Add text-block-level logging to the deskew stage and unify coordinate systems for forensic analysis.
- [ ] **ACTIVE: Isotropic Coordinate System (ICRS) Migration**
- [x] **DONE:** Finalize `docs/specs/ISOTROPIC_COORDINATE_SPEC.md`.
- [ ] **Phase 1: Codebase Audit & Dependency Mapping.** Identify all usages of `0-1f` normalization and landmark comparisons.
- [ ] **Phase 2: Transition Layer.** Implement ICRS math utilities and temporary conversion bridges.
- [ ] **Technical Reference Backlog**
- [ ] Create `docs/reference/DATABASE_SCHEMA.md`
- [ ] Create `docs/reference/SYNC_PROTOCOL.md`
- [ ] Create `docs/reference/OCR_ENGINE_STRATEGY.md`
- [ ] Create `docs/reference/ALIGNMENT_PIPELINE.md`
- [x] **DONE: Revert Deskew Resolution to 2048x2048.** Add 2048 tier to `NativePaddleEngine` and revert target size in `OdometerOcrUtils`.
- [x] **DONE: Fix NV21 Stability and Geometric Integrity.** Fix `nativeClear` to clear chroma to 128, rotate both Luma/Chroma, and fix `srcH` in `deskewMlKit`.
- [x] **DONE: Revert srcH Geometry for Deskew Parity.** Revert `deskewMlKit` to use buffer-relative height for candidate filtering to match working version (24ab86a).
- [x] **DONE: Restore Interpolation Parity and Implement Multi-Mode OCR.** Change flags to `INTER_LINEAR` for ML Kit parity and add constructor-based mode to `NativePaddleEngine`.
- [ ] **Naming Cleanup:** Rename `NativePaddleEngine` to `NativeVisionSystem` and `fullBufferSet` to `dashboardPool` (or similar) to accurately reflect their purpose.
- [ ] **Chain-of-Command Audit:** Repository-wide audit to eliminate variable-assignment anti-patterns (storing slices/handles in local vars) in the alignment experiment code.
- [ ] **Migrate NDK directory to Git Subproject (`ndk/`)**
- **Current State:** Using standalone `android-ndk-r20b/` directory.
- **Target State:** Use `ndk/` directory as a git subproject, locked to the `r20b` revision.
- **Implementation Details:**
- Add the NDK repository as a submodule/subproject at the root.
- Ensure the branch/tag checked out in `ndk/` matches the `r20b` version.
- Update `local.properties`, `build.gradle`, and any environment scripts (e.g., in `dev-ai-interaction/`) to point to the new `ndk/` path instead of `android-ndk-r20b/`.
- **Verification Checks:**
- **Directory Parity:** Confirm that `ndk/` contains the exact same toolchains, sysroots, and headers as the current `android-ndk-r20b/` (accounting for the single extra directory level).
- **JNI Build:** Run `./gradlew assembleDebug` to ensure the C++ components (`MemoryBridge.cpp`, etc.) compile without header or link errors.
- **Path Audit:** Search the entire project for hardcoded strings of `android-ndk-r20b` to ensure no build scripts are left pointing to the old path.
- **Git Hygiene:** Verify `.gitmodules` is correctly updated and the submodule is not "dirty."
- [ ] **BufferSet Audit:** Perform a repository-wide audit to eliminate variable-assignment anti-patterns (e.g., storing `manager.s` or `slice.yuv` into local variables). Enforce direct chaining from the root `BufferSet` to ensure state is resolved at use-time and prevent stale-pointer/affinity errors.
- [x] **DONE:** Phase 25.0 - Short-Run Rotation Refactor (Recovery).
- [x] **DONE:** Phase 25.1 - Extract Annotate & Compress Utilities.
- [x] **DONE:** Phase 25.2 - BufferSet Incremental Migration (Complete).
- [x] Step 4: Vehicle Bridge Migration.
- [x] Step 5: Dashboard Migration & Decommissioning.
- [x] Step 5.4: Smart Snapshot API & Visualization Improvement.
- [x] Step 5.5: Fix takeSnapshot destructive resize.
- [x] **DONE:** Phase 25.3 - BufferSet Pointer Invalidation Fix (JNI In-Place & Smart Proxy).
- [x] **DONE:** Remove OCR Gating and Unannotated Snapshots (Phase 25.4).
- [ ] **ACTIVE:** Host-Side PaddleOCR Simulator (`dev-ai-interaction/host_paddle_simulator.py`)
- [x] **DONE:** Implement baseline pipeline (Deskew -> Align -> Crop -> OCR).
- [x] **DONE:** Replicate Android "Embedded Preview" resolution logic for DNG processing.
- [x] **DONE:** Identify and replicate "Hybrid Geometry Bug" (Axis-specific movement vs. Width-normalized crops).
- [ ] Complete full accuracy validation sweep across 141 ground-truth images.
- [ ] Sync simulator with proposed Android geometry fixes (Square-Normalization for all axes).
- [ ] **BUG:** Normalization Discrepancy. Odometer crop coordinates (Y) in database appear to be normalized relative to Image Width rather than Height for some vehicles (e.g., Honda).
- [ ] Audit `ManageVehiclesScreen.kt` and `OdometerOcrUtils.kt` to ensure axis-specific normalization is strictly enforced repository-wide.
- [ ] If width-normalization for Y is intended (square coordinate system), provide technical justification and update `BUFFER_SET_SPEC.md` to document this non-standard behavior.
- [ ] **ACTIVE:** Phase 3: Total Scope Sanitization & Annotation Fix (Shadowing & Offset Resolution).
- [ ] **ACTIVE:** Phase 23 - Foundational YUV Handle Infrastructure (Standard Multi-Plane Descriptor).
- [x] **DONE:** Phase 11.5 - Protocol Hardening (Execution Rigor Mandates).
- [x] **DONE:** Phase 9 - Stateless Native Snapshot Utility.
- [ ] **BUG:** Investigate/Fix inability to write to `dev-ai-interaction/plans` during plan mode.
- [ ] **PLAN:** Phase 2 - Paddle V3 General Model Integration & Greedy Numeric Decoding (linked to `dev-ai-interaction/plans/paddle-v3-greedy.md`)

## Core Logic & Recovery
- **NativePaddleEngine Refactoring:**
- [ ] Extract hardcoded buffer/tensor dimensions (512x128, 2048x2048) into a unified configuration registry or array to ensure consistent initialization across pre-allocated buffers, PaddlePredictor input resizes, and runtime dimension checks, eliminating brittle `if/else` logic.
- [ ] **Phase 115: BufferSet Architectural Migration (Active)**
- [x] **Phase 1: Native Core Infrastructure**
- [x] **Phase 2: Kotlin API & Structural Verification**
- [x] **Phase 3: Recognition Pool Migration (320x48)**
- [x] **Phase 4: Discovery Pool Migration (512x128)**
- [x] **Phase 5: Vehicle-Specific Pool Swap (Complete)**
- [x] **DONE:** Phase 6: Managed Sub-Crops & Flip Optimization
- [ ] Phase 7: Final Migration & Decommissioning
- **OCR Engine Stabilization (Active):**
- [ ] **Phase 62: Robust Alignment & Contrast Stretching**
- [ ] Implement Width-Weighted Median Deskewing in `OdometerOcrUtils.kt`.
- [ ] **Robust Paddle Deskew (Refinement 2.2):** Resolve "0.0 degree" swamping in noisy dashboards. Implement Cluster-Based Voting. See handover: `dev-ai-interaction/plans/DESKEW_HANDOVER.md`
- [ ] Add rotational gating (±20°) to deskew logic.
- [x] **DONE:** Implement `applyContrastStretch` using OpenCV histogram analysis.
- [ ] Expand `runMultiStepOcr` refinement loop with S-75% and S-80% stages.
- [x] **DONE:** Add `deskew_data` forensic logging to JSON reports.
- [x] **DONE:** Include landmark `angle` in discovery results.
- [ ] Synchronize Python scripts for `.strip()` consistency.
- [ ] **High-Resolution DNG & Zero-Copy Ingestion (Option B Migration):**
- **Context:** `BitmapFactory` currently extracts low-res (680px) thumbnails from DNG files. We need full 12MP+ sensor data for accurate OCR. Transitioning to a YUV-primary path reduces memory usage by 75% compared to ARGB intermediate buffers.
- [x] **DONE:** Step 1: Create Golden Subset test harness and baseline resolution logging.
- [x] **DONE:** Step 2: Implement the YUV Bridge (Confirmed framework limitations for DNG sensor access).
- [ ] **ACTIVE:** Step 3: Native Ingestion & Universal YUV Adapter. See `dev-ai-interaction/plans/high-res-ingestion-step-3.md`
- [ ] Enhance `BufferSet.normalizeYUV()` with a high-performance C++ implementation to adapt any YUV format to the internal NV21 standard.
- [ ] Implement `nativeIngestJpegToYuv` using OpenCV `imread` (libjpeg-turbo) for zero-copy JPEG ingestion with in-place NV21 conversion.
- [ ] Implement `nativeTestImread` diagnostic to probe native DNG support.
- [x] **DONE:** Step 4: Integrate native RAW decoder (e.g. `LibRaw`) for zero-copy DNG development (with in-place NV21 conversion).
- [ ] **ACTIVE:** Step 5: Final Optimization & Normalization.
- [x] **DONE:** Ensure Chroma planes are neutralized (clearChroma) after high-fidelity ingestion.
- [ ] Unify reporting and metadata across ingestion dispatcher.
- [ ] **Sub-Pixel Landmark Refinement:**
- **Context:** Now that we have 12MP sensor data (up from 0.3MP thumbnails), the landmark detection precision is limited by our 0.0-1.0 normalized coordinate system.
- [ ] Implement a sub-pixel alignment stage that uses the high-resolution luma data to refine vehicle anchor points before crop extraction.
- [ ] **Optimization (Camera2 Borrowing):** Implement "Buffer Borrowing" strategy. Temporarily point `BufferSet.primary` to the hardware Y-plane from `ImageReader`, then release the hardware lock immediately after the first `BufferSet.flip()` to achieve zero-copy ingestion of 12MP data.
- [ ] **Optimization (Dual-Plane BufferSet):** Explore extending `BufferSet` to handle dual UV planes. A `.mono` accessor would return a static/virtual plane of 128s, while `.color` holds actual chroma. `clearColor()` would only affect the `.color` slice.
- **Identity & Matching:**
- [ ] **Multi-Scale Discovery:** Implement a multi-resolution discovery pipeline (e.g., full-res + 2048x2048) to resolve "landmark blindness" where ML Kit fails to detect small dashboard digits in high-resolution photos.
- [ ] **Conflict Resolution Integration:** Connect the existing `ConflictResolutionScreen` to the identification flow for ambiguous results.

## Application Engineering
- **OCR Engine Implementation:**
- [ ] **Advanced DB-PostProcess (Refinement 2.1) for NativePaddleEngine:**
- [ ] Switch `boundingRect` to `minAreaRect` for tilted text support.
- [ ] Implement `unclipBox` expansion logic (Ratio 1.5) to prevent digit clipping.
- [ ] Implement `warpPerspective` for rotated text crop extraction.
- [ ] Update Normalization constants to ImageNet standards (0.485/0.229).
- [x] **DONE:** Final Validation (Phase 4):
- [x] **DONE:** Benchmark accuracy vs. ML Kit (12-image test set / 140-image ground truth).
- [x] **DONE:** Compare "Veto" accuracy between ML Kit and Paddle-Lite discovery.
- [x] **Paddle Valley Mono Iterative Implementation:**
- [x] Implement `runPaddleValleyMonoIterative` mirroring `runMLKitIterative` architecture.
- [x] Use global `MemoryBridge` pools (`pool512x128`, `experimentRecBridge320x48`) for zero-allocation processing.
- [x] Implement sophisticated pixel-walking `expandByValleyStop` logic.
- [x] Add mandatory 4-pixel padding to recognition stage.
- [x] Integrate into experiment alignment screen and reports.
- [x] Strip debug information and excessive logging from the Paddle Lite `x86_64` Android build to reduce binary size (currently ~9.6MB).
- **Alignment & Processing:**
- [ ] **Multi-Strategy Voting:** Implement a voting mechanism to select the most consistent odometer result across all successful alignment strategies.
- [ ] **Dashboard Polarity:** Refine dashboard polarity detection to go beyond simple corner sampling (needed for Algorithm A/B fallback logic).
- [ ] **Adaptive Thresholding:** Investigate and resolve Otsu's threshold "blackout" issues where it occasionally erases all text in specific dash reports.

## Location & Sync
- [ ] **Location Lookup Worker:** Re-implement the background geocoding worker for automated gas station identification. (Currently missing from codebase).
- [ ] **Sync Parity:** Update `CsvManager` and `GoogleSheetsClient` to handle the latitude, longitude, and formatted address fields.

## Future Features & Integrations
- [ ] **Expense Reports & Receipts:** Take a picture of the receipt and store it for future reference. Attempt to parse the receipt, store name (may also be discovered by EXIF lat/long but only if it maps to a repair shop, as receipts may be photographed at a later time and location), cost, and line items.
- [ ] **ODB-II Integration:** Integration for live odometer reading.
- [ ] **Cloud Image Backup Options:** Add additional image backup options:
- [ ] Google Photos
- [ ] Amazon Photos
- [ ] Dropbox
- [ ] SSH to personal server
- [ ] HTTP to personal server (including CGI file to receive, save, and retrieve files)
- [ ] Investigate if there is a library that exposes all the various cloud storage options.
- [ ] **Advanced Reports and Charts:** Implement advanced reports and charts.
- [ ] **UI Polish:** Polish for dark mode / tablet / ensure it works on all size/resolution screens.
- [ ] **Improve reference dash photo setup UI:** Add odometer confirmation dialog.
- [ ] **Settings Toggle:** Add settings toggle for OCR confidence threshold.

## Refactoring & Technical Debt
- [ ] **Refactor `OdometerOcrUtils.kt`:** Decompose into smaller utilities (`BitmapMathUtils`, `OpenCvFilters`, `MlKitWrapper`, `DigitSanitizationUtils`).
- [ ] **Refactor `TfLiteOcrUtils.kt`:** Extract DBNet post-processing logic into a standalone `DbNetMath` object to separate algorithms from Android framework dependencies.

## Engineering Mandates (New)
- [ ] **Architecture:** Phase out `ALPHA_8` Bitmaps in favor of `NV21` (YUV) or `CV_8UC1` (Grayscale) native buffers. Use `MemoryBridge` raw memory access instead of hardware-mapped `ALPHA_8` to avoid HWUI/Skia stability issues (like the `makeImage` SIGSEGV). Avoid `ALPHA_8` for new features.
- [ ] **OCR:** Refactor `OdometerOcrUtils` and `DiscoveryOcrUtils` to source directly from native grayscale/YUV buffers for all engines.

## Completed / Historical
- [x] **Hierarchical Tiered Logic:** Move `performTier1Veto` to be the mandatory first step. If one vehicle survives, stop and declare winner.
- [x] **Strict "No Match" Logic:** Ensure "No match" is reported if Tiers 1-3 fail or all are vetoed.
- [x] Re-enable `NativePaddleEngine` in `OcrHarness`.
- [x] **Alignment Registry:** Refactor `runExperiment` to use the `AlignmentEngine` interface, enabling dynamic registration of ORB, Anchor-Tri, Hub, and future strategies.
- [x] **Report Flexibility:** All OCR, matching, and alignment reporting must be dynamic. Do not assume a fixed number of engines or strategies.
- [x] **Traceability:** HTML and JSON reports must maintain parity and provide a frame-by-frame trace of how data is processed.
- [x] **7-Segment Robustness:** Implemented `clean7SegmentDigits` with 180° rotation recovery and unified character remapping.
- [x] **Local Python Benchmarking:** Verified high-res Paddle performance on host Linux system.
- [x] **Deep Trace Phase 1:** Multi-column HTML/JSON reports with timing and veto diagnostics.
- [x] **Deep Trace Phase 2:** OCR pre-processing grid (CLAHE, OTSU, etc.) in experiment reports.
- [x] **Text-Based Leveling:** 0.2° threshold auto-rotation implemented.
- [x] **Reference Image Rendering:** Blue/Red box visibility fix.
- [x] **OCR Filtering:** Area-based block exclusion for reference photos.
- [x] **TFLite Strategy Evaluation:** Documented why Paddle-to-TFLite outperforms specialized native TFLite models.
- [x] **Tesseract Diagnostic:** Identified root cause of "garbage" output and proposed binarization/PSM fixes.

## Explicitly Rejected Ideas
- **Dynamic Veto Frequency Filter / Global IDF Word Filter:** A global registry complicates logic across multiple vehicles. Creating the list dynamically on-demand is cheap, and commonly duplicated high-value anchor words would be incorrectly subtracted.
- **Needle-Based Correction:** The current anchor-triangle approach accomplishes the same goal more reliably, without failing when the needle is cropped by the frame edges.
- **Unclip Box for Recognition:** Tried in Phase 2.1; found inferior to `expandByValleyStop` logic. Digit clipping was reduced but contrast-based expansion proved more robust across varying dashboard polarities.
- [ ] Implement 'Skip-Deskew' discovery pipeline: Bypass engines, resolve deskew/zoom/pan geometrically from landmark mapping.

# TODO: Gas Pump Extraction
- [x] DONE: Decouple `takeSnapshot` utility (See: plans/decouple-take-snapshot.md)
- [x] DONE: Implement Gas Pump Field Extraction Experiment on Android (See: dev-ai-interaction/plans/integrate-pump-extraction.md)
- [ ] Fix Pump Experiment reporting (Incremental JSON/HTML, Path Unification). (See: dev-ai-interaction/plans/pump-reporting-and-analysis.md)

# TODO: ICRS Alignment Core Migration
- [x] Phase 1: Define ICRS Architecture (See: docs/specs/ISOTROPIC_COORDINATE_SPEC.md)
- [x] Phase 2: Database Source of Truth Migration
- [x] Phase 3: Push Bridge Down to Alignment Core (See: dev-ai-interaction/plans/phase-3-unified-icrs-matrix.md)
- [x] Phase 4: Purge Bridges and Standardize BufferSet (See: dev-ai-interaction/plans/phase-4-bridge-decommissioning.md)
- [x] Phase 5: Final Verification & Cleanup (See: dev-ai-interaction/plans/phase-5-final-restoration.md)
- [x] Restore metadata reporting keys.
- [x] Serialize forensic winning_anchors to JSON.

# Phase 116: Pure Native A/B Testing
- [x] DONE: Phase 0: Repository Cleanup
- [x] DONE: Phase 1: Dual Native Architecture
- [x] DONE: Phase 2: Deskew & Early Rotation
- [x] DONE: Phase 3: Diverged Alignment & Refinement
- [x] DONE: Phase 4: Engine API Purge
- [x] DONE: Phase 5.1: Hollowing Out the Standard Path (Transitioning dependencies and reporting) (See: dev-ai-interaction/plans/phase-116-part-5-1-hollow-out.md)
- [x] DONE: Phase 5.2: Logic Removal (Stop Standard path execution)
- [x] DONE: Phase 5.3: ARGB Eradication (Buffer Removal & Dead Code Elimination) (See: dev-ai-interaction/plans/phase-116-part-5-3-argb-eradication.md)

# TODO: Protocol Alignment
- [x] DONE: Fix memory and GEMINI.md discrepancies (Strike System, No-Deploy, Plan Mode transitions). (See: dev-ai-interaction/plans/protocol-alignment.md)

# TODO: Gas Pump Field Extraction
- [x] DONE: Phase 1: Multi-Scale Discovery & Hunk Construction (See: dev-ai-interaction/plans/pump-multi-scale-discovery.md)
- [x] DONE: Phase 1.1: ICRS Coordinate Migration (See: dev-ai-interaction/plans/pump-icrs-migration.md)
- [x] DONE: Phase 2: Horizontal Stitching & Lane Grouping (See: dev-ai-interaction/plans/pump-lane-grouping.md)
- [/] IN PROGRESS: Phase 3 & 4: Lane-Pairing & Selection Heuristics (See: dev-ai-interaction/plans/pump-final-extraction.md)
- [ ] Phase 3.5: CDF Diagnostic & 40% Stretch (See: dev-ai-interaction/plans/pump-cdf-diagnostic.md)
- [ ] Phase 4: High-Resolution Extraction & Recognition

# TODO: OCR Performance Optimization
- [x] DONE: Implement `nativePopulateMonoTensor`.
- [x] DONE: Optimize `OdometerOcrUtils` to bypass Bitmap-to-Mat roundtrips during deskew.
- [x] DONE: Phase 117: Native Heatmap Fusing & Zero-Copy Math (See: dev-ai-interaction/plans/phase-117-native-heatmap-fusing.md)
- [ ] Parallelize `nativePopulateMonoTensor` using SIMD/OpenMP for 2048px tensors.
- [x] DONE: Implement microscopic instrumentation for JNI boundaries and inference stages.
- [ ] Offload Valley Expansion algorithm to C++ to eliminate JNI per-pixel overhead.

# TODO List
- [x] Phase 117 Reversion: Remove `nativeHeatmapToTextAreas` (See: plans/revert-117-native-heatmap.md)
- [x] Final diagnostic cleanup in `OdometerOcrUtils.kt`
- [x] Implement `automaticContrastStretch` and adaptive polarity (See: plans/adaptive-contrast-and-polarity.md)
- [x] Implement Global Clustering and Uniform-Color Valley (See: plans/global-clustering-and-uniform-valley.md)
- [x] Refine Pump Logic: Dual expansion engines and report cleanup (See: plans/dual-expansion-and-pump-refine.md)
- [x] Refactor Pump Experiment for N-Sets and Tree-Based Reporting (See: plans/n-sets-tree-reporting.md)
- [x] Refine Histogram Sensitivity (64 bins) and Debug Valley Expansion (See: plans/refine-hist-sensitivity-and-debug-expansion.md)
- [x] Expose Detection Speed and Refine Expansion Robustness (See: plans/speed-reporting-and-expansion-robustness.md)
- [x] Capacity Upgrade (2560x2560) and Logic Refinements (See: plans/upgrade-capacity-and-refine-logic.md)
- [x] Fix Pump Pipeline Expansion Integration (See: plans/pump-pipeline-expansion-fix-v2.md)
- [x] Implement Shoulder-Based Contrast and Simplify Expansion (See: plans/shoulder-contrast-and-expansion-simplify.md)
- [x] Diagnostic Color Tiers and Shoulder Contrast Activation (See: plans/diagnostic-tiers-and-shoulder-contrast.md)
- [x] Implement Pull-Back Expansion and Zero Padding (See: plans/pullback-zero-padding.md)
- [x] Unify Pull-Back Thresholds and Restore Forensic Trace (See: plans/unify-pullback-inputs.md)
- [x] Restore Capacity Baseline and Refine Retraction (See: plans/restore-capacity-and-refine-retraction.md)
- [x] Implement Adaptive Bimodal Expansion Engine (See: plans/bimodal-expansion-engine.md)
- [x] Fix Expansion Stop Logic and Trace Logging (See: plans/fix-trace-and-expansion-logic.md)
- [x] Restore forensic coordinate validation and diagnostic logging (See: plans/forensic-bounds-logging.md)
- [x] Fix Active Algorithm, ASCII Expansion, and Contrast Tweak (See: plans/fix-algorithm-and-expand-ascii.md)
- [x] Implement Variance-Based Expansion with Content Floor (See: plans/variance-expansion-with-floor.md)
- [x] Standardize Sandbox Paths to Absolute (See: plans/standardize-sandbox-paths.md)
- [x] Restore Lost Operational Mandates (See: plans/restore-mandates.md)
- [ ] Height-Relative Expansion, Robust Mapping, and Raw Consolidation (See: plans/height-relative-expansion.md)
- [ ] TODO: Investigate bimodal brightness-based stop logic for glare/shadow (e.g., look for brightness drop/climb)
- [ ] ICRS Migration and Legacy Decommissioning: Phase 1 Bridge & Safety (See: plans/icrs-migration-and-decommissioning.md) [IN PROGRESS]

