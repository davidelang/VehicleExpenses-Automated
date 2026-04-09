# Vehicle Expenses Automated — TODO List

## Critical Recovery (Re-implement lost work)
- **Identity & Matching:**
  - [ ] **Tiered Identity Module:** Replace weighted consensus with hierarchical logic (Veto -> Histogram -> Agreement -> Spatial -> Ask User).
  - [ ] **Dynamic Veto Frequency Filter:** Implement global frequency check to disqualify common words (e.g., "mph") from being anchors. Fixes "Honda Bias".
  - [ ] **Strict "No Match" Logic:** Ensure "No match" is reported if Tiers 1-3 fail or all are vetoed.
- **OCR Engines:**
  - [ ] **PaddleOCR (PP-OCRv4) via TFLite:** Re-integrate Paddle models using stable TFLite runtime.
- **Deep Trace Report (Phased):**
  - [x] **Phase 1: Foundation:** New column layout (Global Discovery, Match/Align, 3x Odo Traces per vehicle, Summary). Includes timing, specific veto words, and cached reference data. Flex-ready for engine/strategy changes.
  - [ ] **Phase 2: Deep OCR Trace:** Expand Global and Odo columns to show pre-processing steps (Grayscale, Bilateral, CLAHE, OTSU) + OCR grid for each.
  - [ ] **Phase 3: Hub + Needle:** Implement needle-based rotational correction and add as a 3rd alignment strategy.
- **Location Features:**
  - [ ] **Automated Gas Station Lookup:** Re-implement `LocationLookupWorker` for background geocoding.
  - [ ] **Location Parity in Sync:** Restore lat/long/location handling in `CsvManager` and `GoogleSheetsClient`.

## Bug Fixes / Tasks
- [ ] **Reference Image Rendering:** Fix missing "Other Text" (blue) crop boxes in reference dash previews. Currently, only the red odometer box is shown.

## High Priority (next)
- Improve GlobalWordCounts (IDF) calculation:
  - Extract text and compute local word counts per-image as they are loaded/updated in the Manage Vehicles page.
  - When a vehicle is saved or created, re-process the aggregate global word counts across all vehicles.
  - Store results in GlobalMetadata or similar for efficient access during matching.

## Engineering Mandates (New)
- **Report Flexibility:** All OCR, matching, and alignment reporting must be dynamic. Do not assume a fixed number of engines or strategies.
- **Memory Safety:** Every intermediate bitmap must be recycled immediately after use.
- **Traceability:** HTML and JSON reports must maintain parity and provide a frame-by-frame trace of how data is processed.

## Completed
- [1511259] Stability & Report UI Fix: Implement bitmap recycling and fix quality crash
- [82592c0] EXIF Location Extraction (Centralized in OdometerOcrUtils)
- [82592c0] Final Visibility & Rescue Build (ORB Rescue fallbacks)

Last updated: 2026-04-09
