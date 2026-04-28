# Vehicle Expenses Automated — TODO Updated

## Infrastructure & Protocol
- [ ] **BUG:** Investigate/Fix inability to write to `dev-ai-interaction/plans` during plan mode.
- [ ] **PLAN:** Phase 2 - Paddle V3 General Model Integration & Greedy Numeric Decoding (linked to `dev-ai-interaction/plans/paddle-v3-greedy.md`)
- [ ] **PLAN:** Phase 3 - ImageNet (Norm B) vs Standard (Norm A) Normalization Sweep (linked to `dev-ai-interaction/plans/normalization-sweep.md`)

## Core Logic & Recovery
- **Identity & Matching:**
  - [ ] **Conflict Resolution Integration:** Connect the existing `ConflictResolutionScreen` to the identification flow for ambiguous results.

## Application Engineering
- **OCR Engine Implementation:**
  - [ ] **Advanced DB-PostProcess (Refinement 2.1) for NativePaddleEngine:**
    - [ ] Switch `boundingRect` to `minAreaRect` for tilted text support.
    - [ ] Implement `unclipBox` expansion logic (Ratio 1.5) to prevent digit clipping.
    - [ ] Implement `warpPerspective` for rotated text crop extraction.
    - [ ] Update Normalization constants to ImageNet standards (0.485/0.229).
  - [ ] **Final Validation (Phase 4):**
    - [ ] Benchmark accuracy vs. ML Kit using the 12-image test set.
    - [ ] Compare "Veto" accuracy between ML Kit and Paddle-Lite discovery.
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
- [ ] **Memory Safety:** Every intermediate bitmap must be recycled immediately after use.

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
