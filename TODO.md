# Vehicle Expenses Automated — TODO List

## Critical Recovery (Re-implement lost work)
- **Identity & Matching:**
  - [ ] **Tiered Identity Module:** Replace weighted consensus with hierarchical logic (Veto -> Histogram -> Agreement -> Spatial -> Ask User).
  - [ ] **Dynamic Veto Frequency Filter:** Implement global frequency check to disqualify common words (e.g., "mph") from being anchors. Fixes "Honda Bias".
  - [ ] **Strict "No Match" Logic:** Ensure "No match" is reported if Tiers 1-3 fail or all are vetoed.
- **OCR Engines:**
  - [ ] **PaddleOCR (PP-OCRv4) via TFLite:** Re-integrate Paddle models using stable TFLite runtime.
- **Experiment Harness:**
  - [ ] **Visibility:** Add real-time granular progress logging (e.g., "Matching vs Honda...", "OCR on crop...").
- **Location Features:**
  - [ ] **Automated Gas Station Lookup:** Re-implement `LocationLookupWorker` for background geocoding.
  - [ ] **Location Parity in Sync:** Restore lat/long/location handling in `CsvManager` and `GoogleSheetsClient`.

## High Priority (next)
- Improve GlobalWordCounts (IDF) calculation:
  - Extract text and compute local word counts per-image as they are loaded/updated in the Manage Vehicles page.
  - When a vehicle is saved or created, re-process the aggregate global word counts across all vehicles.
  - Store results in GlobalMetadata or similar for efficient access during matching.


## Medium Priority
- EXIF Location Extraction:
  - Extract lat/long from image EXIF data when recording a fillup.
  - Store lat/long in the database.
  - Add a "location" text field to the database.
  - Update impacted areas: Room schema, Quick Fill page, Import page, Google Sheets sync, and CSV import/export.


## Low Priority / Future
- Landmark crop box UI improvements:
  - Change button text to "Highlight other parts of the image that may change"
  - Allow clearing all boxes, clearing the last box, saving the current box (which starts a new box)
  - When finished, the landmark area needs to be saved
- ODB-II integration for live odometer.
- Unit selection in setup (override units for fill, normalize when saving/syncing)
- Additional image backup options (Google Photos, Amazon Photos, Dropbox, SSH, HTTP, etc.)
  - Full Amazon Photos API integration (authenticated two-way: download shared album for testing + upload new photos to album)
- Preferences screen (OCR thresholds, storage backends, sync settings)
- Advanced reports and charts
- UI polish for dark mode / tablet

## Completed
- [1511259] Stability & Report UI Fix: Implement bitmap recycling and fix quality crash
- [82592c0] EXIF Location Extraction (Centralized in OdometerOcrUtils)
- [82592c0] Final Visibility & Rescue Build (ORB Rescue fallbacks)
- Automatic OCR on every photo capture
- Camera-first flow + Gallery-only import
- Reference dash photo auto-matching
- Full crop editing UI for odometer (drag any corner, clear/replace, live preview)
- OCR debug + confirmation dialogs
- New-vehicle creation flow
- Alignment Experiment screen with live progress and ZIP extraction

Last updated: 2026-04-09
